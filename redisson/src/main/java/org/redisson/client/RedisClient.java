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
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.redisson.api.RFuture;
import org.redisson.client.handler.CommandBatchEncoder;
import org.redisson.client.handler.CommandDecoder;
import org.redisson.client.handler.CommandEncoder;
import org.redisson.client.handler.CommandsQueue;
import org.redisson.client.handler.ConnectionWatchdog;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.misc.RPromise;
import org.redisson.misc.RedissonPromise;
import org.redisson.misc.URLBuilder;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
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
import io.netty.util.concurrent.GlobalEventExecutor;

/**
 * Low-level Redis client
 * 
 * @author Nikita Koksharov
 *
 */
public class RedisClient {

    private final Bootstrap bootstrap;
    private final InetSocketAddress addr;
    private final ChannelGroup channels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);

    private ExecutorService executor;
    private final long commandTimeout;
    private Timer timer;
    private boolean hasOwnGroup;

    public RedisClient(String address) {
        this(URLBuilder.create(address));
    }
    
    public RedisClient(URL address) {
        this(new HashedWheelTimer(), Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2), new NioEventLoopGroup(), address);
        hasOwnGroup = true;
    }

    public RedisClient(Timer timer, ExecutorService executor, EventLoopGroup group, URL address) {
        this(timer, executor, group, address.getHost(), address.getPort());
    }
    
    public RedisClient(String host, int port) {
        this(new HashedWheelTimer(), Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2), new NioEventLoopGroup(), NioSocketChannel.class, host, port, 10000, 10000);
        hasOwnGroup = true;
    }
    
    public RedisClient(Timer timer, ExecutorService executor, EventLoopGroup group, String host, int port) {
        this(timer, executor, group, NioSocketChannel.class, host, port, 10000, 10000);
    }
    
    public RedisClient(String host, int port, int connectTimeout, int commandTimeout) {
        this(new HashedWheelTimer(), Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2), new NioEventLoopGroup(), NioSocketChannel.class, host, port, connectTimeout, commandTimeout);
    }

    public RedisClient(final Timer timer, ExecutorService executor, EventLoopGroup group, Class<? extends SocketChannel> socketChannelClass, String host, int port, 
                        int connectTimeout, int commandTimeout) {
        if (timer == null) {
            throw new NullPointerException("timer param can't be null");
        }
        this.executor = executor;
        this.timer = timer;
        addr = new InetSocketAddress(host, port);
        bootstrap = new Bootstrap().channel(socketChannelClass).group(group).remoteAddress(addr);
        bootstrap.handler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ch.pipeline().addFirst(new ConnectionWatchdog(bootstrap, channels, timer),
                    CommandEncoder.INSTANCE,
                    CommandBatchEncoder.INSTANCE,
                    new CommandsQueue(),
                    new CommandDecoder(RedisClient.this.executor));
            }
        });

        bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeout);
        this.commandTimeout = commandTimeout;
    }


    public InetSocketAddress getAddr() {
        return addr;
    }

    public long getCommandTimeout() {
        return commandTimeout;
    }

    public Bootstrap getBootstrap() {
        return bootstrap;
    }

    public RedisConnection connect() {
        try {
            ChannelFuture future = bootstrap.connect();
            future.syncUninterruptibly();
            return new RedisConnection(this, future.channel());
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
                    final RedisConnection c = new RedisConnection(RedisClient.this, future.channel());
                    bootstrap.group().execute(new Runnable() {
                        public void run() {
                            if (!f.trySuccess(c)) {
                                c.closeAsync();
                            }
                        }
                    });
                } else {
                    bootstrap.group().execute(new Runnable() {
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
            ChannelFuture future = bootstrap.connect();
            future.syncUninterruptibly();
            return new RedisPubSubConnection(this, future.channel());
        } catch (Exception e) {
            throw new RedisConnectionException("Unable to connect to: " + addr, e);
        }
    }

    public RFuture<RedisPubSubConnection> connectPubSubAsync() {
        final RPromise<RedisPubSubConnection> f = new RedissonPromise<RedisPubSubConnection>();
        ChannelFuture channelFuture = bootstrap.connect();
        channelFuture.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(final ChannelFuture future) throws Exception {
                if (future.isSuccess()) {
                    final RedisPubSubConnection c = new RedisPubSubConnection(RedisClient.this, future.channel());
                    bootstrap.group().execute(new Runnable() {
                        public void run() {
                            if (!f.trySuccess(c)) {
                                c.closeAsync();
                            }
                        }
                    });
                } else {
                    bootstrap.group().execute(new Runnable() {
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
        if (hasOwnGroup) {
            timer.stop();
            executor.shutdown();
            try {
                executor.awaitTermination(15, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            bootstrap.group().shutdownGracefully();
            
        }
    }

    public ChannelGroupFuture shutdownAsync() {
        for (Channel channel : channels) {
            RedisConnection connection = RedisConnection.getFrom(channel);
            if (connection != null) {
                connection.setClosed(true);
            }
        }
        return channels.close();
    }

    @Deprecated
    public Map<String, String> serverInfo() {
        try {
            return serverInfoAsync().sync().get();
        } catch (Exception e) {
            throw new RedisConnectionException("Unable to retrieve server into from: " + addr, e);
        }
    }

    @Deprecated
    public RFuture<Map<String, String>> serverInfoAsync() {
        final RedisConnection connection = connect();
        RFuture<Map<String, String>> async = connection.async(RedisCommands.INFO_SERVER);
        async.addListener(new FutureListener<Map<String, String>>() {
            @Override
            public void operationComplete(Future<Map<String, String>> future) throws Exception {
                connection.closeAsync();
            }
        });
        return async;
    }

    @Override
    public String toString() {
        return "[addr=" + addr + "]";
    }

}
