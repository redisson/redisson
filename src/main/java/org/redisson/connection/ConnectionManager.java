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

/**
 *
 * @author Nikita Koksharov
 *
 */
//TODO ping support
public class ConnectionManager {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final Queue<RedisConnection> connections = new ConcurrentLinkedQueue<RedisConnection>();
    private final Queue<PubSubConnectionEntry> pubSubConnections = new ConcurrentLinkedQueue<PubSubConnectionEntry>();
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

    public <K, V> RedisConnection<K, V> connectionWriteOp() {
        return connectionReadOp();
    }

    public <K, V> RedisConnection<K, V> connectionReadOp() {
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

    public <K, V> PubSubConnectionEntry subscribe(RedisPubSubAdapter<K, V> listener, K channel) {
        for (PubSubConnectionEntry entry : pubSubConnections) {
            if (entry.subscribe(listener, channel)) {
                return entry;
            }
        }

        acquireConnection();

        RedisPubSubConnection<K, V> conn = balancer.nextClient().connectPubSub(codec);
        if (config.getPassword() != null) {
            conn.auth(config.getPassword());
        }
        PubSubConnectionEntry entry = new PubSubConnectionEntry(conn, config.getSubscriptionsPerConnection());
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

    public <K> void unsubscribe(PubSubConnectionEntry entry, K channel) {
        entry.unsubscribe(channel);
        if (entry.tryClose()) {
            pubSubConnections.remove(entry);
            activeConnections.release();
        }
    }

    public void release(RedisConnection сonnection) {
        connections.add(сonnection);
        activeConnections.release();
    }

    public void shutdown() {
        for (RedisClient client : clients) {
            client.shutdown();
        }
    }

}
