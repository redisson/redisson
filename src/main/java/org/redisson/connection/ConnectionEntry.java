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

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;

import com.lambdaworks.redis.RedisClient;
import com.lambdaworks.redis.RedisConnection;
import com.lambdaworks.redis.pubsub.RedisPubSubConnection;

public class ConnectionEntry {

    private volatile boolean freezed;
    private final RedisClient client;

    private final Semaphore subscribeConnectionsSemaphore;
    private final Semaphore connectionsSemaphore;

    private final Queue<RedisPubSubConnection> subscribeConnections = new ConcurrentLinkedQueue<RedisPubSubConnection>();
    private final Queue<RedisConnection> connections = new ConcurrentLinkedQueue<RedisConnection>();

    private final int poolSize;

    public ConnectionEntry(RedisClient client, int poolSize, int subscribePoolSize) {
        super();
        this.client = client;
        this.poolSize = poolSize;
        this.connectionsSemaphore = new Semaphore(poolSize);
        this.subscribeConnectionsSemaphore = new Semaphore(subscribePoolSize);
    }

    public RedisClient getClient() {
        return client;
    }

    public boolean isFreezed() {
        return freezed;
    }

    public void setFreezed(boolean freezed) {
        this.freezed = freezed;
    }

    public void shutdown() {
        connectionsSemaphore.acquireUninterruptibly(poolSize);
        client.shutdown();
    }

    public Semaphore getSubscribeConnectionsSemaphore() {
        return subscribeConnectionsSemaphore;
    }

    public Semaphore getConnectionsSemaphore() {
        return connectionsSemaphore;
    }

    public Queue<RedisPubSubConnection> getSubscribeConnections() {
        return subscribeConnections;
    }

    public Queue<RedisConnection> getConnections() {
        return connections;
    }

}
