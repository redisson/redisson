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
import com.lambdaworks.redis.pubsub.RedisPubSubConnection;

public class SlaveConnectionEntry extends ConnectionEntry {
    
    private final Semaphore subscribeConnectionsSemaphore;
    private final Queue<RedisPubSubConnection> allSubscribeConnections = new ConcurrentLinkedQueue<RedisPubSubConnection>();
    private final Queue<RedisPubSubConnection> freeSubscribeConnections = new ConcurrentLinkedQueue<RedisPubSubConnection>();

    public SlaveConnectionEntry(RedisClient client, int poolSize, int subscribePoolSize) {
        super(client, poolSize);
        this.subscribeConnectionsSemaphore = new Semaphore(subscribePoolSize);
    }

    public Queue<RedisPubSubConnection> getAllSubscribeConnections() {
        return allSubscribeConnections;
    }
    
    public void registerSubscribeConnection(RedisPubSubConnection connection) {
        allSubscribeConnections.offer(connection);
    }
    
    public RedisPubSubConnection pollFreeSubscribeConnection() {
        return freeSubscribeConnections.poll();
    }
    
    public void offerFreeSubscribeConnection(RedisPubSubConnection connection) {
        freeSubscribeConnections.offer(connection);
    }
    
    public Semaphore getSubscribeConnectionsSemaphore() {
        return subscribeConnectionsSemaphore;
    }
    
}

