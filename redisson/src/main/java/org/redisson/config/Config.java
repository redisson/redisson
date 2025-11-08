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
package org.redisson.config;

import io.netty.channel.EventLoopGroup;
import org.redisson.api.NameMapper;
import org.redisson.client.DefaultCredentialsResolver;
import org.redisson.client.DefaultNettyHook;
import org.redisson.client.NettyHook;
import org.redisson.client.codec.Codec;
import org.redisson.codec.Kryo5Codec;
import org.redisson.connection.AddressResolverGroupFactory;
import org.redisson.connection.ConnectionListener;
import org.redisson.connection.SequentialDnsAddressResolverFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.URL;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.Executor;
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

    private String password;

    private String username;

    private CredentialsResolver credentialsResolver = new DefaultCredentialsResolver();

    private int threads = 16;

    private int nettyThreads = 32;

    private Executor nettyExecutor;

    private Codec codec;

    private ExecutorService executor;

    private boolean referenceEnabled = true;

    private TransportMode transportMode = TransportMode.NIO;

    private EventLoopGroup eventLoopGroup;

    private long lockWatchdogTimeout = 30 * 1000;

    private int lockWatchdogBatchSize = 100;

    private long fairLockWaitTimeout = 5 * 60000;

    private boolean checkLockSyncedSlaves = true;

    private long slavesSyncTimeout = 1000;

    private long reliableTopicWatchdogTimeout = TimeUnit.MINUTES.toMillis(10);

    private boolean keepPubSubOrder = true;

    private boolean useScriptCache = true;

    private int minCleanUpDelay = 5;

    private int maxCleanUpDelay = 30*60;

    private int cleanUpKeysAmount = 100;

    private NettyHook nettyHook = new DefaultNettyHook();

    private ConnectionListener connectionListener;

    private boolean useThreadClassLoader = true;

    private AddressResolverGroupFactory addressResolverGroupFactory = new SequentialDnsAddressResolverFactory();

    private boolean lazyInitialization;

    private Protocol protocol = Protocol.RESP2;

    private Set<ValkeyCapability> valkeyCapabilities = Collections.emptySet();

    private NameMapper nameMapper = NameMapper.direct();

    private CommandMapper commandMapper = CommandMapper.direct();
    
    private SslVerificationMode sslVerificationMode = SslVerificationMode.STRICT;
    
    private String sslKeystoreType;
    
    private SslProvider sslProvider = SslProvider.JDK;
    
    private URL sslTruststore;
    
    private String sslTruststorePassword;
    
    private URL sslKeystore;
    
    private String sslKeystorePassword;
    
    private String[] sslProtocols;
    
    private String[] sslCiphers;
    
    private TrustManagerFactory sslTrustManagerFactory;
    
    private KeyManagerFactory sslKeyManagerFactory;
    
    private boolean tcpKeepAlive;
    
    private int tcpKeepAliveCount;
    
    private int tcpKeepAliveIdle;
    
    private int tcpKeepAliveInterval;
    
    private int tcpUserTimeout;
    
    private boolean tcpNoDelay = true;

    public Config() {
    }

    public Config(Config oldConf) {
        setNettyHook(oldConf.getNettyHook());
        setNettyExecutor(oldConf.getNettyExecutor());
        setExecutor(oldConf.getExecutor());

        if (oldConf.getCodec() == null) {
            // use it by default
            oldConf.setCodec(new Kryo5Codec());
        }

        setConnectionListener(oldConf.getConnectionListener());
        setUseThreadClassLoader(oldConf.isUseThreadClassLoader());
        setMinCleanUpDelay(oldConf.getMinCleanUpDelay());
        setMaxCleanUpDelay(oldConf.getMaxCleanUpDelay());
        setCleanUpKeysAmount(oldConf.getCleanUpKeysAmount());
        setUseScriptCache(oldConf.isUseScriptCache());
        setKeepPubSubOrder(oldConf.isKeepPubSubOrder());
        setLockWatchdogTimeout(oldConf.getLockWatchdogTimeout());
        setLockWatchdogBatchSize(oldConf.getLockWatchdogBatchSize());
        setFairLockWaitTimeout(oldConf.getFairLockWaitTimeout());
        setCheckLockSyncedSlaves(oldConf.isCheckLockSyncedSlaves());
        setSlavesSyncTimeout(oldConf.getSlavesSyncTimeout());
        setNettyThreads(oldConf.getNettyThreads());
        setThreads(oldConf.getThreads());
        setUsername(oldConf.getUsername());
        setPassword(oldConf.getPassword());
        setCredentialsResolver(oldConf.getCredentialsResolver());
        setCodec(oldConf.getCodec());
        setReferenceEnabled(oldConf.isReferenceEnabled());
        setEventLoopGroup(oldConf.getEventLoopGroup());
        setTransportMode(oldConf.getTransportMode());
        setAddressResolverGroupFactory(oldConf.getAddressResolverGroupFactory());
        setReliableTopicWatchdogTimeout(oldConf.getReliableTopicWatchdogTimeout());
        setLazyInitialization(oldConf.isLazyInitialization());
        setProtocol(oldConf.getProtocol());
        setValkeyCapabilities(oldConf.getValkeyCapabilities());
        setNameMapper(oldConf.getNameMapper());
        setCommandMapper(oldConf.getCommandMapper());
        setSslProvider(oldConf.getSslProvider());
        setSslTruststore(oldConf.getSslTruststore());
        setSslTruststorePassword(oldConf.getSslTruststorePassword());
        setSslKeystoreType(oldConf.getSslKeystoreType());
        setSslKeystore(oldConf.getSslKeystore());
        setSslKeystorePassword(oldConf.getSslKeystorePassword());
        setSslProtocols(oldConf.getSslProtocols());
        setSslCiphers(oldConf.getSslCiphers());
        setSslKeyManagerFactory(oldConf.getSslKeyManagerFactory());
        setSslTrustManagerFactory(oldConf.getSslTrustManagerFactory());
        setSslVerificationMode(oldConf.getSslVerificationMode());

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
     * Redis data codec. Default is Kryo5Codec codec
     *
     * @see org.redisson.client.codec.Codec
     * @see org.redisson.codec.Kryo5Codec
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
            replicatedServersConfig = config;
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

    public boolean isSingleConfig() {
        return singleServerConfig != null;
    }

    /**
     * Password for Redis authentication. Should be null if not needed.
     * <p>
     * Default is <code>null</code>
     *
     * @param password for connection
     * @return config
     */
    public Config setPassword(String password) {
        this.password = password;
        return this;
    }

    public String getPassword() {
        return password;
    }

    /**
     * Username for Redis authentication. Should be null if not needed
     * <p>
     * Default is <code>null</code>
     * <p>
     * Requires Redis 6.0+
     *
     * @param username for connection
     * @return config
     */
    public Config setUsername(String username) {
        this.username = username;
        return this;
    }

    public String getUsername() {
        return username;
    }

    public CredentialsResolver getCredentialsResolver() {
        return credentialsResolver;
    }

    /**
     * Defines Credentials resolver which is invoked during connection for Redis server authentication.
     * It makes possible to specify dynamically changing Redis credentials.
     *
     * @see EntraIdCredentialsResolver
     *
     * @param credentialsResolver Credentials resolver object
     * @return config
     */
    public Config setCredentialsResolver(CredentialsResolver credentialsResolver) {
        this.credentialsResolver = credentialsResolver;
        return this;
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

    public Executor getNettyExecutor() {
        return nettyExecutor;
    }

    /**
     * Use external Executor for Netty.
     * <p>
     * For example, it allows to define <code>Executors.newVirtualThreadPerTaskExecutor()</code>
     * to use virtual threads.
     * <p>
     * The caller is responsible for closing the Executor.
     *
     * @param nettyExecutor netty executor object
     * @return config
     */
    public Config setNettyExecutor(Executor nettyExecutor) {
        this.nettyExecutor = nettyExecutor;
        return this;
    }

    /**
     * Use external ExecutorService. ExecutorService processes 
     * all listeners of <code>RTopic</code>, <code>RPatternTopic</code>
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
     * This parameter is only used if fair lock has been acquired without waitTimeout parameter definition
     *
     * Default is 5*60000 milliseconds
     *
     * @param fairLockWaitTimeout in milliseconds
     * @return config
     */
    public Config setFairLockWaitTimeout(long fairLockWaitTimeout) {
        this.fairLockWaitTimeout = fairLockWaitTimeout;
        return this;
    }

    public long getFairLockWaitTimeout() {
        return fairLockWaitTimeout;
    }

    /**
     * This parameter is only used if lock has been acquired without leaseTimeout parameter definition.
     * Defines amount of locks utilized in a single lock watchdog execution.
     * <p>
     * Default is 100
     *
     * @param lockWatchdogBatchSize amount of locks used by a single lock watchdog execution
     * @return config
     */
    public Config setLockWatchdogBatchSize(int lockWatchdogBatchSize) {
        this.lockWatchdogBatchSize = lockWatchdogBatchSize;
        return this;
    }
    public int getLockWatchdogBatchSize() {
        return lockWatchdogBatchSize;
    }

    /**
     * Defines whether to check synchronized slaves amount
     * with actual slaves amount after lock acquisition.
     * <p>
     * Default is <code>true</code>.
     *
     * @param checkLockSyncedSlaves <code>true</code> if check required,
     *                             <code>false</code> otherwise.
     * @return config
     */
    public Config setCheckLockSyncedSlaves(boolean checkLockSyncedSlaves) {
        this.checkLockSyncedSlaves = checkLockSyncedSlaves;
        return this;
    }

    public boolean isCheckLockSyncedSlaves() {
        return checkLockSyncedSlaves;
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
     * Default is <code>true</code>.
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

    public long getSlavesSyncTimeout() {
        return slavesSyncTimeout;
    }

    /**
     * Defines slaves synchronization timeout applied to each operation of {@link org.redisson.api.RLock},
     * {@link org.redisson.api.RSemaphore}, {@link org.redisson.api.RPermitExpirableSemaphore} objects.
     * <p>
     * Default is <code>1000</code> milliseconds.
     *
     * @param timeout timeout in milliseconds
     * @return config
     */
    public Config setSlavesSyncTimeout(long timeout) {
        this.slavesSyncTimeout = timeout;
        return this;
    }

    public boolean isLazyInitialization() {
        return lazyInitialization;
    }

    /**
     * Defines whether Redisson connects to Redis only when
     * first Redis call is made and not during Redisson instance creation.
     * <p>
     * Default value is <code>false</code>
     *
     * @param lazyInitialization <code>true</code> connects to Redis only when first Redis call is made,
     *                           <code>false</code> connects to Redis during Redisson instance creation.
     * @return config
     */
    public Config setLazyInitialization(boolean lazyInitialization) {
        this.lazyInitialization = lazyInitialization;
        return this;
    }

    public Protocol getProtocol() {
        return protocol;
    }

    /**
     * Defines Redis protocol version.
     * <p>
     * Default value is <code>RESP2</code>
     *
     * @param protocol Redis protocol version
     * @return config
     */
    public Config setProtocol(Protocol protocol) {
        this.protocol = protocol;
        return this;
    }

    public Set<ValkeyCapability> getValkeyCapabilities() {
        return valkeyCapabilities;
    }

    /**
     * Allows to declare which Valkey capabilities should be supported.
     *
     * @param valkeyCapabilities Valkey capabilites
     * @return config
     */
    public Config setValkeyCapabilities(Set<ValkeyCapability> valkeyCapabilities) {
        this.valkeyCapabilities = valkeyCapabilities;
        return this;
    }

    public NameMapper getNameMapper() {
        return nameMapper;
    }

    /**
     * Defines Name mapper which maps Redisson object name.
     * Applied to all Redisson objects.
     *
     * @param nameMapper name mapper object
     * @return config
     */
    public Config setNameMapper(NameMapper nameMapper) {
        this.nameMapper = nameMapper;
        return this;
    }

    public CommandMapper getCommandMapper() {
        return commandMapper;
    }

    /**
     * Defines Command mapper which maps Redis command name.
     * Applied to all Redis commands.
     *
     * @param commandMapper Redis command name mapper object
     * @return config
     */
    public Config setCommandMapper(CommandMapper commandMapper) {
        this.commandMapper = commandMapper;
        return this;
    }
    
    public SslProvider getSslProvider() {
        return sslProvider;
    }
    
    /**
     * Defines SSL provider used to handle SSL connections.
     * <p>
     * Default is <code>JDK</code>
     *
     * @param sslProvider ssl provider
     * @return config
     */
    public Config setSslProvider(SslProvider sslProvider) {
        this.sslProvider = sslProvider;
        return this;
    }
    
    public URL getSslTruststore() {
        return sslTruststore;
    }
    
    /**
     * Defines path to SSL truststore
     * <p>
     * Default is <code>null</code>
     *
     * @param sslTruststore truststore path
     * @return config
     */
    public Config setSslTruststore(URL sslTruststore) {
        this.sslTruststore = sslTruststore;
        return this;
    }
    
    public String getSslTruststorePassword() {
        return sslTruststorePassword;
    }
    
    /**
     * Defines password for SSL truststore.
     * SSL truststore is read on each new connection creation and can be dynamically reloaded.
     * <p>
     * Default is <code>null</code>
     *
     * @param sslTruststorePassword - password
     * @return config
     */
    public Config setSslTruststorePassword(String sslTruststorePassword) {
        this.sslTruststorePassword = sslTruststorePassword;
        return this;
    }
    
    public URL getSslKeystore() {
        return sslKeystore;
    }
    
    /**
     * Defines path to SSL keystore.
     * SSL keystore is read on each new connection creation and can be dynamically reloaded.
     * <p>
     * Default is <code>null</code>
     *
     * @param sslKeystore path to keystore
     * @return config
     */
    public Config setSslKeystore(URL sslKeystore) {
        this.sslKeystore = sslKeystore;
        return this;
    }
    
    public String getSslKeystorePassword() {
        return sslKeystorePassword;
    }
    
    /**
     * Defines password for SSL keystore
     * <p>
     * Default is <code>null</code>
     *
     * @param sslKeystorePassword password
     * @return config
     */
    public Config setSslKeystorePassword(String sslKeystorePassword) {
        this.sslKeystorePassword = sslKeystorePassword;
        return this;
    }
    
    public String[] getSslProtocols() {
        return sslProtocols;
    }
    
    /**
     * Defines SSL protocols.
     * Example values: TLSv1.3, TLSv1.2, TLSv1.1, TLSv1
     * <p>
     * Default is <code>null</code>
     *
     * @param sslProtocols protocols
     * @return config
     */
    public Config setSslProtocols(String[] sslProtocols) {
        this.sslProtocols = sslProtocols;
        return this;
    }
    
    public String getSslKeystoreType() {
        return sslKeystoreType;
    }
    
    /**
     * Defines SSL keystore type.
     * <p>
     * Default is <code>null</code>
     *
     * @param sslKeystoreType keystore type
     * @return config
     */
    public Config setSslKeystoreType(String sslKeystoreType) {
        this.sslKeystoreType = sslKeystoreType;
        return this;
    }
    
    public String[] getSslCiphers() {
        return sslCiphers;
    }
    
    /**
     * Defines SSL ciphers.
     * <p>
     * Default is <code>null</code>
     *
     * @param sslCiphers ciphers
     * @return config
     */
    public Config setSslCiphers(String[] sslCiphers) {
        this.sslCiphers = sslCiphers;
        return this;
    }
    
    public TrustManagerFactory getSslTrustManagerFactory() {
        return sslTrustManagerFactory;
    }
    
    /**
     * Defines SSL TrustManagerFactory.
     * <p>
     * Default is <code>null</code>
     *
     * @param trustManagerFactory trust manager value
     * @return config
     */
    public Config setSslTrustManagerFactory(TrustManagerFactory trustManagerFactory) {
        this.sslTrustManagerFactory = trustManagerFactory;
        return this;
    }
    
    public KeyManagerFactory getSslKeyManagerFactory() {
        return sslKeyManagerFactory;
    }
    
    /**
     * Defines SSL KeyManagerFactory.
     * <p>
     * Default is <code>null</code>
     *
     * @param keyManagerFactory key manager value
     * @return config
     */
    public Config setSslKeyManagerFactory(KeyManagerFactory keyManagerFactory) {
        this.sslKeyManagerFactory = keyManagerFactory;
        return this;
    }
    
    public SslVerificationMode getSslVerificationMode() {
        return sslVerificationMode;
    }
    
    /**
     * Defines SSL verification mode, which prevents man-in-the-middle attacks.
     *
     * <p>
     * Default is <code>SslVerificationMode.STRICT</code>
     *
     * @param sslVerificationMode mode value
     * @return config
     */
    public Config setSslVerificationMode(SslVerificationMode sslVerificationMode) {
        this.sslVerificationMode = sslVerificationMode;
        return this;
    }
    
    public boolean isTcpKeepAlive() {
        return tcpKeepAlive;
    }
    
    /**
     * Enables TCP keepAlive for connection
     * <p>
     * Default is <code>false</code>
     *
     * @param tcpKeepAlive boolean value
     * @return config
     */
    public Config setTcpKeepAlive(boolean tcpKeepAlive) {
        this.tcpKeepAlive = tcpKeepAlive;
        return this;
    }
    
    public int getTcpKeepAliveCount() {
        return tcpKeepAliveCount;
    }
    
    /**
     * Defines the maximum number of keepalive probes
     * TCP should send before dropping the connection.
     *
     * @param tcpKeepAliveCount maximum number of keepalive probes
     * @return config
     */
    public Config setTcpKeepAliveCount(int tcpKeepAliveCount) {
        this.tcpKeepAliveCount = tcpKeepAliveCount;
        return this;
    }
    
    public int getTcpKeepAliveIdle() {
        return tcpKeepAliveIdle;
    }
    
    /**
     * Defines the time in seconds the connection needs to remain idle
     * before TCP starts sending keepalive probes,
     *
     * @param tcpKeepAliveIdle time in seconds
     * @return config
     */
    public Config setTcpKeepAliveIdle(int tcpKeepAliveIdle) {
        this.tcpKeepAliveIdle = tcpKeepAliveIdle;
        return this;
    }
    
    public int getTcpKeepAliveInterval() {
        return tcpKeepAliveInterval;
    }
    
    /**
     * Defines the time in seconds between individual keepalive probes.
     *
     * @param tcpKeepAliveInterval time in seconds
     * @return config
     */
    public Config setTcpKeepAliveInterval(int tcpKeepAliveInterval) {
        this.tcpKeepAliveInterval = tcpKeepAliveInterval;
        return this;
    }
    
    public int getTcpUserTimeout() {
        return tcpUserTimeout;
    }
    
    /**
     * Defines the maximum amount of time in milliseconds that transmitted data may
     * remain unacknowledged, or buffered data may remain untransmitted
     * (due to zero window size) before TCP will forcibly close the connection.
     *
     * @param tcpUserTimeout time in milliseconds
     * @return config
     */
    public Config setTcpUserTimeout(int tcpUserTimeout) {
        this.tcpUserTimeout = tcpUserTimeout;
        return this;
    }
    
    public boolean isTcpNoDelay() {
        return tcpNoDelay;
    }
    
    /**
     * Enables TCP noDelay for connection
     * <p>
     * Default is <code>true</code>
     *
     * @param tcpNoDelay boolean value
     * @return config
     */
    public Config setTcpNoDelay(boolean tcpNoDelay) {
        this.tcpNoDelay = tcpNoDelay;
        return this;
    }
}
