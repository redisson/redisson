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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;

public class ConnectionEntry {

    final Logger log = LoggerFactory.getLogger(getClass());

    private volatile boolean freezed;
    final RedisClient client;

    public enum Mode {SLAVE, MASTER}

    private final Mode serverMode;
    private final ConnectionListener connectListener;
    private final Queue<RedisConnection> connections = new ConcurrentLinkedQueue<RedisConnection>();
    private final AtomicInteger connectionsCounter = new AtomicInteger();

    public ConnectionEntry(RedisClient client, int poolSize, ConnectionListener connectListener, Mode serverMode) {
        this.client = client;
        this.connectionsCounter.set(poolSize);
        this.connectListener = connectListener;
        this.serverMode = serverMode;
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
            int value = connectionsCounter.get();
            if (connectionsCounter.compareAndSet(value, value - 1)) {
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
        final Promise<RedisConnection> connectionFuture = client.getBootstrap().group().next().newPromise();
        Future<RedisConnection> future = client.connectAsync();
        future.addListener(new FutureListener<RedisConnection>() {
            @Override
            public void operationComplete(Future<RedisConnection> future) throws Exception {
                if (!future.isSuccess()) {
                    return;
                }
                RedisConnection conn = future.getNow();
                log.debug("new connection created: {}", conn);

                FutureConnectionListener<RedisConnection> listener = new FutureConnectionListener<RedisConnection>(connectionFuture, conn);
                connectListener.onConnect(config, serverMode, listener);
                listener.executeCommands();
                addReconnectListener(config, conn);
            }

        });
        return connectionFuture;
    }

    private void addReconnectListener(final MasterSlaveServersConfig config, RedisConnection conn) {
        conn.setReconnectListener(new ReconnectListener() {
            @Override
            public void onReconnect(RedisConnection conn, Promise<RedisConnection> connectionFuture) {
                FutureConnectionListener<RedisConnection> listener = new FutureConnectionListener<RedisConnection>(connectionFuture, conn);
                connectListener.onConnect(config, serverMode, listener);
                listener.executeCommands();
            }
        });
    }

    public Future<RedisPubSubConnection> connectPubSub(final MasterSlaveServersConfig config) {
        final Promise<RedisPubSubConnection> connectionFuture = client.getBootstrap().group().next().newPromise();
        Future<RedisPubSubConnection> future = client.connectPubSubAsync();
        future.addListener(new FutureListener<RedisPubSubConnection>() {
            @Override
            public void operationComplete(Future<RedisPubSubConnection> future) throws Exception {
                if (!future.isSuccess()) {
                    return;
                }
                RedisPubSubConnection conn = future.getNow();
                log.debug("new pubsub connection created: {}", conn);

                FutureConnectionListener<RedisPubSubConnection> listener = new FutureConnectionListener<RedisPubSubConnection>(connectionFuture, conn);
                connectListener.onConnect(config, serverMode, listener);
                listener.executeCommands();

                addReconnectListener(config, conn);
            }
        });
        return connectionFuture;
    }

    @Override
    public String toString() {
        return "ConnectionEntry [freezed=" + freezed + ", client=" + client + "]";
    }

}
