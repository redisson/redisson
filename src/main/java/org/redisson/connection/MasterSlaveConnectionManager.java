/**
 * Copyright 2016 Nikita Koksharov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.redisson.connection;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.redisson.BaseMasterSlaveServersConfig;
import org.redisson.Config;
import org.redisson.MasterSlaveServersConfig;
import org.redisson.ReadMode;
import org.redisson.Version;
import org.redisson.client.BaseRedisPubSubListener;
import org.redisson.client.RedisClient;
import org.redisson.client.RedisConnection;
import org.redisson.client.RedisNodeNotFoundException;
import org.redisson.client.RedisPubSubConnection;
import org.redisson.client.RedisPubSubListener;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.pubsub.PubSubType;
import org.redisson.cluster.ClusterSlotRange;
import org.redisson.connection.ClientConnectionsEntry.FreezeReason;
import org.redisson.core.NodeType;
import org.redisson.misc.InfinitySemaphoreLatch;
import org.redisson.pubsub.AsyncSemaphore;
import org.redisson.pubsub.TransferListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.HashedWheelTimer;
import io.netty.util.Timeout;
import io.netty.util.Timer;
import io.netty.util.TimerTask;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.ImmediateEventExecutor;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.PlatformDependent;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class MasterSlaveConnectionManager implements ConnectionManager {

    private final Timeout dummyTimeout = new Timeout() {
        @Override
        public Timer timer() {
            return null;
        }

        @Override
        public TimerTask task() {
            return null;
        }

        @Override
        public boolean isExpired() {
            return false;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public boolean cancel() {
            return false;
        }
    };

    public static final int MAX_SLOT = 16384;

    protected final ClusterSlotRange singleSlotRange = new ClusterSlotRange(0, MAX_SLOT-1);

    private final Logger log = LoggerFactory.getLogger(getClass());

    private HashedWheelTimer timer;

    protected Codec codec;

    protected EventLoopGroup group;

    protected ConnectionInitializer connectListener = new DefaultConnectionListener();

    protected Class<? extends SocketChannel> socketChannelClass;

    protected final ConcurrentMap<String, PubSubConnectionEntry> name2PubSubConnection = PlatformDependent.newConcurrentHashMap();
    
    protected final Queue<PubSubConnectionEntry> freePubSubConnections = new ConcurrentLinkedQueue<PubSubConnectionEntry>();

    protected MasterSlaveServersConfig config;

    private final Map<Integer, MasterSlaveEntry> entries = PlatformDependent.newConcurrentHashMap();

    private final Promise<Boolean> shutdownPromise;

    private final InfinitySemaphoreLatch shutdownLatch = new InfinitySemaphoreLatch();

    private final Set<RedisClientEntry> clients = Collections.newSetFromMap(PlatformDependent.<RedisClientEntry, Boolean>newConcurrentHashMap());

    private IdleConnectionWatcher connectionWatcher;

    private final ConnectionEventsHub connectionEventsHub = new ConnectionEventsHub();
    
    private final AsyncSemaphore[] locks = new AsyncSemaphore[50];
    
    private final Semaphore freePubSubLock = new Semaphore(1);
    
    {
        for (int i = 0; i < locks.length; i++) {
            locks[i] = new AsyncSemaphore(1);
        }
    }

    public MasterSlaveConnectionManager(MasterSlaveServersConfig cfg, Config config) {
        this(config);
        init(cfg);
    }

    public MasterSlaveConnectionManager(Config cfg) {
        Version.logVersion();

        if (cfg.isUseLinuxNativeEpoll()) {
            if (cfg.getEventLoopGroup() == null) {
                this.group = new EpollEventLoopGroup(cfg.getThreads());
            } else {
                this.group = cfg.getEventLoopGroup();
            }

            this.socketChannelClass = EpollSocketChannel.class;
        } else {
            if (cfg.getEventLoopGroup() == null) {
                this.group = new NioEventLoopGroup(cfg.getThreads());
            } else {
                this.group = cfg.getEventLoopGroup();
            }

            this.socketChannelClass = NioSocketChannel.class;
        }
        this.codec = cfg.getCodec();
        this.shutdownPromise = newPromise();
    }

    public boolean isClusterMode() {
        return false;
    }

    public IdleConnectionWatcher getConnectionWatcher() {
        return connectionWatcher;
    }

    @Override
    public MasterSlaveServersConfig getConfig() {
        return config;
    }

    @Override
    public Codec getCodec() {
        return codec;
    }

    public Set<MasterSlaveEntry> getEntrySet() {
        return new HashSet<MasterSlaveEntry>(entries.values());
    }
    
    protected void init(MasterSlaveServersConfig config) {
        this.config = config;

        int[] timeouts = new int[]{config.getRetryInterval(), config.getTimeout(), config.getReconnectionTimeout()};
        Arrays.sort(timeouts);
        int minTimeout = timeouts[0];
        if (minTimeout % 100 != 0) {
            minTimeout = (minTimeout % 100) / 2;
        } else if (minTimeout == 100) {
            minTimeout = 50;
        } else {
            minTimeout = 100;
        }
        timer = new HashedWheelTimer(minTimeout, TimeUnit.MILLISECONDS);

        connectionWatcher = new IdleConnectionWatcher(this, config);

        try {
            initEntry(config);
        } catch (RuntimeException e) {
            stopThreads();
            throw e;
        }
    }

    public ConnectionInitializer getConnectListener() {
        return connectListener;
    }

    protected void initEntry(MasterSlaveServersConfig config) {
        HashSet<ClusterSlotRange> slots = new HashSet<ClusterSlotRange>();
        slots.add(singleSlotRange);

        MasterSlaveEntry entry;
        if (config.getReadMode() == ReadMode.MASTER) {
            entry = new SingleEntry(slots, this, config);
            Future<Void> f = entry.setupMasterEntry(config.getMasterAddress().getHost(), config.getMasterAddress().getPort());
            f.syncUninterruptibly();
        } else {
            entry = createMasterSlaveEntry(config, slots);
        }
        
        for (int slot = singleSlotRange.getStartSlot(); slot < singleSlotRange.getEndSlot() + 1; slot++) {
            addEntry(slot, entry);
        }
    }

    protected MasterSlaveEntry createMasterSlaveEntry(MasterSlaveServersConfig config,
            HashSet<ClusterSlotRange> slots) {
        MasterSlaveEntry entry = new MasterSlaveEntry(slots, this, config);
        List<Future<Void>> fs = entry.initSlaveBalancer(java.util.Collections.<URI>emptySet());
        for (Future<Void> future : fs) {
            future.syncUninterruptibly();
        }
        Future<Void> f = entry.setupMasterEntry(config.getMasterAddress().getHost(), config.getMasterAddress().getPort());
        f.syncUninterruptibly();
        return entry;
    }

    protected MasterSlaveServersConfig create(BaseMasterSlaveServersConfig<?> cfg) {
        MasterSlaveServersConfig c = new MasterSlaveServersConfig();
        c.setRetryInterval(cfg.getRetryInterval());
        c.setRetryAttempts(cfg.getRetryAttempts());
        c.setTimeout(cfg.getTimeout());
        c.setPingTimeout(cfg.getPingTimeout());
        c.setLoadBalancer(cfg.getLoadBalancer());
        c.setPassword(cfg.getPassword());
        c.setClientName(cfg.getClientName());
        c.setMasterConnectionPoolSize(cfg.getMasterConnectionPoolSize());
        c.setSlaveConnectionPoolSize(cfg.getSlaveConnectionPoolSize());
        c.setSlaveSubscriptionConnectionPoolSize(cfg.getSlaveSubscriptionConnectionPoolSize());
        c.setSubscriptionsPerConnection(cfg.getSubscriptionsPerConnection());
        c.setConnectTimeout(cfg.getConnectTimeout());
        c.setIdleConnectionTimeout(cfg.getIdleConnectionTimeout());

        c.setFailedAttempts(cfg.getFailedAttempts());
        c.setReconnectionTimeout(cfg.getReconnectionTimeout());
        c.setMasterConnectionMinimumIdleSize(cfg.getMasterConnectionMinimumIdleSize());
        c.setSlaveConnectionMinimumIdleSize(cfg.getSlaveConnectionMinimumIdleSize());
        c.setSlaveSubscriptionConnectionMinimumIdleSize(cfg.getSlaveSubscriptionConnectionMinimumIdleSize());
        c.setReadMode(cfg.getReadMode());

        return c;
    }

    @Override
    public RedisClient createClient(NodeType type, String host, int port) {
        RedisClient client = createClient(host, port, config.getConnectTimeout(), config.getRetryInterval() * config.getRetryAttempts());
        clients.add(new RedisClientEntry(client, this, type));
        return client;
    }

    public void shutdownAsync(RedisClient client) {
        clients.remove(new RedisClientEntry(client, this, null));
        client.shutdownAsync();
    }

    @Override
    public RedisClient createClient(String host, int port, int timeout, int commandTimeout) {
        return new RedisClient(group, socketChannelClass, host, port, timeout, commandTimeout);
    }

    @Override
    public int calcSlot(String key) {
        return singleSlotRange.getStartSlot();
    }

    @Override
    public PubSubConnectionEntry getPubSubEntry(String channelName) {
        return name2PubSubConnection.get(channelName);
    }

    @Override
    public Future<PubSubConnectionEntry> psubscribe(final String channelName, final Codec codec, final RedisPubSubListener<?> listener) {
        final AsyncSemaphore lock = locks[Math.abs(channelName.hashCode() % locks.length)];
        final Promise<PubSubConnectionEntry> result = newPromise();
        lock.acquire(new Runnable() {
            @Override
            public void run() {
                Future<PubSubConnectionEntry> future = psubscribe(channelName, codec, listener, lock);
                future.addListener(new TransferListener<PubSubConnectionEntry>(result));
            }
        });
        return result;
    }
    
    public Future<PubSubConnectionEntry> psubscribe(String channelName, Codec codec, RedisPubSubListener<?> listener, AsyncSemaphore semaphore) {
        Promise<PubSubConnectionEntry> promise = newPromise();
        subscribe(codec, channelName, listener, promise, PubSubType.PSUBSCRIBE, semaphore);
        return promise;
    }

    public Future<PubSubConnectionEntry> subscribe(final Codec codec, final String channelName, final RedisPubSubListener<?> listener) {
        final AsyncSemaphore lock = locks[Math.abs(channelName.hashCode() % locks.length)];
        final Promise<PubSubConnectionEntry> result = newPromise();
        lock.acquire(new Runnable() {
            @Override
            public void run() {
                Future<PubSubConnectionEntry> future = subscribe(codec, channelName, listener, lock);
                future.addListener(new TransferListener<PubSubConnectionEntry>(result));
            }
        });
        return result;
    }
    
    public Future<PubSubConnectionEntry> subscribe(Codec codec, String channelName, RedisPubSubListener<?> listener, AsyncSemaphore semaphore) {
        Promise<PubSubConnectionEntry> promise = newPromise();
        subscribe(codec, channelName, listener, promise, PubSubType.SUBSCRIBE, semaphore);
        return promise;
    }

    public AsyncSemaphore getSemaphore(String channelName) {
        return locks[Math.abs(channelName.hashCode() % locks.length)];
    }
    
    private void subscribe(final Codec codec, final String channelName, final RedisPubSubListener<?> listener, 
            final Promise<PubSubConnectionEntry> promise, PubSubType type, final AsyncSemaphore lock) {
        final PubSubConnectionEntry сonnEntry = name2PubSubConnection.get(channelName);
        if (сonnEntry != null) {
            сonnEntry.addListener(channelName, listener);
            сonnEntry.getSubscribeFuture(channelName, type).addListener(new FutureListener<Void>() {
                @Override
                public void operationComplete(Future<Void> future) throws Exception {
                    lock.release();
                    promise.trySuccess(сonnEntry);
                }
            });
            return;
        }

        freePubSubLock.acquireUninterruptibly();
        final PubSubConnectionEntry freeEntry = freePubSubConnections.peek();
        if (freeEntry == null) {
            connect(codec, channelName, listener, promise, type, lock);
            return;
        }
        
        int remainFreeAmount = freeEntry.tryAcquire();
        if (remainFreeAmount == -1) {
            throw new IllegalStateException();
        }
        
        final PubSubConnectionEntry oldEntry = name2PubSubConnection.putIfAbsent(channelName, freeEntry);
        if (oldEntry != null) {
            freeEntry.release();
            freePubSubLock.release();
            
            oldEntry.addListener(channelName, listener);
            oldEntry.getSubscribeFuture(channelName, type).addListener(new FutureListener<Void>() {
                @Override
                public void operationComplete(Future<Void> future) throws Exception {
                    lock.release();
                    promise.trySuccess(oldEntry);
                }
            });
            return;
        }
        
        if (remainFreeAmount == 0) {
            freePubSubConnections.poll();
        }
        freePubSubLock.release();
        
        freeEntry.addListener(channelName, listener);
        freeEntry.getSubscribeFuture(channelName, type).addListener(new FutureListener<Void>() {
            @Override
            public void operationComplete(Future<Void> future) throws Exception {
                lock.release();
                promise.trySuccess(freeEntry);
            }
        });
        
        
        if (PubSubType.PSUBSCRIBE == type) {
            freeEntry.psubscribe(codec, channelName);
        } else {
            freeEntry.subscribe(codec, channelName);
        }
    }

    private void connect(final Codec codec, final String channelName, final RedisPubSubListener<?> listener,
            final Promise<PubSubConnectionEntry> promise, final PubSubType type, final AsyncSemaphore lock) {
        final int slot = calcSlot(channelName);
        Future<RedisPubSubConnection> connFuture = nextPubSubConnection(slot);
        connFuture.addListener(new FutureListener<RedisPubSubConnection>() {

            @Override
            public void operationComplete(Future<RedisPubSubConnection> future) throws Exception {
                if (!future.isSuccess()) {
                    freePubSubLock.release();
                    lock.release();
                    promise.tryFailure(future.cause());
                    return;
                }

                RedisPubSubConnection conn = future.getNow();
                
                final PubSubConnectionEntry entry = new PubSubConnectionEntry(conn, config.getSubscriptionsPerConnection());
                entry.tryAcquire();
                
                final PubSubConnectionEntry oldEntry = name2PubSubConnection.putIfAbsent(channelName, entry);
                if (oldEntry != null) {
                    releaseSubscribeConnection(slot, entry);
                    
                    freePubSubLock.release();
                    
                    oldEntry.addListener(channelName, listener);
                    oldEntry.getSubscribeFuture(channelName, type).addListener(new FutureListener<Void>() {
                        @Override
                        public void operationComplete(Future<Void> future) throws Exception {
                            lock.release();
                            promise.trySuccess(oldEntry);
                        }
                    });
                    return;
                }
                
                freePubSubConnections.add(entry);
                freePubSubLock.release();
                
                entry.addListener(channelName, listener);
                entry.getSubscribeFuture(channelName, type).addListener(new FutureListener<Void>() {
                    @Override
                    public void operationComplete(Future<Void> future) throws Exception {
                        lock.release();
                        promise.trySuccess(entry);
                    }
                });
                
                if (PubSubType.PSUBSCRIBE == type) {
                    entry.psubscribe(codec, channelName);
                } else {
                    entry.subscribe(codec, channelName);
                }
                
            }
        });
    }

    public Codec unsubscribe(final String channelName, final AsyncSemaphore lock) {
        final PubSubConnectionEntry entry = name2PubSubConnection.remove(channelName);
        if (entry == null) {
            lock.release();
            return null;
        }
        
        Codec entryCodec = entry.getConnection().getChannels().get(channelName);
        entry.unsubscribe(channelName, new BaseRedisPubSubListener() {
            
            @Override
            public boolean onStatus(PubSubType type, String channel) {
                if (type == PubSubType.UNSUBSCRIBE && channel.equals(channelName)) {
                    
                    if (entry.release() == 1) {
                        freePubSubConnections.add(entry);
                    }
                    
                    lock.release();
                    return true;
                }
                return false;
            }
            
        });
        
        return entryCodec;
    }
    
    @Override
    public Future<Codec> unsubscribe(final String channelName, boolean temporaryDown) {
        final PubSubConnectionEntry entry = name2PubSubConnection.remove(channelName);
        if (entry == null) {
            return null;
        }
        freePubSubConnections.remove(entry);
        
        final Codec entryCodec = entry.getConnection().getChannels().get(channelName);
        if (temporaryDown) {
            final Promise<Codec> result = newPromise();
            entry.unsubscribe(channelName, new BaseRedisPubSubListener() {
                
                @Override
                public boolean onStatus(PubSubType type, String channel) {
                    if (type == PubSubType.UNSUBSCRIBE && channel.equals(channelName)) {
                        result.trySuccess(entryCodec);
                        return true;
                    }
                    return false;
                }
                
            });
            return result;
        }
        entry.unsubscribe(channelName, null);
        return newSucceededFuture(entryCodec);    
    }
    
    public Codec punsubscribe(final String channelName, final AsyncSemaphore lock) {
        final PubSubConnectionEntry entry = name2PubSubConnection.remove(channelName);
        if (entry == null) {
            lock.release();
            return null;
        }
        
        Codec entryCodec = entry.getConnection().getPatternChannels().get(channelName);
        entry.punsubscribe(channelName, new BaseRedisPubSubListener() {
            
            @Override
            public boolean onStatus(PubSubType type, String channel) {
                if (type == PubSubType.PUNSUBSCRIBE && channel.equals(channelName)) {
                    
                    if (entry.release() == 1) {
                        freePubSubConnections.add(entry);
                    }
                    
                    lock.release();
                    return true;
                }
                return false;
            }
            
        });
        
        return entryCodec;
    }

    
    @Override
    public Future<Codec> punsubscribe(final String channelName, boolean temporaryDown) {
        final PubSubConnectionEntry entry = name2PubSubConnection.remove(channelName);
        if (entry == null) {
            return null;
        }
        freePubSubConnections.remove(entry);
        
        final Codec entryCodec = entry.getConnection().getChannels().get(channelName);
        if (temporaryDown) {
            final Promise<Codec> result = newPromise();
            entry.punsubscribe(channelName, new BaseRedisPubSubListener() {
                
                @Override
                public boolean onStatus(PubSubType type, String channel) {
                    if (type == PubSubType.PUNSUBSCRIBE && channel.equals(channelName)) {
                        result.trySuccess(entryCodec);
                        return true;
                    }
                    return false;
                }
                
            });
            return result;
        }
        entry.punsubscribe(channelName, null);
        return newSucceededFuture(entryCodec);
    }

    @Override
    public MasterSlaveEntry getEntry(InetSocketAddress addr) {
        // TODO optimize
        for (Entry<Integer, MasterSlaveEntry> entry : entries.entrySet()) {
            if (entry.getValue().getClient().getAddr().equals(addr)) {
                return entry.getValue();
            }
        }
        return null;
    }

    public MasterSlaveEntry getEntry(int slot) {
        return entries.get(slot);
    }
    
    protected void slaveDown(ClusterSlotRange slotRange, String host, int port, FreezeReason freezeReason) {
        getEntry(slotRange.getStartSlot()).slaveDown(host, port, freezeReason);
    }

    protected void changeMaster(int slot, String host, int port) {
        getEntry(slot).changeMaster(host, port);
    }

    protected void addEntry(Integer slot, MasterSlaveEntry entry) {
        entries.put(slot, entry);
    }

    protected MasterSlaveEntry removeMaster(Integer slot) {
        return entries.remove(slot);
    }

    @Override
    public Future<RedisConnection> connectionWriteOp(NodeSource source, RedisCommand<?> command) {
        MasterSlaveEntry entry = source.getEntry();
        if (entry == null) {
            entry = getEntry(source);
        }
        return entry.connectionWriteOp();
    }

    private MasterSlaveEntry getEntry(NodeSource source) {
        // workaround for slots in migration state
        if (source.getRedirect() != null) {
            MasterSlaveEntry e = getEntry(source.getAddr());
            if (e == null) {
                throw new RedisNodeNotFoundException("No node for slot: " + source.getAddr());
            }
            return e;
        }
        
        MasterSlaveEntry e = getEntry(source.getSlot());
        if (e == null) {
            throw new RedisNodeNotFoundException("No node with slot: " + source.getSlot());
        }
        return e;
    }

    @Override
    public Future<RedisConnection> connectionReadOp(NodeSource source, RedisCommand<?> command) {
        MasterSlaveEntry entry = source.getEntry();
        if (entry == null && source.getSlot() != null) {
            entry = getEntry(source.getSlot());
        }
        if (source.getAddr() != null) {
            return entry.connectionReadOp(source.getAddr());
        }
        return entry.connectionReadOp();
    }

    Future<RedisPubSubConnection> nextPubSubConnection(int slot) {
        return getEntry(slot).nextPubSubConnection();
    }

    protected void releaseSubscribeConnection(int slot, PubSubConnectionEntry entry) {
        this.getEntry(slot).returnPubSubConnection(entry);
    }

    @Override
    public void releaseWrite(NodeSource source, RedisConnection connection) {
        MasterSlaveEntry entry = source.getEntry();
        if (entry == null) {
            entry = getEntry(source);
        }
        entry.releaseWrite(connection);
    }

    @Override
    public void releaseRead(NodeSource source, RedisConnection connection) {
        MasterSlaveEntry entry = source.getEntry();
        if (entry == null) {
            entry = getEntry(source);
        }
        entry.releaseRead(connection);
    }

    @Override
    public void shutdown() {
        shutdown(2, 15, TimeUnit.SECONDS);//default netty value
    }

    @Override
    public void shutdown(long quietPeriod, long timeout, TimeUnit unit) {
        shutdownLatch.close();
        shutdownPromise.trySuccess(true);
        shutdownLatch.awaitUninterruptibly();

        for (MasterSlaveEntry entry : entries.values()) {
            entry.shutdown();
        }
        timer.stop();
        group.shutdownGracefully(quietPeriod, timeout, unit).syncUninterruptibly();
    }

    @Override
    public boolean isShuttingDown() {
        return shutdownLatch.isClosed();
    }

    @Override
    public boolean isShutdown() {
        return group.isTerminated();
    }

    @Override
    public Collection<RedisClientEntry> getClients() {
        return Collections.unmodifiableCollection(clients);
    }

    @Override
    public <R> Promise<R> newPromise() {
        return ImmediateEventExecutor.INSTANCE.newPromise();
    }

    @Override
    public <R> Future<R> newSucceededFuture(R value) {
        return ImmediateEventExecutor.INSTANCE.newSucceededFuture(value);
    }

    @Override
    public <R> Future<R> newFailedFuture(Throwable cause) {
        return ImmediateEventExecutor.INSTANCE.newFailedFuture(cause);
    }

    @Override
    public EventLoopGroup getGroup() {
        return group;
    }

    @Override
    public Timeout newTimeout(TimerTask task, long delay, TimeUnit unit) {
        try {
            return timer.newTimeout(task, delay, unit);
        } catch (IllegalStateException e) {
            // timer is shutdown
            return dummyTimeout;
        }
    }

    @Override
    public InfinitySemaphoreLatch getShutdownLatch() {
        return shutdownLatch;
    }

    @Override
    public Future<Boolean> getShutdownPromise() {
        return shutdownPromise;
    }

    @Override
    public ConnectionEventsHub getConnectionEventsHub() {
        return connectionEventsHub;
    }

    protected void stopThreads() {
        timer.stop();
        try {
            group.shutdownGracefully().await();
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
    
    public URI getLastClusterNode() {
        return null;
    }
}
