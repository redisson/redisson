/**
 * Copyright (c) 2013-2021 Nikita Koksharov
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
import org.redisson.api.RFuture;
import org.redisson.client.RedisClient;
import org.redisson.client.RedisConnection;
import org.redisson.client.RedisPubSubConnection;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.config.ReadMode;
import org.redisson.pubsub.AsyncSemaphore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Deque;
import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

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
    private volatile Iterator<RedisConnection> iter;

    public enum FreezeReason {MANAGER, RECONNECT, SYSTEM}

    private volatile FreezeReason freezeReason;
    final RedisClient client;

    private volatile NodeType nodeType;
    private final ConnectionManager connectionManager;

    private final AtomicLong firstFailTime = new AtomicLong(0);

    private volatile boolean initialized = false;

    public ClientConnectionsEntry(RedisClient client, int poolMinSize, int poolMaxSize, int subscribePoolMinSize, int subscribePoolMaxSize,
            ConnectionManager connectionManager, NodeType nodeType) {
        this.client = client;
        this.freeConnectionsCounter = new AsyncSemaphore(poolMaxSize);
        this.connectionManager = connectionManager;
        this.nodeType = nodeType;
        this.freeSubscribeConnectionsCounter = new AsyncSemaphore(subscribePoolMaxSize);

        if (subscribePoolMaxSize > 0) {
            connectionManager.getConnectionWatcher().add(this, subscribePoolMinSize, subscribePoolMaxSize, freeSubscribeConnections, freeSubscribeConnectionsCounter, c -> {
                freeSubscribeConnections.remove(c);
                return allSubscribeConnections.remove(c);
            });
        }
        connectionManager.getConnectionWatcher().add(this, poolMinSize, poolMaxSize, freeConnections, freeConnectionsCounter, c -> {
                freeConnections.remove(c);
                return allConnections.remove(c);
            });

        iter = freeConnections.iterator();
    }
    
    public boolean isMasterForRead() {
        return getFreezeReason() == FreezeReason.SYSTEM
                        && connectionManager.getConfig().getReadMode() == ReadMode.MASTER_SLAVE
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

    public RFuture<Void> shutdownAsync() {
        connectionManager.getConnectionWatcher().remove(this);
        return client.shutdownAsync();
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

    public int getFreeAmount() {
        return freeConnectionsCounter.getCounter();
    }

    private boolean isPolled(RedisCommand<?> command) {
        return command == null
                || RedisCommands.FLUSHDB.getName().equals(command.getName())
                || RedisCommands.FLUSHALL.getName().equals(command.getName())
                || RedisCommands.BLOCKING_COMMAND_NAMES.contains(command.getName())
                || RedisCommands.BLOCKING_COMMANDS.contains(command)
                || RedisCommands.PUBSUB_COMMANDS.contains(command.getName())
                || RedisCommands.SCAN_COMMANDS.contains(command.getName());
    }

    public void acquireConnection(Runnable runnable, RedisCommand<?> command) {
        if (isPolled(command)) {
            freeConnectionsCounter.acquire(runnable);
            return;
        }

        runnable.run();
    }
    
    public void removeConnection(Runnable runnable) {
        freeConnectionsCounter.remove(runnable);
    }

    public void releaseConnection() {
        freeConnectionsCounter.release();
    }

    AtomicBoolean lock = new AtomicBoolean();

    public RedisConnection pollConnection(RedisCommand<?> command) {
        if (isPolled(command)) {
            while (true) {
                if (lock.compareAndSet(false, true)) {
                    if (!iter.hasNext()) {
                        iter = freeConnections.iterator();
                    }
                    try {
                        if (iter.hasNext()) {
                            RedisConnection c = iter.next();
                            iter.remove();
                            if (c != null) {
                                c.incUsage();
                                c.setPooled(true);
                            }
                            return c;
                        }
                        return null;
                    } finally {
                        lock.set(false);
                    }
                }
            }
        }

        while (true) {
            if (lock.compareAndSet(false, true)) {
                if (!iter.hasNext()) {
                    iter = freeConnections.iterator();
                }
                try {
                    if (iter.hasNext()) {
                        RedisConnection c = iter.next();
                        if (c != null) {
                            c.incUsage();
                        }
                        return c;
                    }
                    return null;
                } finally {
                    lock.set(false);
                }
            }
        }
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
        if (connection.getUsage() == 0) {
            freeConnections.add(connection);
            return;
        }
        connection.decUsage();
        if (connection.isPooled() && connection.getUsage() == 0) {
            freeConnections.add(connection);
            connection.setPooled(false);
        }
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

    public void acquireSubscribeConnection(Runnable runnable) {
        freeSubscribeConnectionsCounter.acquire(runnable);
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
                + ", client=" + client + ", nodeType=" + nodeType + ", firstFail=" + firstFailTime
                + "]";
    }

}

