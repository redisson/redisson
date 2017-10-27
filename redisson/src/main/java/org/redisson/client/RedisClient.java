/**
 * Copyright 2016 Nikita Koksharov
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

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.redisson.api.RFuture;
import org.redisson.client.handler.RedisChannelInitializer;
import org.redisson.client.handler.RedisChannelInitializer.Type;
import org.redisson.misc.RPromise;
import org.redisson.misc.RedissonPromise;
import org.redisson.misc.URIBuilder;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.ChannelGroupFuture;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.HashedWheelTimer;
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

    private final Bootstrap bootstrap;
    private final Bootstrap pubSubBootstrap;
    private final InetSocketAddress addr;
    private final ChannelGroup channels;

    private ExecutorService executor;
    private final long commandTimeout;
    private Timer timer;
    private RedisClientConfig config;

    private boolean hasOwnTimer;
    private boolean hasOwnExecutor;
    private boolean hasOwnGroup;

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

        this.config = copy;
        this.executor = copy.getExecutor();
        this.timer = copy.getTimer();
        
        addr = new InetSocketAddress(copy.getAddress().getHost(), copy.getAddress().getPort());
        
        channels = new DefaultChannelGroup(copy.getGroup().next()); 
        bootstrap = createBootstrap(copy, Type.PLAIN);
        pubSubBootstrap = createBootstrap(copy, Type.PUBSUB);
        
        this.commandTimeout = copy.getCommandTimeout();
    }

    private Bootstrap createBootstrap(RedisClientConfig config, Type type) {
        Bootstrap bootstrap = new Bootstrap()
                        .channel(config.getSocketChannelClass())
                        .group(config.getGroup())
                        .remoteAddress(addr);

        bootstrap.handler(new RedisChannelInitializer(bootstrap, config, this, channels, type));
        bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, config.getConnectTimeout());
        bootstrap.option(ChannelOption.SO_KEEPALIVE, config.isKeepAlive());
        bootstrap.option(ChannelOption.TCP_NODELAY, config.isTcpNoDelay());
        return bootstrap;
    }
    
    /*
     * Use {@link #create(RedisClientConfig)}
     * 
     */
    @Deprecated
    public RedisClient(String address) {
        this(URIBuilder.create(address));
    }
    
    /*
     * Use {@link #create(RedisClientConfig)}
     * 
     */
    @Deprecated
    public RedisClient(URI address) {
        this(new HashedWheelTimer(), Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2), new NioEventLoopGroup(), address);
        hasOwnGroup = true;
    }

    /*
     * Use {@link #create(RedisClientConfig)}
     * 
     */
    @Deprecated
    public RedisClient(Timer timer, ExecutorService executor, EventLoopGroup group, URI address) {
        this(timer, executor, group, address.getHost(), address.getPort());
    }
    
    /*
     * Use {@link #create(RedisClientConfig)}
     * 
     */
    @Deprecated
    public RedisClient(String host, int port) {
        this(new HashedWheelTimer(), Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2), new NioEventLoopGroup(), NioSocketChannel.class, host, port, 10000, 10000);
        hasOwnGroup = true;
    }

    /*
     * Use {@link #create(RedisClientConfig)}
     * 
     */
    @Deprecated
    public RedisClient(Timer timer, ExecutorService executor, EventLoopGroup group, String host, int port) {
        this(timer, executor, group, NioSocketChannel.class, host, port, 10000, 10000);
    }
    
    /*
     * Use {@link #create(RedisClientConfig)}
     * 
     */
    @Deprecated
    public RedisClient(String host, int port, int connectTimeout, int commandTimeout) {
        this(new HashedWheelTimer(), Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2), new NioEventLoopGroup(), NioSocketChannel.class, host, port, connectTimeout, commandTimeout);
    }

    /*
     * Use {@link #create(RedisClientConfig)}
     * 
     */
    @Deprecated
    public RedisClient(final Timer timer, ExecutorService executor, EventLoopGroup group, Class<? extends SocketChannel> socketChannelClass, String host, int port, 
                        int connectTimeout, int commandTimeout) {
        RedisClientConfig config = new RedisClientConfig();
        config.setTimer(timer).setExecutor(executor).setGroup(group).setSocketChannelClass(socketChannelClass)
        .setAddress(host, port).setConnectTimeout(connectTimeout).setCommandTimeout(commandTimeout);
        
        this.config = config;
        this.executor = config.getExecutor();
        this.timer = config.getTimer();
        
        addr = new InetSocketAddress(config.getAddress().getHost(), config.getAddress().getPort());
        
        channels = new DefaultChannelGroup(config.getGroup().next());
        bootstrap = createBootstrap(config, Type.PLAIN);
        pubSubBootstrap = createBootstrap(config, Type.PUBSUB);
        
        this.commandTimeout = config.getCommandTimeout();
    }

    public String getIpAddr() {
        return addr.getAddress().getHostAddress() + ":" + addr.getPort();
    }

    public InetSocketAddress getAddr() {
        return addr;
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
            throw new RedisConnectionException("Unable to connect to: " + addr, e);
        }
    }

    public RFuture<RedisConnection> connectAsync() {
        final RPromise<RedisConnection> f = new RedissonPromise<RedisConnection>();
        ChannelFuture channelFuture = bootstrap.connect();
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
        return f;
    }

    public RedisPubSubConnection connectPubSub() {
        try {
            return connectPubSubAsync().syncUninterruptibly().getNow();
        } catch (Exception e) {
            throw new RedisConnectionException("Unable to connect to: " + addr, e);
        }
    }

    public RFuture<RedisPubSubConnection> connectPubSubAsync() {
        final RPromise<RedisPubSubConnection> f = new RedissonPromise<RedisPubSubConnection>();
        ChannelFuture channelFuture = pubSubBootstrap.connect();
        channelFuture.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(final ChannelFuture future) throws Exception {
                if (future.isSuccess()) {
                    final RedisPubSubConnection c = RedisPubSubConnection.getFrom(future.channel());
                    c.<RedisPubSubConnection>getConnectionPromise().addListener(new FutureListener<RedisPubSubConnection>() {
                        @Override
                        public void operationComplete(final Future<RedisPubSubConnection> future) throws Exception {
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
        return f;
    }

    public void shutdown() {
        shutdownAsync().syncUninterruptibly();
    }

    public RFuture<Void> shutdownAsync() {
        for (Channel channel : channels) {
            RedisConnection connection = RedisConnection.getFrom(channel);
            if (connection != null) {
                connection.setClosed(true);
            }
        }
        ChannelGroupFuture channelsFuture = channels.close();
        
        final RPromise<Void> result = new RedissonPromise<Void>();
        channelsFuture.addListener(new FutureListener<Void>() {
            @Override
            public void operationComplete(Future<Void> future) throws Exception {
                if (!future.isSuccess()) {
                    result.tryFailure(future.cause());
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
        return "[addr=" + addr + "]";
    }

}
