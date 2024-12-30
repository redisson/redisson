/**
 * Copyright (c) 2013-2024 Nikita Koksharov
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

import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.redisson.api.RFuture;
import org.redisson.client.RedisClientConfig;
import org.redisson.client.RedisConnection;
import org.redisson.client.RedisRetryException;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.QueueCommand;
import org.redisson.client.protocol.RedisCommands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

/**
 * 
 * @author Nikita Koksharov
 *
 */
@Sharable
public class PingConnectionHandler extends ChannelInboundHandlerAdapter {

    private static final Logger log = LoggerFactory.getLogger(PingConnectionHandler.class);
    
    private final RedisClientConfig config;

    public PingConnectionHandler(RedisClientConfig config) {
        this.config = config;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        RedisConnection connection = RedisConnection.getFrom(ctx.channel());
        connection.getConnectionPromise().whenComplete((res, e) -> {
            if (e == null) {
                sendPing(ctx);
            }
        });
        ctx.fireChannelActive();
    }

    private void sendPing(ChannelHandlerContext ctx) {
        RedisConnection connection = RedisConnection.getFrom(ctx.channel());
        if (isClosed(ctx, connection)) {
            return;
        }

        RFuture<String> future;
        QueueCommand currentCommand = connection.getCurrentCommandData();
        if (connection.getUsage() == 0 && (currentCommand == null || !currentCommand.isBlockingCommand())) {
            int timeout = Math.max(config.getCommandTimeout(), config.getPingConnectionInterval() / 2);
            future = connection.async(timeout, StringCodec.INSTANCE, RedisCommands.PING);
        } else {
            future = null;
        }

        config.getTimer().newTimeout(timeout -> {
            if (isClosed(ctx, connection)) {
                return;
            }

            QueueCommand cd = connection.getCurrentCommandData();
            if (cd != null && cd.isBlockingCommand()) {
                sendPing(ctx);
                return;
            }

            if (connection.getUsage() == 0
                    && future != null
                        && (future.cancel(false) || cause(future) != null)) {

                Throwable cause = cause(future);

                if (!(cause instanceof RedisRetryException)) {
                    if (!future.isCancelled()) {
                        log.error("Unable to send PING command over channel: {}", ctx.channel(), cause);
                    }

                    log.debug("channel: {} closed due to PING response timeout set in {} ms", ctx.channel(), config.getPingConnectionInterval());
                    ctx.channel().close();
                    connection.getRedisClient().getConfig().getFailedNodeDetector().onPingFailed(cause);
                } else {
                    connection.getRedisClient().getConfig().getFailedNodeDetector().onPingSuccessful();
                    sendPing(ctx);
                }
            } else {
                connection.getRedisClient().getConfig().getFailedNodeDetector().onPingSuccessful();
                sendPing(ctx);
            }
        }, config.getPingConnectionInterval(), TimeUnit.MILLISECONDS);
    }

    private static boolean isClosed(ChannelHandlerContext ctx, RedisConnection connection) {
        return connection.isClosed()
                            || ctx.isRemoved()
                                || connection.getRedisClient().isShutdown();
    }

    protected Throwable cause(RFuture<?> future) {
        try {
            future.toCompletableFuture().getNow(null);
            return null;
        } catch (CompletionException ex2) {
            return ex2.getCause();
        } catch (CancellationException ex1) {
            return ex1;
        }
    }

}
