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

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.concurrent.FutureListener;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;

import org.redisson.Config;
import org.redisson.codec.RedisCodecWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lambdaworks.redis.RedisClient;
import com.lambdaworks.redis.RedisConnection;
import com.lambdaworks.redis.codec.RedisCodec;
import com.lambdaworks.redis.pubsub.RedisPubSubAdapter;
import com.lambdaworks.redis.pubsub.RedisPubSubConnection;

/**
 *
 * @author Nikita Koksharov
 *
 */
//TODO ping support
public interface ConnectionManager {

    <T> FutureListener<T> createReleaseWriteListener(final RedisConnection conn);

    <T> FutureListener<T> createReleaseReadListener(final RedisConnection conn);

    <K, V> RedisConnection<K, V> connectionWriteOp();

    <K, V> RedisConnection<K, V> connectionReadOp();

    PubSubConnectionEntry getEntry(String channelName);

    <K, V> PubSubConnectionEntry subscribe(String channelName);

    <K, V> PubSubConnectionEntry subscribe(RedisPubSubAdapter<K, V> listener, String channelName);

    void unsubscribe(PubSubConnectionEntry entry, String channelName);

    void releaseWrite(RedisConnection сonnection);

    void releaseRead(RedisConnection сonnection);

    void shutdown();

    EventLoopGroup getGroup();

}
