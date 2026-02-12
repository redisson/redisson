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
package org.redisson.client;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.util.AttributeKey;
import io.netty.util.Timeout;
import org.redisson.RedissonShutdownException;
import org.redisson.api.RFuture;
import org.redisson.client.codec.Codec;
import org.redisson.client.handler.CommandsQueue;
import org.redisson.client.handler.CommandsQueuePubSub;
import org.redisson.client.protocol.*;
import org.redisson.config.DelayStrategy;
import org.redisson.misc.CompletableFutureWrapper;
import org.redisson.misc.LogHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Deque;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedisConnection implements RedisCommands {

    public enum Status {OPEN, CLOSED, CLOSED_IDLE}

    private static final Logger LOG = LoggerFactory.getLogger(RedisConnection.class);
    private static final AttributeKey<RedisConnection> CONNECTION = AttributeKey.valueOf("connection");

    final RedisClient redisClient;

    private volatile CompletableFuture<Void> fastReconnect;
    private volatile Status status = Status.OPEN;
    volatile Channel channel;

    private CompletableFuture<?> connectionPromise;
    private volatile long lastUsageTime;
    @Deprecated
    private Runnable connectedListener;
    @Deprecated
    private Runnable disconnectedListener;

    private final AtomicInteger usage = new AtomicInteger();

    public <C> RedisConnection(RedisClient redisClient, Channel channel, CompletableFuture<C> connectionPromise) {
        this.redisClient = redisClient;
        this.connectionPromise = connectionPromise;

        updateChannel(channel);
        lastUsageTime = System.nanoTime();

        LOG.debug("Connection created {}", redisClient);
    }
    
    protected RedisConnection(RedisClient redisClient) {
        this.redisClient = redisClient;
    }
    
    public void fireConnected() {
        if (connectedListener != null) {
            connectedListener.run();
        }
        if (redisClient.getConfig().getConnectedListener() != null) {
            redisClient.getConfig().getConnectedListener().accept(redisClient.getAddr());
        }
    }

    public int incUsage() {
        return usage.incrementAndGet();
    }

    public int getUsage() {
        return usage.get();
    }

    public int decUsage() {
        return usage.decrementAndGet();
    }

    @Deprecated
    public void setConnectedListener(Runnable connectedListener) {
        this.connectedListener = connectedListener;
    }

    public void fireDisconnected() {
        if (disconnectedListener != null) {
            disconnectedListener.run();
        }
        if (redisClient.getConfig().getDisconnectedListener() != null) {
            redisClient.getConfig().getDisconnectedListener().accept(redisClient.getAddr());
        }
    }

    @Deprecated
    public void setDisconnectedListener(Runnable disconnectedListener) {
        this.disconnectedListener = disconnectedListener;
    }

    public <C extends RedisConnection> CompletableFuture<C> getConnectionPromise() {
        return (CompletableFuture<C>) connectionPromise;
    }
    
    public static <C extends RedisConnection> C getFrom(Channel channel) {
        return (C) channel.attr(RedisConnection.CONNECTION).get();
    }

    public CommandData<?, ?> getLastCommand() {
        Deque<QueueCommandHolder> queue = channel.attr(CommandsQueue.COMMANDS_QUEUE).get();
        if (queue != null) {
            QueueCommandHolder holder = queue.peekLast();
            if (holder != null) {
                if (holder.getCommand() instanceof CommandData) {
                    return (CommandData<?, ?>) holder.getCommand();
                }
            }
        }
        return null;
    }

    public QueueCommand getCurrentCommandData() {
        Queue<QueueCommandHolder> queue = channel.attr(CommandsQueue.COMMANDS_QUEUE).get();
        if (queue != null) {
            QueueCommandHolder holder = queue.peek();
            if (holder != null) {
                return holder.getCommand();
            }
        }

        QueueCommandHolder holder = channel.attr(CommandsQueuePubSub.CURRENT_COMMAND).get();
        if (holder != null) {
            return holder.getCommand();
        }
        return null;
    }

    public CommandData<?, ?> getCurrentCommand() {
        Queue<QueueCommandHolder> queue = channel.attr(CommandsQueue.COMMANDS_QUEUE).get();
        if (queue != null) {
            QueueCommandHolder holder = queue.peek();
            if (holder != null) {
                if (holder.getCommand() instanceof CommandData) {
                    return (CommandData<?, ?>) holder.getCommand();
                }
            }
        }

        QueueCommandHolder holder = channel.attr(CommandsQueuePubSub.CURRENT_COMMAND).get();
        if (holder != null && holder.getCommand() instanceof CommandData) {
            return (CommandData<?, ?>) holder.getCommand();
        }
        return null;
    }

    public long getLastUsageTime() {
        return lastUsageTime;
    }

    public void setLastUsageTime(long lastUsageTime) {
        this.lastUsageTime = lastUsageTime;
    }

    public boolean isOpen() {
        return channel.isOpen();
    }

    /**
     * Check is channel connected and ready for transfer
     *
     * @return true if so
     */
    public boolean isActive() {
        return channel.isActive();
    }

    public void updateChannel(Channel channel) {
        if (channel == null) {
            throw new NullPointerException();
        }
        this.channel = channel;
        channel.attr(CONNECTION).set(this);
    }

    public RedisClient getRedisClient() {
        return redisClient;
    }

    public <R> R await(CompletableFuture<R> future) {
        try {
            return future.get(redisClient.getCommandTimeout(), TimeUnit.MILLISECONDS);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RedisException) {
                throw (RedisException) e.getCause();
            }
            throw new RedisException("Unexpected exception while processing command", e.getCause());
        } catch (TimeoutException e) {
            RedisTimeoutException ex = new RedisTimeoutException("Command execution timeout for " + redisClient.getAddr());
            future.completeExceptionally(ex);
            throw ex;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    public <T> T sync(RedisCommand<T> command, Object... params) {
        return sync(null, command, params);
    }

    public <T, R> ChannelFuture send(CommandData<T, R> data) {
        return channel.writeAndFlush(data);
    }

    public ChannelFuture send(CommandsData data) {
        return channel.writeAndFlush(data);
    }

    public <T, R> R sync(Codec encoder, RedisCommand<T> command, Object... params) {
        CompletableFuture<R> promise = new CompletableFuture<>();
        send(new CommandData<T, R>(promise, encoder, command, params));
        return await(promise);
    }

    public <T, R> RFuture<R> async(RedisCommand<T> command, Object... params) {
        return async(-1, command, params);
    }
    
    public <T, R> RFuture<R> async(long timeout, RedisCommand<T> command, Object... params) {
        return async(timeout, null, command, params);
    }

    public <T, R> RFuture<R> async(Codec codec, RedisCommand<T> command, Object... params) {
        return async(-1, codec, command, params);
    }

    public <T, R> RFuture<R> async(int retryAttempts, DelayStrategy delayStrategy, long timeout, Codec codec, RedisCommand<T> command, Object... params) {
        CompletableFuture<R> result = new CompletableFuture<>();
        AtomicInteger attempts = new AtomicInteger();
        async(result, retryAttempts, attempts, delayStrategy, timeout, codec, command, params);
        return new CompletableFutureWrapper<>(result);
    }

    private <T, R> void async(CompletableFuture<R> promise, int maxAttempts, AtomicInteger attempts, DelayStrategy delayStrategy,
                              long timeout, Codec codec, RedisCommand<T> command, Object... params) {
        RFuture<R> f = async(timeout, codec, command, params);
        f.whenComplete((r, e) -> {
            if (e != null) {
                if (attempts.get() < maxAttempts) {
                    Duration delay = delayStrategy.calcDelay(attempts.get());
                    attempts.incrementAndGet();
                    redisClient.getTimer().newTimeout(t -> {
                        async(promise, maxAttempts, attempts, delayStrategy, timeout, codec, command, params);
                    }, delay.toMillis(), TimeUnit.MILLISECONDS);
                    return;
                }

                promise.completeExceptionally(e);
                return;
            }

            promise.complete(r);
        });
    }

    public <T, R> RFuture<R> async(long timeout, Codec codec, RedisCommand<T> command, Object... params) {
        CompletableFuture<R> promise = new CompletableFuture<>();
        if (timeout == -1) {
            timeout = redisClient.getCommandTimeout();
        }
        
        if (redisClient.isShutdown()) {
            RedissonShutdownException cause = new RedissonShutdownException("Redis client " + redisClient.getAddr() + " is shutdown");
            return new CompletableFutureWrapper<>(cause);
        }

        Timeout scheduledFuture = redisClient.getTimer().newTimeout(t -> {
            RedisTimeoutException ex = new RedisTimeoutException("Command execution timeout for "
                    + LogHelper.toString(command, params) + ", Redis client: " + redisClient);
            promise.completeExceptionally(ex);
        }, timeout, TimeUnit.MILLISECONDS);
        
        promise.whenComplete((res, e) -> {
            scheduledFuture.cancel();
        });
        
        ChannelFuture writeFuture = send(new CommandData<>(promise, codec, command, params));
        writeFuture.addListener((ChannelFutureListener) future -> {
            if (!future.isSuccess()) {
                promise.completeExceptionally(future.cause());
            }
        });
        return new CompletableFutureWrapper<>(promise);
    }

    public <T, R> CommandData<T, R> create(Codec encoder, RedisCommand<T> command, Object... params) {
        CompletableFuture<R> promise = new CompletableFuture<>();
        return new CommandData<>(promise, encoder, command, params);
    }

    public boolean isClosed() {
        return status != Status.OPEN;
    }

    public boolean isFastReconnect() {
        return fastReconnect != null;
    }
    
    public void clearFastReconnect() {
        fastReconnect.complete(null);
        fastReconnect = null;
    }

    public void close() {
        try {
            closeAsync().sync();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            throw e;
        }
    }

    private void closeInternal() {
        channel.close();
    }
    
    public CompletionStage<Void> forceFastReconnectAsync() {
        CompletableFuture<Void> promise = new CompletableFuture<>();
        fastReconnect = promise;
        closeInternal();
        return promise;
    }

    /**
     * Access to Netty channel.
     * This method is provided to use in debug info only.
     * 
     * @return channel
     */
    public Channel getChannel() {
        return channel;
    }

    public ChannelFuture closeIdleAsync() {
        status = Status.CLOSED_IDLE;
        closeInternal();
        return channel.closeFuture();
    }

    public boolean isClosedIdle() {
        return status == Status.CLOSED_IDLE;
    }

    public ChannelFuture closeAsync() {
        if (status == Status.CLOSED) {
            return channel.closeFuture();
        }

        status = Status.CLOSED;
        closeInternal();
        return channel.closeFuture();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "@" + System.identityHashCode(this)
                        + " [redisClient=" + redisClient
                        + ", channel=" + channel
                        + ", currentCommand=" + getCurrentCommandData()
                        + ", usage=" + usage + "]";
    }

}
