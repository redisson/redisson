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

import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReferenceArray;

import org.redisson.Version;
import org.redisson.api.NodeType;
import org.redisson.api.RFuture;
import org.redisson.client.BaseRedisPubSubListener;
import org.redisson.client.RedisClient;
import org.redisson.client.RedisClientConfig;
import org.redisson.client.RedisConnection;
import org.redisson.client.RedisNodeNotFoundException;
import org.redisson.client.RedisPubSubConnection;
import org.redisson.client.RedisPubSubListener;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.pubsub.PubSubType;
import org.redisson.cluster.ClusterSlotRange;
import org.redisson.command.CommandSyncService;
import org.redisson.config.BaseMasterSlaveServersConfig;
import org.redisson.config.Config;
import org.redisson.config.MasterSlaveServersConfig;
import org.redisson.config.TransportMode;
import org.redisson.misc.InfinitySemaphoreLatch;
import org.redisson.misc.RPromise;
import org.redisson.misc.RedissonPromise;
import org.redisson.misc.TransferListener;
import org.redisson.misc.URIBuilder;
import org.redisson.pubsub.AsyncSemaphore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.EpollDatagramChannel;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.kqueue.KQueueDatagramChannel;
import io.netty.channel.kqueue.KQueueEventLoopGroup;
import io.netty.channel.kqueue.KQueueSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.resolver.dns.DnsAddressResolverGroup;
import io.netty.resolver.dns.DnsServerAddressStreamProviders;
import io.netty.util.HashedWheelTimer;
import io.netty.util.Timeout;
import io.netty.util.Timer;
import io.netty.util.TimerTask;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
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

    protected final EventLoopGroup group;

    protected final Class<? extends SocketChannel> socketChannelClass;

    protected final ConcurrentMap<String, PubSubConnectionEntry> name2PubSubConnection = PlatformDependent.newConcurrentHashMap();
    
    protected final Queue<PubSubConnectionEntry> freePubSubConnections = new ConcurrentLinkedQueue<PubSubConnectionEntry>();

    protected DNSMonitor dnsMonitor;
    
    protected MasterSlaveServersConfig config;

    private final AtomicReferenceArray<MasterSlaveEntry> slot2entry = new AtomicReferenceArray<MasterSlaveEntry>(MAX_SLOT);
    private final Map<RedisClient, MasterSlaveEntry> client2entry = PlatformDependent.newConcurrentHashMap();

    private final RPromise<Boolean> shutdownPromise;

    private final InfinitySemaphoreLatch shutdownLatch = new InfinitySemaphoreLatch();

    private final Map<RedisClient, RedisClientEntry> clientEntries = PlatformDependent.newConcurrentHashMap();

    private IdleConnectionWatcher connectionWatcher;

    private final ConnectionEventsHub connectionEventsHub = new ConnectionEventsHub();
    
    private final AsyncSemaphore[] locks = new AsyncSemaphore[50];
    
    private final ExecutorService executor; 
    
    private final AsyncSemaphore freePubSubLock = new AsyncSemaphore(1);
    
    private final CommandSyncService commandExecutor;
    
    private final Config cfg;

    protected final DnsAddressResolverGroup resolverGroup;
    
    {
        for (int i = 0; i < locks.length; i++) {
            locks[i] = new AsyncSemaphore(1);
        }
    }

    public MasterSlaveConnectionManager(MasterSlaveServersConfig cfg, Config config) {
        this(config);
        initTimer(cfg);
        this.config = cfg;
        initSingleEntry();
    }

    public MasterSlaveConnectionManager(Config cfg) {
        Version.logVersion();

        if (cfg.getTransportMode() == TransportMode.EPOLL) {
            if (cfg.getEventLoopGroup() == null) {
                this.group = new EpollEventLoopGroup(cfg.getNettyThreads(), new DefaultThreadFactory("redisson-netty"));
            } else {
                this.group = cfg.getEventLoopGroup();
            }

            this.socketChannelClass = EpollSocketChannel.class;
            this.resolverGroup = new DnsAddressResolverGroup(EpollDatagramChannel.class, DnsServerAddressStreamProviders.platformDefault());
        } else if (cfg.getTransportMode() == TransportMode.KQUEUE) {
            if (cfg.getEventLoopGroup() == null) {
                this.group = new KQueueEventLoopGroup(cfg.getNettyThreads(), new DefaultThreadFactory("redisson-netty"));
            } else {
                this.group = cfg.getEventLoopGroup();
            }

            this.socketChannelClass = KQueueSocketChannel.class;
            this.resolverGroup = new DnsAddressResolverGroup(KQueueDatagramChannel.class, DnsServerAddressStreamProviders.platformDefault());
        } else {
            if (cfg.getEventLoopGroup() == null) {
                this.group = new NioEventLoopGroup(cfg.getNettyThreads(), new DefaultThreadFactory("redisson-netty"));
            } else {
                this.group = cfg.getEventLoopGroup();
            }

            this.socketChannelClass = NioSocketChannel.class;
            this.resolverGroup = new DnsAddressResolverGroup(NioDatagramChannel.class, DnsServerAddressStreamProviders.platformDefault());
        }
        
        if (cfg.getExecutor() == null) {
            int threads = Runtime.getRuntime().availableProcessors() * 2;
            if (cfg.getThreads() != 0) {
                threads = cfg.getThreads();
            }
            executor = Executors.newFixedThreadPool(threads, new DefaultThreadFactory("redisson"));
        } else {
            executor = cfg.getExecutor();
        }

        this.cfg = cfg;
        this.codec = cfg.getCodec();
        this.shutdownPromise = newPromise();
        this.commandExecutor = new CommandSyncService(this);
    }

    public boolean isClusterMode() {
        return false;
    }
    
    public CommandSyncService getCommandExecutor() {
        return commandExecutor;
    }

    public IdleConnectionWatcher getConnectionWatcher() {
        return connectionWatcher;
    }

    public Config getCfg() {
        return cfg;
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
    public Collection<MasterSlaveEntry> getEntrySet() {
        return client2entry.values();
    }
    
    protected void initTimer(MasterSlaveServersConfig config) {
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
        
        timer = new HashedWheelTimer(Executors.defaultThreadFactory(), minTimeout, TimeUnit.MILLISECONDS, 1024);
        
        // to avoid assertion error during timer.stop invocation
        try {
            Field leakField = HashedWheelTimer.class.getDeclaredField("leak");
            leakField.setAccessible(true);
            leakField.set(timer, null);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        
        connectionWatcher = new IdleConnectionWatcher(this, config);
    }

    protected void initSingleEntry() {
        try {
            HashSet<ClusterSlotRange> slots = new HashSet<ClusterSlotRange>();
            slots.add(singleSlotRange);
    
            MasterSlaveEntry entry;
            if (config.checkSkipSlavesInit()) {
                entry = new SingleEntry(slots, this, config);
            } else {
                entry = createMasterSlaveEntry(config, slots);
            }
            RFuture<RedisClient> f = entry.setupMasterEntry(config.getMasterAddress());
            f.syncUninterruptibly();
            
            for (int slot = singleSlotRange.getStartSlot(); slot < singleSlotRange.getEndSlot() + 1; slot++) {
                addEntry(slot, entry);
            }
            
            if (config.getDnsMonitoringInterval() != -1) {
                dnsMonitor = new DNSMonitor(this, f.getNow(), 
                        config.getSlaveAddresses(), config.getDnsMonitoringInterval(), resolverGroup);
                dnsMonitor.start();
            }
        } catch (RuntimeException e) {
            stopThreads();
            throw e;
        }
    }
    
    protected MasterSlaveEntry createMasterSlaveEntry(MasterSlaveServersConfig config,
            HashSet<ClusterSlotRange> slots) {
        MasterSlaveEntry entry = new MasterSlaveEntry(slots, this, config);
        List<RFuture<Void>> fs = entry.initSlaveBalancer(java.util.Collections.<URI>emptySet());
        for (RFuture<Void> future : fs) {
            future.syncUninterruptibly();
        }
        return entry;
    }

    protected MasterSlaveServersConfig create(BaseMasterSlaveServersConfig<?> cfg) {
        MasterSlaveServersConfig c = new MasterSlaveServersConfig();
        
        c.setSslEnableEndpointIdentification(cfg.isSslEnableEndpointIdentification());
        c.setSslProvider(cfg.getSslProvider());
        c.setSslTruststore(cfg.getSslTruststore());
        c.setSslTruststorePassword(cfg.getSslTruststorePassword());
        c.setSslKeystore(cfg.getSslKeystore());
        c.setSslKeystorePassword(cfg.getSslKeystorePassword());
        
        c.setRetryInterval(cfg.getRetryInterval());
        c.setRetryAttempts(cfg.getRetryAttempts());
        c.setTimeout(cfg.getTimeout());
        c.setPingTimeout(cfg.getPingTimeout());
        c.setLoadBalancer(cfg.getLoadBalancer());
        c.setPassword(cfg.getPassword());
        c.setClientName(cfg.getClientName());
        c.setMasterConnectionPoolSize(cfg.getMasterConnectionPoolSize());
        c.setSlaveConnectionPoolSize(cfg.getSlaveConnectionPoolSize());
        c.setSubscriptionConnectionPoolSize(cfg.getSubscriptionConnectionPoolSize());
        c.setSubscriptionsPerConnection(cfg.getSubscriptionsPerConnection());
        c.setConnectTimeout(cfg.getConnectTimeout());
        c.setIdleConnectionTimeout(cfg.getIdleConnectionTimeout());

        c.setFailedAttempts(cfg.getFailedAttempts());
        c.setReconnectionTimeout(cfg.getReconnectionTimeout());
        c.setMasterConnectionMinimumIdleSize(cfg.getMasterConnectionMinimumIdleSize());
        c.setSlaveConnectionMinimumIdleSize(cfg.getSlaveConnectionMinimumIdleSize());
        c.setSubscriptionConnectionMinimumIdleSize(cfg.getSubscriptionConnectionMinimumIdleSize());
        c.setReadMode(cfg.getReadMode());
        c.setSubscriptionMode(cfg.getSubscriptionMode());
        c.setDnsMonitoringInterval(cfg.getDnsMonitoringInterval());

        return c;
    }

    @Override
    public RedisClient createClient(NodeType type, URI address) {
        RedisClient client = createClient(type, address, config.getConnectTimeout(), config.getRetryInterval() * config.getRetryAttempts());
        clientEntries.put(client, new RedisClientEntry(client, commandExecutor, type));
        return client;
    }
    
    @Override
    public RedisClient createClient(NodeType type, InetSocketAddress address, URI uri) {
        RedisClient client = createClient(type, address, uri, config.getConnectTimeout(), config.getRetryInterval() * config.getRetryAttempts());
        clientEntries.put(client, new RedisClientEntry(client, commandExecutor, type));
        return client;
    }

    @Override
    public void shutdownAsync(RedisClient client) {
        if (clientEntries.remove(client) == null) {
            log.error("Can't find client {}", client);
        }
        client.shutdownAsync();
    }

    @Override
    public RedisClient createClient(NodeType type, URI address, int timeout, int commandTimeout) {
        RedisClientConfig redisConfig = createRedisConfig(type, address, timeout, commandTimeout);
        return RedisClient.create(redisConfig);
    }
    
    private RedisClient createClient(NodeType type, InetSocketAddress address, URI uri, int timeout, int commandTimeout) {
        RedisClientConfig redisConfig = createRedisConfig(type, null, timeout, commandTimeout);
        redisConfig.setAddress(address, uri);
        return RedisClient.create(redisConfig);
    }


    protected RedisClientConfig createRedisConfig(NodeType type, URI address, int timeout, int commandTimeout) {
        RedisClientConfig redisConfig = new RedisClientConfig();
        redisConfig.setAddress(address)
              .setTimer(timer)
              .setExecutor(executor)
              .setResolverGroup(resolverGroup)
              .setGroup(group)
              .setSocketChannelClass(socketChannelClass)
              .setConnectTimeout(timeout)
              .setCommandTimeout(commandTimeout)
              .setSslEnableEndpointIdentification(config.isSslEnableEndpointIdentification())
              .setSslProvider(config.getSslProvider())
              .setSslTruststore(config.getSslTruststore())
              .setSslTruststorePassword(config.getSslTruststorePassword())
              .setSslKeystore(config.getSslKeystore())
              .setSslKeystorePassword(config.getSslKeystorePassword())
              .setClientName(config.getClientName())
              .setKeepPubSubOrder(cfg.isKeepPubSubOrder())
              .setPingConnectionInterval(config.getPingConnectionInterval())
              .setKeepAlive(config.isKeepAlive())
              .setTcpNoDelay(config.isTcpNoDelay());
        
        if (type != NodeType.SENTINEL) {
            redisConfig.setDatabase(config.getDatabase());
            redisConfig.setPassword(config.getPassword());
        }
        
        return redisConfig;
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
    public RFuture<PubSubConnectionEntry> psubscribe(final String channelName, final Codec codec, final RedisPubSubListener<?>... listeners) {
        final AsyncSemaphore lock = getSemaphore(channelName);
        final RPromise<PubSubConnectionEntry> result = newPromise();
        lock.acquire(new Runnable() {
            @Override
            public void run() {
                RFuture<PubSubConnectionEntry> future = psubscribe(channelName, codec, lock, listeners);
                future.addListener(new TransferListener<PubSubConnectionEntry>(result));
            }
        });
        return result;
    }
    
    public RFuture<PubSubConnectionEntry> psubscribe(String channelName, Codec codec, AsyncSemaphore semaphore, RedisPubSubListener<?>... listeners) {
        RPromise<PubSubConnectionEntry> promise = newPromise();
        subscribe(codec, channelName, promise, PubSubType.PSUBSCRIBE, semaphore, listeners);
        return promise;
    }

    public RFuture<PubSubConnectionEntry> subscribe(final Codec codec, final String channelName, final RedisPubSubListener<?>... listeners) {
        final AsyncSemaphore lock = getSemaphore(channelName);
        final RPromise<PubSubConnectionEntry> result = newPromise();
        lock.acquire(new Runnable() {
            @Override
            public void run() {
                RFuture<PubSubConnectionEntry> future = subscribe(codec, channelName, lock, listeners);
                future.addListener(new TransferListener<PubSubConnectionEntry>(result));
            }
        });
        return result;
    }
    
    public RFuture<PubSubConnectionEntry> subscribe(Codec codec, String channelName, AsyncSemaphore semaphore, RedisPubSubListener<?>... listeners) {
        RPromise<PubSubConnectionEntry> promise = newPromise();
        subscribe(codec, channelName, promise, PubSubType.SUBSCRIBE, semaphore, listeners);
        return promise;
    }

    public AsyncSemaphore getSemaphore(String channelName) {
        return locks[Math.abs(channelName.hashCode() % locks.length)];
    }
    
    private void subscribe(final Codec codec, final String channelName, 
            final RPromise<PubSubConnectionEntry> promise, final PubSubType type, final AsyncSemaphore lock, final RedisPubSubListener<?>... listeners) {
        final PubSubConnectionEntry connEntry = name2PubSubConnection.get(channelName);
        if (connEntry != null) {
            subscribe(channelName, promise, type, lock, connEntry, listeners);
            return;
        }

        freePubSubLock.acquire(new Runnable() {

            @Override
            public void run() {
                if (promise.isDone()) {
                    return;
                }
                
                final PubSubConnectionEntry freeEntry = freePubSubConnections.peek();
                if (freeEntry == null) {
                    connect(codec, channelName, promise, type, lock, listeners);
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
                    
                    subscribe(channelName, promise, type, lock, oldEntry, listeners);
                    return;
                }
                
                if (remainFreeAmount == 0) {
                    freePubSubConnections.poll();
                }
                freePubSubLock.release();
                
                subscribe(channelName, promise, type, lock, freeEntry, listeners);
                
                if (PubSubType.PSUBSCRIBE == type) {
                    freeEntry.psubscribe(codec, channelName);
                } else {
                    freeEntry.subscribe(codec, channelName);
                }
            }
            
        });
    }

    private void subscribe(final String channelName, final RPromise<PubSubConnectionEntry> promise,
            final PubSubType type, final AsyncSemaphore lock, final PubSubConnectionEntry connEntry,
            final RedisPubSubListener<?>... listeners) {
        for (RedisPubSubListener<?> listener : listeners) {
            connEntry.addListener(channelName, listener);
        }
        connEntry.getSubscribeFuture(channelName, type).addListener(new FutureListener<Void>() {
            @Override
            public void operationComplete(Future<Void> future) throws Exception {
                lock.release();
                promise.trySuccess(connEntry);
            }
        });
    }

    private void connect(final Codec codec, final String channelName,
            final RPromise<PubSubConnectionEntry> promise, final PubSubType type, final AsyncSemaphore lock, final RedisPubSubListener<?>... listeners) {
        final int slot = calcSlot(channelName);
        RFuture<RedisPubSubConnection> connFuture = nextPubSubConnection(slot);
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

                    subscribe(channelName, promise, type, lock, oldEntry, listeners);
                    return;
                }
                
                freePubSubConnections.add(entry);
                freePubSubLock.release();
                
                subscribe(channelName, promise, type, lock, entry, listeners);
                
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
    public RFuture<Codec> unsubscribe(final String channelName, boolean temporaryDown) {
        final PubSubConnectionEntry entry = name2PubSubConnection.remove(channelName);
        if (entry == null) {
            return null;
        }
        freePubSubConnections.remove(entry);
        
        final Codec entryCodec = entry.getConnection().getChannels().get(channelName);
        if (temporaryDown) {
            final RPromise<Codec> result = newPromise();
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
    public RFuture<Codec> punsubscribe(final String channelName, boolean temporaryDown) {
        final PubSubConnectionEntry entry = name2PubSubConnection.remove(channelName);
        if (entry == null) {
            return null;
        }
        freePubSubConnections.remove(entry);
        
        final Codec entryCodec = entry.getConnection().getChannels().get(channelName);
        if (temporaryDown) {
            final RPromise<Codec> result = newPromise();
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

    public MasterSlaveEntry getEntry(InetSocketAddress address) {
        for (MasterSlaveEntry entry : client2entry.values()) {
            InetSocketAddress addr = entry.getClient().getAddr();
            if (addr.getAddress().equals(address.getAddress()) && addr.getPort() == address.getPort()) {
                return entry;
            }
        }
        return null;
    }
    
    private MasterSlaveEntry getEntry(URI addr) {
        for (MasterSlaveEntry entry : client2entry.values()) {
            if (URIBuilder.compare(entry.getClient().getAddr(), addr)) {
                return entry;
            }
            if (entry.hasSlave(addr)) {
                return entry;
            }
        }
        return null;
    }
    
    public MasterSlaveEntry getEntry(RedisClient redisClient) {
        MasterSlaveEntry entry = client2entry.get(redisClient);
        if (entry != null) {
            return entry;
        }
        
        for (MasterSlaveEntry mentry : client2entry.values()) {
            if (mentry.hasSlave(redisClient)) {
                return mentry;
            }
        }
        return null;
    }
    
    @Override
    public MasterSlaveEntry getEntry(int slot) {
        return slot2entry.get(slot);
    }
    
    protected final void changeMaster(int slot, URI address) {
        MasterSlaveEntry entry = getEntry(slot);
        client2entry.remove(entry.getClient());
        entry.changeMaster(address);
        client2entry.put(entry.getClient(), entry);
    }

    protected final void addEntry(Integer slot, MasterSlaveEntry entry) {
        slot2entry.set(slot, entry);
        entry.addSlotRange(slot);
        client2entry.put(entry.getClient(), entry);
    }

    protected final MasterSlaveEntry removeEntry(Integer slot) {
        MasterSlaveEntry entry = slot2entry.getAndSet(slot, null);
        entry.removeSlotRange(slot);
        if (entry.getSlotRanges().isEmpty()) {
            client2entry.remove(entry.getClient());
        }
        return entry;
    }

    @Override
    public RFuture<RedisConnection> connectionWriteOp(NodeSource source, RedisCommand<?> command) {
        MasterSlaveEntry entry = getEntry(source);
        if (entry == null) {
            RedisNodeNotFoundException ex = new RedisNodeNotFoundException("Node: " + source + " hasn't been discovered yet");
            return RedissonPromise.newFailedFuture(ex);
        }
        return entry.connectionWriteOp(command);
    }

    private MasterSlaveEntry getEntry(NodeSource source) {
        if (source.getRedirect() != null) {
            return getEntry(source.getAddr());
        }

        MasterSlaveEntry entry = source.getEntry();
        if (entry == null && source.getSlot() != null) {
            entry = getEntry(source.getSlot());
        }
        if (source.getRedisClient() != null) {
            entry = getEntry(source.getRedisClient());
        }
        return entry;
    }
    
    @Override
    public RFuture<RedisConnection> connectionReadOp(NodeSource source, RedisCommand<?> command) {
        MasterSlaveEntry entry = getEntry(source);
        if (entry == null) {
            RedisNodeNotFoundException ex = new RedisNodeNotFoundException("Node: " + source + " hasn't been discovered yet");
            return RedissonPromise.newFailedFuture(ex);
        }

        if (source.getRedirect() != null) {
            return entry.connectionReadOp(command, source.getAddr());
        }
        
        return entry.connectionReadOp(command);
    }

    RFuture<RedisPubSubConnection> nextPubSubConnection(int slot) {
        MasterSlaveEntry entry = getEntry(slot);
        if (entry == null) {
            RedisNodeNotFoundException ex = new RedisNodeNotFoundException("Node for slot: " + slot + " hasn't been discovered yet");
            return RedissonPromise.newFailedFuture(ex);
        }
        return entry.nextPubSubConnection();
    }

    protected void releaseSubscribeConnection(int slot, PubSubConnectionEntry pubSubEntry) {
        MasterSlaveEntry entry = getEntry(slot);
        if (entry == null) {
            log.error("Node for slot: " + slot + " can't be found");
        } else {
            entry.returnPubSubConnection(pubSubEntry);
        }
    }

    @Override
    public void releaseWrite(NodeSource source, RedisConnection connection) {
        MasterSlaveEntry entry = getEntry(source);
        if (entry == null) {
            log.error("Node: " + source + " can't be found");
        } else {
            entry.releaseWrite(connection);
        }
    }

    @Override
    public void releaseRead(NodeSource source, RedisConnection connection) {
        MasterSlaveEntry entry = getEntry(source);
        if (entry == null) {
            log.error("Node: " + source + " can't be found");
        } else {
            entry.releaseRead(connection);
        }
        
    }

    @Override
    public void shutdown() {
        shutdown(2, 15, TimeUnit.SECONDS);//default netty value
    }

    @Override
    public void shutdown(long quietPeriod, long timeout, TimeUnit unit) {
        if (dnsMonitor != null) {
            dnsMonitor.stop();
        }

        timer.stop();
        
        shutdownLatch.close();
        shutdownPromise.trySuccess(true);
        shutdownLatch.awaitUninterruptibly();

        for (MasterSlaveEntry entry : getEntrySet()) {
            entry.shutdown();
        }

        if (cfg.getExecutor() == null) {
            executor.shutdown();
            try {
                executor.awaitTermination(timeout, unit);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        resolverGroup.close();
        
        if (cfg.getEventLoopGroup() == null) {
            group.shutdownGracefully(quietPeriod, timeout, unit).syncUninterruptibly();
        }
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
        return Collections.unmodifiableCollection(clientEntries.values());
    }

    @Override
    public <R> RPromise<R> newPromise() {
        return new RedissonPromise<R>();
    }

    @Override
    public <R> RFuture<R> newSucceededFuture(R value) {
        return RedissonPromise.newSucceededFuture(value);
    }

    @Override
    public <R> RFuture<R> newFailedFuture(Throwable cause) {
        return RedissonPromise.newFailedFuture(cause);
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
    public RFuture<Boolean> getShutdownPromise() {
        return shutdownPromise;
    }

    @Override
    public ConnectionEventsHub getConnectionEventsHub() {
        return connectionEventsHub;
    }

    protected void stopThreads() {
        timer.stop();
        executor.shutdown();
        try {
            executor.awaitTermination(15, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        group.shutdownGracefully().syncUninterruptibly();
    }
    
    public ExecutorService getExecutor() {
        return executor;
    }
    
    public URI getLastClusterNode() {
        return null;
    }
}
