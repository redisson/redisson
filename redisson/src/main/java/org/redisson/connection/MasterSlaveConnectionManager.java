/**
 * Copyright (c) 2013-2019 Nikita Koksharov
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReferenceArray;

import org.redisson.Version;
import org.redisson.api.NodeType;
import org.redisson.api.RFuture;
import org.redisson.client.RedisClient;
import org.redisson.client.RedisClientConfig;
import org.redisson.client.RedisConnection;
import org.redisson.client.RedisException;
import org.redisson.client.RedisNodeNotFoundException;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.cluster.ClusterSlotRange;
import org.redisson.command.CommandSyncService;
import org.redisson.config.BaseMasterSlaveServersConfig;
import org.redisson.config.Config;
import org.redisson.config.MasterSlaveServersConfig;
import org.redisson.config.TransportMode;
import org.redisson.misc.CountableListener;
import org.redisson.misc.InfinitySemaphoreLatch;
import org.redisson.misc.RPromise;
import org.redisson.misc.RedissonPromise;
import org.redisson.misc.URIBuilder;
import org.redisson.pubsub.PublishSubscribeService;
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
import io.netty.resolver.AddressResolverGroup;
import io.netty.resolver.DefaultAddressResolverGroup;
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

    protected final UUID id;
    
    public static final int MAX_SLOT = 16384;

    protected final ClusterSlotRange singleSlotRange = new ClusterSlotRange(0, MAX_SLOT-1);

    private final Logger log = LoggerFactory.getLogger(getClass());

    private HashedWheelTimer timer;

    protected Codec codec;

    protected final EventLoopGroup group;

    protected final Class<? extends SocketChannel> socketChannelClass;

    protected DNSMonitor dnsMonitor;
    
    protected MasterSlaveServersConfig config;

    private final AtomicReferenceArray<MasterSlaveEntry> slot2entry = new AtomicReferenceArray<MasterSlaveEntry>(MAX_SLOT);
    private final Map<RedisClient, MasterSlaveEntry> client2entry = PlatformDependent.newConcurrentHashMap();

    private final RPromise<Boolean> shutdownPromise;

    private final InfinitySemaphoreLatch shutdownLatch = new InfinitySemaphoreLatch();

    private IdleConnectionWatcher connectionWatcher;

    private final ConnectionEventsHub connectionEventsHub = new ConnectionEventsHub();
    
    private final ExecutorService executor; 
    
    private final CommandSyncService commandExecutor;

    private final Config cfg;

    protected final AddressResolverGroup<InetSocketAddress> resolverGroup;
    
    private PublishSubscribeService subscribeService;
    
    private final Map<Object, RedisConnection> nodeConnections = PlatformDependent.newConcurrentHashMap();
    
    public MasterSlaveConnectionManager(MasterSlaveServersConfig cfg, Config config, UUID id) {
        this(config, id);
        this.config = cfg;
        
        initTimer(cfg);
        initSingleEntry();
    }

    protected MasterSlaveConnectionManager(Config cfg, UUID id) {
        this.id = id;
        Version.logVersion();

        if (cfg.getTransportMode() == TransportMode.EPOLL) {
            if (cfg.getEventLoopGroup() == null) {
                this.group = new EpollEventLoopGroup(cfg.getNettyThreads(), new DefaultThreadFactory("redisson-netty"));
            } else {
                this.group = cfg.getEventLoopGroup();
            }

            this.socketChannelClass = EpollSocketChannel.class;
            if (PlatformDependent.isAndroid()) {
                this.resolverGroup = DefaultAddressResolverGroup.INSTANCE;
            } else {
                this.resolverGroup = cfg.getAddressResolverGroupFactory().create(EpollDatagramChannel.class, DnsServerAddressStreamProviders.platformDefault());
            }
        } else if (cfg.getTransportMode() == TransportMode.KQUEUE) {
            if (cfg.getEventLoopGroup() == null) {
                this.group = new KQueueEventLoopGroup(cfg.getNettyThreads(), new DefaultThreadFactory("redisson-netty"));
            } else {
                this.group = cfg.getEventLoopGroup();
            }

            this.socketChannelClass = KQueueSocketChannel.class;
            if (PlatformDependent.isAndroid()) {
                this.resolverGroup = DefaultAddressResolverGroup.INSTANCE;
            } else {
                this.resolverGroup = cfg.getAddressResolverGroupFactory().create(KQueueDatagramChannel.class, DnsServerAddressStreamProviders.platformDefault());
            }
        } else {
            if (cfg.getEventLoopGroup() == null) {
                this.group = new NioEventLoopGroup(cfg.getNettyThreads(), new DefaultThreadFactory("redisson-netty"));
            } else {
                this.group = cfg.getEventLoopGroup();
            }

            this.socketChannelClass = NioSocketChannel.class;
            if (PlatformDependent.isAndroid()) {
                this.resolverGroup = DefaultAddressResolverGroup.INSTANCE;
            } else {
                this.resolverGroup = cfg.getAddressResolverGroupFactory().create(NioDatagramChannel.class, DnsServerAddressStreamProviders.platformDefault());
            }
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
        this.shutdownPromise = new RedissonPromise<Boolean>();
        this.commandExecutor = new CommandSyncService(this);
    }
    
    protected void closeNodeConnections() {
        List<RFuture<Void>> futures = new ArrayList<RFuture<Void>>();
        for (RedisConnection connection : nodeConnections.values()) {
            RFuture<Void> future = connection.getRedisClient().shutdownAsync();
            futures.add(future);
        }
        
        for (RFuture<Void> future : futures) {
            future.syncUninterruptibly();
        }
    }
    
    protected void closeNodeConnection(RedisConnection conn) {
        if (nodeConnections.values().remove(conn)) {
            conn.closeAsync();
        }
    }
    
    protected RFuture<RedisConnection> connectToNode(BaseMasterSlaveServersConfig<?> cfg, final URI addr, RedisClient client, String sslHostname) {
        final Object key;
        if (client != null) {
            key = client;
        } else {
            key = addr;
        }
        RedisConnection connection = nodeConnections.get(key);
        if (connection != null) {
            if (!connection.isActive()) {
                nodeConnections.remove(key);
                connection.closeAsync();
            } else {
                return RedissonPromise.newSucceededFuture(connection);
            }
        }

        if (addr != null) {
            client = createClient(NodeType.MASTER, addr, cfg.getConnectTimeout(), cfg.getTimeout(), sslHostname);
        }
        final RPromise<RedisConnection> result = new RedissonPromise<RedisConnection>();
        RFuture<RedisConnection> future = client.connectAsync();
        future.addListener(new FutureListener<RedisConnection>() {
            @Override
            public void operationComplete(Future<RedisConnection> future) throws Exception {
                if (!future.isSuccess()) {
                    result.tryFailure(future.cause());
                    return;
                }

                RedisConnection connection = future.getNow();
                if (connection.isActive()) {
                    nodeConnections.put(key, connection);
                    result.trySuccess(connection);
                } else {
                    connection.closeAsync();
                    result.tryFailure(new RedisException("Connection to " + connection.getRedisClient().getAddr() + " is not active!"));
                }
            }
        });

        return result;
    }
    
    public UUID getId() {
        return id;
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
        int[] timeouts = new int[]{config.getRetryInterval(), config.getTimeout()};
        Arrays.sort(timeouts);
        int minTimeout = timeouts[0];
        if (minTimeout % 100 != 0) {
            minTimeout = (minTimeout % 100) / 2;
        } else if (minTimeout == 100) {
            minTimeout = 50;
        } else {
            minTimeout = 100;
        }
        
        timer = new HashedWheelTimer(Executors.defaultThreadFactory(), minTimeout, TimeUnit.MILLISECONDS, 1024, false);
        
        connectionWatcher = new IdleConnectionWatcher(this, config);
        subscribeService = new PublishSubscribeService(this, config);
    }

    protected void initSingleEntry() {
        try {
            MasterSlaveEntry entry;
            if (config.checkSkipSlavesInit()) {
                entry = new SingleEntry(this, config);
            } else {
                entry = createMasterSlaveEntry(config);
            }
            RFuture<RedisClient> f = entry.setupMasterEntry(config.getMasterAddress());
            f.syncUninterruptibly();
        
            for (int slot = singleSlotRange.getStartSlot(); slot < singleSlotRange.getEndSlot() + 1; slot++) {
                addEntry(slot, entry);
            }
            
            startDNSMonitoring(f.getNow());
        } catch (RuntimeException e) {
            stopThreads();
            throw e;
        }
    }

    protected void startDNSMonitoring(RedisClient masterHost) {
        if (config.getDnsMonitoringInterval() != -1) {
            dnsMonitor = new DNSMonitor(this, masterHost, 
                    config.getSlaveAddresses(), config.getDnsMonitoringInterval(), resolverGroup);
            dnsMonitor.start();
        }
    }
    
    protected MasterSlaveEntry createMasterSlaveEntry(MasterSlaveServersConfig config) {
        MasterSlaveEntry entry = new MasterSlaveEntry(this, config);
        List<RFuture<Void>> fs = entry.initSlaveBalancer(java.util.Collections.<URI>emptySet());
        for (RFuture<Void> future : fs) {
            future.syncUninterruptibly();
        }
        return entry;
    }

    protected MasterSlaveServersConfig create(BaseMasterSlaveServersConfig<?> cfg) {
        MasterSlaveServersConfig c = new MasterSlaveServersConfig();
        
        c.setPingConnectionInterval(cfg.getPingConnectionInterval());
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

        c.setFailedSlaveCheckInterval(cfg.getFailedSlaveCheckInterval());
        c.setFailedSlaveReconnectionInterval(cfg.getFailedSlaveReconnectionInterval());
        c.setMasterConnectionMinimumIdleSize(cfg.getMasterConnectionMinimumIdleSize());
        c.setSlaveConnectionMinimumIdleSize(cfg.getSlaveConnectionMinimumIdleSize());
        c.setSubscriptionConnectionMinimumIdleSize(cfg.getSubscriptionConnectionMinimumIdleSize());
        c.setReadMode(cfg.getReadMode());
        c.setSubscriptionMode(cfg.getSubscriptionMode());
        c.setDnsMonitoringInterval(cfg.getDnsMonitoringInterval());
        c.setKeepAlive(cfg.isKeepAlive());

        return c;
    }

    @Override
    public RedisClient createClient(NodeType type, URI address, String sslHostname) {
        RedisClient client = createClient(type, address, config.getConnectTimeout(), config.getTimeout(), sslHostname);
        return client;
    }
    
    @Override
    public RedisClient createClient(NodeType type, InetSocketAddress address, URI uri, String sslHostname) {
        RedisClient client = createClient(type, address, uri, config.getConnectTimeout(), config.getTimeout(), sslHostname);
        return client;
    }

    @Override
    public RedisClient createClient(NodeType type, URI address, int timeout, int commandTimeout, String sslHostname) {
        RedisClientConfig redisConfig = createRedisConfig(type, address, timeout, commandTimeout, sslHostname);
        return RedisClient.create(redisConfig);
    }
    
    private RedisClient createClient(NodeType type, InetSocketAddress address, URI uri, int timeout, int commandTimeout, String sslHostname) {
        RedisClientConfig redisConfig = createRedisConfig(type, null, timeout, commandTimeout, sslHostname);
        redisConfig.setAddress(address, uri);
        return RedisClient.create(redisConfig);
    }


    protected RedisClientConfig createRedisConfig(NodeType type, URI address, int timeout, int commandTimeout, String sslHostname) {
        RedisClientConfig redisConfig = new RedisClientConfig();
        redisConfig.setAddress(address)
              .setTimer(timer)
              .setExecutor(executor)
              .setResolverGroup(resolverGroup)
              .setGroup(group)
              .setSocketChannelClass(socketChannelClass)
              .setConnectTimeout(timeout)
              .setCommandTimeout(commandTimeout)
              .setSslHostname(sslHostname)
              .setSslEnableEndpointIdentification(config.isSslEnableEndpointIdentification())
              .setSslProvider(config.getSslProvider())
              .setSslTruststore(config.getSslTruststore())
              .setSslTruststorePassword(config.getSslTruststorePassword())
              .setSslKeystore(config.getSslKeystore())
              .setSslKeystorePassword(config.getSslKeystorePassword())
              .setClientName(config.getClientName())
              .setDecodeInExecutor(cfg.isDecodeInExecutor())
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
    public int calcSlot(byte[] key) {
        return singleSlotRange.getStartSlot();
    }

    @Override
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

    @Override
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
    
    protected final RFuture<RedisClient> changeMaster(int slot, URI address) {
        final MasterSlaveEntry entry = getEntry(slot);
        final RedisClient oldClient = entry.getClient();
        RFuture<RedisClient> future = entry.changeMaster(address);
        future.addListener(new FutureListener<RedisClient>() {
            @Override
            public void operationComplete(Future<RedisClient> future) throws Exception {
                if (future.isSuccess()) {
                    client2entry.remove(oldClient);
                    client2entry.put(entry.getClient(), entry);
                }
            }
        });
        return future;
    }
    
    protected final void addEntry(Integer slot, MasterSlaveEntry entry) {
        MasterSlaveEntry oldEntry = slot2entry.getAndSet(slot, entry);
        if (oldEntry != entry) {
            entry.incReference();
        }
        client2entry.put(entry.getClient(), entry);
    }

    protected final MasterSlaveEntry removeEntry(Integer slot) {
        MasterSlaveEntry entry = slot2entry.getAndSet(slot, null);
        if (entry.decReference() == 0) {
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
        // fix for https://github.com/redisson/redisson/issues/1548
        if (source.getRedirect() != null &&
                !URIBuilder.compare(entry.getClient().getAddr(), source.getAddr()) &&
                entry.hasSlave(source.getAddr())) {
            return entry.redirectedConnectionWriteOp(command, source.getAddr());
        }
        return entry.connectionWriteOp(command);
    }

    private MasterSlaveEntry getEntry(NodeSource source) {
        if (source.getRedirect() != null) {
            return getEntry(source.getAddr());
        }

        MasterSlaveEntry entry = source.getEntry();
        if (source.getRedisClient() != null) {
            entry = getEntry(source.getRedisClient());
        }
        if (entry == null && source.getSlot() != null) {
            entry = getEntry(source.getSlot());
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
        if (source.getRedisClient() != null) {
            return entry.connectionReadOp(command, source.getRedisClient());
        }
        
        return entry.connectionReadOp(command);
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
        shutdown(0, 2, TimeUnit.SECONDS);//default netty value
    }

    @Override
    public void shutdown(long quietPeriod, long timeout, TimeUnit unit) {
        if (dnsMonitor != null) {
            dnsMonitor.stop();
        }
        
        connectionWatcher.stop();

        if (cfg.getExecutor() == null) {
            executor.shutdown();
            try {
                executor.awaitTermination(timeout, unit);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        RPromise<Void> result = new RedissonPromise<Void>();
        CountableListener<Void> listener = new CountableListener<Void>(result, null, getEntrySet().size());
        for (MasterSlaveEntry entry : getEntrySet()) {
            entry.shutdownAsync().addListener(listener);
        }
        
        result.awaitUninterruptibly(timeout, unit);
        resolverGroup.close();

        timer.stop();
        shutdownLatch.close();
        shutdownPromise.trySuccess(true);
        shutdownLatch.awaitUninterruptibly();
        
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
        shutdown();
    }
    
    public PublishSubscribeService getSubscribeService() {
        return subscribeService;
    }
    
    public ExecutorService getExecutor() {
        return executor;
    }
    
    public URI getLastClusterNode() {
        return null;
    }
}
