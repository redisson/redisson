/**
 * Copyright (c) 2013-2019 Nikita Koksharov
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
import java.util.concurrent.atomic.AtomicLong;

import org.redisson.api.NodeType;
import org.redisson.api.RFuture;
import org.redisson.client.RedisClient;
import org.redisson.client.RedisConnection;
import org.redisson.client.RedisPubSubConnection;
import org.redisson.config.MasterSlaveServersConfig;
import org.redisson.config.ReadMode;
import org.redisson.pubsub.AsyncSemaphore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class ClientConnectionsEntry {

    final Logger log = LoggerFactory.getLogger(getClass());

    private final Queue<RedisPubSubConnection> allSubscribeConnections = new ConcurrentLinkedQueue<RedisPubSubConnection>();
    private final Queue<RedisPubSubConnection> freeSubscribeConnections = new ConcurrentLinkedQueue<RedisPubSubConnection>();
    private final AsyncSemaphore freeSubscribeConnectionsCounter;

    private final Queue<RedisConnection> allConnections = new ConcurrentLinkedQueue<RedisConnection>();
    private final Queue<RedisConnection> freeConnections = new ConcurrentLinkedQueue<RedisConnection>();
    private final AsyncSemaphore freeConnectionsCounter;

    public enum FreezeReason {MANAGER, RECONNECT, SYSTEM}

    private volatile boolean freezed;
    private FreezeReason freezeReason;
    final RedisClient client;

    private volatile NodeType nodeType;
    private ConnectionManager connectionManager;

    private final AtomicLong firstFailTime = new AtomicLong(0);

    public ClientConnectionsEntry(RedisClient client, int poolMinSize, int poolMaxSize, int subscribePoolMinSize, int subscribePoolMaxSize,
            ConnectionManager connectionManager, NodeType nodeType) {
        this.client = client;
        this.freeConnectionsCounter = new AsyncSemaphore(poolMaxSize);
        this.connectionManager = connectionManager;
        this.nodeType = nodeType;
        this.freeSubscribeConnectionsCounter = new AsyncSemaphore(subscribePoolMaxSize);

        if (subscribePoolMaxSize > 0) {
            connectionManager.getConnectionWatcher().add(subscribePoolMinSize, subscribePoolMaxSize, freeSubscribeConnections, freeSubscribeConnectionsCounter);
        }
        connectionManager.getConnectionWatcher().add(poolMinSize, poolMaxSize, freeConnections, freeConnectionsCounter);
    }
    
    public boolean isMasterForRead() {
        return getFreezeReason() == FreezeReason.SYSTEM && getConfig().getReadMode() == ReadMode.MASTER_SLAVE && getNodeType() == NodeType.MASTER;
    }
    
    public void setNodeType(NodeType nodeType) {
        this.nodeType = nodeType;
    }
    public NodeType getNodeType() {
        return nodeType;
    }

    public void resetFirstFail() {
        firstFailTime.set(0);
    }

    public boolean isFailed() {
        if (firstFailTime.get() != 0) {
            return System.currentTimeMillis() - firstFailTime.get() > connectionManager.getConfig().getFailedSlaveCheckInterval(); 
        }
        return false;
    }
    
    public void trySetupFistFail() {
        firstFailTime.compareAndSet(0, System.currentTimeMillis());
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
    
    public void reset() {
        freeConnectionsCounter.removeListeners();
        freeSubscribeConnectionsCounter.removeListeners();
    }

    public int getFreeAmount() {
        return freeConnectionsCounter.getCounter();
    }

    public void acquireConnection(Runnable runnable) {
        freeConnectionsCounter.acquire(runnable);
    }
    
    public void removeConnection(Runnable runnable) {
        freeConnectionsCounter.remove(runnable);
    }

    public void releaseConnection() {
        freeConnectionsCounter.release();
    }

    public RedisConnection pollConnection() {
        return freeConnections.poll();
    }

    public void releaseConnection(RedisConnection connection) {
        if (client != connection.getRedisClient()) {
            connection.closeAsync();
            return;
        }

        connection.setLastUsageTime(System.currentTimeMillis());
        freeConnections.add(connection);
    }

    public RFuture<RedisConnection> connect() {
        RFuture<RedisConnection> future = client.connectAsync();
        future.onComplete((conn, e) -> {
            if (e != null) {
                return;
            }
            
            onConnect(conn);
            log.debug("new connection created: {}", conn);
            
            allConnections.add(conn);
        });
        return future;
    }
    
    private void onConnect(final RedisConnection conn) {
        conn.setConnectedListener(new Runnable() {
            @Override
            public void run() {
                if (!connectionManager.isShuttingDown()) {
                    connectionManager.getConnectionEventsHub().fireConnect(conn.getRedisClient().getAddr());
                }
            }
        });
        conn.setDisconnectedListener(new Runnable() {
            @Override
            public void run() {
                if (!connectionManager.isShuttingDown()) {
                    connectionManager.getConnectionEventsHub().fireDisconnect(conn.getRedisClient().getAddr());
                }
            }
        });
        
        connectionManager.getConnectionEventsHub().fireConnect(conn.getRedisClient().getAddr());
    }

    public MasterSlaveServersConfig getConfig() {
        return connectionManager.getConfig();
    }

    public RFuture<RedisPubSubConnection> connectPubSub() {
        RFuture<RedisPubSubConnection> future = client.connectPubSubAsync();
        future.onComplete((res, e) -> {
            if (e != null) {
                return;
            }
            
            RedisPubSubConnection conn = future.getNow();
            onConnect(conn);
            log.debug("new pubsub connection created: {}", conn);

            allSubscribeConnections.add(conn);
        });
        return future;
    }
    
    public Queue<RedisConnection> getAllConnections() {
        return allConnections;
    }

    public Queue<RedisPubSubConnection> getAllSubscribeConnections() {
        return allSubscribeConnections;
    }

    public RedisPubSubConnection pollSubscribeConnection() {
        return freeSubscribeConnections.poll();
    }

    public void releaseSubscribeConnection(RedisPubSubConnection connection) {
        if (client != connection.getRedisClient()) {
            connection.closeAsync();
            return;
        }
        
        connection.setLastUsageTime(System.currentTimeMillis());
        freeSubscribeConnections.add(connection);
    }

    public void acquireSubscribeConnection(Runnable runnable) {
        freeSubscribeConnectionsCounter.acquire(runnable);
    }

    public void releaseSubscribeConnection() {
        freeSubscribeConnectionsCounter.release();
    }

    public void freezeMaster(FreezeReason reason) {
        synchronized (this) {
            setFreezed(true);
            setFreezeReason(reason);
        }
    }

    @Override
    public String toString() {
        return "[freeSubscribeConnectionsAmount=" + freeSubscribeConnections.size()
                + ", freeSubscribeConnectionsCounter=" + freeSubscribeConnectionsCounter
                + ", freeConnectionsAmount=" + freeConnections.size() + ", freeConnectionsCounter="
                + freeConnectionsCounter + ", freezed=" + freezed + ", freezeReason=" + freezeReason
                + ", client=" + client + ", nodeType=" + nodeType + ", firstFail=" + firstFailTime
                + "]";
    }

}

