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

import java.util.Map.Entry;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;

import org.redisson.config.Config;

import com.lambdaworks.redis.RedisClient;
import com.lambdaworks.redis.RedisConnection;
import com.lambdaworks.redis.pubsub.RedisPubSubAdapter;
import com.lambdaworks.redis.pubsub.RedisPubSubConnection;
import com.lambdaworks.redis.pubsub.RedisPubSubListener;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class ConnectionManager {

    public static class PubSubEntry {

        private final Semaphore semaphore;
        private final RedisPubSubConnection conn;

        public PubSubEntry(RedisPubSubConnection conn, int subscriptionsPerConnection) {
            super();
            this.conn = conn;
            this.semaphore = new Semaphore(subscriptionsPerConnection);
        }

        public void addListener(RedisPubSubListener listener) {
            conn.addListener(listener);
        }

        public void removeListener(RedisPubSubListener listener) {
            conn.removeListener(listener);
        }

        public boolean subscribe(RedisPubSubAdapter listener, Object channel) {
            if (semaphore.tryAcquire()) {
                conn.addListener(listener);
                conn.subscribe(channel);
                return true;
            }
            return false;
        }

        public void unsubscribe(Object channel) {
            conn.unsubscribe(channel);
            semaphore.release();
        }

    }

    private final Queue<RedisConnection> connections = new ConcurrentLinkedQueue<RedisConnection>();
    private final Queue<PubSubEntry> pubSubConnections = new ConcurrentLinkedQueue<PubSubEntry>();

    private final Semaphore semaphore;
    private final RedisClient redisClient;
    private final Config config;

    public ConnectionManager(Config config) {
        Entry<String, Integer> address = config.getAddresses().entrySet().iterator().next();
        redisClient = new RedisClient(address.getKey(), address.getValue());
        semaphore = new Semaphore(config.getConnectionPoolSize());
        this.config = config;
    }

    public <K, V> RedisConnection<K, V> acquireConnection() {
        semaphore.acquireUninterruptibly();
        RedisConnection<K, V> c = connections.poll();
        if (c == null) {
            c = redisClient.connect(config.getCodec());
        }
        return c;
    }

    public <K, V> PubSubEntry subscribe(RedisPubSubAdapter<K, V> listener, K channel) {
        for (PubSubEntry entry : pubSubConnections) {
            if (entry.subscribe(listener, channel)) {
                return entry;
            }
        }

        RedisPubSubConnection<K, V> conn = redisClient.connectPubSub(config.getCodec());
        PubSubEntry entry = new PubSubEntry(conn, config.getSubscriptionsPerConnection());
        entry.subscribe(listener, channel);
        pubSubConnections.add(entry);
        return entry;
    }

    public <K> void unsubscribe(PubSubEntry entry, K channel) {
        entry.unsubscribe(channel);
    }

    public void release(RedisConnection сonnection) {
        semaphore.release();
        connections.add(сonnection);
    }

    public void shutdown() {
        redisClient.shutdown();
    }

}
