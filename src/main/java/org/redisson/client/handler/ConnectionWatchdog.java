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
package org.redisson.client.handler;

import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

import org.redisson.client.RedisConnection;
import org.redisson.client.RedisException;
import org.redisson.client.RedisPubSubConnection;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.CommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.group.ChannelGroup;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.ImmediateEventExecutor;
import io.netty.util.concurrent.Promise;

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
        RedisConnection connection = RedisConnection.getFrom(ctx.channel());
        connection.onDisconnect();
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
                tryReconnect(group, connection, 1);
            }
        }, 100, TimeUnit.MILLISECONDS);
    }

    private void tryReconnect(final EventLoopGroup group, final RedisConnection connection, final int attempts) {
        if (connection.isClosed() || group.isShuttingDown()) {
            return;
        }

        log.debug("reconnecting {} to {} ", connection, connection.getRedisClient().getAddr(), connection);

        bootstrap.connect().addListener(new ChannelFutureListener() {

            @Override
            public void operationComplete(final ChannelFuture future) throws Exception {
                if (connection.isClosed() || group.isShuttingDown()) {
                    return;
                }

                try {
                    if (future.isSuccess()) {
                        log.debug("{} connected to {}", connection, connection.getRedisClient().getAddr());
                        reconnect(connection, future.channel());
                        return;
                    }
                } catch (RedisException e) {
                    log.warn("Can't connect " + connection + " to " + connection.getRedisClient().getAddr(), e);
                }

                int timeout = 2 << attempts;
                group.schedule(new Runnable() {
                    @Override
                    public void run() {
                        tryReconnect(group, connection, Math.min(BACKOFF_CAP, attempts + 1));
                    }
                }, timeout, TimeUnit.MILLISECONDS);
            }
        });
    }

    private void reconnect(final RedisConnection connection, final Channel channel) {
        if (connection.getReconnectListener() != null) {
            // new connection used only for channel init
            RedisConnection rc = new RedisConnection(connection.getRedisClient(), channel);
            Promise<RedisConnection> connectionFuture = ImmediateEventExecutor.INSTANCE.newPromise();
            connection.getReconnectListener().onReconnect(rc, connectionFuture);
            connectionFuture.addListener(new FutureListener<RedisConnection>() {
                @Override
                public void operationComplete(Future<RedisConnection> future) throws Exception {
                    if (future.isSuccess()) {
                        refresh(connection, channel);
                    }
                }
            });
        } else {
            refresh(connection, channel);
        }
    }

    private void reattachPubSub(RedisConnection connection) {
        if (connection instanceof RedisPubSubConnection) {
            RedisPubSubConnection conn = (RedisPubSubConnection) connection;
            for (Entry<String, Codec> entry : conn.getChannels().entrySet()) {
                conn.subscribe(entry.getValue(), entry.getKey());
            }
            for (Entry<String, Codec> entry : conn.getPatternChannels().entrySet()) {
                conn.psubscribe(entry.getValue(), entry.getKey());
            }
        }
    }

    private void refresh(RedisConnection connection, Channel channel) {
        CommandData<?, ?> commandData = connection.getCurrentCommand();
        connection.updateChannel(channel);
        
        reattachBlockingQueue(connection, commandData);            
        reattachPubSub(connection);
    }

    private void reattachBlockingQueue(RedisConnection connection, final CommandData<?, ?> commandData) {
        if (commandData == null 
                || !commandData.isBlockingCommand()) {
            return;
        }

        ChannelFuture future = connection.send(commandData);
        future.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                if (!future.isSuccess()) {
                    log.error("Can't reconnect blocking queue to new connection. {}", commandData);
                }
            }
        });
    }

}
