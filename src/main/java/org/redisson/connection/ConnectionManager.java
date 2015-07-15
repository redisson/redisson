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
package org.redisson.connection;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.TimeUnit;

import org.redisson.SyncOperation;
import org.redisson.client.RedisClient;
import org.redisson.client.RedisConnection;
import org.redisson.client.RedisPubSubListener;
import org.redisson.client.protocol.Codec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.pubsub.PubSubStatusMessage;

import io.netty.channel.EventLoopGroup;
import io.netty.util.Timeout;
import io.netty.util.TimerTask;
import io.netty.util.concurrent.Future;

/**
 *
 * @author Nikita Koksharov
 *
 */
//TODO ping support
public interface ConnectionManager {

    <R> R read(String key, SyncOperation<R> operation);

    <R> R write(String key, SyncOperation<R> operation);

    <T, R> Future<R> writeAsync(RedisCommand<T> command, Object ... params);

    <T, R> R write(RedisCommand<T> command, Object ... params);

    <T, R> R write(Codec codec, RedisCommand<T> command, Object ... params);

    <T, R> Future<R> writeAsync(Codec codec, RedisCommand<T> command, Object ... params);

    <T, R> R eval(RedisCommand<T> evalCommandType, String script, List<Object> keys, Object ... params);

    <T, R> Future<R> evalAsync(RedisCommand<T> evalCommandType, String script, List<Object> keys, Object ... params);

    <T, R> Future<R> evalAsync(Codec codec, RedisCommand<T> evalCommandType, String script, List<Object> keys, Object ... params);

    <T, R> R read(String key, Codec codec, RedisCommand<T> command, Object ... params);

    <T, R> Future<R> readAsync(String key, Codec codec, RedisCommand<T> command, Object ... params);

    <T, R> R read(String key, RedisCommand<T> command, Object ... params);

    <T, R> R eval(Codec codec, RedisCommand<T> evalCommandType, String script, List<Object> keys, Object ... params);

    <T, R> Future<R> writeAsync(String key, Codec codec, RedisCommand<T> command, Object ... params);

    <T, R> R write(String key, Codec codec, RedisCommand<T> command, Object ... params);

    <T, R> R write(String key, RedisCommand<T> command, Object ... params);

    <T> Future<Queue<Object>> readAllAsync(RedisCommand<T> command, Object ... params);

    <T> Future<Boolean> writeAllAsync(RedisCommand<T> command, Object ... params);

    <T> Future<Void> writeAsyncVoid(String key, RedisCommand<T> command, Object ... params);

    <T, R> Future<R> writeAsync(String key, RedisCommand<T> command, Object ... params);

    <T, R> Future<R> readAsync(String key, RedisCommand<T> command, Object ... params);

    RedisClient createClient(String host, int port, int timeout);

    RedisClient createClient(String host, int port);

    <V> V get(Future<V> future);

    RedisConnection connectionReadOp(int slot);

    PubSubConnectionEntry getEntry(String channelName);

    PubSubConnectionEntry subscribe(String channelName);

    PubSubConnectionEntry psubscribe(String pattern);

    <V> Future<PubSubStatusMessage> subscribe(RedisPubSubListener<V> listener, String channelName);

    Future unsubscribe(String channelName);

    Future punsubscribe(String channelName);

    void releaseRead(int slot, RedisConnection сonnection);

    void shutdown();

    EventLoopGroup getGroup();

    Timeout newTimeout(TimerTask task, long delay, TimeUnit unit);
}
