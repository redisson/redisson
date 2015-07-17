/**
 * Copyright 2014 Nikita Koksharov, Nickolay Borbit
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
import java.util.concurrent.ExecutionException;

import org.redisson.client.handler.CommandDecoder;
import org.redisson.client.handler.CommandEncoder;
import org.redisson.client.handler.CommandsListEncoder;
import org.redisson.client.handler.CommandsQueue;
import org.redisson.client.handler.ConnectionWatchdog;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.StringCodec;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.ChannelGroupFuture;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.concurrent.GlobalEventExecutor;

public class RedisClient {

    private final Bootstrap bootstrap;
    private final InetSocketAddress addr;
    private final ChannelGroup channels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);

    private final long timeout;

    public RedisClient(String host, int port) {
        this(new NioEventLoopGroup(), NioSocketChannel.class, host, port, 60*1000);
    }

    public RedisClient(EventLoopGroup group, Class<? extends SocketChannel> socketChannelClass, String host, int port, int timeout) {
        addr = new InetSocketAddress(host, port);
        bootstrap = new Bootstrap().channel(socketChannelClass).group(group).remoteAddress(addr);
        bootstrap.handler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ch.pipeline().addFirst(new ConnectionWatchdog(bootstrap, channels),
                                        new CommandEncoder(),
                                        new CommandsListEncoder(),
                                        new CommandsQueue(),
                                        new CommandDecoder());
            }
        });

        bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, timeout);
        this.timeout = timeout;
    }

    public InetSocketAddress getAddr() {
        return addr;
    }

    long getTimeout() {
        return timeout;
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
            throw new RedisConnectionException("unable to connect", e);
        }
    }

    public RedisPubSubConnection connectPubSub() {
        try {
            ChannelFuture future = bootstrap.connect();
            future.syncUninterruptibly();
            return new RedisPubSubConnection(this, future.channel());
        } catch (Exception e) {
            throw new RedisConnectionException("unable to connect", e);
        }
    }

    public void shutdown() {
        shutdownAsync().syncUninterruptibly();
    }

    public ChannelGroupFuture shutdownAsync() {
        return channels.close();
    }

    public static void main(String[] args) throws InterruptedException, ExecutionException {
        final RedisClient c = new RedisClient("127.0.0.1", 6379);
        Object r = c.connect().sync(new StringCodec(), RedisCommands.GET, "test1");
        System.out.println(r);
//        final RedisClient c = new RedisClient("127.0.0.1", 26379);
//        RedisConnection rc = c.connect();
//        List<String> res4 = rc.sync(RedisCommands.SENTINEL_GET_MASTER_ADDR_BY_NAME, "mymaster");
//        System.out.println("r: " + res4);
//
//        List<Map<String, String>> res5 = rc.sync(RedisCommands.SENTINEL_SLAVES, "mymaster");
//        System.out.println("r: " + res5);


/*        RedisPubSubConnection rpsc = c.connectPubSub();

            rc.sync(new StringCodec(), RedisCommands.HMSET, "test", "1", "2");
            rc.sync(new StringCodec(), RedisCommands.HMSET, "test", "2", "3");
            List<String> r = rc.sync(new StringCodec(), RedisCommands.HMGET, "test", "1", "2");


            String res1 = rc.sync(RedisCommands.CLIENT_SETNAME, "12333");
            System.out.println("res 12: " + res1);
            String res2 = rc.sync(RedisCommands.CLIENT_GETNAME);
            System.out.println("res name: " + res2);
//            Boolean res3 = rc.sync(new StringCodec(), RedisCommands.EXISTS, "33");
//            System.out.println("res name 2: " + res3);

            Long m = rc.sync(new StringCodec(), RedisCommands.PUBLISH, "sss", "123");
            System.out.println("out: " + m);
            Future<PubSubStatusMessage> m1 = rpsc.psubscribe("ss*");
            System.out.println("out: " + m1.get());
            Future<PubSubStatusMessage> m2 = rpsc.psubscribe("ss*");
            System.out.println("out: " + m2.get());
            rpsc.addListener(new RedisPubSubListener<String>() {
                @Override
                public void onMessage(String channel, String message) {
                    System.out.println("incoming message: " + message);
                }

                @Override
                public void onPatternMessage(String pattern, String channel, String message) {
                    System.out.println("incoming pattern pattern: " + pattern
                            + " channel: " + channel + " message: " + message);

                }
            });


            final RedisClient c2 = new RedisClient("127.0.0.1", 6379);
            Long res = c2.connect().sync(new StringCodec(), RedisCommands.PUBLISH, "sss", "4444");
            System.out.println("published: " + res);

            Future<PubSubStatusMessage> m3 = rpsc.punsubscribe("ss*");
            System.out.println("punsubscribe out: " + m3.get());

            final RedisClient c3 = new RedisClient("127.0.0.1", 6379);
            Long res3 = c3.connect().sync(new StringCodec(), RedisCommands.PUBLISH, "sss", "4444");
            System.out.println("published: " + res3);

*/    }
}

