/**
 * Copyright 2018 Nikita Koksharov
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
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import org.redisson.client.ChannelName;
import org.redisson.client.RedisConnection;
import org.redisson.client.RedisPubSubConnection;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.CommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.group.ChannelGroup;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.netty.util.HashedWheelTimer;
import io.netty.util.Timeout;
import io.netty.util.Timer;
import io.netty.util.TimerTask;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;

/**
 * 
 * @author Nikita Koksharov
 *
 */
@Sharable
public class ConnectionWatchdog extends ChannelInboundHandlerAdapter {

    /** save the watchdog instance. */
    private static final AttributeKey<ConnectionWatchdog> WATCHDOG = AttributeKey.valueOf("watchDog");
    /** save the reconnecting flag(true/false).*/
    private static final AttributeKey<Boolean> RECONNECTING = AttributeKey.valueOf("reconnecting");
    /** save the channel status. */
    private static final AttributeKey<Boolean> DISCONNECTED = AttributeKey.valueOf("disconnected");
    /** save the reconnect attempt count.*/
    private static final AttributeKey<Integer> RECONNECT_ATTEMPTS = AttributeKey.valueOf("reconnectAttempts");
    /** save the reconnect timeout instance. */
    private static final AttributeKey<Timeout> RECONNECT_TIMEOUT_INS = AttributeKey.valueOf("reconnectTimeoutIns");

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final Timer timer;
    private final Bootstrap bootstrap;
    private final ChannelGroup channels;
    private static final int BACKOFF_CAP = 12;
    private static final int DEBUG_MOD_ATTEMPTS = 10;

    public static ConnectionWatchdog getFrom(Channel channel) {
        return channel.attr(WATCHDOG).get();
    }

    public ConnectionWatchdog(Bootstrap bootstrap, ChannelGroup channels, Timer timer) {
        this.bootstrap = bootstrap;
        this.channels  = channels;
        this.timer = timer;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        ctx.channel().attr(WATCHDOG).set(this);
        ctx.channel().attr(RECONNECTING).set(false);
        ctx.channel().attr(DISCONNECTED).set(false);
        channels.add(ctx.channel());
        ctx.fireChannelActive();
    }

    public void doReconnect(Channel channel) {

        RedisConnection connection = RedisConnection.getFrom(channel);
        if (connection != null) {

            Attribute<Boolean> disconnected = channel.attr(DISCONNECTED);
            if (Boolean.TRUE.equals(disconnected.get())) {
                log.info("the status disconnected, reconnectAttempts={}, {}",
                    channel.attr(RECONNECT_ATTEMPTS).get(), connection);
                //fired from the CommandAsyncService.checkAttemptFuture
                if (!connection.isClosed()) {
                    tryReconnect(channel, connection, channel.attr(RECONNECT_ATTEMPTS).get() + 1);
                } else {
                    log.error("the connection closed,{}", connection);
                }
            } else {
                channel.attr(RECONNECT_ATTEMPTS).set(1);
                disconnected.set(true);
                connection.fireDisconnected();
                if (!connection.isClosed()) {
                    if (connection.isFastReconnect()) {
                        tryReconnect(channel, connection, 1);
                    } else {
                        reconnect(channel, connection, 1);
                    }
                } else {
                    log.error("the connection closed,{}", connection);
                }
            }


        } else {
            log.warn("can't get redisConnect from the channel {}", channel);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        log.warn("channelInactive fired, try to reconnect, {}",
            ctx.channel());

        doReconnect(ctx.channel());
        ctx.fireChannelInactive();
    }

    private void reconnect(final Channel oldChannel, final RedisConnection connection, final int attempts) {
        int timeout = 2 << Math.min(BACKOFF_CAP, attempts);
        if (bootstrap.config().group().isShuttingDown()) {
            return;
        }

        try {
            Attribute<Timeout> timeoutAttr = oldChannel.attr(RECONNECT_TIMEOUT_INS);
            if (timeoutAttr.get() != null) {
                return;
            }
            Timeout timeoutObj = timer.newTimeout(new TimerTask() {
                @Override
                public void run(Timeout timeout) throws Exception {
                    oldChannel.attr(RECONNECT_TIMEOUT_INS).set(null);
                    if (attempts % DEBUG_MOD_ATTEMPTS == 1) {
                        if (timer instanceof HashedWheelTimer) {
                            log.info("tryReconnect, attempts={},pendingTimeouts={}, {}", attempts,
                                ((HashedWheelTimer) timer).pendingTimeouts(), connection);
                        } else {
                            log.info("tryReconnect, attempts={}, {}", attempts, connection);
                        }

                    }
                    //save the attempts to channel attr
                    oldChannel.attr(RECONNECT_ATTEMPTS).set(attempts);
                    tryReconnect(oldChannel, connection, attempts + 1);
                }
            }, timeout, TimeUnit.MILLISECONDS);
            timeoutAttr.set(timeoutObj);
        } catch (IllegalStateException e) {
            // skip
        }
    }

    private void tryReconnect(final Channel oldChannel, final RedisConnection connection, final int nextAttempt) {
        if (connection.isClosed() || bootstrap.config().group().isShuttingDown()) {
            return;
        }

        Attribute<Boolean> reconnectingAttr = oldChannel.attr(RECONNECTING);
        try {

            //reconnecting skip it.
            if (Boolean.TRUE.equals(reconnectingAttr.get())) {
                log.info("The redisConnection is reconnecting. {}", connection);
                return;
            }
            reconnectingAttr.set(true);
            bootstrap.connect(connection.getRedisClient().getAddr()).addListener(new ChannelFutureListener() {

                @Override
                public void operationComplete(final ChannelFuture future) throws Exception {
                    if (connection.isClosed() || bootstrap.config().group().isShuttingDown()) {
                        reconnectingAttr.set(false);
                        log.error("The redisConnection is closed, or bootstrap is shuttingdown . {}", connection);
                        return;
                    }

                    if (future.isSuccess()) {
                        final Channel channel = future.channel();
                        if (channel.localAddress().equals(channel.remoteAddress())) {
                            channel.close();
                            log.error("local address and remote address are the same! connected to: {}, localAddress: {} remoteAddress: {}", 
                                    connection.getRedisClient().getAddr(), channel.localAddress(), channel.remoteAddress());
                        } else {
                            RedisConnection c = RedisConnection.getFrom(channel);
                            c.getConnectionPromise().addListener(new FutureListener<RedisConnection>() {
                                @Override
                                public void operationComplete(Future<RedisConnection> future) throws Exception {
                                    if (future.isSuccess()) {
                                        refresh(connection, channel);
                                        reconnectingAttr.set(false);
                                        log.info("reconnect successfully. {} connected to {}, command: {}", connection,
                                            connection.getRedisClient().getAddr(), connection.getCurrentCommand());
                                    } else {
                                        reconnectingAttr.set(false);
                                        log.warn("Can't connect " + connection + " to " + connection.getRedisClient().getAddr(), future.cause());
                                    }
                                    
                                }
                            });
                            return;
                        }
                    }
                    reconnectingAttr.set(false);
                    reconnect(oldChannel, connection, nextAttempt);
                }
            });
        } catch (RejectedExecutionException e) {
            // skip
            reconnectingAttr.set(false);
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
        }
    }

    private void refresh(RedisConnection connection, Channel channel) {
        CommandData<?, ?> currentCommand = connection.getCurrentCommand();
        connection.fireConnected();
        connection.updateChannel(channel);
        
        if (connection.isFastReconnect()) {
            connection.clearFastReconnect();
        }

        reattachBlockingQueue(connection, currentCommand);
        reattachPubSub(connection);
        channel.attr(DISCONNECTED).set(false);
    }

    private void reattachBlockingQueue(RedisConnection connection, CommandData<?, ?> currentCommand) {
        if (currentCommand == null 
                || !currentCommand.isBlockingCommand()
                    || currentCommand.getPromise().isDone()) {
            return;
        }

        log.info("blocking queue sent " + connection);
        ChannelFuture future = connection.send(currentCommand);
        final CommandData<?, ?> cd = currentCommand;
        future.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                if (!future.isSuccess()) {
                    log.error("Can't reconnect blocking queue to new connection. {}", cd);
                }
            }
        });
    }

}
