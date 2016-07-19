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
package org.redisson.client;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.redisson.client.codec.Codec;
import org.redisson.client.handler.CommandsQueue;
import org.redisson.client.protocol.CommandData;
import org.redisson.client.protocol.CommandsData;
import org.redisson.client.protocol.QueueCommand;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.RedisStrictCommand;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.ImmediateEventExecutor;
import io.netty.util.concurrent.Promise;
import io.netty.util.concurrent.ScheduledFuture;

public class RedisConnection implements RedisCommands {

    private static final AttributeKey<RedisConnection> CONNECTION = AttributeKey.valueOf("connection");

    final RedisClient redisClient;

    private volatile boolean closed;
    volatile Channel channel;

    private ReconnectListener reconnectListener;
    private long lastUsageTime;

    private final Future<?> acquireFuture = ImmediateEventExecutor.INSTANCE.newSucceededFuture(this);
    
    public RedisConnection(RedisClient redisClient, Channel channel) {
        super();
        this.redisClient = redisClient;

        updateChannel(channel);
        lastUsageTime = System.currentTimeMillis();
    }

    public static <C extends RedisConnection> C getFrom(Channel channel) {
        return (C) channel.attr(RedisConnection.CONNECTION).get();
    }

    public void removeCurrentCommand() {
        channel.attr(CommandsQueue.CURRENT_COMMAND).remove();
    }
    
    public CommandData getCurrentCommand() {
        QueueCommand command = channel.attr(CommandsQueue.CURRENT_COMMAND).get();
        if (command instanceof CommandData) {
            return (CommandData)command;
        }
        return null;
    }

    public long getLastUsageTime() {
        return lastUsageTime;
    }

    public void setLastUsageTime(long lastUsageTime) {
        this.lastUsageTime = lastUsageTime;
    }

    public void setReconnectListener(ReconnectListener reconnectListener) {
        this.reconnectListener = reconnectListener;
    }

    public ReconnectListener getReconnectListener() {
        return reconnectListener;
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
        this.channel = channel;
        channel.attr(CONNECTION).set(this);
    }

    public RedisClient getRedisClient() {
        return redisClient;
    }

    public <R> R await(Future<R> future) {
        final CountDownLatch l = new CountDownLatch(1);
        future.addListener(new FutureListener<R>() {
            @Override
            public void operationComplete(Future<R> future) throws Exception {
                l.countDown();
            }
        });
        
        try {
            if (!l.await(redisClient.getCommandTimeout(), TimeUnit.MILLISECONDS)) {
                Promise<R> promise = (Promise<R>)future;
                RedisTimeoutException ex = new RedisTimeoutException("Command execution timeout for " + redisClient.getAddr());
                promise.setFailure(ex);
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

    public <T> T sync(RedisStrictCommand<T> command, Object ... params) {
        return sync(null, command, params);
    }

    public <T, R> ChannelFuture send(CommandData<T, R> data) {
        return channel.writeAndFlush(data);
    }

    public ChannelFuture send(CommandsData data) {
        return channel.writeAndFlush(data);
    }

    public <T, R> R sync(Codec encoder, RedisCommand<T> command, Object ... params) {
        Promise<R> promise = ImmediateEventExecutor.INSTANCE.newPromise();
        send(new CommandData<T, R>(promise, encoder, command, params));
        return await(promise);
    }

    public <T, R> Future<R> async(RedisCommand<T> command, Object ... params) {
        return async(null, command, params);
    }
    
    public <T, R> Future<R> async(long timeout, RedisCommand<T> command, Object ... params) {
        return async(null, command, params);
    }

    public <T, R> Future<R> async(Codec encoder, RedisCommand<T> command, Object ... params) {
        return async(-1, encoder, command, params);
    }

    public <T, R> Future<R> async(long timeout, Codec encoder, RedisCommand<T> command, Object ... params) {
        final Promise<R> promise = ImmediateEventExecutor.INSTANCE.newPromise();
        if (timeout == -1) {
            timeout = redisClient.getCommandTimeout();
        }
        
        final ScheduledFuture<?> scheduledFuture = redisClient.getBootstrap().group().next().schedule(new Runnable() {
            @Override
            public void run() {
                RedisTimeoutException ex = new RedisTimeoutException("Command execution timeout for " + redisClient.getAddr());
                promise.tryFailure(ex);
            }
        }, timeout, TimeUnit.MILLISECONDS);
        
        promise.addListener(new FutureListener<R>() {
            @Override
            public void operationComplete(Future<R> future) throws Exception {
                scheduledFuture.cancel(false);
            }
        });
        send(new CommandData<T, R>(promise, encoder, command, params));
        return promise;
    }

    public <T, R> CommandData<T, R> create(Codec encoder, RedisCommand<T> command, Object ... params) {
        Promise<R> promise = ImmediateEventExecutor.INSTANCE.newPromise();
        return new CommandData<T, R>(promise, encoder, command, params);
    }

    public void setClosed(boolean reconnect) {
        this.closed = reconnect;
    }

    public boolean isClosed() {
        return closed;
    }

    public ChannelFuture forceReconnectAsync() {
        return channel.close();
    }

    /**
     * Access to Netty channel.
     * This method is provided to use in debug info only.
     *
     */
    public Channel getChannel() {
        return channel;
    }

    public ChannelFuture closeAsync() {
        setClosed(true);
        return channel.close();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "@" + System.identityHashCode(this) + " [redisClient=" + redisClient + ", channel=" + channel + "]";
    }

    public Future<?> getAcquireFuture() {
        return acquireFuture;
    }

    public void onDisconnect() {
    }
    
}
