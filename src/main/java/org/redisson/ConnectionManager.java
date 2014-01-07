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
package org.redisson;

import java.util.Map.Entry;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;

import org.redisson.config.Config;

import com.lambdaworks.redis.RedisClient;
import com.lambdaworks.redis.RedisConnection;
import com.lambdaworks.redis.codec.RedisCodec;
import com.lambdaworks.redis.pubsub.RedisPubSubConnection;

public class ConnectionManager {

    private final Queue<RedisConnection> connections = new ConcurrentLinkedQueue<RedisConnection>();
    private final Queue<RedisPubSubConnection> pubSubConnections = new ConcurrentLinkedQueue<RedisPubSubConnection>();

    private final Semaphore semaphore;
    private final RedisClient redisClient;
    private final RedisCodec codec;

    public ConnectionManager(Config config) {
        Entry<String, Integer> address = config.getAddresses().entrySet().iterator().next();
        redisClient = new RedisClient(address.getKey(), address.getValue());
        codec = config.getCodec();
        semaphore = new Semaphore(config.getConnectionPoolSize());
    }

    public <K, V> RedisConnection<K, V> acquireConnection() {
        semaphore.acquireUninterruptibly();
        RedisConnection<K, V> c = connections.poll();
        if (c == null) {
            c = redisClient.connect(codec);
        }
        return c;
    }

    public <K, V> RedisPubSubConnection<K, V> acquirePubSubConnection() {
        semaphore.acquireUninterruptibly();
        RedisPubSubConnection<K, V> c = pubSubConnections.poll();
        if (c == null) {
            c = redisClient.connectPubSub(codec);
        }
        return c;
    }

    public void release(RedisConnection сonnection) {
        semaphore.release();
        connections.add(сonnection);
    }

    public void release(RedisPubSubConnection pubSubConnection) {
        semaphore.release();
        pubSubConnections.add(pubSubConnection);
    }

    public void shutdown() {
        redisClient.shutdown();
    }

}
