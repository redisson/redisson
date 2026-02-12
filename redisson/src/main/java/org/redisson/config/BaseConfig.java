/**
 * Copyright (c) 2013-2026 Nikita Koksharov
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
    @Deprecated
    private String password;

    @Deprecated
    private String username;

    @Deprecated
    private CredentialsResolver credentialsResolver = new DefaultCredentialsResolver();

    /**
     * Subscriptions per Redis connection limit
     */
    private int subscriptionsPerConnection = 5;

    /**
     * Name of client connection
     */
    private String clientName;
    
    @Deprecated
    private SslVerificationMode sslVerificationMode = SslVerificationMode.STRICT;
    
    @Deprecated
    private String sslKeystoreType;
    
    @Deprecated
    private SslProvider sslProvider = SslProvider.JDK;
    
    @Deprecated
    private URL sslTruststore;
    
    @Deprecated
    private String sslTruststorePassword;
    
    @Deprecated
    private URL sslKeystore;
    
    @Deprecated
    private String sslKeystorePassword;
    
    @Deprecated
    private String[] sslProtocols;
    
    @Deprecated
    private String[] sslCiphers;
    
    @Deprecated
    private TrustManagerFactory sslTrustManagerFactory;
    
    @Deprecated
    private KeyManagerFactory sslKeyManagerFactory;

    private int pingConnectionInterval = 30000;
    
    @Deprecated
    private boolean keepAlive;
    
    @Deprecated
    private int tcpKeepAliveCount;
    
    @Deprecated
    private int tcpKeepAliveIdle;
    
    @Deprecated
    private int tcpKeepAliveInterval;
    
    @Deprecated
    private int tcpUserTimeout;
    
    @Deprecated
    private boolean tcpNoDelay = true;

    @Deprecated
    private NameMapper nameMapper = NameMapper.direct();

    @Deprecated
    private CommandMapper commandMapper = CommandMapper.direct();
    
    BaseConfig() {
    }

    BaseConfig(T config) {
        if (config.getUsername() != null) {
            setUsername(config.getUsername());
        }
        if (config.getPassword() != null) {
            setPassword(config.getPassword());
        }
        if (!(config.getNameMapper() instanceof DefaultNameMapper)) {
            setNameMapper(config.getNameMapper());
        }
        if (!(config.getCommandMapper() instanceof DefaultCommandMapper)) {
            setCommandMapper(config.getCommandMapper());
        }
        if (!(config.getCredentialsResolver() instanceof DefaultCredentialsResolver)) {
            setCredentialsResolver(config.getCredentialsResolver());
        }
        
        if (config.getSslVerificationMode() != SslVerificationMode.STRICT) {
            setSslVerificationMode(config.getSslVerificationMode());
        }
        if (config.getSslKeystoreType() != null) {
            setSslKeystoreType(config.getSslKeystoreType());
        }
        if (config.getSslProvider() != SslProvider.JDK) {
            setSslProvider(config.getSslProvider());
        }
        if (config.getSslTruststore() != null) {
            setSslTruststore(config.getSslTruststore());
        }
        if (config.getSslTruststorePassword() != null) {
            setSslTruststorePassword(config.getSslTruststorePassword());
        }
        if (config.getSslKeystore() != null) {
            setSslKeystore(config.getSslKeystore());
        }
        if (config.getSslKeystorePassword() != null) {
            setSslKeystorePassword(config.getSslKeystorePassword());
        }
        if (config.getSslProtocols() != null) {
            setSslProtocols(config.getSslProtocols());
        }
        if (config.getSslCiphers() != null) {
            setSslCiphers(config.getSslCiphers());
        }
        if (config.getSslKeyManagerFactory() != null) {
            setSslKeyManagerFactory(config.getSslKeyManagerFactory());
        }
        if (config.getSslTrustManagerFactory() != null) {
            setSslTrustManagerFactory(config.getSslTrustManagerFactory());
        }
        
        if (config.isKeepAlive()) {
            setKeepAlive(config.isKeepAlive());
        }
        if (config.getTcpKeepAliveCount() != 0) {
            setTcpKeepAliveCount(config.getTcpKeepAliveCount());
        }
        if (config.getTcpKeepAliveIdle() != 0) {
            setTcpKeepAliveIdle(config.getTcpKeepAliveIdle());
        }
        if (config.getTcpKeepAliveInterval() != 0) {
            setTcpKeepAliveInterval(config.getTcpKeepAliveInterval());
        }
        if (config.getTcpUserTimeout() != 0) {
            setTcpUserTimeout(config.getTcpUserTimeout());
        }
        if (!config.isTcpNoDelay()) {
            setTcpNoDelay(config.isTcpNoDelay());
        }
        
        setSubscriptionsPerConnection(config.getSubscriptionsPerConnection());
        setRetryAttempts(config.getRetryAttempts());
        setRetryDelay(config.getRetryDelay());
        setReconnectionDelay(config.getReconnectionDelay());
        setTimeout(config.getTimeout());
        setClientName(config.getClientName());
        setConnectTimeout(config.getConnectTimeout());
        setIdleConnectionTimeout(config.getIdleConnectionTimeout());
        setPingConnectionInterval(config.getPingConnectionInterval());
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
     * Use {@link Config#setPassword(String)} instead.
     * Password for Redis authentication. Should be null if not needed.
     * <p>
     * Default is <code>null</code>
     *
     * @param password for connection
     * @return config
     */
    @Deprecated
    public T setPassword(String password) {
        log.warn("password setting is deprecated. Use password setting in Config instead.");
        this.password = password;
        return (T) this;
    }

    @Deprecated
    public String getPassword() {
        return password;
    }

    /**
     * Use {@link Config#setUsername(String)} instead.
     * Username for Redis authentication. Should be null if not needed
     * <p>
     * Default is <code>null</code>
     * <p>
     * Requires Redis 6.0+
     *
     * @param username for connection
     * @return config
     */
    @Deprecated
    public T setUsername(String username) {
        log.warn("username setting is deprecated. Use username setting in object instead.");
        this.username = username;
        return (T) this;
    }

    @Deprecated
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
     * Use {@link #setRetryDelay(DelayStrategy)} instead.
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
    
    @Deprecated
    public SslProvider getSslProvider() {
        return sslProvider;
    }

    /**
     * Use {@link Config#setSslProvider(SslProvider)} instead.
     * Defines SSL provider used to handle SSL connections.
     * <p>
     * Default is <code>JDK</code>
     * 
     * @param sslProvider ssl provider
     * @return config
     */
    @Deprecated
    public T setSslProvider(SslProvider sslProvider) {
        log.warn("sslProvider setting is deprecated. Use sslProvider setting in Config instead.");
        this.sslProvider = sslProvider;
        return (T) this;
    }
    
    @Deprecated
    public URL getSslTruststore() {
        return sslTruststore;
    }

    /**
     * Use {@link Config#setSslTruststore(URL)} instead.
     * Defines path to SSL truststore 
     * <p>
     * Default is <code>null</code>
     *
     * @param sslTruststore truststore path
     * @return config
     */
    @Deprecated
    public T setSslTruststore(URL sslTruststore) {
        log.warn("sslTruststore setting is deprecated. Use sslTruststore setting in Config instead.");
        this.sslTruststore = sslTruststore;
        return (T) this;
    }
    
    @Deprecated
    public String getSslTruststorePassword() {
        return sslTruststorePassword;
    }

    /**
     * Use {@link Config#setSslTruststorePassword(String)} instead.
     * Defines password for SSL truststore.
     * SSL truststore is read on each new connection creation and can be dynamically reloaded.
     * <p>
     * Default is <code>null</code>
     *
     * @param sslTruststorePassword - password
     * @return config
     */
    @Deprecated
    public T setSslTruststorePassword(String sslTruststorePassword) {
        log.warn("sslTruststorePassword setting is deprecated. Use sslTruststorePassword setting in Config instead.");
        this.sslTruststorePassword = sslTruststorePassword;
        return (T) this;
    }
    
    @Deprecated
    public URL getSslKeystore() {
        return sslKeystore;
    }

    /**
     * Use {@link Config#setSslKeystore(URL)} instead.
     * Defines path to SSL keystore.
     * SSL keystore is read on each new connection creation and can be dynamically reloaded.
     * <p>
     * Default is <code>null</code>
     *
     * @param sslKeystore path to keystore
     * @return config
     */
    @Deprecated
    public T setSslKeystore(URL sslKeystore) {
        log.warn("sslKeystore setting is deprecated. Use sslKeystore setting in Config instead.");
        this.sslKeystore = sslKeystore;
        return (T) this;
    }
    
    @Deprecated
    public String getSslKeystorePassword() {
        return sslKeystorePassword;
    }

    /**
     * Use {@link Config#setSslKeystorePassword(String)} instead.
     * Defines password for SSL keystore
     * <p>
     * Default is <code>null</code>
     *
     * @param sslKeystorePassword password
     * @return config
     */
    @Deprecated
    public T setSslKeystorePassword(String sslKeystorePassword) {
        log.warn("sslKeystorePassword setting is deprecated. Use sslKeystorePassword setting in Config instead.");
        this.sslKeystorePassword = sslKeystorePassword;
        return (T) this;
    }
    
    @Deprecated
    public String[] getSslProtocols() {
        return sslProtocols;
    }

    /**
     * Use {@link Config#setSslProtocols(String[])} instead.
     * Defines SSL protocols.
     * Example values: TLSv1.3, TLSv1.2, TLSv1.1, TLSv1
     * <p>
     * Default is <code>null</code>
     *
     * @param sslProtocols protocols
     * @return config
     */
    @Deprecated
    public T setSslProtocols(String[] sslProtocols) {
        log.warn("sslProtocols setting is deprecated. Use sslProtocols setting in Config instead.");
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

    @Deprecated
    public boolean isKeepAlive() {
        return keepAlive;
    }

    /**
     * Use {@link Config#setTcpKeepAlive(boolean)} instead.
     * Enables TCP keepAlive for connection
     * <p>
     * Default is <code>false</code>
     * 
     * @param keepAlive boolean value
     * @return config
     */
    @Deprecated
    public T setKeepAlive(boolean keepAlive) {
        log.warn("setKeepAlive setting is deprecated. Use setTcpKeepAlive setting in Config instead.");
        this.keepAlive = keepAlive;
        return (T) this;
    }

    @Deprecated
    public int getTcpKeepAliveCount() {
        return tcpKeepAliveCount;
    }

    /**
     * Use {@link Config#setTcpKeepAliveCount(int)} instead.
     * Defines the maximum number of keepalive probes
     * TCP should send before dropping the connection.
     *
     * @param tcpKeepAliveCount maximum number of keepalive probes
     * @return config
     */
    @Deprecated
    public T setTcpKeepAliveCount(int tcpKeepAliveCount) {
        log.warn("setTcpKeepAliveCount setting is deprecated. Use setTcpKeepAliveCount setting in Config instead.");
        this.tcpKeepAliveCount = tcpKeepAliveCount;
        return (T) this;
    }
    
    @Deprecated
    public int getTcpKeepAliveIdle() {
        return tcpKeepAliveIdle;
    }

    /**
     * Use {@link Config#setTcpKeepAliveIdle(int)} instead.
     * Defines the time in seconds the connection needs to remain idle
     * before TCP starts sending keepalive probes,
     *
     * @param tcpKeepAliveIdle time in seconds
     * @return config
     */
    @Deprecated
    public T setTcpKeepAliveIdle(int tcpKeepAliveIdle) {
        log.warn("setTcpKeepAliveIdle setting is deprecated. Use setTcpKeepAliveIdle setting in Config instead.");
        this.tcpKeepAliveIdle = tcpKeepAliveIdle;
        return (T) this;
    }
    
    @Deprecated
    public int getTcpKeepAliveInterval() {
        return tcpKeepAliveInterval;
    }

    /**
     * Use {@link Config#setTcpKeepAliveInterval(int)} instead.
     * Defines the time in seconds between individual keepalive probes.
     *
     * @param tcpKeepAliveInterval time in seconds
     * @return config
     */
    @Deprecated
    public T setTcpKeepAliveInterval(int tcpKeepAliveInterval) {
        log.warn("setTcpKeepAliveInterval setting is deprecated. Use setTcpKeepAliveInterval setting in Config instead.");
        this.tcpKeepAliveInterval = tcpKeepAliveInterval;
        return (T) this;
    }
    
    @Deprecated
    public int getTcpUserTimeout() {
        return tcpUserTimeout;
    }

    /**
     * Use {@link Config#setTcpUserTimeout(int)} instead.
     * Defines the maximum amount of time in milliseconds that transmitted data may
     * remain unacknowledged, or buffered data may remain untransmitted
     * (due to zero window size) before TCP will forcibly close the connection.
     *
     * @param tcpUserTimeout time in milliseconds
     * @return config
     */
    @Deprecated
    public T setTcpUserTimeout(int tcpUserTimeout) {
        log.warn("setTcpUserTimeout setting is deprecated. Use setTcpUserTimeout setting in Config instead.");
        this.tcpUserTimeout = tcpUserTimeout;
        return (T) this;
    }
    
    @Deprecated
    public boolean isTcpNoDelay() {
        return tcpNoDelay;
    }

    /**
     * Use {@link Config#setTcpNoDelay(boolean)} instead.
     * Enables TCP noDelay for connection
     * <p>
     * Default is <code>true</code>
     * 
     * @param tcpNoDelay boolean value
     * @return config
     */
    @Deprecated
    public T setTcpNoDelay(boolean tcpNoDelay) {
        log.warn("setTcpNoDelay setting is deprecated. Use setTcpNoDelay setting in Config instead.");
        this.tcpNoDelay = tcpNoDelay;
        return (T) this;
    }


    @Deprecated
    public NameMapper getNameMapper() {
        return nameMapper;
    }

    /**
     * Use {@link Config#setNameMapper(NameMapper)} instead.
     * Defines Name mapper which maps Redisson object name.
     * Applied to all Redisson objects.
     *
     * @param nameMapper name mapper object
     * @return config
     */
    @Deprecated
    public T setNameMapper(NameMapper nameMapper) {
        log.warn("nameMapper setting is deprecated. Use nameMapper setting in Config instead.");
        this.nameMapper = nameMapper;
        return (T) this;
    }

    @Deprecated
    public CredentialsResolver getCredentialsResolver() {
        return credentialsResolver;
    }

    /**
     * Use {@link Config#setCredentialsResolver(CredentialsResolver)} instead.
     * Defines Credentials resolver which is invoked during connection for Redis server authentication.
     * It makes possible to specify dynamically changing Redis credentials.
     *
     * @see EntraIdCredentialsResolver
     *
     * @param credentialsResolver Credentials resolver object
     * @return config
     */
    @Deprecated
    public T setCredentialsResolver(CredentialsResolver credentialsResolver) {
        log.warn("credentialsResolver setting is deprecated. Use credentialsResolver setting in Config instead.");
        this.credentialsResolver = credentialsResolver;
        return (T) this;
    }
    
    @Deprecated
    public String getSslKeystoreType() {
        return sslKeystoreType;
    }

    /**
     * Use {@link Config#setSslKeystoreType(String)} instead.
     * Defines SSL keystore type.
     * <p>
     * Default is <code>null</code>
     *
     * @param sslKeystoreType keystore type
     * @return config
     */
    @Deprecated
    public T setSslKeystoreType(String sslKeystoreType) {
        log.warn("sslKeystoreType setting is deprecated. Use sslKeystoreType setting in Config instead.");
        this.sslKeystoreType = sslKeystoreType;
        return (T) this;
    }
    
    @Deprecated
    public String[] getSslCiphers() {
        return sslCiphers;
    }

    /**
     * Use {@link Config#setSslCiphers(String[])} instead.
     * Defines SSL ciphers.
     * <p>
     * Default is <code>null</code>
     *
     * @param sslCiphers ciphers
     * @return config
     */
    @Deprecated
    public T setSslCiphers(String[] sslCiphers) {
        log.warn("sslCiphers setting is deprecated. Use sslCiphers setting in Config instead.");
        this.sslCiphers = sslCiphers;
        return (T) this;
    }
    
    @Deprecated
    public TrustManagerFactory getSslTrustManagerFactory() {
        return sslTrustManagerFactory;
    }

    /**
     * Use {@link Config#setSslTrustManagerFactory(TrustManagerFactory)} instead.
     * Defines SSL TrustManagerFactory.
     * <p>
     * Default is <code>null</code>
     *
     * @param trustManagerFactory trust manager value
     * @return config
     */
    @Deprecated
    public T setSslTrustManagerFactory(TrustManagerFactory trustManagerFactory) {
        log.warn("trustManagerFactory setting is deprecated. Use trustManagerFactory setting in Config instead.");
        this.sslTrustManagerFactory = trustManagerFactory;
        return (T) this;
    }
    
    @Deprecated
    public KeyManagerFactory getSslKeyManagerFactory() {
        return sslKeyManagerFactory;
    }

    /**
     * Use {@link Config#setSslKeyManagerFactory(KeyManagerFactory)} instead.
     * Defines SSL KeyManagerFactory.
     * <p>
     * Default is <code>null</code>
     *
     * @param keyManagerFactory key manager value
     * @return config
     */
    @Deprecated
    public BaseConfig<T> setSslKeyManagerFactory(KeyManagerFactory keyManagerFactory) {
        log.warn("keyManagerFactory setting is deprecated. Use keyManagerFactory setting in Config instead.");
        this.sslKeyManagerFactory = keyManagerFactory;
        return this;
    }

    @Deprecated
    public CommandMapper getCommandMapper() {
        return commandMapper;
    }

    /**
     * Use {@link Config#setCommandMapper(CommandMapper)} instead.
     * Defines Command mapper which maps Redis command name.
     * Applied to all Redis commands.
     *
     * @param commandMapper Redis command name mapper object
     * @return config
     */
    @Deprecated
    public T setCommandMapper(CommandMapper commandMapper) {
        log.warn("commandMapper setting is deprecated. Use commandMapper setting in Config instead.");
        this.commandMapper = commandMapper;
        return (T) this;
    }
    
    @Deprecated
    public SslVerificationMode getSslVerificationMode() {
        return sslVerificationMode;
    }

    /**
     * Use {@link Config#setSslVerificationMode(SslVerificationMode)} instead.
     * Defines SSL verification mode, which prevents man-in-the-middle attacks.
     *
     * <p>
     * Default is <code>SslVerificationMode.STRICT</code>
     *
     * @param sslVerificationMode mode value
     * @return config
     */
    @Deprecated
    public T setSslVerificationMode(SslVerificationMode sslVerificationMode) {
        log.warn("sslVerificationMode setting is deprecated. Use sslVerificationMode setting in Config instead.");
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
