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
package org.redisson.client;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.DuplexChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.resolver.AddressResolverGroup;
import io.netty.util.Timer;
import org.redisson.config.*;
import org.redisson.misc.RedisURI;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import java.net.InetSocketAddress;
import java.net.URL;
import java.time.Duration;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedisClientConfig {

    private RedisURI address;
    private InetSocketAddress addr;
    
    private Timer timer;
    private ExecutorService executor;
    private EventLoopGroup group;
    private AddressResolverGroup<InetSocketAddress> resolverGroup;
    private Class<? extends DuplexChannel> socketChannelClass = NioSocketChannel.class;
    private int connectTimeout = 10000;
    private int commandTimeout = 10000;

    private String username;
    private String password;
    private int database;
    private String clientName;
    private boolean readOnly;
    private boolean keepPubSubOrder = true;
    private int pingConnectionInterval;
    private boolean keepAlive;
    private int tcpKeepAliveCount;
    private int tcpKeepAliveIdle;
    private int tcpKeepAliveInterval;
    private int tcpUserTimeout;
    private boolean tcpNoDelay;
    
    private String sslHostname;
    private SslVerificationMode sslVerificationMode = SslVerificationMode.STRICT;
    private SslProvider sslProvider = SslProvider.JDK;
    private String sslKeystoreType;
    private URL sslTruststore;
    private String sslTruststorePassword;
    private URL sslKeystore;
    private String sslKeystorePassword;
    private String[] sslProtocols;
    private String[] sslCiphers;
    private TrustManagerFactory sslTrustManagerFactory;
    private KeyManagerFactory sslKeyManagerFactory;
    private NettyHook nettyHook = new DefaultNettyHook();
    private CredentialsResolver credentialsResolver = new DefaultCredentialsResolver();
    private Consumer<InetSocketAddress> connectedListener;
    private Consumer<InetSocketAddress> disconnectedListener;

    private CommandMapper commandMapper = new DefaultCommandMapper();

    private FailedNodeDetector failedNodeDetector = new FailedConnectionDetector();

    private Protocol protocol = Protocol.RESP2;

    private Set<ValkeyCapability> capabilities = Collections.emptySet();

    private DelayStrategy reconnectionDelay = new EqualJitterDelay(Duration.ofMillis(100), Duration.ofSeconds(10));

    public RedisClientConfig() {
    }
    
    RedisClientConfig(RedisClientConfig config) {
        super();
        this.nettyHook = config.nettyHook;
        this.addr = config.addr;
        this.address = config.address;
        this.timer = config.timer;
        this.executor = config.executor;
        this.group = config.group;
        this.socketChannelClass = config.socketChannelClass;
        this.connectTimeout = config.connectTimeout;
        this.commandTimeout = config.commandTimeout;
        this.password = config.password;
        this.username = config.username;
        this.database = config.database;
        this.clientName = config.clientName;
        this.readOnly = config.readOnly;
        this.keepPubSubOrder = config.keepPubSubOrder;
        this.pingConnectionInterval = config.pingConnectionInterval;
        this.keepAlive = config.keepAlive;
        this.tcpNoDelay = config.tcpNoDelay;
        this.sslProvider = config.sslProvider;
        this.sslTruststore = config.sslTruststore;
        this.sslTruststorePassword = config.sslTruststorePassword;
        this.sslKeystore = config.sslKeystore;
        this.sslKeystorePassword = config.sslKeystorePassword;
        this.sslProtocols = config.sslProtocols;
        this.sslCiphers = config.sslCiphers;
        this.resolverGroup = config.resolverGroup;
        this.sslHostname = config.sslHostname;
        this.credentialsResolver = config.credentialsResolver;
        this.connectedListener = config.connectedListener;
        this.disconnectedListener = config.disconnectedListener;
        this.sslKeyManagerFactory = config.sslKeyManagerFactory;
        this.sslTrustManagerFactory = config.sslTrustManagerFactory;
        this.commandMapper = config.commandMapper;
        this.failedNodeDetector = config.failedNodeDetector;
        this.tcpKeepAliveCount = config.tcpKeepAliveCount;
        this.tcpKeepAliveIdle = config.tcpKeepAliveIdle;
        this.tcpKeepAliveInterval = config.tcpKeepAliveInterval;
        this.tcpUserTimeout = config.tcpUserTimeout;
        this.protocol = config.protocol;
        this.sslKeystoreType = config.sslKeystoreType;
        this.sslVerificationMode = config.sslVerificationMode;
        this.capabilities = config.capabilities;
        this.reconnectionDelay = config.reconnectionDelay;
    }

    public NettyHook getNettyHook() {
        return nettyHook;
    }
    public RedisClientConfig setNettyHook(NettyHook nettyHook) {
        this.nettyHook = nettyHook;
        return this;
    }

    public String getSslHostname() {
        return sslHostname;
    }
    public RedisClientConfig setSslHostname(String sslHostname) {
        this.sslHostname = sslHostname;
        return this;
    }

    public RedisClientConfig setAddress(String host, int port) {
        this.address = new RedisURI(RedisURI.REDIS_PROTOCOL + host + ":" + port);
        return this;
    }
    public RedisClientConfig setAddress(String address) {
        this.address = new RedisURI(address);
        return this;
    }
    public RedisClientConfig setAddress(InetSocketAddress addr, RedisURI address) {
        this.addr = addr;
        this.address = address;
        return this;
    }
    public RedisClientConfig setAddress(RedisURI address) {
        this.address = address;
        return this;
    }
    public RedisURI getAddress() {
        return address;
    }
    public InetSocketAddress getAddr() {
        return addr;
    }
    
    public Timer getTimer() {
        return timer;
    }
    public RedisClientConfig setTimer(Timer timer) {
        this.timer = timer;
        return this;
    }
    
    public ExecutorService getExecutor() {
        return executor;
    }
    public RedisClientConfig setExecutor(ExecutorService executor) {
        this.executor = executor;
        return this;
    }
    
    public EventLoopGroup getGroup() {
        return group;
    }
    public RedisClientConfig setGroup(EventLoopGroup group) {
        this.group = group;
        return this;
    }
    
    public Class<? extends DuplexChannel> getSocketChannelClass() {
        return socketChannelClass;
    }
    public RedisClientConfig setSocketChannelClass(Class<? extends DuplexChannel> socketChannelClass) {
        this.socketChannelClass = socketChannelClass;
        return this;
    }
    
    public int getConnectTimeout() {
        return connectTimeout;
    }
    public RedisClientConfig setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
        return this;
    }
    
    public int getCommandTimeout() {
        return commandTimeout;
    }
    public RedisClientConfig setCommandTimeout(int commandTimeout) {
        this.commandTimeout = commandTimeout;
        return this;
    }
    
    public SslProvider getSslProvider() {
        return sslProvider;
    }
    public RedisClientConfig setSslProvider(SslProvider sslMode) {
        this.sslProvider = sslMode;
        return this;
    }
    
    public URL getSslTruststore() {
        return sslTruststore;
    }
    public RedisClientConfig setSslTruststore(URL sslTruststore) {
        this.sslTruststore = sslTruststore;
        return this;
    }
    
    public URL getSslKeystore() {
        return sslKeystore;
    }
    public RedisClientConfig setSslKeystore(URL sslKeystore) {
        this.sslKeystore = sslKeystore;
        return this;
    }
    
    public String getSslKeystorePassword() {
        return sslKeystorePassword;
    }
    public RedisClientConfig setSslKeystorePassword(String sslKeystorePassword) {
        this.sslKeystorePassword = sslKeystorePassword;
        return this;
    }

    public String getSslTruststorePassword() {
        return sslTruststorePassword;
    }
    public RedisClientConfig setSslTruststorePassword(String sslTruststorePassword) {
        this.sslTruststorePassword = sslTruststorePassword;
        return this;
    }

    @Deprecated
    public boolean isSslEnableEndpointIdentification() {
        return this.sslVerificationMode == SslVerificationMode.STRICT;
    }
    @Deprecated
    public RedisClientConfig setSslEnableEndpointIdentification(boolean enableEndpointIdentification) {
        if (enableEndpointIdentification) {
            this.sslVerificationMode = SslVerificationMode.STRICT;
        } else {
            this.sslVerificationMode = SslVerificationMode.NONE;
        }
        return this;
    }

    public String getPassword() {
        return password;
    }
    public RedisClientConfig setPassword(String password) {
        this.password = password;
        return this;
    }
    
    public int getDatabase() {
        return database;
    }
    public RedisClientConfig setDatabase(int database) {
        this.database = database;
        return this;
    }
    
    public String getClientName() {
        return clientName;
    }
    public RedisClientConfig setClientName(String clientName) {
        this.clientName = clientName;
        return this;
    }
    
    public boolean isReadOnly() {
        return readOnly;
    }
    public RedisClientConfig setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
        return this;
    }

    public boolean isKeepPubSubOrder() {
        return keepPubSubOrder;
    }
    public RedisClientConfig setKeepPubSubOrder(boolean keepPubSubOrder) {
        this.keepPubSubOrder = keepPubSubOrder;
        return this;
    }

    public int getPingConnectionInterval() {
        return pingConnectionInterval;
    }    
    public RedisClientConfig setPingConnectionInterval(int pingConnectionInterval) {
        this.pingConnectionInterval = pingConnectionInterval;
        return this;
    }
    
    public boolean isKeepAlive() {
        return keepAlive;
    }
    public RedisClientConfig setKeepAlive(boolean keepAlive) {
        this.keepAlive = keepAlive;
        return this;
    }

    public int getTcpKeepAliveCount() {
        return tcpKeepAliveCount;
    }
    public RedisClientConfig setTcpKeepAliveCount(int tcpKeepAliveCount) {
        this.tcpKeepAliveCount = tcpKeepAliveCount;
        return this;
    }

    public int getTcpKeepAliveIdle() {
        return tcpKeepAliveIdle;
    }
    public RedisClientConfig setTcpKeepAliveIdle(int tcpKeepAliveIdle) {
        this.tcpKeepAliveIdle = tcpKeepAliveIdle;
        return this;
    }

    public int getTcpKeepAliveInterval() {
        return tcpKeepAliveInterval;
    }
    public RedisClientConfig setTcpKeepAliveInterval(int tcpKeepAliveInterval) {
        this.tcpKeepAliveInterval = tcpKeepAliveInterval;
        return this;
    }

    public int getTcpUserTimeout() {
        return tcpUserTimeout;
    }

    public RedisClientConfig setTcpUserTimeout(int tcpUserTimeout) {
        this.tcpUserTimeout = tcpUserTimeout;
        return this;
    }

    public boolean isTcpNoDelay() {
        return tcpNoDelay;
    }
    public RedisClientConfig setTcpNoDelay(boolean tcpNoDelay) {
        this.tcpNoDelay = tcpNoDelay;
        return this;
    }

    public AddressResolverGroup<InetSocketAddress> getResolverGroup() {
        return resolverGroup;
    }
    public RedisClientConfig setResolverGroup(AddressResolverGroup<InetSocketAddress> resolverGroup) {
        this.resolverGroup = resolverGroup;
        return this;
    }

    public String getUsername() {
        return username;
    }
    public RedisClientConfig setUsername(String username) {
        this.username = username;
        return this;
    }

    public String[] getSslProtocols() {
        return sslProtocols;
    }
    public RedisClientConfig setSslProtocols(String[] sslProtocols) {
        this.sslProtocols = sslProtocols;
        return this;
    }

    public String[] getSslCiphers() {
        return sslCiphers;
    }

    public RedisClientConfig setSslCiphers(String[] sslCiphers) {
        this.sslCiphers = sslCiphers;
        return this;
    }

    public CredentialsResolver getCredentialsResolver() {
        return credentialsResolver;
    }

    public RedisClientConfig setCredentialsResolver(CredentialsResolver credentialsResolver) {
        this.credentialsResolver = credentialsResolver;
        return this;
    }

    public Consumer<InetSocketAddress> getConnectedListener() {
        return connectedListener;
    }
    public RedisClientConfig setConnectedListener(Consumer<InetSocketAddress> connectedListener) {
        this.connectedListener = connectedListener;
        return this;
    }

    public Consumer<InetSocketAddress> getDisconnectedListener() {
        return disconnectedListener;
    }
    public RedisClientConfig setDisconnectedListener(Consumer<InetSocketAddress> disconnectedListener) {
        this.disconnectedListener = disconnectedListener;
        return this;
    }

    public TrustManagerFactory getSslTrustManagerFactory() {
        return sslTrustManagerFactory;
    }

    public RedisClientConfig setSslTrustManagerFactory(TrustManagerFactory sslTrustManagerFactory) {
        this.sslTrustManagerFactory = sslTrustManagerFactory;
        return this;
    }

    public KeyManagerFactory getSslKeyManagerFactory() {
        return sslKeyManagerFactory;
    }

    public RedisClientConfig setSslKeyManagerFactory(KeyManagerFactory sslKeyManagerFactory) {
        this.sslKeyManagerFactory = sslKeyManagerFactory;
        return this;
    }

    public CommandMapper getCommandMapper() {
        return commandMapper;
    }

    public RedisClientConfig setCommandMapper(CommandMapper commandMapper) {
        this.commandMapper = commandMapper;
        return this;
    }

    public FailedNodeDetector getFailedNodeDetector() {
        return failedNodeDetector;
    }

    public RedisClientConfig setFailedNodeDetector(FailedNodeDetector failedNodeDetector) {
        this.failedNodeDetector = failedNodeDetector;
        return this;
    }

    public Protocol getProtocol() {
        return protocol;
    }

    public RedisClientConfig setProtocol(Protocol protocol) {
        this.protocol = protocol;
        return this;
    }

    public String getSslKeystoreType() {
        return sslKeystoreType;
    }

    public RedisClientConfig setSslKeystoreType(String sslKeystoreType) {
        this.sslKeystoreType = sslKeystoreType;
        return this;
    }

    public SslVerificationMode getSslVerificationMode() {
        return sslVerificationMode;
    }
    public RedisClientConfig setSslVerificationMode(SslVerificationMode sslVerificationMode) {
        this.sslVerificationMode = sslVerificationMode;
        return this;
    }

    public Set<ValkeyCapability> getCapabilities() {
        return capabilities;
    }

    public RedisClientConfig setCapabilities(Set<ValkeyCapability> capabilities) {
        this.capabilities = capabilities;
        return this;
    }

    public DelayStrategy getReconnectionDelay() {
        return reconnectionDelay;
    }
    public RedisClientConfig setReconnectionDelay(DelayStrategy reconnectionDelay) {
        this.reconnectionDelay = reconnectionDelay;
        return this;
    }
}
