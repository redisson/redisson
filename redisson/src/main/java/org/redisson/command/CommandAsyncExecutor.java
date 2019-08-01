/**
 * Copyright (c) 2013-2019 Nikita Koksharov
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

import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.redisson.SlotCallback;
import org.redisson.api.RFuture;
import org.redisson.api.RedissonClient;
import org.redisson.api.RedissonReactiveClient;
import org.redisson.api.RedissonRxClient;
import org.redisson.client.RedisClient;
import org.redisson.client.RedisException;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.connection.ConnectionManager;
import org.redisson.connection.MasterSlaveEntry;
import org.redisson.liveobject.core.RedissonObjectBuilder;

/**
 *
 * @author Nikita Koksharov
 *
 */
public interface CommandAsyncExecutor {

    RedissonObjectBuilder getObjectBuilder();
    
    ConnectionManager getConnectionManager();

    CommandAsyncExecutor enableRedissonReferenceSupport(RedissonClient redisson);
    
    CommandAsyncExecutor enableRedissonReferenceSupport(RedissonReactiveClient redissonReactive);
    
    CommandAsyncExecutor enableRedissonReferenceSupport(RedissonRxClient redissonReactive);
    
    boolean isRedissonReferenceSupportEnabled();
    
    <V> RedisException convertException(RFuture<V> future);

    boolean await(RFuture<?> future, long timeout, TimeUnit timeoutUnit) throws InterruptedException;
    
    void syncSubscription(RFuture<?> future);
    
    <V> V get(RFuture<V> future);

    <T, R> RFuture<R> writeAsync(MasterSlaveEntry entry, Codec codec, RedisCommand<T> command, Object... params);
    
    <T, R> RFuture<R> writeAsync(byte[] key, Codec codec, RedisCommand<T> command, Object... params);
    
    <T, R> RFuture<R> readAsync(RedisClient client, MasterSlaveEntry entry, Codec codec, RedisCommand<T> command, Object... params);
    
    <T, R> RFuture<R> readAsync(RedisClient client, String name, Codec codec, RedisCommand<T> command, Object... params);
    
    <T, R> RFuture<R> readAsync(RedisClient client, byte[] key, Codec codec, RedisCommand<T> command, Object... params);
    
    <T, R> RFuture<R> readAsync(RedisClient client, Codec codec, RedisCommand<T> command, Object... params);

    <T, R> RFuture<R> evalWriteAllAsync(RedisCommand<T> command, SlotCallback<T, R> callback, String script, List<Object> keys, Object... params);

    <R, T> RFuture<R> writeAllAsync(RedisCommand<T> command, SlotCallback<T, R> callback, Object... params);

    <T, R> RFuture<Collection<R>> readAllAsync(Codec codec, RedisCommand<T> command, Object... params);
    
    <R, T> RFuture<R> readAllAsync(RedisCommand<T> command, SlotCallback<T, R> callback, Object... params);

    <T, R> RFuture<Collection<R>> readAllAsync(Collection<R> results, Codec codec, RedisCommand<T> command, Object... params);
    
    <T, R> RFuture<R> evalReadAsync(RedisClient client, String name, Codec codec, RedisCommand<T> evalCommandType, String script, List<Object> keys, Object... params);

    <T, R> RFuture<R> evalReadAsync(String key, Codec codec, RedisCommand<T> evalCommandType, String script, List<Object> keys, Object... params);

    <T, R> RFuture<R> evalReadAsync(MasterSlaveEntry entry, Codec codec, RedisCommand<T> evalCommandType, String script, List<Object> keys, Object... params);
    
    <T, R> RFuture<R> evalWriteAsync(String key, Codec codec, RedisCommand<T> evalCommandType, String script, List<Object> keys, Object... params);

    <T, R> RFuture<R> evalWriteAsync(MasterSlaveEntry entry, Codec codec, RedisCommand<T> evalCommandType, String script, List<Object> keys, Object... params);
    
    <T, R> RFuture<R> readAsync(byte[] key, Codec codec, RedisCommand<T> command, Object... params);
    
    <T, R> RFuture<R> readAsync(String key, Codec codec, RedisCommand<T> command, Object... params);

    <T, R> RFuture<R> writeAsync(String key, Codec codec, RedisCommand<T> command, Object... params);

    <T, R> RFuture<Collection<R>> readAllAsync(RedisCommand<T> command, Object... params);
    
    <R, T> RFuture<R> writeAllAsync(Codec codec, RedisCommand<T> command, SlotCallback<T, R> callback, Object... params);
    
    <T> RFuture<Void> writeAllAsync(RedisCommand<T> command, Object... params);

    <T, R> RFuture<R> writeAsync(String key, RedisCommand<T> command, Object... params);

    <T, R> RFuture<R> readAsync(String key, RedisCommand<T> command, Object... params);

    <T, R> RFuture<R> readAsync(MasterSlaveEntry entry, Codec codec, RedisCommand<T> command, Object... params);
    
    <T, R> RFuture<R> readRandomAsync(Codec codec, RedisCommand<T> command, Object... params);
    
    <T, R> RFuture<R> readRandomAsync(MasterSlaveEntry entry, Codec codec, RedisCommand<T> command, Object... params);
    
    <V> RFuture<V> pollFromAnyAsync(String name, Codec codec, RedisCommand<Object> command, long secondsTimeout, String... queueNames);

}
