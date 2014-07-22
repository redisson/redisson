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

import org.redisson.async.AsyncOperation;
import org.redisson.async.SyncOperation;

import io.netty.channel.EventLoopGroup;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;

import com.lambdaworks.redis.RedisConnection;
import com.lambdaworks.redis.pubsub.RedisPubSubAdapter;

/**
 *
 * @author Nikita Koksharov
 *
 */
//TODO ping support
public interface ConnectionManager {

    <V> V get(Future<V> future);
    
    <V, R> R read(SyncOperation<V, R> operation);
    
    <V, R> R write(SyncOperation<V, R> operation);
    
    <V, R> R write(AsyncOperation<V, R> asyncOperation);
    
    <V, T> T read(AsyncOperation<V, T> asyncOperation);
    
    <V, T> Future<T> readAsync(AsyncOperation<V, T> asyncOperation);
    
    <V, T> Future<T> writeAsync(AsyncOperation<V, T> asyncOperation);
    
    <T> FutureListener<T> createReleaseWriteListener(RedisConnection conn);

    <T> FutureListener<T> createReleaseReadListener(RedisConnection conn);

    <K, V> RedisConnection<K, V> connectionReadOp();

    PubSubConnectionEntry getEntry(String channelName);

    <K, V> PubSubConnectionEntry subscribe(String channelName);

    <K, V> PubSubConnectionEntry subscribe(RedisPubSubAdapter<V> listener, String channelName);

    Future unsubscribe(String channelName);

    void releaseRead(RedisConnection сonnection);

    void shutdown();

    EventLoopGroup getGroup();

}
