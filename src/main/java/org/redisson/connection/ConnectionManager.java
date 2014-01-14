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

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
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
import com.lambdaworks.redis.pubsub.RedisPubSubListener;

/**
 *
 * @author Nikita Koksharov
 *
 */
//TODO ping support
public class ConnectionManager {

    public static class PubSubEntry {

        private final Semaphore semaphore;
        private final RedisPubSubConnection conn;
        private final int subscriptionsPerConnection;

        public PubSubEntry(RedisPubSubConnection conn, int subscriptionsPerConnection) {
            super();
            this.conn = conn;
            this.subscriptionsPerConnection = subscriptionsPerConnection;
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

        public boolean tryClose() {
            if (semaphore.tryAcquire(subscriptionsPerConnection)) {
                conn.close();
                return true;
            }
            return false;
        }

    }

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final Queue<RedisConnection> connections = new ConcurrentLinkedQueue<RedisConnection>();
    private final Queue<PubSubEntry> pubSubConnections = new ConcurrentLinkedQueue<PubSubEntry>();
    private final List<RedisClient> clients = new ArrayList<RedisClient>();

    private final Semaphore activeConnections;
    private final RedisCodec codec;
    private final Config config;
    private final LoadBalancer balancer;

    public ConnectionManager(Config config) {
        for (URI address : config.getAddresses()) {
            RedisClient client = new RedisClient(address.getHost(), address.getPort());
            clients.add(client);
        }
        balancer = config.getLoadBalancer();
        balancer.init(clients);

        codec = new RedisCodecWrapper(config.getCodec());
        activeConnections = new Semaphore(config.getConnectionPoolSize());
        this.config = config;
    }

    public <K, V> RedisConnection<K, V> connection() {
        acquireConnection();

        RedisConnection<K, V> conn = connections.poll();
        if (conn == null) {
            conn = balancer.nextClient().connect(codec);
            if (config.getPassword() != null) {
                conn.auth(config.getPassword());
            }
        }
        return conn;
    }

    public <K, V> PubSubEntry subscribe(RedisPubSubAdapter<K, V> listener, K channel) {
        for (PubSubEntry entry : pubSubConnections) {
            if (entry.subscribe(listener, channel)) {
                return entry;
            }
        }

        acquireConnection();

        RedisPubSubConnection<K, V> conn = balancer.nextClient().connectPubSub(codec);
        if (config.getPassword() != null) {
            conn.auth(config.getPassword());
        }
        PubSubEntry entry = new PubSubEntry(conn, config.getSubscriptionsPerConnection());
        entry.subscribe(listener, channel);
        pubSubConnections.add(entry);
        return entry;
    }

    private void acquireConnection() {
        if (!activeConnections.tryAcquire()) {
            log.warn("Connection pool gets exhausted! Trying to acquire connection ...");
            long time = System.currentTimeMillis();
            activeConnections.acquireUninterruptibly();
            long endTime = System.currentTimeMillis() - time;
            log.warn("Connection acquired, time spended: {} ms", endTime);
        }
    }

    public <K> void unsubscribe(PubSubEntry entry, K channel) {
        entry.unsubscribe(channel);
        if (entry.tryClose()) {
            pubSubConnections.remove(entry);
            activeConnections.release();
        }
    }

    public void release(RedisConnection сonnection) {
        activeConnections.release();
        connections.add(сonnection);
    }

    public void shutdown() {
        for (RedisClient client : clients) {
            client.shutdown();
        }
    }

}
