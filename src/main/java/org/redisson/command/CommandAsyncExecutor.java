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
package org.redisson.command;

import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.redisson.SlotCallback;
import org.redisson.client.RedisException;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.connection.ConnectionManager;
import org.redisson.connection.MasterSlaveEntry;

import io.netty.util.concurrent.Future;

/**
 *
 * @author Nikita Koksharov
 *
 */
public interface CommandAsyncExecutor {

    ConnectionManager getConnectionManager();

    <V> RedisException convertException(Future<V> future);

    boolean await(Future<?> future, long timeout, TimeUnit timeoutUnit) throws InterruptedException;
    
    <V> V get(Future<V> future);

    <T, R> Future<R> writeAsync(MasterSlaveEntry entry, Codec codec, RedisCommand<T> command, Object ... params);
    
    <T, R> Future<R> writeAsync(Integer slot, Codec codec, RedisCommand<T> command, Object ... params);

    <T, R> Future<R> readAsync(InetSocketAddress client, MasterSlaveEntry entry, Codec codec, RedisCommand<T> command, Object ... params);
    
    <T, R> Future<R> readAsync(InetSocketAddress client, String key, Codec codec, RedisCommand<T> command, Object ... params);

    <T, R> Future<R> evalWriteAllAsync(RedisCommand<T> command, SlotCallback<T, R> callback, String script, List<Object> keys, Object ... params);

    <R, T> Future<R> writeAllAsync(RedisCommand<T> command, SlotCallback<T, R> callback, Object ... params);

    <R, T> Future<R> readAllAsync(RedisCommand<T> command, SlotCallback<T, R> callback, Object ... params);

    <T, R> Future<R> evalReadAsync(InetSocketAddress client, String key, Codec codec, RedisCommand<T> evalCommandType, String script, List<Object> keys, Object ... params);

    <T, R> Future<R> evalReadAsync(String key, Codec codec, RedisCommand<T> evalCommandType, String script, List<Object> keys, Object ... params);

    <T, R> Future<R> evalReadAsync(MasterSlaveEntry entry, Codec codec, RedisCommand<T> evalCommandType, String script, List<Object> keys, Object ... params);
    
    <T, R> Future<R> evalReadAsync(Integer slot, Codec codec, RedisCommand<T> evalCommandType, String script, List<Object> keys, Object ... params);

    <T, R> Future<R> evalWriteAsync(String key, Codec codec, RedisCommand<T> evalCommandType, String script, List<Object> keys, Object ... params);

    <T, R> Future<R> evalWriteAsync(MasterSlaveEntry entry, Codec codec, RedisCommand<T> evalCommandType, String script, List<Object> keys, Object ... params);
    
    <T, R> Future<R> evalWriteAsync(Integer slot, Codec codec, RedisCommand<T> evalCommandType, String script, List<Object> keys, Object ... params);

    <T, R> Future<R> readAsync(String key, Codec codec, RedisCommand<T> command, Object ... params);

    <T, R> Future<R> writeAsync(String key, Codec codec, RedisCommand<T> command, Object ... params);

    <T, R> Future<Collection<R>> readAllAsync(RedisCommand<T> command, Object ... params);

    <T> Future<Void> writeAllAsync(RedisCommand<T> command, Object ... params);

    <T, R> Future<R> writeAsync(String key, RedisCommand<T> command, Object ... params);

    <T, R> Future<R> readAsync(String key, RedisCommand<T> command, Object ... params);

    <T, R> Future<R> readAsync(MasterSlaveEntry entry, Codec codec, RedisCommand<T> command, Object ... params);
    
    <T, R> Future<R> readAsync(Integer slot, Codec codec, RedisCommand<T> command, Object ... params);

    <T, R> Future<R> readRandomAsync(RedisCommand<T> command, Object ... params);


}
