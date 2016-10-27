/**
 * Copyright 2016 Nikita Koksharov
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
import org.redisson.core.NodeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.ImmediateEventExecutor;
import io.netty.util.concurrent.Promise;

import org.redisson.pubsub.AsyncSemaphore;

public class ClientConnectionsEntry {

    final Logger log = LoggerFactory.getLogger(getClass());

    private final Queue<RedisPubSubConnection> allSubscribeConnections = new ConcurrentLinkedQueue<RedisPubSubConnection>();
    private final Queue<RedisPubSubConnection> freeSubscribeConnections = new ConcurrentLinkedQueue<RedisPubSubConnection>();
    private final AsyncSemaphore freeSubscribeConnectionsCounter;

    private final Queue<RedisConnection> freeConnections = new ConcurrentLinkedQueue<RedisConnection>();
    private final AsyncSemaphore freeConnectionsCounter;

    public enum FreezeReason {MANAGER, RECONNECT, SYSTEM}

    private volatile boolean freezed;
    private FreezeReason freezeReason;
    final RedisClient client;

    private final NodeType nodeType;
    private ConnectionManager connectionManager;

    private final AtomicInteger failedAttempts = new AtomicInteger();

    public ClientConnectionsEntry(RedisClient client, int poolMinSize, int poolMaxSize, int subscribePoolMinSize, int subscribePoolMaxSize,
            ConnectionManager connectionManager, NodeType serverMode) {
        this.client = client;
        this.freeConnectionsCounter = new AsyncSemaphore(poolMaxSize);
        this.connectionManager = connectionManager;
        this.nodeType = serverMode;
        this.freeSubscribeConnectionsCounter = new AsyncSemaphore(subscribePoolMaxSize);

        if (subscribePoolMaxSize > 0) {
            connectionManager.getConnectionWatcher().add(subscribePoolMinSize, subscribePoolMaxSize, freeSubscribeConnections, freeSubscribeConnectionsCounter);
        }
        connectionManager.getConnectionWatcher().add(poolMinSize, poolMaxSize, freeConnections, freeConnectionsCounter);
    }

    public NodeType getNodeType() {
        return nodeType;
    }

    public void resetFailedAttempts() {
        failedAttempts.set(0);
    }

    public int getFailedAttempts() {
        return failedAttempts.get();
    }

    public int incFailedAttempts() {
        return failedAttempts.incrementAndGet();
    }

    public RedisClient getClient() {
        return client;
    }

    public boolean isFreezed() {
        return freezed;
    }

    public void setFreezeReason(FreezeReason freezeReason) {
        this.freezeReason = freezeReason;
    }

    public FreezeReason getFreezeReason() {
        return freezeReason;
    }

    public void setFreezed(boolean freezed) {
        this.freezed = freezed;
    }

    public int getFreeAmount() {
        return freeConnectionsCounter.getCounter();
    }

    public void acquireConnection(Runnable runnable) {
        freeConnectionsCounter.acquire(runnable);
    }

    public void releaseConnection() {
        freeConnectionsCounter.release();
    }

    public RedisConnection pollConnection() {
        return freeConnections.poll();
    }

    public void releaseConnection(RedisConnection connection) {
        connection.setLastUsageTime(System.currentTimeMillis());
        freeConnections.add(connection);
    }

    public Future<RedisConnection> connect() {
        final Promise<RedisConnection> connectionFuture = connectionManager.newPromise();
        Future<RedisConnection> future = client.connectAsync();
        future.addListener(new FutureListener<RedisConnection>() {
            @Override
            public void operationComplete(Future<RedisConnection> future) throws Exception {
                if (!future.isSuccess()) {
                    connectionFuture.tryFailure(future.cause());
                    return;
                }
                RedisConnection conn = future.getNow();
                log.debug("new connection created: {}", conn);

                addReconnectListener(connectionFuture, conn);
            }

        });
        return connectionFuture;
    }

    private <T extends RedisConnection> void addReconnectListener(Promise<T> connectionFuture, T conn) {
        addFireEventListener(conn, connectionFuture);

        conn.setReconnectListener(new ReconnectListener() {
            @Override
            public void onReconnect(RedisConnection conn, Promise<RedisConnection> connectionFuture) {
                addFireEventListener(conn, connectionFuture);
            }
        });
    }

    private <T extends RedisConnection> void addFireEventListener(T conn, Promise<T> connectionFuture) {
        connectionManager.getConnectListener().onConnect(connectionFuture, conn, nodeType, connectionManager.getConfig());
        
        if (connectionFuture.isSuccess()) {
            connectionManager.getConnectionEventsHub().fireConnect(connectionFuture.getNow().getRedisClient().getAddr());
            return;
        }

        connectionFuture.addListener(new FutureListener<T>() {
            @Override
            public void operationComplete(Future<T> future) throws Exception {
                if (future.isSuccess()) {
                    connectionManager.getConnectionEventsHub().fireConnect(future.getNow().getRedisClient().getAddr());
                }
            }
        });
    }

    public MasterSlaveServersConfig getConfig() {
        return connectionManager.getConfig();
    }

    public Future<RedisPubSubConnection> connectPubSub() {
        final Promise<RedisPubSubConnection> connectionFuture = connectionManager.newPromise();
        Future<RedisPubSubConnection> future = client.connectPubSubAsync();
        future.addListener(new FutureListener<RedisPubSubConnection>() {
            @Override
            public void operationComplete(Future<RedisPubSubConnection> future) throws Exception {
                if (!future.isSuccess()) {
                    connectionFuture.tryFailure(future.cause());
                    return;
                }
                
                RedisPubSubConnection conn = future.getNow();
                log.debug("new pubsub connection created: {}", conn);

                addReconnectListener(connectionFuture, conn);
                

                allSubscribeConnections.add(conn);
            }
        });
        return connectionFuture;
    }

    public Queue<RedisPubSubConnection> getAllSubscribeConnections() {
        return allSubscribeConnections;
    }

    public RedisPubSubConnection pollSubscribeConnection() {
        return freeSubscribeConnections.poll();
    }

    public void releaseSubscribeConnection(RedisPubSubConnection connection) {
        connection.setLastUsageTime(System.currentTimeMillis());
        freeSubscribeConnections.add(connection);
    }

    public void acquireSubscribeConnection(Runnable runnable) {
        freeSubscribeConnectionsCounter.acquire(runnable);
    }

    public void releaseSubscribeConnection() {
        freeSubscribeConnectionsCounter.release();
    }

    public boolean freezeMaster(FreezeReason reason) {
        synchronized (this) {
            setFreezed(true);
            // only RECONNECT freeze reason could be replaced
            if (getFreezeReason() == null
                    || getFreezeReason() == FreezeReason.RECONNECT) {
                setFreezeReason(reason);
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return "[freeSubscribeConnectionsAmount=" + freeSubscribeConnections.size()
                + ", freeSubscribeConnectionsCounter=" + freeSubscribeConnectionsCounter
                + ", freeConnectionsAmount=" + freeConnections.size() + ", freeConnectionsCounter="
                + freeConnectionsCounter + ", freezed=" + freezed + ", freezeReason=" + freezeReason
                + ", client=" + client + ", nodeType=" + nodeType + ", failedAttempts=" + failedAttempts
                + "]";
    }

}

