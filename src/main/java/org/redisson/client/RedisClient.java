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

import org.redisson.client.handler.CommandDecoder;
import org.redisson.client.handler.CommandEncoder;
import org.redisson.client.handler.CommandBatchEncoder;
import org.redisson.client.handler.CommandsQueue;
import org.redisson.client.handler.ConnectionWatchdog;

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
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.GlobalEventExecutor;
import io.netty.util.concurrent.ImmediateEventExecutor;
import io.netty.util.concurrent.Promise;
import java.util.Map;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.misc.URIBuilder;

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

    private final long commandTimeout;
    private boolean hasOwnGroup;

    public RedisClient(String address) {
        this(URIBuilder.create(address));
    }
    
    public RedisClient(URI address) {
        this(new NioEventLoopGroup(), address);
        hasOwnGroup = true;
    }

    public RedisClient(EventLoopGroup group, URI address) {
        this(group, address.getHost(), address.getPort());
    }
    
    public RedisClient(String host, int port) {
        this(new NioEventLoopGroup(), NioSocketChannel.class, host, port, 10000);
        hasOwnGroup = true;
    }
    
    public RedisClient(EventLoopGroup group, String host, int port) {
        this(group, NioSocketChannel.class, host, port, 10000);
    }

    public RedisClient(EventLoopGroup group, Class<? extends SocketChannel> socketChannelClass, String host, int port, int connectTimeout) {
        this(group, socketChannelClass, host, port, connectTimeout, 10000);
    }
    
    public RedisClient(EventLoopGroup group, Class<? extends SocketChannel> socketChannelClass, String host, int port, int connectTimeout, int commandTimeout) {
        addr = new InetSocketAddress(host, port);
        bootstrap = new Bootstrap().channel(socketChannelClass).group(group).remoteAddress(addr);
        bootstrap.handler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ch.pipeline().addFirst(new ConnectionWatchdog(bootstrap, channels),
                    CommandEncoder.INSTANCE,
                    CommandBatchEncoder.INSTANCE,
                    new CommandsQueue(),
                    new CommandDecoder());
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

    public Future<RedisConnection> connectAsync() {
        final Promise<RedisConnection> f = ImmediateEventExecutor.INSTANCE.newPromise();
        ChannelFuture channelFuture = bootstrap.connect();
        channelFuture.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(final ChannelFuture future) throws Exception {
                 if (future.isSuccess()) {
                     final RedisConnection c = new RedisConnection(RedisClient.this, future.channel());
                     bootstrap.group().execute(new Runnable() {
                         public void run() {
                            f.setSuccess(c);
                         }
                     });
                 } else {
                     bootstrap.group().execute(new Runnable() {
                         public void run() {
                             f.setFailure(future.cause());
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

    public Future<RedisPubSubConnection> connectPubSubAsync() {
        final Promise<RedisPubSubConnection> f = ImmediateEventExecutor.INSTANCE.newPromise();
        ChannelFuture channelFuture = bootstrap.connect();
        channelFuture.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(final ChannelFuture future) throws Exception {
                 if (future.isSuccess()) {
                    final RedisPubSubConnection c = new RedisPubSubConnection(RedisClient.this, future.channel());
                    bootstrap.group().execute(new Runnable() {
                        public void run() {
                            f.setSuccess(c);
                        }
                    });
                } else {
                    bootstrap.group().execute(new Runnable() {
                        public void run() {
                            f.setFailure(future.cause());
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
            bootstrap.group().shutdownGracefully();
        }
    }

    public ChannelGroupFuture shutdownAsync() {
        for (Channel channel : channels) {
            RedisConnection.getFrom(channel).setClosed(true);
        }
        return channels.close();
    }

    /**
     * Execute INFO SERVER operation.
     *
     * @return Map extracted from each response line splitting by ':' symbol
     */
    public Map<String, String> serverInfo() {
        try {
            return serverInfoAsync().sync().get();
        } catch (Exception e) {
            throw new RedisConnectionException("Unable to retrieve server into from: " + addr, e);
        }
    }

    /**
     * Asynchronously execute INFO SERVER operation.
     *
     * @return A future for a map extracted from each response line splitting by
     * ':' symbol
     */
    public Future<Map<String, String>> serverInfoAsync() {
        final RedisConnection connection = connect();
        Promise<Map<String, String>> async = (Promise) connection.async(RedisCommands.SERVER_INFO);
        async.addListener(new GenericFutureListener<Promise<Map<String, String>>>() {
            @Override
            public void operationComplete(Promise<Map<String, String>> future) throws Exception {
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
