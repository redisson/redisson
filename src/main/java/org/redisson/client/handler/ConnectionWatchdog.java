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
package org.redisson.client.handler;

import java.util.concurrent.TimeUnit;

import org.redisson.client.RedisConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.group.ChannelGroup;
import io.netty.util.concurrent.GenericFutureListener;

public class ConnectionWatchdog extends ChannelInboundHandlerAdapter {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final Bootstrap bootstrap;
    private final ChannelGroup channels;
    private static final int BACKOFF_CAP = 12;

    public ConnectionWatchdog(Bootstrap bootstrap, ChannelGroup channels) {
        this.bootstrap = bootstrap;
        this.channels  = channels;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        channels.add(ctx.channel());
        ctx.fireChannelActive();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        RedisConnection connection = ctx.channel().attr(RedisConnection.CONNECTION).get();
        if (!connection.isClosed()) {
            EventLoopGroup group = ctx.channel().eventLoop().parent();
            reconnect(group, connection);
        }
        ctx.fireChannelInactive();
    }

    private void reconnect(final EventLoopGroup group, final RedisConnection connection){
        group.schedule(new Runnable() {
            @Override
            public void run() {
                doReConnect(group, connection, 1);
            }
        }, 100, TimeUnit.MILLISECONDS);
    }

    private void doReConnect(final EventLoopGroup group, final RedisConnection connection, final int attempts) {
        if (connection.isClosed()) {
            return;
        }

        log.debug("reconnecting {} to {} ", connection, connection.getRedisClient().getAddr(), connection);

        bootstrap.connect().addListener(new GenericFutureListener<ChannelFuture>() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                if (connection.isClosed()) {
                    return;
                }

                if (future.isSuccess()) {
                    log.debug("{} connected to {}", connection, connection.getRedisClient().getAddr());
                    connection.updateChannel(future.channel());
                    return;
                }

                int timeout = 2 << attempts;
                group.schedule(new Runnable() {
                    @Override
                    public void run() {
                        doReConnect(group, connection, Math.min(BACKOFF_CAP, attempts + 1));
                    }
                }, timeout, TimeUnit.MILLISECONDS);
            }
        });
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ctx.channel().close();
    }

}
