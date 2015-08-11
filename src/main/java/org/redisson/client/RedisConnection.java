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

import java.util.concurrent.TimeUnit;

import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.CommandData;
import org.redisson.client.protocol.CommandsData;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.RedisStrictCommand;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;

public class RedisConnection implements RedisCommands {

    public static final AttributeKey<RedisConnection> CONNECTION = AttributeKey.valueOf("connection");

    final RedisClient redisClient;

    private volatile boolean closed;
    volatile Channel channel;

    public RedisConnection(RedisClient redisClient, Channel channel) {
        super();
        this.redisClient = redisClient;
        this.channel = channel;

        channel.attr(CONNECTION).set(this);
    }

    public void updateChannel(Channel channel) {
        this.channel = channel;
    }

    public RedisClient getRedisClient() {
        return redisClient;
    }

    public <R> R await(Future<R> cmd) {
        if (!cmd.awaitUninterruptibly(redisClient.getTimeout(), TimeUnit.MILLISECONDS)) {
            Promise<R> promise = (Promise<R>)cmd;
            RedisTimeoutException ex = new RedisTimeoutException();
            promise.setFailure(ex);
            throw ex;
        }
        if (!cmd.isSuccess()) {
            if (cmd.cause() instanceof RedisException) {
                throw (RedisException) cmd.cause();
            }
            throw new RedisException("Unexpected exception while processing command", cmd.cause());
        }
        return cmd.getNow();
    }

    public <T> T sync(RedisStrictCommand<T> command, Object ... params) {
        Future<T> r = async(null, command, params);
        return await(r);
    }

    public <T, R> ChannelFuture send(CommandData<T, R> data) {
        return channel.writeAndFlush(data);
    }

    public ChannelFuture send(CommandsData data) {
        return channel.writeAndFlush(data);
    }

    public <T, R> R sync(Codec encoder, RedisCommand<T> command, Object ... params) {
        Future<R> r = async(encoder, command, params);
        return await(r);
    }

    public <T, R> Future<R> async(RedisCommand<T> command, Object ... params) {
        return async(null, command, params);
    }

    public <T, R> Future<R> async(Codec encoder, RedisCommand<T> command, Object ... params) {
        Promise<R> promise = redisClient.getBootstrap().group().next().<R>newPromise();
        send(new CommandData<T, R>(promise, encoder, command, params));
        return promise;
    }

    public <T, R> CommandData<T, R> create(Codec encoder, RedisCommand<T> command, Object ... params) {
        Promise<R> promise = redisClient.getBootstrap().group().next().<R>newPromise();
        return new CommandData<T, R>(promise, encoder, command, params);
    }

    public void setClosed(boolean reconnect) {
        this.closed = reconnect;
    }

    public boolean isClosed() {
        return closed;
    }

    public ChannelFuture closeAsync() {
        setClosed(true);
        return channel.close();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [redisClient=" + redisClient + ", channel=" + channel + "]";
    }

}
