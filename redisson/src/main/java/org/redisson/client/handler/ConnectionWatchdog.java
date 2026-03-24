/**
 * Copyright (c) 2013-2026 Nikita Koksharov
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
import io.netty.channel.*;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.group.ChannelGroup;
import io.netty.util.Timer;
import org.redisson.client.*;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.CommandData;
import org.redisson.client.protocol.QueueCommand;
import org.redisson.config.DelayStrategy;
import org.redisson.misc.AsyncSemaphore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map.Entry;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * 
 * @author Nikita Koksharov
 *
 */
@Sharable
public class ConnectionWatchdog extends ChannelInboundHandlerAdapter {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final DelayStrategy reconnectionDelay;
    private final Timer timer;
    private final Bootstrap bootstrap;
    private final ChannelGroup channels;
    private final AsyncSemaphore semaphore = new AsyncSemaphore(2);

    public ConnectionWatchdog(Bootstrap bootstrap, ChannelGroup channels, RedisClientConfig config) {
        this.bootstrap = bootstrap;
        this.channels  = channels;
        this.timer = config.getTimer();
        this.reconnectionDelay = config.getReconnectionDelay();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        channels.add(ctx.channel());
        ctx.fireChannelActive();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        RedisConnection connection = RedisConnection.getFrom(ctx.channel());
        if (connection != null) {
            if (!connection.isClosedIdle()) {
                connection.fireDisconnected();
            }

            if (!connection.isClosed()) {
                if (connection.isFastReconnect()) {
                    tryReconnect(connection, 0);
                } else {
                    semaphore.acquire().thenAccept(r -> {
                        reconnect(connection, 0);
                    });
                }
            }
        }
        ctx.fireChannelInactive();
    }

    private void reconnect(RedisConnection connection, int attempt){
        if (connection.isClosed()) {
            semaphore.release();
            return;
        }
        if (connection.getRedisClient().isShutdown()
                || bootstrap.config().group().isShuttingDown()) {
            return;
        }

        Duration timeout = reconnectionDelay.calcDelay(attempt);

        try {
            timer.newTimeout(t -> tryReconnect(connection, attempt + 1), timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (IllegalStateException e) {
            // skip
        }
    }

    private void tryReconnect(RedisConnection connection, int nextAttempt) {
        if (connection.isClosed()) {
            semaphore.release();
            return;
        }
        if (connection.getRedisClient().isShutdown()
                || bootstrap.config().group().isShuttingDown()) {
            return;
        }

        log.debug("reconnecting {} to {} ", connection, connection.getRedisClient().getAddr());

        try {
            bootstrap.connect(connection.getRedisClient().getAddr()).addListener(new ChannelFutureListener() {

                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if (connection.getRedisClient().isShutdown()
                            || connection.isClosed()
                                || bootstrap.config().group().isShuttingDown()) {
                        if (future.isSuccess()) {
                            Channel ch = future.channel();
                            RedisConnection con = RedisConnection.getFrom(ch);
                            if (con != null) {
                                con.closeAsync();
                            }
                        }
                        if (connection.isClosed()) {
                            semaphore.release();
                        }
                        return;
                    }

                    if (future.isSuccess()) {
                        Channel channel = future.channel();
                        if (channel.localAddress().equals(channel.remoteAddress())) {
                            channel.close();
                            log.error("local address and remote address are the same! connected to: {}, localAddress: {} remoteAddress: {}", 
                                    connection.getRedisClient().getAddr(), channel.localAddress(), channel.remoteAddress());
                        } else {
                            RedisConnection c = RedisConnection.getFrom(channel);
                            c.getConnectionPromise().whenComplete((res, e) -> {
                                if (e == null) {
                                    semaphore.release();
                                    if (connection.getRedisClient().isShutdown()
                                            || connection.isClosed()) {
                                        channel.close();
                                        return;
                                    } else {
                                        log.debug("{} connected to {}, command: {}", connection, connection.getRedisClient().getAddr(), connection.getCurrentCommandData());
                                    }
                                    refresh(connection, channel);
                                } else {
                                    channel.close();
                                    reconnect(connection, nextAttempt);
                                }
                            });
                            return;
                        }
                    }

                    reconnect(connection, nextAttempt);
                }
            });
        } catch (RejectedExecutionException e) {
            // skip
        }
    }

    private void reattachPubSub(RedisConnection connection) {
        if (connection instanceof RedisPubSubConnection) {
            RedisPubSubConnection conn = (RedisPubSubConnection) connection;
            for (Entry<ChannelName, Codec> entry : conn.getChannels().entrySet()) {
                conn.subscribe(entry.getValue(), entry.getKey());
            }
            for (Entry<ChannelName, Codec> entry : conn.getPatternChannels().entrySet()) {
                conn.psubscribe(entry.getValue(), entry.getKey());
            }
            for (Entry<ChannelName, Codec> entry : conn.getShardedChannels().entrySet()) {
                conn.ssubscribe(entry.getValue(), entry.getKey());
            }
        }
    }

    private void refresh(RedisConnection connection, Channel channel) {
        QueueCommand currentCommand = connection.getCurrentCommandData();
        connection.fireConnected();
        connection.updateChannel(channel);
        
        if (connection.isFastReconnect()) {
            connection.clearFastReconnect();
        }

        if (currentCommand instanceof CommandData) {
            reattachBlockingQueue(connection, (CommandData<?, ?>) currentCommand);
        }
        reattachPubSub(connection);

        if (currentCommand != null
                && !currentCommand.isBlockingCommand()
                    && !(connection instanceof RedisPubSubConnection)) {
            currentCommand.tryFailure(new RedisReconnectedException("Channel has been reconnected"));
        }
    }

    private void reattachBlockingQueue(RedisConnection connection, CommandData<?, ?> currentCommand) {
        if (currentCommand == null 
                || !currentCommand.isBlockingCommand()
                    || currentCommand.getPromise().isDone()) {
            return;
        }

        ChannelFuture future = connection.send(currentCommand);
        future.addListener((ChannelFutureListener) f -> {
            if (!f.isSuccess()) {
                log.error("Can't reconnect blocking queue by command: {} using connection: {}", currentCommand, connection);
            }
        });
    }

}
