/**
 * Copyright (c) 2013-2024 Nikita Koksharov
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

import io.netty.buffer.ByteBuf;
import org.redisson.api.DefaultNameMapper;
import org.redisson.api.NodeType;
import org.redisson.client.*;
import org.redisson.cluster.ClusterSlotRange;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.config.*;
import org.redisson.liveobject.core.RedissonObjectBuilder;
import org.redisson.misc.RedisURI;
import org.redisson.pubsub.PublishSubscribeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class MasterSlaveConnectionManager implements ConnectionManager {

    public static final int MAX_SLOT = 16384;

    protected final ClusterSlotRange singleSlotRange = new ClusterSlotRange(0, MAX_SLOT-1);

    private final Logger log = LoggerFactory.getLogger(getClass());

    protected DNSMonitor dnsMonitor;

    protected MasterSlaveServersConfig config;

    private MasterSlaveEntry masterSlaveEntry;

    protected final PublishSubscribeService subscribeService;

    protected final ServiceManager serviceManager;

    private final Map<RedisURI, RedisConnection> nodeConnections = new ConcurrentHashMap<>();

    protected final AtomicReference<CompletableFuture<Void>> lazyConnectLatch = new AtomicReference<>();

    private boolean lastAttempt;

    MasterSlaveConnectionManager(BaseMasterSlaveServersConfig<?> cfg, Config configCopy) {
        if (cfg instanceof MasterSlaveServersConfig) {
            this.config = (MasterSlaveServersConfig) cfg;
            if (this.config.getSlaveAddresses().isEmpty()
                    && (this.config.getReadMode() == ReadMode.SLAVE || this.config.getReadMode() == ReadMode.MASTER_SLAVE)) {
                throw new IllegalArgumentException("Slaves aren't defined. readMode can't be SLAVE or MASTER_SLAVE");
            }
        } else {
            this.config = create(cfg);
        }

        serviceManager = new ServiceManager(this.config, configCopy);
        subscribeService = new PublishSubscribeService(this);
    }

    @Override
    public ServiceManager getServiceManager() {
        return serviceManager;
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
    public Collection<MasterSlaveEntry> getEntrySet() {
        lazyConnect();

        if (masterSlaveEntry != null) {
            return Collections.singletonList(masterSlaveEntry);
        }
        return Collections.emptyList();
    }

    protected final void lazyConnect() {
        if (isInitialized()) {
            return;
        }

        CompletableFuture<Void> newFuture = new CompletableFuture<>();
        if (!lazyConnectLatch.compareAndSet(null, newFuture)) {
            CompletableFuture<Void> currentFuture = lazyConnectLatch.get();
            if (currentFuture.isCompletedExceptionally()) {
                if (!lazyConnectLatch.compareAndSet(currentFuture, newFuture)) {
                    lazyConnectLatch.get().join();
                    return;
                }
            } else {
                lazyConnectLatch.get().join();
                return;
            }
        }

        try {
            connect();
            newFuture.complete(null);
        } catch (Exception e) {
            newFuture.completeExceptionally(e);
            throw e;
        }
    }

    @Override
    public final void connect() {
        int attempt = config.getRetryAttempts() + 1;
        for (int i = 0; i < attempt; i++) {
            try {
                if (i == attempt - 1) {
                    lastAttempt = true;
                }
                doConnect(u -> null);
                return;
            } catch (IllegalArgumentException e) {
                shutdown();
                throw e;
            } catch (Exception e) {
                if (i == attempt - 1) {
                    lastAttempt = false;
                    throw e;
                }
                try {
                    Duration timeout = config.getRetryDelay().calcDelay(attempt);
                    Thread.sleep(timeout.toMillis());
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    protected void doConnect(Function<RedisURI, String> hostnameMapper) {
        try {
            if (config.isSlaveNotUsed()) {
                masterSlaveEntry = new SingleEntry(this, config);
            } else {
                masterSlaveEntry = new MasterSlaveEntry(this, config);
            }

            RedisURI uri = new RedisURI(config.getMasterAddress());
            String hostname = hostnameMapper.apply(uri);
            CompletableFuture<RedisClient> masterFuture = masterSlaveEntry.setupMasterEntry(uri, hostname);
            try {
                if (config.getMasterConnectionMinimumIdleSize() == 0) {
                    masterFuture.join();
                } else {
                    masterFuture.get(config.getConnectTimeout()*config.getMasterConnectionMinimumIdleSize(), TimeUnit.MILLISECONDS);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException | TimeoutException e) {
                throw new RedisConnectionException(e);
            }

            if (!config.isSlaveNotUsed()) {
                CompletableFuture<Void> fs = masterSlaveEntry.initSlaveBalancer(hostnameMapper);
                try {
                    if (config.getSlaveConnectionMinimumIdleSize() == 0) {
                        fs.join();
                    } else {
                        fs.get(config.getConnectTimeout()*config.getSlaveConnectionMinimumIdleSize(), TimeUnit.MILLISECONDS);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException | TimeoutException e) {
                    throw new RedisConnectionException(e);
                }
            }

            startDNSMonitoring(masterFuture.getNow(null));
        } catch (Exception e) {
            internalShutdown();
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
                                            slaveAddresses, config.getDnsMonitoringInterval(), serviceManager.getResolverGroup());
            dnsMonitor.start();
        }
    }

    protected MasterSlaveServersConfig create(BaseMasterSlaveServersConfig<?> cfg) {
        MasterSlaveServersConfig c = new MasterSlaveServersConfig();
        
        if (cfg.getUsername() != null) {
            c.setUsername(cfg.getUsername());
        }
        if (cfg.getPassword() != null) {
            c.setPassword(cfg.getPassword());
        }
        if (!(cfg.getNameMapper() instanceof DefaultNameMapper)) {
            c.setNameMapper(cfg.getNameMapper());
        }
        if (!(cfg.getCommandMapper() instanceof DefaultCommandMapper)) {
            c.setCommandMapper(cfg.getCommandMapper());
        }
        if (!(cfg.getCredentialsResolver() instanceof DefaultCredentialsResolver)) {
            c.setCredentialsResolver(cfg.getCredentialsResolver());
        }
        
        if (cfg.getSslVerificationMode() != SslVerificationMode.STRICT) {
            c.setSslVerificationMode(cfg.getSslVerificationMode());
        }
        if (cfg.getSslKeystoreType() != null) {
            c.setSslKeystoreType(cfg.getSslKeystoreType());
        }
        if (cfg.getSslProvider() != SslProvider.JDK) {
            c.setSslProvider(cfg.getSslProvider());
        }
        if (cfg.getSslTruststore() != null) {
            c.setSslTruststore(cfg.getSslTruststore());
        }
        if (cfg.getSslTruststorePassword() != null) {
            c.setSslTruststorePassword(cfg.getSslTruststorePassword());
        }
        if (cfg.getSslKeystore() != null) {
            c.setSslKeystore(cfg.getSslKeystore());
        }
        if (cfg.getSslKeystorePassword() != null) {
            c.setSslKeystorePassword(cfg.getSslKeystorePassword());
        }
        if (cfg.getSslProtocols() != null) {
            c.setSslProtocols(cfg.getSslProtocols());
        }
        if (cfg.getSslCiphers() != null) {
            c.setSslCiphers(cfg.getSslCiphers());
        }
        if (cfg.getSslKeyManagerFactory() != null) {
            c.setSslKeyManagerFactory(cfg.getSslKeyManagerFactory());
        }
        if (cfg.getSslTrustManagerFactory() != null) {
            c.setSslTrustManagerFactory(cfg.getSslTrustManagerFactory());
        }
        
        c.setPingConnectionInterval(cfg.getPingConnectionInterval());
        c.setRetryDelay(cfg.getRetryDelay());
        c.setReconnectionDelay(cfg.getReconnectionDelay());
        c.setRetryAttempts(cfg.getRetryAttempts());
        c.setTimeout(cfg.getTimeout());
        c.setLoadBalancer(cfg.getLoadBalancer());
        c.setClientName(cfg.getClientName());
        c.setMasterConnectionPoolSize(cfg.getMasterConnectionPoolSize());
        c.setSlaveConnectionPoolSize(cfg.getSlaveConnectionPoolSize());
        c.setSubscriptionConnectionPoolSize(cfg.getSubscriptionConnectionPoolSize());
        c.setSubscriptionsPerConnection(cfg.getSubscriptionsPerConnection());
        c.setConnectTimeout(cfg.getConnectTimeout());
        c.setIdleConnectionTimeout(cfg.getIdleConnectionTimeout());
        
        c.setFailedSlaveReconnectionInterval(cfg.getFailedSlaveReconnectionInterval());
        c.setFailedSlaveNodeDetector(cfg.getFailedSlaveNodeDetector());
        c.setMasterConnectionMinimumIdleSize(cfg.getMasterConnectionMinimumIdleSize());
        c.setSlaveConnectionMinimumIdleSize(cfg.getSlaveConnectionMinimumIdleSize());
        c.setSubscriptionConnectionMinimumIdleSize(cfg.getSubscriptionConnectionMinimumIdleSize());
        c.setReadMode(cfg.getReadMode());
        c.setSubscriptionMode(cfg.getSubscriptionMode());
        c.setDnsMonitoringInterval(cfg.getDnsMonitoringInterval());
        c.setKeepAlive(cfg.isKeepAlive());
        c.setTcpKeepAliveCount(cfg.getTcpKeepAliveCount());
        c.setTcpKeepAliveIdle(cfg.getTcpKeepAliveIdle());
        c.setTcpKeepAliveInterval(cfg.getTcpKeepAliveInterval());
        c.setTcpUserTimeout(cfg.getTcpUserTimeout());
        c.setTcpNoDelay(cfg.isTcpNoDelay());
        c.setSubscriptionTimeout(cfg.getSubscriptionTimeout());
        
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

    protected RedisClient createClient(NodeType type, RedisURI address, int timeout, int commandTimeout, String sslHostname) {
        RedisClientConfig redisConfig = createRedisConfig(type, address, timeout, commandTimeout, sslHostname);
        return RedisClient.create(redisConfig);
    }

    private RedisClient createClient(NodeType type, InetSocketAddress address, RedisURI uri, int timeout, int commandTimeout, String sslHostname) {
        RedisClientConfig redisConfig = createRedisConfig(type, null, timeout, commandTimeout, sslHostname);
        redisConfig.setAddress(address, uri);
        return RedisClient.create(redisConfig);
    }

    protected RedisClientConfig createRedisConfig(NodeType type, RedisURI address, int timeout, int commandTimeout, String sslHostname) {
        Config serviceCfg = serviceManager.getCfg();
        RedisClientConfig redisConfig = new RedisClientConfig();
        redisConfig.setAddress(address)
                .setTimer(serviceManager.getTimer())
                .setExecutor(serviceManager.getExecutor())
                .setResolverGroup(serviceManager.getResolverGroup())
                .setGroup(serviceManager.getGroup())
                .setSocketChannelClass(serviceManager.getSocketChannelClass())
                .setConnectTimeout(timeout)
                .setCommandTimeout(commandTimeout)
                .setSslHostname(sslHostname)
                .setSslVerificationMode(serviceCfg.getSslVerificationMode())
                .setSslProvider(serviceCfg.getSslProvider())
                .setSslKeystoreType(Objects.toString(serviceCfg.getSslKeystoreType(), config.getSslKeystoreType()))
                .setSslTruststore(Optional.ofNullable(serviceCfg.getSslTruststore()).orElse(config.getSslTruststore()))
                .setSslTruststorePassword(Objects.toString(serviceCfg.getSslTruststorePassword(), config.getSslTruststorePassword()))
                .setSslKeystore(Optional.ofNullable(serviceCfg.getSslKeystore()).orElse(config.getSslKeystore()))
                .setSslKeystorePassword(Objects.toString(serviceCfg.getSslKeystorePassword(), config.getSslKeystorePassword()))
                .setSslProtocols(Optional.ofNullable(serviceCfg.getSslProtocols()).orElse(config.getSslProtocols()))
                .setSslCiphers(Optional.ofNullable(serviceCfg.getSslCiphers()).orElse(config.getSslCiphers()))
                .setSslKeyManagerFactory(Optional.ofNullable(serviceCfg.getSslKeyManagerFactory()).orElse(config.getSslKeyManagerFactory()))
                .setSslTrustManagerFactory(Optional.ofNullable(serviceCfg.getSslTrustManagerFactory()).orElse(config.getSslTrustManagerFactory()))
                .setClientName(config.getClientName())
                .setKeepPubSubOrder(serviceCfg.isKeepPubSubOrder())
                .setPingConnectionInterval(config.getPingConnectionInterval())
                .setKeepAlive(config.isKeepAlive())
                .setTcpKeepAliveCount(config.getTcpKeepAliveCount())
                .setTcpKeepAliveIdle(config.getTcpKeepAliveIdle())
                .setTcpKeepAliveInterval(config.getTcpKeepAliveInterval())
                .setTcpUserTimeout(config.getTcpUserTimeout())
                .setTcpNoDelay(config.isTcpNoDelay())
                .setUsername(Objects.toString(serviceCfg.getUsername(), config.getUsername()))
                .setPassword(Objects.toString(serviceCfg.getPassword(), config.getPassword()))
                .setNettyHook(serviceCfg.getNettyHook())
                .setFailedNodeDetector(config.getFailedSlaveNodeDetector())
                .setProtocol(serviceCfg.getProtocol())
                .setCapabilities(serviceCfg.getValkeyCapabilities())
                .setReconnectionDelay(config.getReconnectionDelay())
                .setCommandMapper(serviceManager.getCommandMapper())
                .setCredentialsResolver(serviceCfg.getCredentialsResolver())
                .setConnectedListener(addr -> {
                    if (!serviceManager.isShuttingDown()) {
                        NodeType nt = getNodeType(type, addr);
                        serviceManager.getConnectionEventsHub().fireConnect(addr, nt);
                    }
                })
                .setDisconnectedListener(addr -> {
                    if (!serviceManager.isShuttingDown()) {
                        NodeType nt = getNodeType(type, addr);
                        serviceManager.getConnectionEventsHub().fireDisconnect(addr, nt);
                    }
                });
        
        if (redisConfig.getCredentialsResolver() instanceof DefaultCredentialsResolver) {
            redisConfig.setCredentialsResolver(config.getCredentialsResolver());
        }
        if (redisConfig.getSslVerificationMode() == SslVerificationMode.STRICT && config.getSslVerificationMode() != SslVerificationMode.STRICT) {
            redisConfig.setSslVerificationMode(config.getSslVerificationMode());
        }
        if (redisConfig.getSslProvider() == SslProvider.JDK && config.getSslProvider() != SslProvider.JDK) {
            redisConfig.setSslProvider(config.getSslProvider());
        }
        
        if (type != NodeType.SENTINEL) {
            redisConfig.setDatabase(config.getDatabase());
        }
        
        return redisConfig;
    }

    private NodeType getNodeType(NodeType type, InetSocketAddress address) {
        if (getServiceManager().getCfg().isSingleConfig()) {
            return NodeType.MASTER;
        }

        if (type != NodeType.SENTINEL) {
            MasterSlaveEntry entry = getEntry(address);
            if (entry != null) {
                if (!entry.isInit()) {
                    return type;
                }
                InetSocketAddress addr = entry.getClient().getAddr();
                if (addr.getAddress().equals(address.getAddress())
                        && addr.getPort() == address.getPort()) {
                    return NodeType.MASTER;
                }
            }
            return NodeType.SLAVE;
        }
        return type;
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
    public int calcSlot(ByteBuf key) {
        return singleSlotRange.getStartSlot();
    }

    @Override
    public MasterSlaveEntry getEntry(InetSocketAddress address) {
        lazyConnect();

        return masterSlaveEntry;
    }

    @Override
    public MasterSlaveEntry getEntry(RedisURI addr) {
        lazyConnect();

        return masterSlaveEntry;
    }

    @Override
    public MasterSlaveEntry getEntry(RedisClient redisClient) {
        lazyConnect();

        return masterSlaveEntry;
    }

    @Override
    public MasterSlaveEntry getEntry(String name) {
        int slot = calcSlot(name);
        return getEntry(slot);
    }

    public MasterSlaveEntry getEntry(int slot) {
        lazyConnect();

        return masterSlaveEntry;
    }

    @Override
    public MasterSlaveEntry getWriteEntry(int slot) {
        return getEntry(slot);
    }

    @Override
    public MasterSlaveEntry getReadEntry(int slot) {
        return getEntry(slot);
    }

    protected CompletableFuture<RedisClient> changeMaster(int slot, RedisURI address) {
        MasterSlaveEntry entry = getEntry(slot);
        return entry.changeMaster(address);
    }

    protected void internalShutdown() {
        if (lazyConnectLatch.get() == null && lastAttempt) {
            shutdown();
        }
    }

    @Override
    public void shutdown() {
        shutdown(0, 10, TimeUnit.SECONDS); //default netty value
    }

    @Override
    public void shutdown(long quietPeriod, long timeout, TimeUnit unit) {
        if (dnsMonitor != null) {
            dnsMonitor.stop();
        }
        long timeoutInNanos = unit.toNanos(timeout);

        serviceManager.close();
        serviceManager.getConnectionWatcher().stop();
        serviceManager.getResolverGroup().close();

        long startTime = System.nanoTime();
        serviceManager.shutdownFutures(timeout, unit);
        timeoutInNanos = Math.max(0, timeoutInNanos - (System.nanoTime() - startTime));

        if (isInitialized()) {
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (MasterSlaveEntry entry : getEntrySet()) {
                futures.add(entry.shutdownAsync());
            }
            CompletableFuture<Void> future = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));

            try {
                startTime = System.nanoTime();
                future.get(timeoutInNanos, TimeUnit.NANOSECONDS);
                timeoutInNanos = Math.max(0, timeoutInNanos - (System.nanoTime() - startTime));
            } catch (Exception e) {
                // skip
            }
        }

        if (serviceManager.getCfg().getExecutor() == null) {
            serviceManager.getExecutor().shutdown();
            try {
                startTime = System.nanoTime();
                serviceManager.getExecutor().awaitTermination(timeoutInNanos, TimeUnit.NANOSECONDS);
                timeoutInNanos = Math.max(0, timeoutInNanos - (System.nanoTime() - startTime));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        serviceManager.getTimer().stop();

        if (serviceManager.getCfg().getEventLoopGroup() == null) {
            if (timeoutInNanos < quietPeriod) {
                quietPeriod = 0;
            }
            serviceManager.getGroup()
                    .shutdownGracefully(unit.toNanos(quietPeriod), timeoutInNanos, TimeUnit.NANOSECONDS)
                    .syncUninterruptibly();
        }
    }

    private boolean isInitialized() {
        return !serviceManager.getCfg().isLazyInitialization()
                    || (lazyConnectLatch.get() != null
                            && lazyConnectLatch.get().isDone()
                                && !lazyConnectLatch.get().isCompletedExceptionally());
    }

    @Override
    public PublishSubscribeService getSubscribeService() {
        return subscribeService;
    }

    @Override
    public RedisURI getLastClusterNode() {
        return null;
    }

    @Override
    public CommandAsyncExecutor createCommandExecutor(RedissonObjectBuilder objectBuilder, RedissonObjectBuilder.ReferenceType referenceType) {
        return CommandAsyncExecutor.create(this, objectBuilder, referenceType);
    }
}
