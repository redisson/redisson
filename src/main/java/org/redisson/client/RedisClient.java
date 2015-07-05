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
import java.util.concurrent.TimeUnit;

import org.redisson.client.handler.RedisCommandsQueue;
import org.redisson.client.handler.RedisDecoder;
import org.redisson.client.handler.RedisEncoder;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.StringCodec;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

public class RedisClient {

    private Bootstrap bootstrap;
    private InetSocketAddress addr;

    private long timeout;
    private TimeUnit timeoutUnit;

    public RedisClient(String host, int port) {
        this(new NioEventLoopGroup(), NioSocketChannel.class, host, port, 60*1000);
    }

    public RedisClient(EventLoopGroup group, Class<? extends SocketChannel> socketChannelClass, String host, int port, int timeout) {
        addr = new InetSocketAddress(host, port);
        bootstrap = new Bootstrap().channel(socketChannelClass).group(group).remoteAddress(addr);
        bootstrap.handler(new ChannelInitializer<Channel>() {

            @Override
            protected void initChannel(Channel ch) throws Exception {
                ch.pipeline().addFirst(
                        new RedisEncoder(),
                        new RedisCommandsQueue(),
                        new RedisDecoder());
            }

        });

        setTimeout(timeout, TimeUnit.MILLISECONDS);
    }

    /**
     * Set the default timeout for {@link RedisConnection connections} created by
     * this client. The timeout applies to connection attempts and non-blocking
     * commands.
     *
     * @param timeout   Сonnection timeout.
     * @param unit      Unit of time for the timeout.
     */
    public void setTimeout(long timeout, TimeUnit unit) {
        this.timeout = timeout;
        this.timeoutUnit = unit;
        bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) unit.toMillis(timeout));
    }

    long getTimeout() {
        return timeout;
    }

    TimeUnit getTimeoutUnit() {
        return timeoutUnit;
    }

    Bootstrap getBootstrap() {
        return bootstrap;
    }

    public RedisConnection connect() {
        ChannelFuture future = bootstrap.connect();
        future.syncUninterruptibly();
        return new RedisConnection(this, future.channel());
    }

    public static void main(String[] args) throws InterruptedException {
        final RedisClient c = new RedisClient("127.0.0.1", 6379);
        RedisConnection rc = c.connect();
//        for (int i = 0; i < 10000; i++) {
            String res1 = rc.sync(new StringCodec(), RedisCommands.CLIENT_SETNAME, "12333");
            System.out.println("res 12: " + res1);
            String res2 = rc.sync(new StringCodec(), RedisCommands.CLIENT_GETNAME);
            System.out.println("res name: " + res2);

/*            Future<String> res = rc.execute(new StringCodec(), RedisCommands.SET, "test", "" + Math.random());
            res.addListener(new FutureListener<String>() {

                @Override
                public void operationComplete(Future<String> future) throws Exception {
//                    System.out.println("res 1: " + future.getNow());
                }

            });

            Future<String> r = rc.execute(new StringCodec(), RedisCommands.GET, "test");
            r.addListener(new FutureListener<Object>() {

                @Override
                public void operationComplete(Future<Object> future) throws Exception {
                    System.out.println("res 2: " + future.getNow());
                }
            });
*///        }
    }
}
