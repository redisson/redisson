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
package org.redisson.command;

import io.netty.buffer.ByteBuf;
import org.redisson.SlotCallback;
import org.redisson.api.RFuture;
import org.redisson.client.RedisClient;
import org.redisson.client.RedisException;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.connection.ConnectionManager;
import org.redisson.connection.MasterSlaveEntry;
import org.redisson.connection.NodeSource;
import org.redisson.liveobject.core.RedissonObjectBuilder;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 *
 * @author Nikita Koksharov
 *
 */
public interface CommandAsyncExecutor {

    RedissonObjectBuilder getObjectBuilder();
    
    ConnectionManager getConnectionManager();

    RedisException convertException(ExecutionException e);

    <V> void transfer(CompletableFuture<V> future1, CompletableFuture<V> future2);

    <V> V getNow(CompletableFuture<V> future);

    <V> V get(RFuture<V> future);

    <V> V get(CompletableFuture<V> future);
    
    <V> V getInterrupted(RFuture<V> future) throws InterruptedException;

    <V> V getInterrupted(CompletableFuture<V> future) throws InterruptedException;

    <T, R> RFuture<R> writeAsync(RedisClient client, Codec codec, RedisCommand<T> command, Object... params);

    <T, R> RFuture<R> writeAsync(MasterSlaveEntry entry, Codec codec, RedisCommand<T> command, Object... params);
    
    <T, R> RFuture<R> writeAsync(byte[] key, Codec codec, RedisCommand<T> command, Object... params);
    
    <T, R> RFuture<R> readAsync(RedisClient client, MasterSlaveEntry entry, Codec codec, RedisCommand<T> command, Object... params);
    
    <T, R> RFuture<R> readAsync(RedisClient client, String name, Codec codec, RedisCommand<T> command, Object... params);
    
    <T, R> RFuture<R> readAsync(RedisClient client, byte[] key, Codec codec, RedisCommand<T> command, Object... params);
    
    <T, R> RFuture<R> readAsync(RedisClient client, Codec codec, RedisCommand<T> command, Object... params);

    <R> List<CompletableFuture<R>> executeAllAsync(RedisCommand<?> command, Object... params);

    <R> List<CompletableFuture<R>> writeAllAsync(RedisCommand<?> command, Object... params);

    <R> List<CompletableFuture<R>> readAllAsync(Codec codec, RedisCommand<?> command, Object... params);

    <R> List<CompletableFuture<R>> readAllAsync(RedisCommand<?> command, Object... params);

    <T, R> RFuture<R> evalReadAsync(RedisClient client, String name, Codec codec, RedisCommand<T> evalCommandType, String script, List<Object> keys, Object... params);

    <T, R> RFuture<R> evalReadAsync(String key, Codec codec, RedisCommand<T> evalCommandType, String script, List<Object> keys, Object... params);

    <T, R> RFuture<R> evalReadAsync(MasterSlaveEntry entry, Codec codec, RedisCommand<T> evalCommandType, String script, List<Object> keys, Object... params);
    
    <T, R> RFuture<R> evalWriteAsync(String key, Codec codec, RedisCommand<T> evalCommandType, String script, List<Object> keys, Object... params);

    <T, R> RFuture<R> evalWriteNoRetryAsync(String key, Codec codec, RedisCommand<T> evalCommandType, String script, List<Object> keys, Object... params);

    <T, R> RFuture<R> evalWriteAsync(MasterSlaveEntry entry, Codec codec, RedisCommand<T> evalCommandType, String script, List<Object> keys, Object... params);
    
    <T, R> RFuture<R> readAsync(byte[] key, Codec codec, RedisCommand<T> command, Object... params);
    
    <T, R> RFuture<R> readAsync(String key, Codec codec, RedisCommand<T> command, Object... params);

    <T, R> RFuture<R> writeAsync(String key, Codec codec, RedisCommand<T> command, Object... params);

    <T> RFuture<Void> writeAllVoidAsync(RedisCommand<T> command, Object... params);

    <T, R> RFuture<R> writeAsync(String key, RedisCommand<T> command, Object... params);

    <T, R> RFuture<R> readAsync(String key, RedisCommand<T> command, Object... params);

    <T, R> RFuture<R> readAsync(MasterSlaveEntry entry, Codec codec, RedisCommand<T> command, Object... params);
    
    <T, R> RFuture<R> readRandomAsync(Codec codec, RedisCommand<T> command, Object... params);
    
    <T, R> RFuture<R> readRandomAsync(MasterSlaveEntry entry, Codec codec, RedisCommand<T> command, Object... params);

    <V, R> RFuture<R> async(boolean readOnlyMode, NodeSource source, Codec codec,
                      RedisCommand<V> command, Object[] params, boolean ignoreRedirect, boolean noRetry);

    <V> RFuture<V> pollFromAnyAsync(String name, Codec codec, RedisCommand<?> command, long secondsTimeout, String... queueNames);

    ByteBuf encode(Codec codec, Object value);

    ByteBuf encodeMapKey(Codec codec, Object value);

    ByteBuf encodeMapValue(Codec codec, Object value);

    <T, R> RFuture<R> readBatchedAsync(Codec codec, RedisCommand<T> command, SlotCallback<T, R> callback, String... keys);

    <T, R> RFuture<R> writeBatchedAsync(Codec codec, RedisCommand<T> command, SlotCallback<T, R> callback, String... keys);

    boolean isEvalShaROSupported();

    void setEvalShaROSupported(boolean value);

}
