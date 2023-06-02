/**
 * Copyright (c) 2013-2022 Nikita Koksharov
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

import org.redisson.api.NodeType;
import org.redisson.client.*;
import org.redisson.cluster.ClusterSlotRange;
import org.redisson.config.BaseConfig;
import org.redisson.config.BaseMasterSlaveServersConfig;
import org.redisson.config.MasterSlaveServersConfig;
import org.redisson.config.ReadMode;
import org.redisson.misc.RedisURI;
import org.redisson.pubsub.PublishSubscribeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.*;
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
    
    public MasterSlaveConnectionManager(BaseMasterSlaveServersConfig<?> cfg, ServiceManager serviceManager) {
        this.serviceManager = serviceManager;

        if (cfg instanceof MasterSlaveServersConfig) {
            this.config = (MasterSlaveServersConfig) cfg;
            if (this.config.getSlaveAddresses().isEmpty()
                    && (this.config.getReadMode() == ReadMode.SLAVE || this.config.getReadMode() == ReadMode.MASTER_SLAVE)) {
                throw new IllegalArgumentException("Slaves aren't defined. readMode can't be SLAVE or MASTER_SLAVE");
            }
        } else {
            this.config = create(cfg);
        }

        serviceManager.setConfig(this.config);
        serviceManager.initTimer();
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
    public boolean isClusterMode() {
        return false;
    }
    
    @Override
    public Collection<MasterSlaveEntry> getEntrySet() {
        if (masterSlaveEntry != null) {
            return Collections.singletonList(masterSlaveEntry);
        }
        return Collections.emptyList();
    }
    
    public void connect() {
        try {
            if (config.isSlaveNotUsed()) {
                masterSlaveEntry = new SingleEntry(this, serviceManager.getConnectionWatcher(), config);
            } else {
                masterSlaveEntry = new MasterSlaveEntry(this, serviceManager.getConnectionWatcher(), config);
            }
            CompletableFuture<RedisClient> masterFuture = masterSlaveEntry.setupMasterEntry(new RedisURI(config.getMasterAddress()));
            masterFuture.join();

            if (!config.isSlaveNotUsed()) {
                CompletableFuture<Void> fs = masterSlaveEntry.initSlaveBalancer(getDisconnectedNodes());
                fs.join();
            }

            startDNSMonitoring(masterFuture.getNow(null));
        } catch (Exception e) {
            shutdown();
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
        c.setSslCiphers(cfg.getSslCiphers());
        c.setSslKeyManagerFactory(cfg.getSslKeyManagerFactory());
        c.setSslTrustManagerFactory(cfg.getSslTrustManagerFactory());

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
        c.setCredentialsResolver(cfg.getCredentialsResolver());
        c.setCommandMapper(cfg.getCommandMapper());

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
                .setSslEnableEndpointIdentification(config.isSslEnableEndpointIdentification())
                .setSslProvider(config.getSslProvider())
                .setSslTruststore(config.getSslTruststore())
                .setSslTruststorePassword(config.getSslTruststorePassword())
                .setSslKeystore(config.getSslKeystore())
                .setSslKeystorePassword(config.getSslKeystorePassword())
                .setSslProtocols(config.getSslProtocols())
                .setSslCiphers(config.getSslCiphers())
                .setSslKeyManagerFactory(config.getSslKeyManagerFactory())
                .setSslTrustManagerFactory(config.getSslTrustManagerFactory())
                .setClientName(config.getClientName())
                .setKeepPubSubOrder(serviceManager.getCfg().isKeepPubSubOrder())
                .setPingConnectionInterval(config.getPingConnectionInterval())
                .setKeepAlive(config.isKeepAlive())
                .setTcpNoDelay(config.isTcpNoDelay())
                .setUsername(config.getUsername())
                .setPassword(config.getPassword())
                .setNettyHook(serviceManager.getCfg().getNettyHook())
                .setCommandMapper(config.getCommandMapper())
                .setCredentialsResolver(config.getCredentialsResolver())
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

        if (type != NodeType.SENTINEL) {
            redisConfig.setDatabase(config.getDatabase());
        }
        
        return redisConfig;
    }

    private NodeType getNodeType(NodeType type, InetSocketAddress address) {
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
    public MasterSlaveEntry getEntry(InetSocketAddress address) {
        return masterSlaveEntry;
    }

    @Override
    public MasterSlaveEntry getEntry(RedisURI addr) {
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

    protected MasterSlaveEntry getEntry(int slot) {
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
    
    @Override
    public void shutdown() {
        shutdown(0, 2, TimeUnit.SECONDS); //default netty value
    }

    @Override
    public void shutdown(long quietPeriod, long timeout, TimeUnit unit) {
        if (dnsMonitor != null) {
            dnsMonitor.stop();
        }

        serviceManager.getConnectionWatcher().stop();

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
        serviceManager.getResolverGroup().close();

        serviceManager.getShutdownLatch().close();
        if (serviceManager.getCfg().getExecutor() == null) {
            serviceManager.getExecutor().shutdown();
            try {
                serviceManager.getExecutor().awaitTermination(timeout, unit);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        serviceManager.getShutdownPromise().trySuccess(null);
        serviceManager.getShutdownLatch().awaitUninterruptibly();
        
        if (serviceManager.getCfg().getEventLoopGroup() == null) {
            serviceManager.getGroup().shutdownGracefully(quietPeriod, timeout, unit).syncUninterruptibly();
        }

        serviceManager.getTimer().stop();
    }

    @Override
    public PublishSubscribeService getSubscribeService() {
        return subscribeService;
    }

    @Override
    public RedisURI getLastClusterNode() {
        return null;
    }

}
