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

import org.redisson.api.NameMapper;
import org.redisson.client.DefaultCredentialsResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import java.net.URL;
import java.time.Duration;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <T> config type
 */
public class BaseConfig<T extends BaseConfig<T>> {

    protected static final Logger log = LoggerFactory.getLogger("config");

    /**
     * If pooled connection not used for a <code>timeout</code> time
     * and current connections amount bigger than minimum idle connections pool size,
     * then it will closed and removed from pool.
     * Value in milliseconds.
     *
     */
    private int idleConnectionTimeout = 10000;

    /**
     * Timeout during connecting to any Redis server.
     * Value in milliseconds.
     *
     */
    private int connectTimeout = 10000;

    /**
     * Redis server response timeout. Starts to countdown when Redis command was succesfully sent.
     * Value in milliseconds.
     *
     */
    private int timeout = 3000;

    private int subscriptionTimeout = 7500;

    private int retryAttempts = 4;

    @Deprecated
    private int retryInterval = 1500;

    private DelayStrategy retryDelay = new EqualJitterDelay(Duration.ofMillis(1000), Duration.ofSeconds(2));

    private DelayStrategy reconnectionDelay = new EqualJitterDelay(Duration.ofMillis(100), Duration.ofSeconds(10));

    /**
     * Password for Redis authentication. Should be null if not needed
     */
    private String password;

    private String username;

    private CredentialsResolver credentialsResolver = new DefaultCredentialsResolver();

    private int credentialsReapplyInterval = 0;

    /**
     * Subscriptions per Redis connection limit
     */
    private int subscriptionsPerConnection = 5;

    /**
     * Name of client connection
     */
    private String clientName;

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

    private int pingConnectionInterval = 30000;

    private boolean keepAlive;

    private int tcpKeepAliveCount;

    private int tcpKeepAliveIdle;

    private int tcpKeepAliveInterval;

    private int tcpUserTimeout;

    private boolean tcpNoDelay = true;

    private NameMapper nameMapper = NameMapper.direct();

    private CommandMapper commandMapper = CommandMapper.direct();
    
    BaseConfig() {
    }

    BaseConfig(T config) {
        setPassword(config.getPassword());
        setUsername(config.getUsername());
        setSubscriptionsPerConnection(config.getSubscriptionsPerConnection());
        setRetryAttempts(config.getRetryAttempts());
        setRetryDelay(config.getRetryDelay());
        setReconnectionDelay(config.getReconnectionDelay());
        setTimeout(config.getTimeout());
        setClientName(config.getClientName());
        setConnectTimeout(config.getConnectTimeout());
        setIdleConnectionTimeout(config.getIdleConnectionTimeout());
        setSslProvider(config.getSslProvider());
        setSslTruststore(config.getSslTruststore());
        setSslTruststorePassword(config.getSslTruststorePassword());
        setSslKeystoreType(config.getSslKeystoreType());
        setSslKeystore(config.getSslKeystore());
        setSslKeystorePassword(config.getSslKeystorePassword());
        setSslProtocols(config.getSslProtocols());
        setSslCiphers(config.getSslCiphers());
        setSslKeyManagerFactory(config.getSslKeyManagerFactory());
        setSslTrustManagerFactory(config.getSslTrustManagerFactory());
        setPingConnectionInterval(config.getPingConnectionInterval());
        setKeepAlive(config.isKeepAlive());
        setTcpKeepAliveCount(config.getTcpKeepAliveCount());
        setTcpKeepAliveIdle(config.getTcpKeepAliveIdle());
        setTcpKeepAliveInterval(config.getTcpKeepAliveInterval());
        setTcpUserTimeout(config.getTcpUserTimeout());
        setTcpNoDelay(config.isTcpNoDelay());
        setNameMapper(config.getNameMapper());
        setCredentialsResolver(config.getCredentialsResolver());
        setCredentialsReapplyInterval(config.getCredentialsReapplyInterval());
        setCommandMapper(config.getCommandMapper());
        setSslVerificationMode(config.getSslVerificationMode());
        setSubscriptionTimeout(config.getSubscriptionTimeout());
    }

    /**
     * Subscriptions per Redis connection limit
     * <p>
     * Default is <code>5</code>
     *
     * @param subscriptionsPerConnection amount
     * @return config
     */
    public T setSubscriptionsPerConnection(int subscriptionsPerConnection) {
        this.subscriptionsPerConnection = subscriptionsPerConnection;
        return (T) this;
    }

    public int getSubscriptionsPerConnection() {
        return subscriptionsPerConnection;
    }

    /**
     * Password for Redis authentication. Should be null if not needed.
     * <p>
     * Default is <code>null</code>
     *
     * @param password for connection
     * @return config
     */
    public T setPassword(String password) {
        this.password = password;
        return (T) this;
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
    public T setUsername(String username) {
        this.username = username;
        return (T) this;
    }

    public String getUsername() {
        return username;
    }

    /**
     * Error will be thrown if Redis command can't be sent to Redis server after <code>retryAttempts</code>.
     * But if it sent successfully then <code>timeout</code> will be started.
     * <p>
     * Default is <code>3</code> attempts
     *
     * @see #timeout
     * @param retryAttempts retry attempts
     * @return config
     */
    public T setRetryAttempts(int retryAttempts) {
        if (retryAttempts < 0 || retryAttempts == Integer.MAX_VALUE) {
            throw new IllegalArgumentException("retryAttempts setting can't be negative or MAX_VALUE");
        }

        this.retryAttempts = retryAttempts;
        return (T) this;

    }

    public int getRetryAttempts() {
        return retryAttempts;
    }

    /**
     * Defines time interval for another one attempt send Redis command 
     * if it hasn't been sent already.
     * <p>
     * Default is <code>1500</code> milliseconds
     *
     * @param retryInterval - time in milliseconds
     * @return config
     */
    @Deprecated
    public T setRetryInterval(int retryInterval) {
        this.retryInterval = retryInterval;
        this.retryDelay = new ConstantDelay(Duration.ofMillis(retryInterval));
        return (T) this;
    }

    @Deprecated
    public int getRetryInterval() {
        return retryInterval;
    }

    /**
     * Redis server response timeout. Starts to countdown when Redis command has been successfully sent.
     * <p>
     * Default is <code>3000</code> milliseconds
     *
     * @param timeout in milliseconds
     * @return config
     */
    public T setTimeout(int timeout) {
        this.timeout = timeout;
        return (T) this;
    }

    public int getTimeout() {
        return timeout;
    }

    public int getSubscriptionTimeout() {
        return subscriptionTimeout;
    }

    /**
     * Defines subscription timeout applied per channel subscription.
     * <p>
     * Default is <code>7500</code> milliseconds.
     *
     * @param subscriptionTimeout timeout in milliseconds
     * @return config
     */
    public T setSubscriptionTimeout(int subscriptionTimeout) {
        this.subscriptionTimeout = subscriptionTimeout;
        return (T) this;
    }

    /**
     * Setup connection name during connection init
     * via CLIENT SETNAME command
     * <p>
     * Default is <code>null</code>
     *
     * @param clientName name of client
     * @return config
     */
    public T setClientName(String clientName) {
        this.clientName = clientName;
        return (T) this;
    }

    public String getClientName() {
        return clientName;
    }

    /**
     * Timeout during connecting to any Redis server.
     * <p>
     * Default is <code>10000</code> milliseconds.
     * 
     * @param connectTimeout timeout in milliseconds
     * @return config
     */
    public T setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
        return (T) this;
    }

    public int getConnectTimeout() {
        return connectTimeout;
    }

    /**
     * If pooled connection not used for a <code>timeout</code> time
     * and current connections amount bigger than minimum idle connections pool size,
     * then it will closed and removed from pool.
     * <p>
     * Default is <code>10000</code> milliseconds.
     *
     * @param idleConnectionTimeout timeout in milliseconds
     * @return config
     */
    public T setIdleConnectionTimeout(int idleConnectionTimeout) {
        this.idleConnectionTimeout = idleConnectionTimeout;
        return (T) this;
    }

    public int getIdleConnectionTimeout() {
        return idleConnectionTimeout;
    }

    @Deprecated
    public boolean isSslEnableEndpointIdentification() {
        return this.sslVerificationMode == SslVerificationMode.STRICT;
    }

    /**
     * Use {@link #setSslVerificationMode(SslVerificationMode)} instead.
     * 
     * @param sslEnableEndpointIdentification boolean value
     * @return config
     */
    @Deprecated
    public T setSslEnableEndpointIdentification(boolean sslEnableEndpointIdentification) {
        log.warn("sslEnableEndpointIdentification setting is deprecated. Use sslVerificationMode setting instead.");
        if (sslEnableEndpointIdentification) {
            this.sslVerificationMode = SslVerificationMode.STRICT;
        } else {
            this.sslVerificationMode = SslVerificationMode.NONE;
        }
        return (T) this;
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
    public T setSslProvider(SslProvider sslProvider) {
        this.sslProvider = sslProvider;
        return (T) this;
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
    public T setSslTruststore(URL sslTruststore) {
        this.sslTruststore = sslTruststore;
        return (T) this;
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
    public T setSslTruststorePassword(String sslTruststorePassword) {
        this.sslTruststorePassword = sslTruststorePassword;
        return (T) this;
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
    public T setSslKeystore(URL sslKeystore) {
        this.sslKeystore = sslKeystore;
        return (T) this;
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
    public T setSslKeystorePassword(String sslKeystorePassword) {
        this.sslKeystorePassword = sslKeystorePassword;
        return (T) this;
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
    public T setSslProtocols(String[] sslProtocols) {
        this.sslProtocols = sslProtocols;
        return (T) this;
    }

    public int getPingConnectionInterval() {
        return pingConnectionInterval;
    }

    /**
     * Defines PING command sending interval per connection to Redis.
     * <code>0</code> means disable.
     * <p>
     * Default is <code>30000</code>
     * 
     * @param pingConnectionInterval time in milliseconds
     * @return config
     */
    public T setPingConnectionInterval(int pingConnectionInterval) {
        this.pingConnectionInterval = pingConnectionInterval;
        return (T) this;
    }

    public boolean isKeepAlive() {
        return keepAlive;
    }

    /**
     * Enables TCP keepAlive for connection
     * <p>
     * Default is <code>false</code>
     * 
     * @param keepAlive boolean value
     * @return config
     */
    public T setKeepAlive(boolean keepAlive) {
        this.keepAlive = keepAlive;
        return (T) this;
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
    public T setTcpKeepAliveCount(int tcpKeepAliveCount) {
        this.tcpKeepAliveCount = tcpKeepAliveCount;
        return (T) this;
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
    public T setTcpKeepAliveIdle(int tcpKeepAliveIdle) {
        this.tcpKeepAliveIdle = tcpKeepAliveIdle;
        return (T) this;
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
    public T setTcpKeepAliveInterval(int tcpKeepAliveInterval) {
        this.tcpKeepAliveInterval = tcpKeepAliveInterval;
        return (T) this;
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
    public T setTcpUserTimeout(int tcpUserTimeout) {
        this.tcpUserTimeout = tcpUserTimeout;
        return (T) this;
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
    public T setTcpNoDelay(boolean tcpNoDelay) {
        this.tcpNoDelay = tcpNoDelay;
        return (T) this;
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
    public T setNameMapper(NameMapper nameMapper) {
        this.nameMapper = nameMapper;
        return (T) this;
    }

    public CredentialsResolver getCredentialsResolver() {
        return credentialsResolver;
    }

    /**
     * Defines Credentials resolver which is invoked during connection for Redis server authentication.
     * It makes possible to specify dynamically changing Redis credentials.
     *
     * @param credentialsResolver Credentials resolver object
     * @return config
     */
    public T setCredentialsResolver(CredentialsResolver credentialsResolver) {
        this.credentialsResolver = credentialsResolver;
        return (T) this;
    }

    public int getCredentialsReapplyInterval() {
        return credentialsReapplyInterval;
    }

    /**
     * Defines Credentials resolver invoke interval for Valkey or Redis server authentication.
     * <code>0</code> means disable.
     * <p>
     * Default is <code>0</code>
     *
     * @param credentialsReapplyInterval time in milliseconds
     * @return config
     */
    public T setCredentialsReapplyInterval(int credentialsReapplyInterval) {
        this.credentialsReapplyInterval = credentialsReapplyInterval;
        return (T) this;
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
    public T setSslKeystoreType(String sslKeystoreType) {
        this.sslKeystoreType = sslKeystoreType;
        return (T) this;
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
    public T setSslCiphers(String[] sslCiphers) {
        this.sslCiphers = sslCiphers;
        return (T) this;
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
    public T setSslTrustManagerFactory(TrustManagerFactory trustManagerFactory) {
        this.sslTrustManagerFactory = trustManagerFactory;
        return (T) this;
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
    public BaseConfig<T> setSslKeyManagerFactory(KeyManagerFactory keyManagerFactory) {
        this.sslKeyManagerFactory = keyManagerFactory;
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
    public T setCommandMapper(CommandMapper commandMapper) {
        this.commandMapper = commandMapper;
        return (T) this;
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
    public T setSslVerificationMode(SslVerificationMode sslVerificationMode) {
        this.sslVerificationMode = sslVerificationMode;
        return (T) this;
    }

    public DelayStrategy getRetryDelay() {
        return retryDelay;
    }

    /**
     * Defines the delay strategy for a new attempt to send a command.
     * <p>
     * Default is <code>EqualJitterDelay(Duration.ofSeconds(1), Duration.ofSeconds(2))</code>
     *
     * @see DecorrelatedJitterDelay
     * @see EqualJitterDelay
     * @see FullJitterDelay
     * @see ConstantDelay
     *
     * @param retryDelay delay strategy implementation
     * @return options instance
     */
    public T setRetryDelay(DelayStrategy retryDelay) {
        this.retryDelay = retryDelay;
        return (T) this;
    }

    public DelayStrategy getReconnectionDelay() {
        return reconnectionDelay;
    }

    /**
     * Defines the delay strategy for a new attempt to reconnect a connection.
     * <p>
     * Default is <code>EqualJitterDelay(Duration.ofMillis(100), Duration.ofSeconds(10))</code>
     *
     * @see DecorrelatedJitterDelay
     * @see EqualJitterDelay
     * @see FullJitterDelay
     * @see ConstantDelay
     *
     * @param reconnectionDelay delay strategy implementation
     * @return options instance
     */
    public T setReconnectionDelay(DelayStrategy reconnectionDelay) {
        this.reconnectionDelay = reconnectionDelay;
        return (T) this;
    }
}
