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
package org.redisson.client.handler;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.group.ChannelGroup;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.util.NetUtil;
import org.redisson.client.RedisClient;
import org.redisson.client.RedisClientConfig;
import org.redisson.client.RedisConnection;
import org.redisson.config.SslProvider;

import javax.net.ssl.*;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedisChannelInitializer extends ChannelInitializer<Channel> {

    public enum Type {PUBSUB, PLAIN}

    private final RedisClientConfig config;
    private final RedisClient redisClient;
    private final Type type;
    private final ConnectionWatchdog connectionWatchdog;
    private final PingConnectionHandler pingConnectionHandler;
    
    public RedisChannelInitializer(Bootstrap bootstrap, RedisClientConfig config, RedisClient redisClient, ChannelGroup channels, Type type) {
        super();
        this.config = config;
        this.redisClient = redisClient;
        this.type = type;
        
        if (config.getPingConnectionInterval() > 0) {
            pingConnectionHandler = new PingConnectionHandler(config);
        } else {
            pingConnectionHandler = null;
        }
        connectionWatchdog = new ConnectionWatchdog(bootstrap, channels, config.getTimer());
    }
    
    @Override
    protected void initChannel(Channel ch) throws Exception {
        initSsl(config, ch);
        
        if (type == Type.PLAIN) {
            ch.pipeline().addLast(new RedisConnectionHandler(redisClient));
        } else {
            ch.pipeline().addLast(new RedisPubSubConnectionHandler(redisClient));
        }

        ch.pipeline().addLast(
            connectionWatchdog,
            CommandEncoder.INSTANCE,
            CommandBatchEncoder.INSTANCE);

        if (type == Type.PLAIN) {
            ch.pipeline().addLast(new CommandsQueue());
        } else {
            ch.pipeline().addLast(new CommandsQueuePubSub());
        }

        if (pingConnectionHandler != null) {
            ch.pipeline().addLast(pingConnectionHandler);
        }
        
        if (type == Type.PLAIN) {
            ch.pipeline().addLast(new CommandDecoder(config.getAddress().getScheme()));
        } else {
            ch.pipeline().addLast(new CommandPubSubDecoder(config));
        }

        ch.pipeline().addLast(new ErrorsLoggingHandler());

        config.getNettyHook().afterChannelInitialization(ch);
    }
    
    private void initSsl(final RedisClientConfig config, Channel ch) throws KeyStoreException, IOException,
            NoSuchAlgorithmException, CertificateException, SSLException, UnrecoverableKeyException {
        if (!config.getAddress().isSsl()) {
            return;
        }

        io.netty.handler.ssl.SslProvider provided = io.netty.handler.ssl.SslProvider.JDK;
        if (config.getSslProvider() == SslProvider.OPENSSL) {
            provided = io.netty.handler.ssl.SslProvider.OPENSSL;
        }
        
        SslContextBuilder sslContextBuilder = SslContextBuilder.forClient().sslProvider(provided);
        sslContextBuilder.protocols(config.getSslProtocols());
        if (config.getSslTruststore() != null) {
            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            
            InputStream stream = config.getSslTruststore().openStream();
            try {
                char[] password = null;
                if (config.getSslTruststorePassword() != null) {
                    password = config.getSslTruststorePassword().toCharArray();
                }
                keyStore.load(stream, password);
            } finally {
                stream.close();
            }
            
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(keyStore);
            sslContextBuilder.trustManager(trustManagerFactory);
        }

        if (config.getSslKeystore() != null){
            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            
            InputStream stream = config.getSslKeystore().openStream();
            char[] password = null;
            if (config.getSslKeystorePassword() != null) {
                password = config.getSslKeystorePassword().toCharArray();
            }
            try {
                keyStore.load(stream, password);
            } finally {
                stream.close();
            }
            
            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keyStore, password);
            sslContextBuilder.keyManager(keyManagerFactory);
        }
        
        SSLParameters sslParams = new SSLParameters();
        if (config.isSslEnableEndpointIdentification()) {
            sslParams.setEndpointIdentificationAlgorithm("HTTPS");
        } else {
            if (config.getSslTruststore() == null) {
                sslContextBuilder.trustManager(InsecureTrustManagerFactory.INSTANCE);
            }
        }

        SslContext sslContext = sslContextBuilder.build();
        String hostname = config.getSslHostname();
        if (hostname == null || NetUtil.createByteArrayFromIpAddressString(hostname) != null) {
            hostname = config.getAddress().getHost();
        }
        
        SSLEngine sslEngine = sslContext.newEngine(ch.alloc(), hostname, config.getAddress().getPort());
        sslEngine.setSSLParameters(sslParams);
        
        SslHandler sslHandler = new SslHandler(sslEngine);
        ch.pipeline().addLast(sslHandler);
        ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
            
            volatile boolean sslInitDone;
            
            @Override
            public void channelActive(ChannelHandlerContext ctx) throws Exception {
                if (sslInitDone) {
                    super.channelActive(ctx);
                }
            }
            
            @Override
            public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                if (!sslInitDone && evt instanceof SslHandshakeCompletionEvent) {
                    SslHandshakeCompletionEvent e = (SslHandshakeCompletionEvent) evt;
                    if (e.isSuccess()) {
                        sslInitDone = true;
                        ctx.fireChannelActive();
                    } else {
                        RedisConnection connection = RedisConnection.getFrom(ctx.channel());
                        connection.closeAsync();
                        connection.getConnectionPromise().completeExceptionally(e.cause());
                    }
                }

                super.userEventTriggered(ctx, evt);
            }

        });
    }
    
}
