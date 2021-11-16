/**
 * Copyright (c) 2013-2021 Nikita Koksharov
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
import org.redisson.misc.LogHelper;
import org.redisson.misc.RPromise;
import org.redisson.misc.RedissonPromise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Deque;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedisConnection implements RedisCommands {

    private static final Logger LOG = LoggerFactory.getLogger(RedisConnection.class);
    private static final AttributeKey<RedisConnection> CONNECTION = AttributeKey.valueOf("connection");

    final RedisClient redisClient;

    private volatile RPromise<Void> fastReconnect;
    private volatile boolean closed;
    volatile Channel channel;

    private RPromise<?> connectionPromise;
    private long lastUsageTime;
    private Runnable connectedListener;
    private Runnable disconnectedListener;

    private final AtomicInteger usage = new AtomicInteger();

    public <C> RedisConnection(RedisClient redisClient, Channel channel, RPromise<C> connectionPromise) {
        this.redisClient = redisClient;
        this.connectionPromise = connectionPromise;

        updateChannel(channel);
        lastUsageTime = System.nanoTime();

        LOG.debug("Connection created " + redisClient);
    }
    
    protected RedisConnection(RedisClient redisClient) {
        this.redisClient = redisClient;
    }
    
    public void fireConnected() {
        if (connectedListener != null) {
            connectedListener.run();
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

    public void setConnectedListener(Runnable connectedListener) {
        this.connectedListener = connectedListener;
    }

    public void fireDisconnected() {
        if (disconnectedListener != null) {
            disconnectedListener.run();
        }
    }

    public void setDisconnectedListener(Runnable disconnectedListener) {
        this.disconnectedListener = disconnectedListener;
    }

    public <C extends RedisConnection> RPromise<C> getConnectionPromise() {
        return (RPromise<C>) connectionPromise;
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

        QueueCommand command = channel.attr(CommandsQueuePubSub.CURRENT_COMMAND).get();
        if (command instanceof CommandData) {
            return (CommandData<?, ?>) command;
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

    public <R> R await(RFuture<R> future) {
        CountDownLatch l = new CountDownLatch(1);
        future.onComplete((res, e) -> {
            l.countDown();
        });
        
        try {
            if (!l.await(redisClient.getCommandTimeout(), TimeUnit.MILLISECONDS)) {
                RPromise<R> promise = (RPromise<R>) future;
                RedisTimeoutException ex = new RedisTimeoutException("Command execution timeout for " + redisClient.getAddr());
                promise.tryFailure(ex);
                throw ex;
            }
            if (!future.isSuccess()) {
                if (future.cause() instanceof RedisException) {
                    throw (RedisException) future.cause();
                }
                throw new RedisException("Unexpected exception while processing command", future.cause());
            }
            return future.getNow();
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
        RPromise<R> promise = new RedissonPromise<R>();
        send(new CommandData<T, R>(promise, encoder, command, params));
        return await(promise);
    }

    public <T, R> RFuture<R> async(RedisCommand<T> command, Object... params) {
        return async(-1, command, params);
    }
    
    public <T, R> RFuture<R> async(long timeout, RedisCommand<T> command, Object... params) {
        return async(timeout, null, command, params);
    }

    public <T, R> RFuture<R> async(Codec encoder, RedisCommand<T> command, Object... params) {
        return async(-1, encoder, command, params);
    }

    public <T, R> RFuture<R> async(long timeout, Codec encoder, RedisCommand<T> command, Object... params) {
        RPromise<R> promise = new RedissonPromise<R>();
        if (timeout == -1) {
            timeout = redisClient.getCommandTimeout();
        }
        
        if (redisClient.getEventLoopGroup().isShuttingDown()) {
            RedissonShutdownException cause = new RedissonShutdownException("Redisson is shutdown");
            return RedissonPromise.newFailedFuture(cause);
        }

        Timeout scheduledFuture = redisClient.getTimer().newTimeout(t -> {
            RedisTimeoutException ex = new RedisTimeoutException("Command execution timeout for command: "
                    + LogHelper.toString(command, params) + ", Redis client: " + redisClient);
            promise.tryFailure(ex);
        }, timeout, TimeUnit.MILLISECONDS);
        
        promise.onComplete((res, e) -> {
            scheduledFuture.cancel();
        });
        
        ChannelFuture writeFuture = send(new CommandData<T, R>(promise, encoder, command, params));
        writeFuture.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                if (!future.isSuccess()) {
                    promise.tryFailure(future.cause());
                }
            }
        });
        return promise;
    }

    public <T, R> CommandData<T, R> create(Codec encoder, RedisCommand<T> command, Object... params) {
        RPromise<R> promise = new RedissonPromise<R>();
        return new CommandData<T, R>(promise, encoder, command, params);
    }

    private void setClosed(boolean closed) {
        this.closed = closed;
    }

    public boolean isClosed() {
        return closed;
    }

    public boolean isFastReconnect() {
        return fastReconnect != null;
    }
    
    public void clearFastReconnect() {
        fastReconnect.trySuccess(null);
        fastReconnect = null;
    }
    
    private void close() {
        CommandData<?, ?> command = getCurrentCommand();
        if (!isActive()
                || command != null && command.isBlockingCommand()
                    || !connectionPromise.isDone()) {
            channel.close();
        } else {
            RFuture<Void> f = async(RedisCommands.QUIT);
            f.onComplete((res, e) -> {
                channel.close();
            });
        }
    }
    
    public RFuture<Void> forceFastReconnectAsync() {
        RedissonPromise<Void> promise = new RedissonPromise<Void>();
        fastReconnect = promise;
        close();
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

    public ChannelFuture closeAsync() {
        setClosed(true);
        close();
        return channel.closeFuture();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "@" + System.identityHashCode(this) + " [redisClient=" + redisClient + ", channel=" + channel + ", currentCommand=" + getCurrentCommand() + ", usage=" + usage + "]";
    }

}
