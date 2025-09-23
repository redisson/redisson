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

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.redisson.client.*;
import org.redisson.client.protocol.QueueCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.config.Protocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public abstract class BaseConnectionHandler<C extends RedisConnection> extends ChannelInboundHandlerAdapter {

    private static final Logger log = LoggerFactory.getLogger(BaseConnectionHandler.class);

    final Runnable reauthCallback = () -> authWithCredential();
    final RedisClient redisClient;
    final CompletableFuture<C> connectionPromise = new CompletableFuture<>();
    C connection;

    public BaseConnectionHandler(RedisClient redisClient) {
        super();
        this.redisClient = redisClient;
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        if (connection == null) {
            connection = createConnection(ctx);
        }
        super.channelRegistered(ctx);
    }

    abstract C createConnection(ChannelHandlerContext ctx);

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        RedisClientConfig config = redisClient.getConfig();
        config.getCredentialsResolver().removeRenewAuthCallback(reauthCallback);
        super.channelInactive(ctx);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        List<CompletableFuture<Object>> futures = new ArrayList<>(5);

        CompletableFuture<Object> f = authWithCredential();
        futures.add(f);

        RedisClientConfig config = redisClient.getConfig();

        if (config.getProtocol() == Protocol.RESP3) {
            CompletionStage<Object> f1 = connection.async(RedisCommands.HELLO, "3");
            futures.add(f1.toCompletableFuture());
        }

        if (config.getDatabase() != 0) {
            CompletionStage<Object> future = connection.async(RedisCommands.SELECT, config.getDatabase());
            futures.add(future.toCompletableFuture());
        }
        if (config.getClientName() != null) {
            CompletionStage<Object> future = connection.async(RedisCommands.CLIENT_SETNAME, config.getClientName());
            futures.add(future.toCompletableFuture());
        }
        if (!config.getCapabilities().isEmpty()) {
            CompletionStage<Object> future = connection.async(RedisCommands.CLIENT_CAPA, config.getCapabilities().toArray());
            futures.add(future.toCompletableFuture());
        }
        if (config.isReadOnly()) {
            CompletionStage<Object> future = connection.async(RedisCommands.READONLY);
            futures.add(future.toCompletableFuture());
        }
        if (config.getPingConnectionInterval() > 0) {
            CompletionStage<Object> future = connection.async(RedisCommands.PING);
            futures.add(future.toCompletableFuture());
        }

        CompletableFuture<Void> future = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        future.whenComplete((res, e) -> {
            if (e != null) {
                if (e instanceof RedisRetryException) {
                    ctx.executor().schedule(() -> {
                        channelActive(ctx);
                    }, 1, TimeUnit.SECONDS);
                    return;
                }
                connection.closeAsync();
                connectionPromise.completeExceptionally(e);
                return;
            }

            Duration renewalInterval = config.getCredentialsResolver().timeToLive();
            if (config.getCredentialsReapplyInterval() > 0) {
                renewalInterval = Duration.ofMillis(config.getCredentialsReapplyInterval());
            }
            if (renewalInterval.toMillis() > 0) {
                reapplyCredential(ctx, renewalInterval);
            }

            config.getCredentialsResolver().addRenewAuthCallback(reauthCallback);

            ctx.fireChannelActive();
            connectionPromise.complete(connection);
        });
    }

    private CompletableFuture<Object> authWithCredential() {
        RedisClientConfig config = redisClient.getConfig();
        InetSocketAddress addr = redisClient.resolveAddr().getNow(null);
        CompletionStage<Object> f = config.getCredentialsResolver().resolve(addr)
                .thenCompose(credentials -> {
                    String password = Objects.toString(config.getAddress().getPassword(),
                            Objects.toString(credentials.getPassword(), config.getPassword()));
                    if (password != null) {
                        CompletionStage<Object> future;
                        String username = Objects.toString(config.getAddress().getUsername(),
                                Objects.toString(credentials.getUsername(), config.getUsername()));
                        if (username != null) {
                            future = connection.async(RedisCommands.AUTH, username, password);
                        } else {
                            future = connection.async(RedisCommands.AUTH, password);
                        }
                        return future;
                    }
                    return CompletableFuture.completedFuture(null);
                });

        return f.toCompletableFuture();
    }

    private void reapplyCredential(ChannelHandlerContext ctx, Duration renewalInterval) {
        if (isClosed(ctx, connection)) {
            return;
        }

        CompletableFuture<Object> future;
        QueueCommand currentCommand = connection.getCurrentCommandData();
        if (connection.getUsage() == 0 && (currentCommand == null || !currentCommand.isBlockingCommand())) {
            future = authWithCredential();
        } else {
            future = null;
        }

        RedisClientConfig config = redisClient.getConfig();

        config.getTimer().newTimeout(timeout -> {
            if (isClosed(ctx, connection)) {
                return;
            }

            QueueCommand cd = connection.getCurrentCommandData();
            if (cd != null && cd.isBlockingCommand()) {
                reapplyCredential(ctx, renewalInterval);
                return;
            }

            if (connection.getUsage() == 0 && future != null && (future.cancel(false) || cause(future) != null)) {
                Throwable cause = cause(future);
                if (!(cause instanceof RedisRetryException)) {
                    if (!future.isCancelled()) {
                        log.error("Unable to send AUTH command over channel: {}", ctx.channel(), cause);
                    }

                    log.debug("channel: {} closed due to AUTH response timeout set in {} ms", ctx.channel(), config.getCredentialsReapplyInterval());
                    ctx.channel().close();
                } else {
                    reapplyCredential(ctx, renewalInterval);
                }

            } else {
                reapplyCredential(ctx, renewalInterval);
            }
        }, renewalInterval.toMillis(), TimeUnit.MILLISECONDS);
    }

    protected Throwable cause(CompletableFuture<?> future) {
        try {
            future.toCompletableFuture().getNow(null);
            return null;
        } catch (CompletionException ex2) {
            return ex2.getCause();
        } catch (CancellationException ex1) {
            return ex1;
        }
    }

    private static boolean isClosed(ChannelHandlerContext ctx, RedisConnection connection) {
        return connection.isClosed()
                || !ctx.channel().equals(connection.getChannel())
                || ctx.isRemoved()
                || connection.getRedisClient().isShutdown();
    }
    
}
