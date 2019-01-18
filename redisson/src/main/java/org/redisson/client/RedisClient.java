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
package org.redisson.client;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.redisson.api.RFuture;
import org.redisson.client.handler.RedisChannelInitializer;
import org.redisson.client.handler.RedisChannelInitializer.Type;
import org.redisson.misc.RPromise;
import org.redisson.misc.RedissonPromise;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.EpollDatagramChannel;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.ChannelGroupFuture;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.resolver.AddressResolver;
import io.netty.resolver.dns.DnsAddressResolverGroup;
import io.netty.resolver.dns.DnsServerAddressStreamProviders;
import io.netty.util.HashedWheelTimer;
import io.netty.util.NetUtil;
import io.netty.util.Timer;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;

/**
 * Low-level Redis client
 * 
 * @author Nikita Koksharov
 *
 */
public class RedisClient {

    private final AtomicReference<RFuture<InetSocketAddress>> resolvedAddrFuture = new AtomicReference<RFuture<InetSocketAddress>>();
    private final Bootstrap bootstrap;
    private final Bootstrap pubSubBootstrap;
    private final URI uri;
    private InetSocketAddress resolvedAddr;
    private final ChannelGroup channels;

    private ExecutorService executor;
    private final long commandTimeout;
    private Timer timer;
    private RedisClientConfig config;

    private boolean hasOwnTimer;
    private boolean hasOwnExecutor;
    private boolean hasOwnGroup;
    private boolean hasOwnResolver;

    public static RedisClient create(RedisClientConfig config) {
        return new RedisClient(config);
    }
    
    private RedisClient(RedisClientConfig config) {
        RedisClientConfig copy = new RedisClientConfig(config);
        if (copy.getTimer() == null) {
            copy.setTimer(new HashedWheelTimer());
            hasOwnTimer = true;
        }
        if (copy.getGroup() == null) {
            copy.setGroup(new NioEventLoopGroup());
            hasOwnGroup = true;
        }
        if (copy.getExecutor() == null) {
            copy.setExecutor(Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2));
            hasOwnExecutor = true;
        }
        if (copy.getResolverGroup() == null) {
            if (config.getSocketChannelClass() == EpollSocketChannel.class) {
                copy.setResolverGroup(new DnsAddressResolverGroup(EpollDatagramChannel.class, DnsServerAddressStreamProviders.platformDefault()));
            } else {
                copy.setResolverGroup(new DnsAddressResolverGroup(NioDatagramChannel.class, DnsServerAddressStreamProviders.platformDefault()));
            }
            hasOwnResolver = true;
        }

        this.config = copy;
        this.executor = copy.getExecutor();
        this.timer = copy.getTimer();
        
        uri = copy.getAddress();
        resolvedAddr = copy.getAddr();
        
        if (resolvedAddr != null) {
            resolvedAddrFuture.set(RedissonPromise.newSucceededFuture(resolvedAddr));
        }
        
        channels = new DefaultChannelGroup(copy.getGroup().next()); 
        bootstrap = createBootstrap(copy, Type.PLAIN);
        pubSubBootstrap = createBootstrap(copy, Type.PUBSUB);
        
        this.commandTimeout = copy.getCommandTimeout();
    }

    private Bootstrap createBootstrap(RedisClientConfig config, Type type) {
        Bootstrap bootstrap = new Bootstrap()
                        .resolver(config.getResolverGroup())
                        .channel(config.getSocketChannelClass())
                        .group(config.getGroup());

        bootstrap.handler(new RedisChannelInitializer(bootstrap, config, this, channels, type));
        bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, config.getConnectTimeout());
        bootstrap.option(ChannelOption.SO_KEEPALIVE, config.isKeepAlive());
        bootstrap.option(ChannelOption.TCP_NODELAY, config.isTcpNoDelay());
        return bootstrap;
    }
    
    public InetSocketAddress getAddr() {
        return resolvedAddr;
    }

    public long getCommandTimeout() {
        return commandTimeout;
    }

    public EventLoopGroup getEventLoopGroup() {
        return bootstrap.config().group();
    }
    
    public RedisClientConfig getConfig() {
        return config;
    }

    public RedisConnection connect() {
        try {
            return connectAsync().syncUninterruptibly().getNow();
        } catch (Exception e) {
            throw new RedisConnectionException("Unable to connect to: " + uri, e);
        }
    }
    
    public RFuture<InetSocketAddress> resolveAddr() {
        if (resolvedAddrFuture.get() != null) {
            return resolvedAddrFuture.get();
        }
        
        final RPromise<InetSocketAddress> promise = new RedissonPromise<InetSocketAddress>();
        if (!resolvedAddrFuture.compareAndSet(null, promise)) {
            return resolvedAddrFuture.get();
        }
        
        byte[] addr = NetUtil.createByteArrayFromIpAddressString(uri.getHost());
        if (addr != null) {
            try {
                resolvedAddr = new InetSocketAddress(InetAddress.getByAddress(uri.getHost(), addr), uri.getPort());
            } catch (UnknownHostException e) {
                // skip
            }
            promise.trySuccess(resolvedAddr);
            return promise;
        }
        
        AddressResolver<InetSocketAddress> resolver = (AddressResolver<InetSocketAddress>) bootstrap.config().resolver().getResolver(bootstrap.config().group().next());
        Future<InetSocketAddress> resolveFuture = resolver.resolve(InetSocketAddress.createUnresolved(uri.getHost(), uri.getPort()));
        resolveFuture.addListener(new FutureListener<InetSocketAddress>() {
            @Override
            public void operationComplete(Future<InetSocketAddress> future) throws Exception {
                if (!future.isSuccess()) {
                    promise.tryFailure(future.cause());
                    return;
                }
                
                InetSocketAddress resolved = future.getNow();
                resolvedAddr = createInetSocketAddress(resolved, uri.getHost());
                promise.trySuccess(resolvedAddr);
            }

        });
        return promise;
    }

    private InetSocketAddress createInetSocketAddress(InetSocketAddress resolved, String host) {
        byte[] addr = NetUtil.createByteArrayFromIpAddressString(resolved.getAddress().getHostAddress());
        try {
            return new InetSocketAddress(InetAddress.getByAddress(host, addr), resolved.getPort());
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }
    
    public RFuture<RedisConnection> connectAsync() {
        final RPromise<RedisConnection> f = new RedissonPromise<RedisConnection>();
        
        RFuture<InetSocketAddress> addrFuture = resolveAddr();
        addrFuture.addListener(new FutureListener<InetSocketAddress>() {
            @Override
            public void operationComplete(Future<InetSocketAddress> future) throws Exception {
                if (!future.isSuccess()) {
                    f.tryFailure(future.cause());
                    return;
                }
                
                ChannelFuture channelFuture = bootstrap.connect(future.getNow());
                channelFuture.addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(final ChannelFuture future) throws Exception {
                        if (future.isSuccess()) {
                            final RedisConnection c = RedisConnection.getFrom(future.channel());
                            c.getConnectionPromise().addListener(new FutureListener<RedisConnection>() {
                                @Override
                                public void operationComplete(final Future<RedisConnection> future) throws Exception {
                                    bootstrap.config().group().execute(new Runnable() {
                                        @Override
                                        public void run() {
                                            if (future.isSuccess()) {
                                                if (!f.trySuccess(c)) {
                                                    c.closeAsync();
                                                }
                                            } else {
                                                f.tryFailure(future.cause());
                                                c.closeAsync();
                                            }
                                        }
                                    });
                                }
                            });
                        } else {
                            bootstrap.config().group().execute(new Runnable() {
                                public void run() {
                                    f.tryFailure(future.cause());
                                }
                            });
                        }
                    }
                });
            }
        });
        
        return f;
    }

    public RedisPubSubConnection connectPubSub() {
        try {
            return connectPubSubAsync().syncUninterruptibly().getNow();
        } catch (Exception e) {
            throw new RedisConnectionException("Unable to connect to: " + uri, e);
        }
    }

    public RFuture<RedisPubSubConnection> connectPubSubAsync() {
        final RPromise<RedisPubSubConnection> f = new RedissonPromise<RedisPubSubConnection>();
        
        RFuture<InetSocketAddress> nameFuture = resolveAddr();
        nameFuture.addListener(new FutureListener<InetSocketAddress>() {
            @Override
            public void operationComplete(Future<InetSocketAddress> future) throws Exception {
                if (!future.isSuccess()) {
                    f.tryFailure(future.cause());
                    return;
                }
                
                ChannelFuture channelFuture = pubSubBootstrap.connect(future.getNow());
                channelFuture.addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(final ChannelFuture future) throws Exception {
                        if (future.isSuccess()) {
                            final RedisPubSubConnection c = RedisPubSubConnection.getFrom(future.channel());
                            c.<RedisPubSubConnection>getConnectionPromise().addListener(new FutureListener<RedisPubSubConnection>() {
                                @Override
                                public void operationComplete(final Future<RedisPubSubConnection> future) throws Exception {
                                    pubSubBootstrap.config().group().execute(new Runnable() {
                                        @Override
                                        public void run() {
                                            if (future.isSuccess()) {
                                                if (!f.trySuccess(c)) {
                                                    c.closeAsync();
                                                }
                                            } else {
                                                f.tryFailure(future.cause());
                                                c.closeAsync();
                                            }
                                        }
                                    });
                                }
                            });
                        } else {
                            pubSubBootstrap.config().group().execute(new Runnable() {
                                public void run() {
                                    f.tryFailure(future.cause());
                                }
                            });
                        }
                    }
                });
            }
        });
        
        return f;
    }

    public void shutdown() {
        shutdownAsync().syncUninterruptibly();
    }

    public RFuture<Void> shutdownAsync() {
        for (Channel channel : channels) {
            RedisConnection connection = RedisConnection.getFrom(channel);
            if (connection != null) {
                connection.closeAsync();
            }
        }

        final RPromise<Void> result = new RedissonPromise<Void>();
        ChannelGroupFuture channelsFuture = channels.newCloseFuture();
        channelsFuture.addListener(new FutureListener<Void>() {
            @Override
            public void operationComplete(Future<Void> future) throws Exception {
                if (!future.isSuccess()) {
                    result.tryFailure(future.cause());
                    return;
                }
                
                if (!hasOwnTimer && !hasOwnExecutor && !hasOwnResolver && !hasOwnGroup) {
                    result.trySuccess(null);
                    return;
                }
                
                Thread t = new Thread() {
                    @Override
                    public void run() {
                        try {
                            if (hasOwnTimer) {
                                timer.stop();
                            }
                            
                            if (hasOwnExecutor) {
                                executor.shutdown();
                                executor.awaitTermination(15, TimeUnit.SECONDS);
                            }
                            
                            if (hasOwnResolver) {
                                bootstrap.config().resolver().close();
                            }
                            if (hasOwnGroup) {
                                bootstrap.config().group().shutdownGracefully();
                            }
                        } catch (Exception e) {
                            result.tryFailure(e);
                            return;
                        }
                        
                        result.trySuccess(null);
                    }
                };
                t.start();
            }
        });
        
        return result;
    }

    @Override
    public String toString() {
        return "[addr=" + uri + "]";
    }

}
