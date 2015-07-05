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

import org.redisson.client.handler.RedisCommandsQueue;
import org.redisson.client.handler.RedisData;
import org.redisson.client.handler.RedisDecoder;
import org.redisson.client.handler.RedisEncoder;
import org.redisson.client.protocol.Codec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.StringCodec;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;

public class RedisClient {

    private Class<? extends SocketChannel> socketChannelClass = NioSocketChannel.class;
    private Bootstrap bootstrap;
    private EventLoopGroup group = new NioEventLoopGroup();
    private InetSocketAddress addr;
    private Channel channel;

    public RedisClient(String host, int port) {
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
    }

    public ChannelFuture connect() {
        ChannelFuture future = bootstrap.connect();
        channel = future.channel();
        return future;
    }

//    public <R> Future<R> execute(Codec encoder, RedisCommand<R> command, Object ... params) {
//        Promise<R> promise = bootstrap.group().next().<R>newPromise();
//        channel.writeAndFlush(new RedisData<R, R>(promise, encoder, command, params));
//        return promise;
//    }

    public <T, R> Future<R> execute(Codec encoder, RedisCommand<T> command, Object ... params) {
        Promise<R> promise = bootstrap.group().next().<R>newPromise();
        channel.writeAndFlush(new RedisData<T, R>(promise, encoder, command, params));
        return promise;
    }

    public static void main(String[] args) throws InterruptedException {
        RedisClient rc = new RedisClient("127.0.0.1", 6379);
        rc.connect().sync();
        Future<String> res = rc.execute(new StringCodec(), RedisCommands.SET, "test", "" + Math.random());
        res.addListener(new FutureListener<String>() {

            @Override
            public void operationComplete(Future<String> future) throws Exception {
                System.out.println("res 1: " + future.getNow());
            }

        });

        Future<String> r = rc.execute(new StringCodec(), RedisCommands.GET, "test");
        r.addListener(new FutureListener<Object>() {

            @Override
            public void operationComplete(Future<Object> future) throws Exception {
                System.out.println("res 2: " + future.getNow());
            }
        });
    }
}
