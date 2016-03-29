/**
 * Copyright 2014 Nikita Koksharov, Nickolay Borbit
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
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
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

    protected static final int MAX_SLOT = 16384;

    protected final ClusterSlotRange singleSlotRange = new ClusterSlotRange(0, MAX_SLOT);

    private final Logger log = LoggerFactory.getLogger(getClass());

    private HashedWheelTimer timer;

    protected Codec codec;

    protected EventLoopGroup group;

    protected ConnectionInitializer connectListener = new DefaultConnectionListener();

    protected Class<? extends SocketChannel> socketChannelClass;

    protected final ConcurrentMap<String, PubSubConnectionEntry> name2PubSubConnection = PlatformDependent.newConcurrentHashMap();

    protected MasterSlaveServersConfig config;

    protected boolean isClusterMode;

    protected final Map<ClusterSlotRange, MasterSlaveEntry> entries = PlatformDependent.newConcurrentHashMap();

    private final Promise<Boolean> shutdownPromise;
    
    private final InfinitySemaphoreLatch shutdownLatch = new InfinitySemaphoreLatch();

    private final Set<RedisClientEntry> clients = Collections.newSetFromMap(PlatformDependent.<RedisClientEntry, Boolean>newConcurrentHashMap());

    private IdleConnectionWatcher connectionWatcher;

    private final ConnectionEventsHub connectionEventsHub = new ConnectionEventsHub();

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
        this.isClusterMode = cfg.isClusterConfig();
    }

    public boolean isClusterMode() {
        return isClusterMode;
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

    @Override
    public Map<ClusterSlotRange, MasterSlaveEntry> getEntries() {
        return entries;
    }

    protected void init(MasterSlaveServersConfig config) {
        this.config = config;

        int[] timeouts = new int[] {config.getRetryInterval(), config.getTimeout(), config.getReconnectionTimeout()};
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
        addEntry(singleSlotRange, entry);
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
        RedisClient client = createClient(host, port, config.getConnectTimeout());
        clients.add(new RedisClientEntry(client, this, type));
        return client;
    }

    public void shutdownAsync(RedisClient client) {
        clients.remove(new RedisClientEntry(client, this, null));
        client.shutdownAsync();
    }

    @Override
    public RedisClient createClient(String host, int port, int timeout) {
        return new RedisClient(group, socketChannelClass, host, port, timeout);
    }

    @Override
    public int calcSlot(String key) {
        return 0;
    }

    @Override
    public PubSubConnectionEntry getPubSubEntry(String channelName) {
        return name2PubSubConnection.get(channelName);
    }

    @Override
    public Future<PubSubConnectionEntry> psubscribe(String channelName, Codec codec) {
        Promise<PubSubConnectionEntry> promise = newPromise();
        psubscribe(channelName, codec, promise);
        return promise;
    }

    private void psubscribe(final String channelName, final Codec codec, final Promise<PubSubConnectionEntry> promise) {
        // multiple channel names per PubSubConnections are allowed
        PubSubConnectionEntry сonnEntry = name2PubSubConnection.get(channelName);
        if (сonnEntry != null) {
            promise.setSuccess(сonnEntry);
            return;
        }

        Set<PubSubConnectionEntry> entries = new HashSet<PubSubConnectionEntry>(name2PubSubConnection.values());
        for (PubSubConnectionEntry entry : entries) {
            if (entry.tryAcquire()) {
                PubSubConnectionEntry oldEntry = name2PubSubConnection.putIfAbsent(channelName, entry);
                if (oldEntry != null) {
                    entry.release();
                    promise.setSuccess(oldEntry);
                    return;
                }

                synchronized (entry) {
                    if (!entry.isActive()) {
                        entry.release();
                        psubscribe(channelName, codec, promise);
                        return;
                    }
                    entry.psubscribe(codec, channelName);
                    promise.setSuccess(entry);
                    return;
                }
            }
        }

        final int slot = 0;
        Future<RedisPubSubConnection> connFuture = nextPubSubConnection(slot);
        connFuture.addListener(new FutureListener<RedisPubSubConnection>() {
            @Override
            public void operationComplete(Future<RedisPubSubConnection> future) throws Exception {
                if (!future.isSuccess()) {
                    promise.setFailure(future.cause());
                    return;
                }

                RedisPubSubConnection conn = future.getNow();

                PubSubConnectionEntry entry = new PubSubConnectionEntry(conn, config.getSubscriptionsPerConnection());
                entry.tryAcquire();
                PubSubConnectionEntry oldEntry = name2PubSubConnection.putIfAbsent(channelName, entry);
                if (oldEntry != null) {
                    releaseSubscribeConnection(slot, entry);
                    promise.setSuccess(oldEntry);
                    return;
                }

                synchronized (entry) {
                    if (!entry.isActive()) {
                        entry.release();
                        psubscribe(channelName, codec, promise);
                        return;
                    }
                    entry.psubscribe(codec, channelName);
                    promise.setSuccess(entry);
                }
            }
        });
    }

    public Promise<PubSubConnectionEntry> subscribe(final Codec codec, final String channelName, final RedisPubSubListener<?> listener) {
        Promise<PubSubConnectionEntry> promise = newPromise();
        subscribe(codec, channelName, listener, promise);
        return promise;
    }

    private void subscribe(final Codec codec, final String channelName, final RedisPubSubListener<?> listener, final Promise<PubSubConnectionEntry> promise) {
        PubSubConnectionEntry сonnEntry = name2PubSubConnection.get(channelName);
        if (сonnEntry != null) {
            synchronized (сonnEntry) {
                if (сonnEntry.isActive()) {
                    сonnEntry.addListener(channelName, listener);
                    promise.setSuccess(сonnEntry);
                    return;
                }
            }
            connect(codec, channelName, listener, promise);
            return;
        }

        Set<PubSubConnectionEntry> entries = new HashSet<PubSubConnectionEntry>(name2PubSubConnection.values());
        for (PubSubConnectionEntry entry : entries) {
            if (entry.tryAcquire()) {
                PubSubConnectionEntry oldEntry = name2PubSubConnection.putIfAbsent(channelName, entry);
                if (oldEntry != null) {
                    entry.release();
                    synchronized (oldEntry) {
                        if (oldEntry.isActive()) {
                            oldEntry.addListener(channelName, listener);
                            promise.setSuccess(oldEntry);
                            return;
                        }
                    }
                    subscribe(codec, channelName, listener, promise);
                    return;
                }
                synchronized (entry) {
                    if (!entry.isActive()) {
                        entry.release();
                        subscribe(codec, channelName, listener, promise);
                        return;
                    }
                    entry.subscribe(codec, listener, channelName);
                    promise.setSuccess(entry);
                    return;
                }
            }
        }

        connect(codec, channelName, listener, promise);
    }

    private void connect(final Codec codec, final String channelName, final RedisPubSubListener<?> listener,
            final Promise<PubSubConnectionEntry> promise) {
        final int slot = 0;
        Future<RedisPubSubConnection> connFuture = nextPubSubConnection(slot);
        connFuture.addListener(new FutureListener<RedisPubSubConnection>() {

            @Override
            public void operationComplete(Future<RedisPubSubConnection> future) throws Exception {
                if (!future.isSuccess()) {
                    promise.setFailure(future.cause());
                    return;
                }

                RedisPubSubConnection conn = future.getNow();
                PubSubConnectionEntry entry = new PubSubConnectionEntry(conn, config.getSubscriptionsPerConnection());
                entry.tryAcquire();
                PubSubConnectionEntry oldEntry = name2PubSubConnection.putIfAbsent(channelName, entry);
                if (oldEntry != null) {
                    releaseSubscribeConnection(slot, entry);

                    synchronized (oldEntry) {
                        if (oldEntry.isActive()) {
                            oldEntry.addListener(channelName, listener);
                            promise.setSuccess(oldEntry);
                            return;
                        }
                    }
                    subscribe(codec, channelName, listener, promise);
                    return;
                }
                synchronized (entry) {
                    if (!entry.isActive()) {
                        entry.release();
                        subscribe(codec, channelName, listener, promise);
                        return;
                    }
                    entry.subscribe(codec, listener, channelName);
                    promise.setSuccess(entry);
                    return;
                }
            }
        });
    }

    @Override
    public Codec unsubscribe(final String channelName) {
        final PubSubConnectionEntry entry = name2PubSubConnection.remove(channelName);
        if (entry == null) {
            return null;
        }

        Codec entryCodec = entry.getConnection().getChannels().get(channelName);
        entry.unsubscribe(channelName, new BaseRedisPubSubListener() {

            @Override
            public boolean onStatus(PubSubType type, String channel) {
                if (type == PubSubType.UNSUBSCRIBE && channel.equals(channelName)) {
                    synchronized (entry) {
                        if (entry.tryClose()) {
                            releaseSubscribeConnection(0, entry);
                        }
                    }
                    return true;
                }
                return false;
            }

        });
        return entryCodec;
    }

    @Override
    public Codec punsubscribe(final String channelName) {
        final PubSubConnectionEntry entry = name2PubSubConnection.remove(channelName);
        if (entry == null) {
            return null;
        }

        Codec entryCodec = entry.getConnection().getPatternChannels().get(channelName);
        entry.punsubscribe(channelName, new BaseRedisPubSubListener() {

            @Override
            public boolean onStatus(PubSubType type, String channel) {
                if (type == PubSubType.PUNSUBSCRIBE && channel.equals(channelName)) {
                    synchronized (entry) {
                        if (entry.tryClose()) {
                            releaseSubscribeConnection(0, entry);
                        }
                    }
                    return true;
                }
                return false;
            }

        });
        return entryCodec;
    }

    @Override
    public MasterSlaveEntry getEntry(InetSocketAddress addr) {
        // TODO optimize
        for (Entry<ClusterSlotRange, MasterSlaveEntry> entry : entries.entrySet()) {
            if (entry.getValue().getClient().getAddr().equals(addr)) {
                return entry.getValue();
            }
        }
        return null;
    }

    protected MasterSlaveEntry getEntry(ClusterSlotRange slotRange) {
        return entries.get(slotRange);
    }

    protected MasterSlaveEntry getEntry(int slot) {
        // TODO optimize
        for (Entry<ClusterSlotRange, MasterSlaveEntry> entry : entries.entrySet()) {
            if (entry.getKey().isOwn(slot)) {
                return entry.getValue();
            }
        }
        return null;
    }

    protected void slaveDown(ClusterSlotRange slotRange, String host, int port, FreezeReason freezeReason) {
        getEntry(slotRange).slaveDown(host, port, freezeReason);
    }

    protected void changeMaster(ClusterSlotRange slotRange, String host, int port) {
        getEntry(slotRange).changeMaster(host, port);
    }

    protected void addEntry(ClusterSlotRange slotRange, MasterSlaveEntry entry) {
        entries.put(slotRange, entry);
    }

    protected MasterSlaveEntry removeMaster(ClusterSlotRange slotRange) {
        return entries.remove(slotRange);
    }

    @Override
    public Future<RedisConnection> connectionWriteOp(NodeSource source, RedisCommand<?> command) {
        MasterSlaveEntry e = getEntry(source, command);
        return e.connectionWriteOp();
    }

    private MasterSlaveEntry getEntry(NodeSource source) {
        MasterSlaveEntry e = getEntry(source.getSlot());
        if (e == null) {
            throw new RedisNodeNotFoundException("No node with slot: " + source.getSlot());
        }
        return e;
    }

    private MasterSlaveEntry getEntry(NodeSource source, RedisCommand<?> command) {
        MasterSlaveEntry e = getEntry(source.getSlot());
        if (e == null) {
            throw new RedisNodeNotFoundException("No node for slot: " + source.getSlot() + " and command " + command);
        }
        return e;
    }

    @Override
    public Future<RedisConnection> connectionReadOp(NodeSource source, RedisCommand<?> command) {
        MasterSlaveEntry e = getEntry(source, command);
        if (source.getAddr() != null) {
            return e.connectionReadOp(source.getAddr());
        }
        return e.connectionReadOp();
    }

    Future<RedisPubSubConnection> nextPubSubConnection(int slot) {
        return getEntry(slot).nextPubSubConnection();
    }

    protected void releaseSubscribeConnection(int slot, PubSubConnectionEntry entry) {
        this.getEntry(slot).returnPubSubConnection(entry);
    }

    @Override
    public void releaseWrite(NodeSource source, RedisConnection connection) {
        getEntry(source).releaseWrite(connection);
    }

    @Override
    public void releaseRead(NodeSource source, RedisConnection connection) {
        getEntry(source).releaseRead(connection);
    }

    @Override
    public void shutdown() {
        shutdownLatch.close();
        shutdownPromise.trySuccess(true);
        shutdownLatch.awaitUninterruptibly();
        
        for (MasterSlaveEntry entry : entries.values()) {
            entry.shutdown();
        }
        timer.stop();
        group.shutdownGracefully().syncUninterruptibly();
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
}
