/**
 * Copyright (c) 2013-2021 Nikita Koksharov
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
import io.netty.resolver.AddressResolver;
import io.netty.resolver.AddressResolverGroup;
import io.netty.resolver.DefaultAddressResolverGroup;
import io.netty.resolver.dns.DnsServerAddressStreamProviders;
import io.netty.util.*;
import io.netty.util.Timer;
import io.netty.util.TimerTask;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.*;
import io.netty.util.internal.PlatformDependent;
import org.redisson.ElementsSubscribeService;
import org.redisson.Version;
import org.redisson.api.NodeType;
import org.redisson.client.*;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.cluster.ClusterSlotRange;
import org.redisson.config.*;
import org.redisson.misc.InfinitySemaphoreLatch;
import org.redisson.misc.RedisURI;
import org.redisson.pubsub.PublishSubscribeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class MasterSlaveConnectionManager implements ConnectionManager {

    public static final Timeout DUMMY_TIMEOUT = new Timeout() {
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
            return true;
        }
    };

    protected final String id;
    
    public static final int MAX_SLOT = 16384;

    protected final ClusterSlotRange singleSlotRange = new ClusterSlotRange(0, MAX_SLOT-1);

    private final Logger log = LoggerFactory.getLogger(getClass());

    private HashedWheelTimer timer;

    protected Codec codec;

    protected final EventLoopGroup group;

    protected final Class<? extends SocketChannel> socketChannelClass;

    protected DNSMonitor dnsMonitor;
    
    protected MasterSlaveServersConfig config;

    private MasterSlaveEntry masterSlaveEntry;

    private final Promise<Void> shutdownPromise = ImmediateEventExecutor.INSTANCE.newPromise();

    private final InfinitySemaphoreLatch shutdownLatch = new InfinitySemaphoreLatch();

    private IdleConnectionWatcher connectionWatcher;

    private final ConnectionEventsHub connectionEventsHub = new ConnectionEventsHub();
    
    private final ExecutorService executor; 
    
    private final Config cfg;

    protected final AddressResolverGroup<InetSocketAddress> resolverGroup;

    private final ElementsSubscribeService elementsSubscribeService = new ElementsSubscribeService(this);

    protected PublishSubscribeService subscribeService;
    
    private final Map<RedisURI, RedisConnection> nodeConnections = new ConcurrentHashMap<>();
    
    public MasterSlaveConnectionManager(MasterSlaveServersConfig cfg, Config config, UUID id) {
        this(config, id);
        this.config = cfg;

        if (cfg.getSlaveAddresses().isEmpty()
                && (cfg.getReadMode() == ReadMode.SLAVE || cfg.getReadMode() == ReadMode.MASTER_SLAVE)) {
            throw new IllegalArgumentException("Slaves aren't defined. readMode can't be SLAVE or MASTER_SLAVE");
        }

        initTimer(cfg);
        initSingleEntry();
    }

    protected MasterSlaveConnectionManager(Config cfg, UUID id) {
        this.id = id.toString();
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

        if (cfg.getConnectionListener() != null) {
            connectionEventsHub.addListener(cfg.getConnectionListener());
        }
    }
    
    protected void closeNodeConnections() {
        nodeConnections.values().stream()
                .map(c -> c.getRedisClient().shutdownAsync())
                .forEach(f -> f.toCompletableFuture().join());
    }
    
    protected void closeNodeConnection(RedisConnection conn) {
        if (nodeConnections.values().removeAll(Arrays.asList(conn))) {
            conn.closeAsync();
        }
    }

    protected final void disconnectNode(RedisURI addr) {
        RedisConnection conn = nodeConnections.remove(addr);
        if (conn != null) {
            nodeConnections.values().removeAll(Arrays.asList(conn));
            conn.closeAsync();
        }
    }

    protected final CompletionStage<RedisConnection> connectToNode(BaseConfig<?> cfg, RedisURI addr, String sslHostname) {
        return connectToNode(NodeType.MASTER, cfg, addr, sslHostname);
    }

    protected final CompletionStage<RedisConnection> connectToNode(NodeType type, BaseConfig<?> cfg, RedisURI addr, String sslHostname) {
        RedisConnection conn = nodeConnections.get(addr);
        if (conn != null) {
            if (!conn.isActive()) {
                closeNodeConnection(conn);
            } else {
                return CompletableFuture.completedFuture(conn);
            }
        }

        RedisClient client = createClient(type, addr, cfg.getConnectTimeout(), cfg.getTimeout(), sslHostname);
        CompletionStage<RedisConnection> future = client.connectAsync();
        return future.thenCompose(connection -> {
            if (connection.isActive()) {
                if (!addr.isIP()) {
                    RedisURI address = new RedisURI(addr.getScheme()
                                 + "://" + connection.getRedisClient().getAddr().getAddress().getHostAddress()
                                 + ":" + connection.getRedisClient().getAddr().getPort());
                    nodeConnections.put(address, connection);
                }
                nodeConnections.put(addr, connection);
                return CompletableFuture.completedFuture(connection);
            } else {
                connection.closeAsync();
                CompletableFuture<RedisConnection> f = new CompletableFuture<>();
                f.completeExceptionally(new RedisException("Connection to " + connection.getRedisClient().getAddr() + " is not active!"));
                return f;
            }
        });
    }
    
    @Override
    public String getId() {
        return id;
    }
    
    @Override
    public boolean isClusterMode() {
        return false;
    }
    
    @Override
    public IdleConnectionWatcher getConnectionWatcher() {
        return connectionWatcher;
    }

    @Override
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
        if (masterSlaveEntry != null) {
            return Collections.singletonList(masterSlaveEntry);
        }
        return Collections.emptyList();
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
        
        timer = new HashedWheelTimer(new DefaultThreadFactory("redisson-timer"), minTimeout, TimeUnit.MILLISECONDS, 1024, false);
        
        connectionWatcher = new IdleConnectionWatcher(this, config);
        subscribeService = new PublishSubscribeService(this, config);
    }

    protected void initSingleEntry() {
        try {
            if (config.checkSkipSlavesInit()) {
                masterSlaveEntry = new SingleEntry(this, config);
            } else {
                masterSlaveEntry = new MasterSlaveEntry(this, config);
            }
            CompletableFuture<RedisClient> masterFuture = masterSlaveEntry.setupMasterEntry(new RedisURI(config.getMasterAddress()));
            masterFuture.join();

            if (!config.checkSkipSlavesInit()) {
                CompletableFuture<Void> fs = masterSlaveEntry.initSlaveBalancer(getDisconnectedNodes());
                fs.join();
            }

            startDNSMonitoring(masterFuture.getNow(null));
        } catch (Exception e) {
            stopThreads();
            if (e instanceof CompletionException) {
                if (e.getCause() instanceof RuntimeException) {
                    throw (RuntimeException) e.getCause();
                }
                throw new RedisConnectionException(e.getCause());
            }
            throw e;
        }
    }

    protected void startDNSMonitoring(RedisClient masterHost) {
        if (masterHost.getConfig().getAddress().isIP()) {
            return;
        }

        if (config.getDnsMonitoringInterval() != -1) {
            Set<RedisURI> slaveAddresses = config.getSlaveAddresses().stream().map(r -> new RedisURI(r)).collect(Collectors.toSet());
            dnsMonitor = new DNSMonitor(this, masterHost, 
                                            slaveAddresses, config.getDnsMonitoringInterval(), resolverGroup);
            dnsMonitor.start();
        }
    }

    protected Collection<RedisURI> getDisconnectedNodes() {
        return Collections.emptySet();
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
        c.setSslProtocols(cfg.getSslProtocols());
        
        c.setRetryInterval(cfg.getRetryInterval());
        c.setRetryAttempts(cfg.getRetryAttempts());
        c.setTimeout(cfg.getTimeout());
        c.setLoadBalancer(cfg.getLoadBalancer());
        c.setPassword(cfg.getPassword());
        c.setUsername(cfg.getUsername());
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
        c.setTcpNoDelay(cfg.isTcpNoDelay());
        c.setNameMapper(cfg.getNameMapper());

        return c;
    }

    @Override
    public RedisClient createClient(NodeType type, RedisURI address, String sslHostname) {
        RedisClient client = createClient(type, address, config.getConnectTimeout(), config.getTimeout(), sslHostname);
        return client;
    }
    
    @Override
    public RedisClient createClient(NodeType type, InetSocketAddress address, RedisURI uri, String sslHostname) {
        RedisClient client = createClient(type, address, uri, config.getConnectTimeout(), config.getTimeout(), sslHostname);
        return client;
    }

    @Override
    public RedisClient createClient(NodeType type, RedisURI address, int timeout, int commandTimeout, String sslHostname) {
        RedisClientConfig redisConfig = createRedisConfig(type, address, timeout, commandTimeout, sslHostname);
        return RedisClient.create(redisConfig);
    }
    
    private RedisClient createClient(NodeType type, InetSocketAddress address, RedisURI uri, int timeout, int commandTimeout, String sslHostname) {
        RedisClientConfig redisConfig = createRedisConfig(type, null, timeout, commandTimeout, sslHostname);
        redisConfig.setAddress(address, uri);
        return RedisClient.create(redisConfig);
    }


    protected RedisClientConfig createRedisConfig(NodeType type, RedisURI address, int timeout, int commandTimeout, String sslHostname) {
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
              .setSslProtocols(config.getSslProtocols())
              .setClientName(config.getClientName())
              .setKeepPubSubOrder(cfg.isKeepPubSubOrder())
              .setPingConnectionInterval(config.getPingConnectionInterval())
              .setKeepAlive(config.isKeepAlive())
              .setTcpNoDelay(config.isTcpNoDelay())
              .setUsername(config.getUsername())
              .setPassword(config.getPassword())
              .setNettyHook(cfg.getNettyHook());
        
        if (type != NodeType.SENTINEL) {
            redisConfig.setDatabase(config.getDatabase());
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
        return masterSlaveEntry;
    }
    
    protected MasterSlaveEntry getEntry(RedisURI addr) {
        return masterSlaveEntry;
    }

    @Override
    public MasterSlaveEntry getEntry(RedisClient redisClient) {
        return masterSlaveEntry;
    }

    @Override
    public MasterSlaveEntry getEntry(String name) {
        int slot = calcSlot(name);
        return getEntry(slot);
    }

    @Override
    public MasterSlaveEntry getEntry(int slot) {
        return masterSlaveEntry;
    }

    protected CompletableFuture<RedisClient> changeMaster(int slot, RedisURI address) {
        MasterSlaveEntry entry = getEntry(slot);
        return entry.changeMaster(address);
    }
    
    @Override
    public CompletableFuture<RedisConnection> connectionWriteOp(NodeSource source, RedisCommand<?> command) {
        MasterSlaveEntry entry = getEntry(source);
        if (entry == null) {
            CompletableFuture<RedisConnection> f = new CompletableFuture<>();
            f.completeExceptionally(createNodeNotFoundException(source));
            return f;
        }
        // fix for https://github.com/redisson/redisson/issues/1548
        if (source.getRedirect() != null 
                && !RedisURI.compare(entry.getClient().getAddr(), source.getAddr()) 
                    && entry.hasSlave(source.getAddr())) {
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
    public CompletableFuture<RedisConnection> connectionReadOp(NodeSource source, RedisCommand<?> command) {
        MasterSlaveEntry entry = getEntry(source);
        if (entry == null) {
            CompletableFuture<RedisConnection> f = new CompletableFuture<>();
            f.completeExceptionally(createNodeNotFoundException(source));
            return f;
        }

        if (source.getRedirect() != null) {
            return entry.connectionReadOp(command, source.getAddr());
        }
        if (source.getRedisClient() != null) {
            return entry.connectionReadOp(command, source.getRedisClient());
        }
        
        return entry.connectionReadOp(command);
    }

    public RedisNodeNotFoundException createNodeNotFoundException(NodeSource source) {
        RedisNodeNotFoundException ex;
        if (source.getSlot() != null && source.getAddr() == null && source.getRedisClient() == null) {
            ex = new RedisNodeNotFoundException("Node for slot: " + source.getSlot() + " hasn't been discovered yet. Check cluster slots coverage using CLUSTER NODES command. Increase value of retryAttempts and/or retryInterval settings.");
        } else {
            ex = new RedisNodeNotFoundException("Node: " + source + " hasn't been discovered yet. Increase value of retryAttempts and/or retryInterval settings.");
        }
        return ex;
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
        shutdown(0, 2, TimeUnit.SECONDS); //default netty value
    }

    @Override
    public void shutdown(long quietPeriod, long timeout, TimeUnit unit) {
        if (dnsMonitor != null) {
            dnsMonitor.stop();
        }
        
        connectionWatcher.stop();

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (MasterSlaveEntry entry : getEntrySet()) {
            futures.add(entry.shutdownAsync());
        }
        CompletableFuture<Void> future = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));

        try {
            future.get(timeout, unit);
        } catch (Exception e) {
            // skip
        }
        resolverGroup.close();

        shutdownLatch.close();
        if (cfg.getExecutor() == null) {
            executor.shutdown();
            try {
                executor.awaitTermination(timeout, unit);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        shutdownPromise.trySuccess(null);
        shutdownLatch.awaitUninterruptibly();
        
        if (cfg.getEventLoopGroup() == null) {
            group.shutdownGracefully(quietPeriod, timeout, unit).syncUninterruptibly();
        }
        
        timer.stop();
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
            if (isShuttingDown()) {
                return DUMMY_TIMEOUT;
            }
            
            throw e;
        }
    }

    @Override
    public InfinitySemaphoreLatch getShutdownLatch() {
        return shutdownLatch;
    }

    @Override
    public Future<Void> getShutdownPromise() {
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

    public ElementsSubscribeService getElementsSubscribeService() {
        return elementsSubscribeService;
    }

    public ExecutorService getExecutor() {
        return executor;
    }
    
    public RedisURI getLastClusterNode() {
        return null;
    }

    @Override
    public RedisURI applyNatMap(RedisURI address) {
        return address;
    }

    @Override
    public CompletableFuture<RedisURI> resolveIP(RedisURI address) {
        return resolveIP(address.getScheme(), address);
    }

    protected CompletableFuture<RedisURI> resolveIP(String scheme, RedisURI address) {
        if (address.isIP()) {
            RedisURI addr = toURI(scheme, address.getHost(), "" + address.getPort());
            return CompletableFuture.completedFuture(addr);
        }

        CompletableFuture<RedisURI> result = new CompletableFuture<>();
        AddressResolver<InetSocketAddress> resolver = resolverGroup.getResolver(getGroup().next());
        InetSocketAddress addr = InetSocketAddress.createUnresolved(address.getHost(), address.getPort());
        Future<InetSocketAddress> future = resolver.resolve(addr);
        future.addListener((FutureListener<InetSocketAddress>) f -> {
            if (!f.isSuccess()) {
                log.error("Unable to resolve " + address, f.cause());
                result.completeExceptionally(f.cause());
                return;
            }

            InetSocketAddress s = f.getNow();
            RedisURI uri = toURI(scheme, s.getAddress().getHostAddress(), "" + address.getPort());
            result.complete(uri);
        });
        return result;
    }

    protected RedisURI toURI(String scheme, String host, String port) {
        // convert IPv6 address to unified compressed format
        if (NetUtil.isValidIpV6Address(host)) {
            byte[] addr = NetUtil.createByteArrayFromIpAddressString(host);
            try {
                InetAddress ia = InetAddress.getByAddress(host, addr);
                host = ia.getHostAddress();
            } catch (UnknownHostException e) {
                throw new RuntimeException(e);
            }
        }
        RedisURI uri = new RedisURI(scheme + "://" + host + ":" + port);
        return applyNatMap(uri);
    }

}
