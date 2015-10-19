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
import org.redisson.client.ReconnectListener;
import org.redisson.client.RedisClient;
import org.redisson.client.RedisConnection;
import org.redisson.client.RedisPubSubConnection;
import org.redisson.client.protocol.RedisCommands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;

public class ConnectionEntry {

    final Logger log = LoggerFactory.getLogger(getClass());

    private volatile boolean freezed;
    final RedisClient client;

    private final Queue<RedisConnection> connections = new ConcurrentLinkedQueue<RedisConnection>();
    private final AtomicInteger connectionsCounter = new AtomicInteger();

    public ConnectionEntry(RedisClient client, int poolSize) {
        this.client = client;
        this.connectionsCounter.set(poolSize);
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

    public int getFreeAmount() {
        return connectionsCounter.get();
    }

    public boolean tryAcquireConnection() {
        while (true) {
            if (connectionsCounter.get() == 0) {
                return false;
            }
            if (connectionsCounter.compareAndSet(connectionsCounter.get(), connectionsCounter.get() - 1)) {
                return true;
            }
        }
    }

    public void releaseConnection() {
        connectionsCounter.incrementAndGet();
    }

    public RedisConnection pollConnection() {
        return connections.poll();
    }

    public void releaseConnection(RedisConnection connection) {
        connections.add(connection);
    }

    public Future<RedisConnection> connect(final MasterSlaveServersConfig config) {
        Future<RedisConnection> future = client.connectAsync();
        future.addListener(new FutureListener<RedisConnection>() {
            @Override
            public void operationComplete(Future<RedisConnection> future) throws Exception {
                if (!future.isSuccess()) {
                    return;
                }
                RedisConnection conn = future.getNow();
                log.debug("new connection created: {}", conn);

                prepareConnection(config, conn);
                conn.setReconnectListener(new ReconnectListener() {
                    @Override
                    public void onReconnect(RedisConnection conn) {
                        prepareConnection(config, conn);
                    }
                });
            }
        });
        return future;
    }

    private void prepareConnection(MasterSlaveServersConfig config, RedisConnection conn) {
        if (config.getPassword() != null) {
            conn.sync(RedisCommands.AUTH, config.getPassword());
        }
        if (config.getDatabase() != 0) {
            conn.sync(RedisCommands.SELECT, config.getDatabase());
        }
        if (config.getClientName() != null) {
            conn.sync(RedisCommands.CLIENT_SETNAME, config.getClientName());
        }
    }

    public Future<RedisPubSubConnection> connectPubSub(final MasterSlaveServersConfig config) {
        Future<RedisPubSubConnection> future = client.connectPubSubAsync();
        future.addListener(new FutureListener<RedisPubSubConnection>() {
            @Override
            public void operationComplete(Future<RedisPubSubConnection> future) throws Exception {
                if (!future.isSuccess()) {
                    return;
                }
                RedisPubSubConnection conn = future.getNow();
                log.debug("new pubsub connection created: {}", conn);

                prepareConnection(config, conn);
                conn.setReconnectListener(new ReconnectListener() {
                    @Override
                    public void onReconnect(RedisConnection conn) {
                        prepareConnection(config, conn);
                    }
                });
            }
        });
        return future;
    }

    @Override
    public String toString() {
        return "ConnectionEntry [freezed=" + freezed + ", client=" + client + "]";
    }

}
