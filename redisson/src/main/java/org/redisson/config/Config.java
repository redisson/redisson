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
package org.redisson.config;

import io.netty.channel.EventLoopGroup;
import org.redisson.client.DefaultNettyHook;
import org.redisson.client.NettyHook;
import org.redisson.client.codec.Codec;
import org.redisson.codec.MarshallingCodec;
import org.redisson.connection.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Redisson configuration
 *
 * @author Nikita Koksharov
 *
 */
public class Config {

    static final Logger log = LoggerFactory.getLogger(Config.class);

    private SentinelServersConfig sentinelServersConfig;

    private MasterSlaveServersConfig masterSlaveServersConfig;

    private SingleServerConfig singleServerConfig;

    private ClusterServersConfig clusterServersConfig;

    private ReplicatedServersConfig replicatedServersConfig;

    private ConnectionManager connectionManager;

    private int threads = 16;

    private int nettyThreads = 32;

    private Codec codec;

    private ExecutorService executor;

    private boolean referenceEnabled = true;

    private TransportMode transportMode = TransportMode.NIO;

    private EventLoopGroup eventLoopGroup;

    private long lockWatchdogTimeout = 30 * 1000;

    private long reliableTopicWatchdogTimeout = TimeUnit.MINUTES.toMillis(10);

    private boolean keepPubSubOrder = true;

    private boolean useScriptCache = false;

    private int minCleanUpDelay = 5;

    private int maxCleanUpDelay = 30*60;

    private int cleanUpKeysAmount = 100;

    private NettyHook nettyHook = new DefaultNettyHook();

    private ConnectionListener connectionListener;

    private boolean useThreadClassLoader = true;

    private AddressResolverGroupFactory addressResolverGroupFactory = new DnsAddressResolverGroupFactory();

    public Config() {
    }

    public Config(Config oldConf) {
        setNettyHook(oldConf.getNettyHook());
        setExecutor(oldConf.getExecutor());

        if (oldConf.getCodec() == null) {
            // use it by default
            oldConf.setCodec(new MarshallingCodec());
        }

        setConnectionListener(oldConf.getConnectionListener());
        setUseThreadClassLoader(oldConf.isUseThreadClassLoader());
        setMinCleanUpDelay(oldConf.getMinCleanUpDelay());
        setMaxCleanUpDelay(oldConf.getMaxCleanUpDelay());
        setCleanUpKeysAmount(oldConf.getCleanUpKeysAmount());
        setUseScriptCache(oldConf.isUseScriptCache());
        setKeepPubSubOrder(oldConf.isKeepPubSubOrder());
        setLockWatchdogTimeout(oldConf.getLockWatchdogTimeout());
        setNettyThreads(oldConf.getNettyThreads());
        setThreads(oldConf.getThreads());
        setCodec(oldConf.getCodec());
        setReferenceEnabled(oldConf.isReferenceEnabled());
        setEventLoopGroup(oldConf.getEventLoopGroup());
        setTransportMode(oldConf.getTransportMode());
        setAddressResolverGroupFactory(oldConf.getAddressResolverGroupFactory());
        setReliableTopicWatchdogTimeout(oldConf.getReliableTopicWatchdogTimeout());

        if (oldConf.getSingleServerConfig() != null) {
            setSingleServerConfig(new SingleServerConfig(oldConf.getSingleServerConfig()));
        }
        if (oldConf.getMasterSlaveServersConfig() != null) {
            setMasterSlaveServersConfig(new MasterSlaveServersConfig(oldConf.getMasterSlaveServersConfig()));
        }
        if (oldConf.getSentinelServersConfig() != null) {
            setSentinelServersConfig(new SentinelServersConfig(oldConf.getSentinelServersConfig()));
        }
        if (oldConf.getClusterServersConfig() != null) {
            setClusterServersConfig(new ClusterServersConfig(oldConf.getClusterServersConfig()));
        }
        if (oldConf.getReplicatedServersConfig() != null) {
            setReplicatedServersConfig(new ReplicatedServersConfig(oldConf.getReplicatedServersConfig()));
        }
        if (oldConf.getConnectionManager() != null) {
            useCustomServers(oldConf.getConnectionManager());
        }

    }

    public NettyHook getNettyHook() {
        return nettyHook;
    }

    /**
     * Netty hook applied to Netty Bootstrap and Channel objects.
     *
     * @param nettyHook - netty hook object
     * @return config
     */
    public Config setNettyHook(NettyHook nettyHook) {
        this.nettyHook = nettyHook;
        return this;
    }

    /**
     * Redis data codec. Default is MarshallingCodec codec
     *
     * @see org.redisson.client.codec.Codec
     * @see org.redisson.codec.MarshallingCodec
     * 
     * @param codec object
     * @return config
     */
    public Config setCodec(Codec codec) {
        this.codec = codec;
        return this;
    }

    public Codec getCodec() {
        return codec;
    }

    /**
     * Config option indicate whether Redisson Reference feature is enabled.
     * <p>
     * Default value is <code>true</code>
     * 
     * @return <code>true</code> if Redisson Reference feature enabled
     */
    public boolean isReferenceEnabled() {
        return referenceEnabled;
    }

    /**
     * Config option for enabling Redisson Reference feature
     * <p>
     * Default value is <code>true</code>
     * 
     * @param redissonReferenceEnabled flag
     */
    public void setReferenceEnabled(boolean redissonReferenceEnabled) {
        this.referenceEnabled = redissonReferenceEnabled;
    }

    /**
     * Init cluster servers configuration
     *
     * @return config
     */
    public ClusterServersConfig useClusterServers() {
        return useClusterServers(new ClusterServersConfig());
    }

    ClusterServersConfig useClusterServers(ClusterServersConfig config) {
        checkMasterSlaveServersConfig();
        checkSentinelServersConfig();
        checkSingleServerConfig();
        checkReplicatedServersConfig();

        if (clusterServersConfig == null) {
            clusterServersConfig = config;
        }
        return clusterServersConfig;
    }

    protected ClusterServersConfig getClusterServersConfig() {
        return clusterServersConfig;
    }

    protected void setClusterServersConfig(ClusterServersConfig clusterServersConfig) {
        this.clusterServersConfig = clusterServersConfig;
    }

    /**
     * Init Replicated servers configuration.
     * Most used with Azure Redis Cache or AWS Elasticache
     *
     * @return ReplicatedServersConfig
     */
    public ReplicatedServersConfig useReplicatedServers() {
        return useReplicatedServers(new ReplicatedServersConfig());
    }

    ReplicatedServersConfig useReplicatedServers(ReplicatedServersConfig config) {
        checkClusterServersConfig();
        checkMasterSlaveServersConfig();
        checkSentinelServersConfig();
        checkSingleServerConfig();

        if (replicatedServersConfig == null) {
            replicatedServersConfig = new ReplicatedServersConfig();
        }
        return replicatedServersConfig;
    }

    protected ReplicatedServersConfig getReplicatedServersConfig() {
        return replicatedServersConfig;
    }

    protected void setReplicatedServersConfig(ReplicatedServersConfig replicatedServersConfig) {
        this.replicatedServersConfig = replicatedServersConfig;
    }

    /**
     * Returns the connection manager if supplied via
     * {@link #useCustomServers(ConnectionManager)}
     * 
     * @return ConnectionManager
     */
    ConnectionManager getConnectionManager() {
        return connectionManager;
    }

    /**
     * This is an extension point to supply custom connection manager.
     * 
     * @see ReplicatedConnectionManager on how to implement a connection
     *      manager.
     * @param connectionManager for supply
     */
    public void useCustomServers(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    /**
     * Init single server configuration.
     *
     * @return SingleServerConfig
     */
    public SingleServerConfig useSingleServer() {
        return useSingleServer(new SingleServerConfig());
    }

    SingleServerConfig useSingleServer(SingleServerConfig config) {
        checkClusterServersConfig();
        checkMasterSlaveServersConfig();
        checkSentinelServersConfig();
        checkReplicatedServersConfig();

        if (singleServerConfig == null) {
            singleServerConfig = config;
        }
        return singleServerConfig;
    }

    protected SingleServerConfig getSingleServerConfig() {
        return singleServerConfig;
    }

    protected void setSingleServerConfig(SingleServerConfig singleConnectionConfig) {
        this.singleServerConfig = singleConnectionConfig;
    }

    /**
     * Init sentinel servers configuration.
     *
     * @return SentinelServersConfig
     */
    public SentinelServersConfig useSentinelServers() {
        return useSentinelServers(new SentinelServersConfig());
    }

    SentinelServersConfig useSentinelServers(SentinelServersConfig sentinelServersConfig) {
        checkClusterServersConfig();
        checkSingleServerConfig();
        checkMasterSlaveServersConfig();
        checkReplicatedServersConfig();

        if (this.sentinelServersConfig == null) {
            this.sentinelServersConfig = sentinelServersConfig;
        }
        return this.sentinelServersConfig;
    }

    protected SentinelServersConfig getSentinelServersConfig() {
        return sentinelServersConfig;
    }

    protected void setSentinelServersConfig(SentinelServersConfig sentinelConnectionConfig) {
        this.sentinelServersConfig = sentinelConnectionConfig;
    }

    /**
     * Init master/slave servers configuration.
     *
     * @return MasterSlaveServersConfig
     */
    public MasterSlaveServersConfig useMasterSlaveServers() {
        return useMasterSlaveServers(new MasterSlaveServersConfig());
    }

    MasterSlaveServersConfig useMasterSlaveServers(MasterSlaveServersConfig config) {
        checkClusterServersConfig();
        checkSingleServerConfig();
        checkSentinelServersConfig();
        checkReplicatedServersConfig();

        if (masterSlaveServersConfig == null) {
            masterSlaveServersConfig = config;
        }
        return masterSlaveServersConfig;
    }

    protected MasterSlaveServersConfig getMasterSlaveServersConfig() {
        return masterSlaveServersConfig;
    }

    protected void setMasterSlaveServersConfig(MasterSlaveServersConfig masterSlaveConnectionConfig) {
        this.masterSlaveServersConfig = masterSlaveConnectionConfig;
    }

    public boolean isClusterConfig() {
        return clusterServersConfig != null;
    }

    public boolean isSentinelConfig() {
        return sentinelServersConfig != null;
    }

    public int getThreads() {
        return threads;
    }

    /**
     * Threads amount shared across all listeners of <code>RTopic</code> object, 
     * invocation handlers of <code>RRemoteService</code> object  
     * and <code>RExecutorService</code> tasks.
     * <p>
     * Default is <code>16</code>.
     * <p>
     * <code>0</code> means <code>current_processors_amount * 2</code>
     *
     * @param threads amount
     * @return config
     */
    public Config setThreads(int threads) {
        this.threads = threads;
        return this;
    }

    private void checkClusterServersConfig() {
        if (clusterServersConfig != null) {
            throw new IllegalStateException("cluster servers config already used!");
        }
    }

    private void checkSentinelServersConfig() {
        if (sentinelServersConfig != null) {
            throw new IllegalStateException("sentinel servers config already used!");
        }
    }

    private void checkMasterSlaveServersConfig() {
        if (masterSlaveServersConfig != null) {
            throw new IllegalStateException("master/slave servers already used!");
        }
    }

    private void checkSingleServerConfig() {
        if (singleServerConfig != null) {
            throw new IllegalStateException("single server config already used!");
        }
    }

    private void checkReplicatedServersConfig() {
        if (replicatedServersConfig != null) {
            throw new IllegalStateException("Replication servers config already used!");
        }
    }

    /**
     * Transport mode
     * <p>
     * Default is {@link TransportMode#NIO}
     *
     * @param transportMode param
     * @return config
     */
    public Config setTransportMode(TransportMode transportMode) {
        this.transportMode = transportMode;
        return this;
    }

    public TransportMode getTransportMode() {
        return transportMode;
    }

    /**
     * Threads amount shared between all redis clients used by Redisson.
     * <p>
     * Default is <code>32</code>.
     * <p>
     * <code>0</code> means <code>current_processors_amount * 2</code>
     *
     * @param nettyThreads amount
     * @return config
     */
    public Config setNettyThreads(int nettyThreads) {
        this.nettyThreads = nettyThreads;
        return this;
    }

    public int getNettyThreads() {
        return nettyThreads;
    }

    /**
     * Use external ExecutorService. ExecutorService processes 
     * all listeners of <code>RTopic</code>, 
     * <code>RRemoteService</code> invocation handlers  
     * and <code>RExecutorService</code> tasks.
     * <p>
     * The caller is responsible for closing the ExecutorService.
     * 
     * @param executor object
     * @return config
     */
    public Config setExecutor(ExecutorService executor) {
        this.executor = executor;
        return this;
    }

    public ExecutorService getExecutor() {
        return executor;
    }

    /**
     * Use external EventLoopGroup. EventLoopGroup processes all
     * Netty connection tied to Redis servers. Each EventLoopGroup creates
     * own threads and each Redisson client creates own EventLoopGroup by default.
     * So if there are multiple Redisson instances in same JVM
     * it would be useful to share one EventLoopGroup among them.
     * <p>
     * Only {@link io.netty.channel.epoll.EpollEventLoopGroup}, 
     * {@link io.netty.channel.kqueue.KQueueEventLoopGroup}
     * {@link io.netty.channel.nio.NioEventLoopGroup} can be used.
     * <p>
     * The caller is responsible for closing the EventLoopGroup.
     *
     * @param eventLoopGroup object
     * @return config
     */
    public Config setEventLoopGroup(EventLoopGroup eventLoopGroup) {
        this.eventLoopGroup = eventLoopGroup;
        return this;
    }

    public EventLoopGroup getEventLoopGroup() {
        return eventLoopGroup;
    }

    /**
     * This parameter is only used if lock has been acquired without leaseTimeout parameter definition. 
     * Lock expires after <code>lockWatchdogTimeout</code> if watchdog 
     * didn't extend it to next <code>lockWatchdogTimeout</code> time interval.
     * <p>  
     * This prevents against infinity locked locks due to Redisson client crush or 
     * any other reason when lock can't be released in proper way.
     * <p>
     * Default is 30000 milliseconds
     * 
     * @param lockWatchdogTimeout timeout in milliseconds
     * @return config
     */
    public Config setLockWatchdogTimeout(long lockWatchdogTimeout) {
        this.lockWatchdogTimeout = lockWatchdogTimeout;
        return this;
    }

    public long getLockWatchdogTimeout() {
        return lockWatchdogTimeout;
    }

    /**
     * Defines whether to keep PubSub messages handling in arrival order 
     * or handle messages concurrently. 
     * <p>
     * This setting applied only for PubSub messages per channel.
     * <p>
     * Default is <code>true</code>.
     * 
     * @param keepPubSubOrder - <code>true</code> if order required, <code>false</code> otherwise.
     * @return config
     */
    public Config setKeepPubSubOrder(boolean keepPubSubOrder) {
        this.keepPubSubOrder = keepPubSubOrder;
        return this;
    }

    public boolean isKeepPubSubOrder() {
        return keepPubSubOrder;
    }

    /**
     * Used to switch between {@link io.netty.resolver.dns.DnsAddressResolverGroup} implementations.
     * Switch to round robin {@link io.netty.resolver.dns.RoundRobinDnsAddressResolverGroup} when you need to optimize the url resolving.
     * 
     * @param addressResolverGroupFactory
     * @return config
     */
    public Config setAddressResolverGroupFactory(AddressResolverGroupFactory addressResolverGroupFactory) {
        this.addressResolverGroupFactory = addressResolverGroupFactory;
        return this;
    }

    public AddressResolverGroupFactory getAddressResolverGroupFactory() {
        return addressResolverGroupFactory;
    }

    @Deprecated
    public static Config fromJSON(String content) throws IOException {
        log.error("JSON configuration is deprecated and will be removed in future!");
        ConfigSupport support = new ConfigSupport();
        return support.fromJSON(content, Config.class);
    }

    @Deprecated
    public static Config fromJSON(InputStream inputStream) throws IOException {
        log.error("JSON configuration is deprecated and will be removed in future!");
        ConfigSupport support = new ConfigSupport();
        return support.fromJSON(inputStream, Config.class);
    }

    @Deprecated
    public static Config fromJSON(File file, ClassLoader classLoader) throws IOException {
        log.error("JSON configuration is deprecated and will be removed in future!");
        ConfigSupport support = new ConfigSupport();
        return support.fromJSON(file, Config.class, classLoader);
    }

    @Deprecated
    public static Config fromJSON(File file) throws IOException {
        log.error("JSON configuration is deprecated and will be removed in future!");
        return fromJSON(file, null);
    }

    @Deprecated
    public static Config fromJSON(URL url) throws IOException {
        log.error("JSON configuration is deprecated and will be removed in future!");
        ConfigSupport support = new ConfigSupport();
        return support.fromJSON(url, Config.class);
    }

    @Deprecated
    public static Config fromJSON(Reader reader) throws IOException {
        log.error("JSON configuration is deprecated and will be removed in future!");
        ConfigSupport support = new ConfigSupport();
        return support.fromJSON(reader, Config.class);
    }

    @Deprecated
    public String toJSON() throws IOException {
        log.error("JSON configuration is deprecated and will be removed in future!");
        ConfigSupport support = new ConfigSupport();
        return support.toJSON(this);
    }

    /**
     * Read config object stored in YAML format from <code>String</code>
     *
     * @param content of config
     * @return config
     * @throws IOException error
     */
    public static Config fromYAML(String content) throws IOException {
        ConfigSupport support = new ConfigSupport();
        return support.fromYAML(content, Config.class);
    }

    /**
     * Read config object stored in YAML format from <code>InputStream</code>
     *
     * @param inputStream object
     * @return config
     * @throws IOException error
     */
    public static Config fromYAML(InputStream inputStream) throws IOException {
        ConfigSupport support = new ConfigSupport();
        return support.fromYAML(inputStream, Config.class);
    }

    /**
     * Read config object stored in YAML format from <code>File</code>
     *
     * @param file object
     * @return config
     * @throws IOException error
     */
    public static Config fromYAML(File file) throws IOException {
        return fromYAML(file, null);
    }

    public static Config fromYAML(File file, ClassLoader classLoader) throws IOException {
        ConfigSupport support = new ConfigSupport();
        return support.fromYAML(file, Config.class, classLoader);
    }

    /**
     * Read config object stored in YAML format from <code>URL</code>
     *
     * @param url object
     * @return config
     * @throws IOException error
     */
    public static Config fromYAML(URL url) throws IOException {
        ConfigSupport support = new ConfigSupport();
        return support.fromYAML(url, Config.class);
    }

    /**
     * Read config object stored in YAML format from <code>Reader</code>
     *
     * @param reader object
     * @return config
     * @throws IOException error
     */
    public static Config fromYAML(Reader reader) throws IOException {
        ConfigSupport support = new ConfigSupport();
        return support.fromYAML(reader, Config.class);
    }

    /**
     * Convert current configuration to YAML format
     *
     * @return config in yaml format
     * @throws IOException error
     */
    public String toYAML() throws IOException {
        ConfigSupport support = new ConfigSupport();
        return support.toYAML(this);
    }

    /**
     * Defines whether to use Lua-script cache on Redis side. 
     * Most Redisson methods are Lua-script based and this setting turned
     * on could increase speed of such methods execution and save network traffic.
     * <p>
     * Default is <code>false</code>.
     * 
     * @param useScriptCache - <code>true</code> if Lua-script caching is required, <code>false</code> otherwise.
     * @return config
     */
    public Config setUseScriptCache(boolean useScriptCache) {
        this.useScriptCache = useScriptCache;
        return this;
    }

    public boolean isUseScriptCache() {
        return useScriptCache;
    }

    public int getMinCleanUpDelay() {
        return minCleanUpDelay;
    }
    
    /**
     * Defines minimum delay in seconds for clean up process of expired entries.
     * <p>
     * Applied to JCache, RSetCache, RMapCache, RListMultimapCache, RSetMultimapCache objects.
     * <p>
     * Default is <code>5</code>.
     * 
     * @param minCleanUpDelay - delay in seconds
     * @return config
     */
    public Config setMinCleanUpDelay(int minCleanUpDelay) {
        this.minCleanUpDelay = minCleanUpDelay;
        return this;
    }

    public int getMaxCleanUpDelay() {
        return maxCleanUpDelay;
    }
    
    /**
     * Defines maximum delay in seconds for clean up process of expired entries.
     * <p>
     * Applied to JCache, RSetCache, RMapCache, RListMultimapCache, RSetMultimapCache objects.
     * <p>
     * Default is <code>1800</code>.
     *
     * @param maxCleanUpDelay - delay in seconds
     * @return config
     */
    public Config setMaxCleanUpDelay(int maxCleanUpDelay) {
        this.maxCleanUpDelay = maxCleanUpDelay;
        return this;
    }

    public int getCleanUpKeysAmount() {
        return cleanUpKeysAmount;
    }

    /**
     * Defines expired keys amount deleted per single operation during clean up process of expired entries.
     * <p>
     * Applied to JCache, RSetCache, RMapCache, RListMultimapCache, RSetMultimapCache objects.
     * <p>
     * Default is <code>100</code>.
     *
     * @param cleanUpKeysAmount - delay in seconds
     * @return config
     */
    public Config setCleanUpKeysAmount(int cleanUpKeysAmount) {
        this.cleanUpKeysAmount = cleanUpKeysAmount;
        return this;
    }

    public boolean isUseThreadClassLoader() {
        return useThreadClassLoader;
    }

    /**
     * Defines whether to supply Thread ContextClassLoader to Codec.
     * Usage of Thread.getContextClassLoader() may resolve ClassNotFoundException error.
     * For example, this error arise if Redisson is used in both Tomcat and deployed application.
     * <p>
     * Default is <code>true</code>.
     *
     * @param useThreadClassLoader <code>true</code> if Thread ContextClassLoader is used, <code>false</code> otherwise.
     * @return config
     */
    public Config setUseThreadClassLoader(boolean useThreadClassLoader) {
        this.useThreadClassLoader = useThreadClassLoader;
        return this;
    }

    public long getReliableTopicWatchdogTimeout() {
        return reliableTopicWatchdogTimeout;
    }

    /**
     * Reliable Topic subscriber expires after <code>timeout</code> if watchdog
     * didn't extend it to next <code>timeout</code> time interval.
     * <p>
     * This prevents against infinity grow of stored messages in topic due to Redisson client crush or
     * any other reason when subscriber can't consumer messages anymore.
     * <p>
     * Default is 600000 milliseconds
     *
     * @param timeout timeout in milliseconds
     * @return config
     */
    public Config setReliableTopicWatchdogTimeout(long timeout) {
        this.reliableTopicWatchdogTimeout = timeout;
        return this;
    }

    public ConnectionListener getConnectionListener() {
        return connectionListener;
    }

    /**
     * Sets connection listener which is triggered
     * when Redisson connected/disconnected to Redis server
     *
     * @param connectionListener - connection listener
     * @return config
     */
    public Config setConnectionListener(ConnectionListener connectionListener) {
        this.connectionListener = connectionListener;
        return this;
    }
}
