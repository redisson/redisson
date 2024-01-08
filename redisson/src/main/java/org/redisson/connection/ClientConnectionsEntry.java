/**
 * Copyright (c) 2013-2024 Nikita Koksharov
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

import io.netty.channel.ChannelFuture;
import org.redisson.api.NodeType;
import org.redisson.client.RedisClient;
import org.redisson.client.RedisConnection;
import org.redisson.client.RedisPubSubConnection;
import org.redisson.client.protocol.CommandData;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.config.MasterSlaveServersConfig;
import org.redisson.config.ReadMode;
import org.redisson.misc.AsyncSemaphore;
import org.redisson.misc.WrappedLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

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
    private final Queue<RedisConnection> freeConnections = new ConcurrentLinkedQueue<>();
    private final AsyncSemaphore freeConnectionsCounter;

    public enum FreezeReason {MANAGER, RECONNECT, SYSTEM}

    private volatile FreezeReason freezeReason;
    final RedisClient client;

    private volatile NodeType nodeType;
    private final IdleConnectionWatcher idleConnectionWatcher;
    private final ConnectionManager connectionManager;

    private final MasterSlaveServersConfig config;

    private volatile boolean initialized = false;

    private final WrappedLock lock = new WrappedLock();

    public ClientConnectionsEntry(RedisClient client, int poolMinSize, int poolMaxSize,
                                  ConnectionManager connectionManager, NodeType nodeType, MasterSlaveServersConfig config) {
        this.client = client;
        this.freeConnectionsCounter = new AsyncSemaphore(poolMaxSize);
        this.idleConnectionWatcher = connectionManager.getServiceManager().getConnectionWatcher();
        this.connectionManager = connectionManager;
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

    public WrappedLock getLock() {
        return lock;
    }

    @Override
    public String toString() {
        return "[freeSubscribeConnectionsAmount=" + freeSubscribeConnections.size()
                + ", freeSubscribeConnectionsCounter=" + freeSubscribeConnectionsCounter
                + ", freeConnectionsAmount=" + freeConnections.size() + ", freeConnectionsCounter="
                + freeConnectionsCounter + ", freezeReason=" + freezeReason
                + ", client=" + client + ", nodeType=" + nodeType
                + "]";
    }

    public void reattachPubSub() {
        freeSubscribeConnectionsCounter.removeListeners();

        for (RedisPubSubConnection connection : allSubscribeConnections) {
            connection.closeAsync();
            connectionManager.getSubscribeService().reattachPubSub(connection);
        }
        freeSubscribeConnections.clear();
        allSubscribeConnections.clear();
    }

    public void nodeDown() {
        freeConnectionsCounter.removeListeners();

        for (RedisConnection connection : allConnections) {
            connection.closeAsync();
            reattachBlockingQueue(connection.getCurrentCommand());
        }
        freeConnections.clear();
        allConnections.clear();

        reattachPubSub();
    }

    private void reattachBlockingQueue(CommandData<?, ?> commandData) {
        if (commandData == null
                || !commandData.isBlockingCommand()
                || commandData.getPromise().isDone()) {
            return;
        }

        String key = null;
        for (int i = 0; i < commandData.getParams().length; i++) {
            Object param = commandData.getParams()[i];
            if ("STREAMS".equals(param)) {
                key = (String) commandData.getParams()[i+1];
                break;
            }
        }
        if (key == null) {
            key = (String) commandData.getParams()[0];
        }

        MasterSlaveEntry entry = connectionManager.getEntry(key);
        if (entry == null) {
            connectionManager.getServiceManager().newTimeout(timeout ->
                    reattachBlockingQueue(commandData), 1, TimeUnit.SECONDS);
            return;
        }

        CompletableFuture<RedisConnection> newConnectionFuture = entry.connectionWriteOp(commandData.getCommand());
        newConnectionFuture.whenComplete((newConnection, e) -> {
            if (e != null) {
                connectionManager.getServiceManager().newTimeout(timeout ->
                        reattachBlockingQueue(commandData), 1, TimeUnit.SECONDS);
                return;
            }

            commandData.getPromise().whenComplete((r, ex) -> {
                entry.releaseWrite(newConnection);
            });

            ChannelFuture channelFuture = newConnection.send(commandData);
            channelFuture.addListener(future -> {
                if (!future.isSuccess()) {
                    connectionManager.getServiceManager().newTimeout(timeout ->
                            reattachBlockingQueue(commandData), 1, TimeUnit.SECONDS);
                    return;
                }
                log.info("command '{}' has been resent to '{}'", commandData, newConnection.getRedisClient());
            });
        });
    }


}

