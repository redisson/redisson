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
import java.util.concurrent.atomic.AtomicInteger;

import org.redisson.MasterSlaveServersConfig;
import org.redisson.client.RedisClient;
import org.redisson.client.RedisPubSubConnection;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;

public class SubscribesConnectionEntry extends ConnectionEntry {

    private final Queue<RedisPubSubConnection> allSubscribeConnections = new ConcurrentLinkedQueue<RedisPubSubConnection>();
    private final Queue<RedisPubSubConnection> freeSubscribeConnections = new ConcurrentLinkedQueue<RedisPubSubConnection>();
    private final AtomicInteger connectionsCounter = new AtomicInteger();

    public SubscribesConnectionEntry(RedisClient client, int poolSize, int subscribePoolSize) {
        super(client, poolSize);
        connectionsCounter.set(subscribePoolSize);
    }

    public Queue<RedisPubSubConnection> getAllSubscribeConnections() {
        return allSubscribeConnections;
    }

    public RedisPubSubConnection pollFreeSubscribeConnection() {
        return freeSubscribeConnections.poll();
    }

    public void releaseSubscribeConnection(RedisPubSubConnection connection) {
        freeSubscribeConnections.add(connection);
    }

    public int getFreeSubscribeAmount() {
        return connectionsCounter.get();
    }

    public boolean tryAcquireSubscribeConnection() {
        while (true) {
            if (connectionsCounter.get() == 0) {
                return false;
            }
            if (connectionsCounter.compareAndSet(connectionsCounter.get(), connectionsCounter.get() - 1)) {
                return true;
            }
        }
    }

    public void releaseSubscribeConnection() {
        connectionsCounter.incrementAndGet();
    }

    public Future<RedisPubSubConnection> connectPubSub(MasterSlaveServersConfig config) {
        Future<RedisPubSubConnection> future = super.connectPubSub(config);
        future.addListener(new FutureListener<RedisPubSubConnection>() {
            @Override
            public void operationComplete(Future<RedisPubSubConnection> future) throws Exception {
                if (!future.isSuccess()) {
                    return;
                }
                RedisPubSubConnection conn = future.getNow();
                allSubscribeConnections.add(conn);
            }
        });
        return future;
    }


}

