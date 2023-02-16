/**
 * Copyright (c) 2013-2022 Nikita Koksharov
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

import org.redisson.api.NodeType;
import org.redisson.client.RedisClient;
import org.redisson.client.RedisConnection;
import org.redisson.client.RedisPubSubConnection;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.config.MasterSlaveServersConfig;
import org.redisson.config.ReadMode;
import org.redisson.misc.AsyncSemaphore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Deque;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class ClientConnectionsEntry {

    final Logger log = LoggerFactory.getLogger(getClass());

    private final Queue<RedisPubSubConnection> allSubscribeConnections = new ConcurrentLinkedQueue<>();
    private final Queue<RedisPubSubConnection> freeSubscribeConnections = new ConcurrentLinkedQueue<>();
    private final AsyncSemaphore freeSubscribeConnectionsCounter;

    private final Queue<RedisConnection> allConnections = new ConcurrentLinkedQueue<>();
    private final Deque<RedisConnection> freeConnections = new ConcurrentLinkedDeque<>();
    private final AsyncSemaphore freeConnectionsCounter;

    public enum FreezeReason {MANAGER, RECONNECT, SYSTEM}

    private volatile FreezeReason freezeReason;
    final RedisClient client;

    private volatile NodeType nodeType;
    private final IdleConnectionWatcher idleConnectionWatcher;

    private final MasterSlaveServersConfig config;

    private volatile boolean initialized = false;

    public ClientConnectionsEntry(RedisClient client, int poolMinSize, int poolMaxSize,
                                  IdleConnectionWatcher idleConnectionWatcher, NodeType nodeType, MasterSlaveServersConfig config) {
        this.client = client;
        this.freeConnectionsCounter = new AsyncSemaphore(poolMaxSize);
        this.idleConnectionWatcher = idleConnectionWatcher;
        this.nodeType = nodeType;
        this.config = config;
        this.freeSubscribeConnectionsCounter = new AsyncSemaphore(config.getSubscriptionConnectionPoolSize());

        if (config.getSubscriptionConnectionPoolSize() > 0) {
            idleConnectionWatcher.add(this, config.getSubscriptionConnectionMinimumIdleSize(),
                                                config.getSubscriptionConnectionPoolSize(),
                                                freeSubscribeConnections,
                                                freeSubscribeConnectionsCounter, c -> {
                freeSubscribeConnections.remove(c);
                return allSubscribeConnections.remove(c);
            });
        }
        idleConnectionWatcher.add(this, poolMinSize, poolMaxSize, freeConnections, freeConnectionsCounter, c -> {
                freeConnections.remove(c);
                return allConnections.remove(c);
            });
    }
    
    public boolean isMasterForRead() {
        return getFreezeReason() == FreezeReason.SYSTEM
                        && config.getReadMode() == ReadMode.MASTER_SLAVE
                            && getNodeType() == NodeType.MASTER;
    }

    public boolean isInitialized() {
        return this.initialized;
    }

    public void setInitialized(boolean isInited) {
        this.initialized = isInited;
    }
    
    public void setNodeType(NodeType nodeType) {
        this.nodeType = nodeType;
    }

    public NodeType getNodeType() {
        return nodeType;
    }

    public void resetFirstFail() {
        client.resetFirstFail();
    }

    public boolean isFailed() {
        if (client.getFirstFailTime() != 0) {
            return System.currentTimeMillis() - client.getFirstFailTime() > config.getFailedSlaveCheckInterval();
        }
        return false;
    }
    
    public void trySetupFistFail() {
        client.trySetupFirstFail();
    }

    public CompletableFuture<Void> shutdownAsync() {
        idleConnectionWatcher.remove(this);
        return client.shutdownAsync().toCompletableFuture();
    }

    public RedisClient getClient() {
        return client;
    }

    public boolean isFreezed() {
        return freezeReason != null;
    }

    public void setFreezeReason(FreezeReason freezeReason) {
        this.freezeReason = freezeReason;
        if (freezeReason != null) {
            this.initialized = false;
        }
    }

    public FreezeReason getFreezeReason() {
        return freezeReason;
    }

    public void reset() {
        freeConnectionsCounter.removeListeners();
        freeSubscribeConnectionsCounter.removeListeners();
    }

    public CompletableFuture<Void> acquireConnection(RedisCommand<?> command) {
        return freeConnectionsCounter.acquire();
    }
    
    public void releaseConnection() {
        freeConnectionsCounter.release();
    }

    public void addConnection(RedisConnection conn) {
        conn.setLastUsageTime(System.nanoTime());
        if (conn instanceof RedisPubSubConnection) {
            freeSubscribeConnections.add((RedisPubSubConnection) conn);
        } else {
            freeConnections.add(conn);
        }
    }

    public RedisConnection pollConnection(RedisCommand<?> command) {
        RedisConnection c = freeConnections.poll();
        if (c != null) {
            c.incUsage();
        }
        return c;
    }

    public void releaseConnection(RedisConnection connection) {
        if (connection.isClosed()) {
            return;
        }

        if (client != connection.getRedisClient()) {
            connection.closeAsync();
            return;
        }

        connection.setLastUsageTime(System.nanoTime());
        freeConnections.add(connection);
        connection.decUsage();
    }

    public CompletionStage<RedisConnection> connect() {
        CompletionStage<RedisConnection> future = client.connectAsync();
        return future.whenComplete((conn, e) -> {
            if (e != null) {
                return;
            }

            log.debug("new connection created: {}", conn);
            
            allConnections.add(conn);
        });
    }

    public CompletionStage<RedisPubSubConnection> connectPubSub() {
        CompletionStage<RedisPubSubConnection> future = client.connectPubSubAsync();
        return future.whenComplete((conn, e) -> {
            if (e != null) {
                return;
            }
            
            log.debug("new pubsub connection created: {}", conn);

            allSubscribeConnections.add(conn);
        });
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
        if (connection.isClosed()) {
            return;
        }

        if (client != connection.getRedisClient()) {
            connection.closeAsync();
            return;
        }
        
        connection.setLastUsageTime(System.nanoTime());
        freeSubscribeConnections.add(connection);
    }

    public CompletableFuture<Void> acquireSubscribeConnection() {
        return freeSubscribeConnectionsCounter.acquire();
    }

    public void releaseSubscribeConnection() {
        freeSubscribeConnectionsCounter.release();
    }

    @Override
    public String toString() {
        return "[freeSubscribeConnectionsAmount=" + freeSubscribeConnections.size()
                + ", freeSubscribeConnectionsCounter=" + freeSubscribeConnectionsCounter
                + ", freeConnectionsAmount=" + freeConnections.size() + ", freeConnectionsCounter="
                + freeConnectionsCounter + ", freezeReason=" + freezeReason
                + ", client=" + client + ", nodeType=" + nodeType + ", firstFail=" + client.getFirstFailTime()
                + "]";
    }

}

