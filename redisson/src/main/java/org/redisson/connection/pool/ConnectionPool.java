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
package org.redisson.connection.pool;

import org.redisson.api.NodeType;
import org.redisson.api.RFuture;
import org.redisson.client.RedisConnection;
import org.redisson.client.RedisConnectionException;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.config.MasterSlaveServersConfig;
import org.redisson.connection.ClientConnectionsEntry;
import org.redisson.connection.ClientConnectionsEntry.FreezeReason;
import org.redisson.connection.ConnectionManager;
import org.redisson.connection.MasterSlaveEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Base connection pool class 
 * 
 * @author Nikita Koksharov
 *
 * @param <T> - connection type
 */
abstract class ConnectionPool<T extends RedisConnection> {

    private final Logger log = LoggerFactory.getLogger(getClass());

    protected final Queue<ClientConnectionsEntry> entries = new ConcurrentLinkedQueue<>();

    final ConnectionManager connectionManager;

    final MasterSlaveServersConfig config;

    final MasterSlaveEntry masterSlaveEntry;

    ConnectionPool(MasterSlaveServersConfig config, ConnectionManager connectionManager, MasterSlaveEntry masterSlaveEntry) {
        this.config = config;
        this.masterSlaveEntry = masterSlaveEntry;
        this.connectionManager = connectionManager;
    }

    public CompletableFuture<Void> add(ClientConnectionsEntry entry) {
        CompletableFuture<Void> promise = initConnections(entry, true);
        return promise.thenAccept(r -> {
            entries.add(entry);
        });
    }

    public CompletableFuture<Void> initConnections(ClientConnectionsEntry entry) {
        return initConnections(entry, false);
    }
    
    private CompletableFuture<Void> initConnections(ClientConnectionsEntry entry, boolean checkFreezed) {
        int minimumIdleSize = getMinimumIdleSize(entry);

        if (minimumIdleSize == 0 || (checkFreezed && entry.isFreezed())) {
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<Void> initPromise = new CompletableFuture<>();
        AtomicInteger initializedConnections = new AtomicInteger(minimumIdleSize);
        int startAmount = Math.min(2, minimumIdleSize);
        AtomicInteger requests = new AtomicInteger(startAmount);
        for (int i = 0; i < startAmount; i++) {
            createConnection(checkFreezed, requests, entry, initPromise, minimumIdleSize, initializedConnections);
        }
        return initPromise;
    }

    private void createConnection(boolean checkFreezed, AtomicInteger requests, ClientConnectionsEntry entry,
                                  CompletableFuture<Void> initPromise, int minimumIdleSize, AtomicInteger initializedConnections) {

        if ((checkFreezed && entry.isFreezed()) || !tryAcquireConnection(entry)) {
            int totalInitializedConnections = minimumIdleSize - initializedConnections.get();
            Throwable cause = new RedisConnectionException(
                    "Unable to init enough connections amount! Only " + totalInitializedConnections + " of " + minimumIdleSize + " were initialized. Server: "
                                        + entry.getClient().getAddr());
            initPromise.completeExceptionally(cause);
            return;
        }

        CompletableFuture<Void> f = acquireConnection(entry, null);
        f.thenAccept(r -> {
            CompletableFuture<T> promise = new CompletableFuture<T>();
            createConnection(entry, promise);
            promise.whenComplete((conn, e) -> {
                if (e == null) {
                    conn.decUsage();
                    if (!initPromise.isDone()) {
                        entry.addConnection(conn);
                    } else {
                        conn.closeAsync();
                    }
                }

                releaseConnection(entry);

                if (e != null) {
                    if (initPromise.isDone()) {
                        return;
                    }

                    for (RedisConnection connection : entry.getAllConnections()) {
                        if (!connection.isClosed()) {
                            connection.closeAsync();
                        }
                    }
                    entry.getAllConnections().clear();

                    for (RedisConnection connection : entry.getAllSubscribeConnections()) {
                        if (!connection.isClosed()) {
                            connection.closeAsync();
                        }
                    }
                    entry.getAllSubscribeConnections().clear();

                    int totalInitializedConnections = minimumIdleSize - initializedConnections.get();
                    String errorMsg;
                    if (totalInitializedConnections == 0) {
                        errorMsg = "Unable to connect to Redis server: " + entry.getClient().getAddr();
                    } else {
                        errorMsg = "Unable to init enough connections amount! Only " + totalInitializedConnections
                                + " of " + minimumIdleSize + " were initialized. Redis server: " + entry.getClient().getAddr();
                    }
                    Throwable cause = new RedisConnectionException(errorMsg, e);
                    initPromise.completeExceptionally(cause);
                    return;
                }

                int value = initializedConnections.decrementAndGet();
                if (value == 0) {
                    if (initPromise.complete(null)) {
                        log.info("{} connections initialized for {}", minimumIdleSize, entry.getClient().getAddr());
                    }
                } else if (value > 0 && !initPromise.isDone()) {
                    if (requests.incrementAndGet() <= minimumIdleSize) {
                        createConnection(checkFreezed, requests, entry, initPromise, minimumIdleSize, initializedConnections);
                    }
                }
            });
        });
    }

    protected CompletableFuture<Void> acquireConnection(ClientConnectionsEntry entry, RedisCommand<?> command) {
        return entry.acquireConnection(command);
    }

    protected abstract int getMinimumIdleSize(ClientConnectionsEntry entry);

    public CompletableFuture<T> get(RedisCommand<?> command) {
        List<ClientConnectionsEntry> entriesCopy = new LinkedList<ClientConnectionsEntry>(entries);
        for (Iterator<ClientConnectionsEntry> iterator = entriesCopy.iterator(); iterator.hasNext();) {
            ClientConnectionsEntry entry = iterator.next();
            if (!((!entry.isFreezed() || entry.isMasterForRead()) 
                    && tryAcquireConnection(entry))) {
                iterator.remove();
            }
        }
        if (!entriesCopy.isEmpty()) {
            ClientConnectionsEntry entry = config.getLoadBalancer().getEntry(entriesCopy);
            return acquireConnection(command, entry);
        }
        
        List<InetSocketAddress> failed = new LinkedList<>();
        List<InetSocketAddress> freezed = new LinkedList<>();
        for (ClientConnectionsEntry entry : entries) {
            if (entry.isFailed()) {
                failed.add(entry.getClient().getAddr());
            } else if (entry.isFreezed()) {
                freezed.add(entry.getClient().getAddr());
            }
        }

        StringBuilder errorMsg = new StringBuilder(getClass().getSimpleName() + " no available Redis entries. Master entry host: " + masterSlaveEntry.getClient().getAddr());
        if (!freezed.isEmpty()) {
            errorMsg.append(" Disconnected hosts: ").append(freezed);
        }
        if (!failed.isEmpty()) {
            errorMsg.append(" Hosts disconnected due to errors during `failedSlaveCheckInterval`: ").append(failed);
        }

        RedisConnectionException exception = new RedisConnectionException(errorMsg.toString());
        CompletableFuture<T> result = new CompletableFuture<>();
        result.completeExceptionally(exception);
        return result;
    }

    public CompletableFuture<T> get(RedisCommand<?> command, ClientConnectionsEntry entry) {
        return acquireConnection(command, entry);
    }

    protected final CompletableFuture<T> acquireConnection(RedisCommand<?> command, ClientConnectionsEntry entry) {
        CompletableFuture<T> result = new CompletableFuture<T>();

        CompletableFuture<Void> f = acquireConnection(entry, command);
        f.thenAccept(r -> {
            connectTo(entry, result, command);
        });
        result.whenComplete((r, e) -> {
            if (e != null) {
                f.completeExceptionally(e);
            }
        });
        return result;
    }
        
    protected boolean tryAcquireConnection(ClientConnectionsEntry entry) {
        if (entry.getNodeType() == NodeType.SLAVE && entry.isFailed()) {
            checkForReconnect(entry, null);
            return false;
        }
        return true;
    }

    protected T poll(ClientConnectionsEntry entry, RedisCommand<?> command) {
        return (T) entry.pollConnection(command);
    }

    protected CompletionStage<T> connect(ClientConnectionsEntry entry) {
        return (CompletionStage<T>) entry.connect();
    }

    private void connectTo(ClientConnectionsEntry entry, CompletableFuture<T> promise, RedisCommand<?> command) {
        if (promise.isDone()) {
            connectionManager.getGroup().submit(() -> {
                releaseConnection(entry);
            });
            return;
        }

        T conn = poll(entry, command);
        if (conn != null) {
            if (!conn.isActive() && entry.getNodeType() == NodeType.SLAVE) {
                entry.trySetupFistFail();
            }

            connectedSuccessful(entry, promise, conn);
            return;
        }

        createConnection(entry, promise);
    }

    private void createConnection(ClientConnectionsEntry entry, CompletableFuture<T> promise) {
        CompletionStage<T> connFuture = connect(entry);
        connFuture.whenComplete((conn, e) -> {
            if (e != null) {
                promiseFailure(entry, promise, e);
                return;
            }

            if (!conn.isActive()) {
                promiseFailure(entry, promise, conn);
                return;
            }

            promise.thenApply(c -> c.incUsage());
            connectedSuccessful(entry, promise, conn);
        });
    }

    private void connectedSuccessful(ClientConnectionsEntry entry, CompletableFuture<T> promise, T conn) {
        if (conn.isActive() && entry.getNodeType() == NodeType.SLAVE) {
            entry.resetFirstFail();
        }

        if (!promise.complete(conn)) {
            releaseConnection(entry, conn);
            releaseConnection(entry);
        }
    }

    private void promiseFailure(ClientConnectionsEntry entry, CompletableFuture<T> promise, Throwable cause) {
        if (entry.getNodeType() == NodeType.SLAVE) {
            entry.trySetupFistFail();
            if (entry.isFailed()) {
            checkForReconnect(entry, cause);
        }
        }

        releaseConnection(entry);

        promise.completeExceptionally(cause);
    }

    private void promiseFailure(ClientConnectionsEntry entry, CompletableFuture<T> promise, T conn) {
        if (entry.getNodeType() == NodeType.SLAVE) {
            entry.trySetupFistFail();
            if (entry.isFailed()) {
            conn.closeAsync();
            entry.getAllConnections().remove(conn);
            checkForReconnect(entry, null);
            } else {
            releaseConnection(entry, conn);
        }
        } else {
            releaseConnection(entry, conn);
        }

        releaseConnection(entry);

        RedisConnectionException cause = new RedisConnectionException(conn + " is not active!");
        promise.completeExceptionally(cause);
    }

    private void checkForReconnect(ClientConnectionsEntry entry, Throwable cause) {
        if (masterSlaveEntry.slaveDown(entry, FreezeReason.RECONNECT)) {
            log.error("slave " + entry.getClient().getAddr() + " has been disconnected after " 
                        + config.getFailedSlaveCheckInterval() + " ms interval since moment of the first failed connection", cause);
            scheduleCheck(entry);
            }
        }

    private void scheduleCheck(ClientConnectionsEntry entry) {

        connectionManager.getConnectionEventsHub().fireDisconnect(entry.getClient().getAddr());

        connectionManager.newTimeout(timeout -> {
            synchronized (entry) {
                if (entry.getFreezeReason() != FreezeReason.RECONNECT
                        || connectionManager.isShuttingDown()) {
                    return;
                }
            }

            CompletionStage<RedisConnection> connectionFuture = entry.getClient().connectAsync();
            connectionFuture.whenComplete((c, e) -> {
                    synchronized (entry) {
                        if (entry.getFreezeReason() != FreezeReason.RECONNECT) {
                            return;
                        }
                    }

                    if (e != null) {
                        scheduleCheck(entry);
                        return;
                    }
                    if (!c.isActive()) {
                        c.closeAsync();
                        scheduleCheck(entry);
                        return;
                    }

                    RFuture<String> f = c.async(RedisCommands.PING);
                    f.whenComplete((t, ex) -> {
                        try {
                            synchronized (entry) {
                                if (entry.getFreezeReason() != FreezeReason.RECONNECT) {
                                    return;
                                }
                            }

                            if ("PONG".equals(t)) {
                                if (masterSlaveEntry.slaveUp(entry, FreezeReason.RECONNECT)) {
                                    log.info("slave {} has been successfully reconnected", entry.getClient().getAddr());
                                }
                            } else {
                                scheduleCheck(entry);
                            }
                        } finally {
                            c.closeAsync();
                        }
                    });
            });
        }, config.getFailedSlaveReconnectionInterval(), TimeUnit.MILLISECONDS);
    }

    public void returnConnection(ClientConnectionsEntry entry, T connection) {
        if (entry == null) {
            connection.closeAsync();
            return;
        }
        if (entry.isFreezed() && entry.getFreezeReason() != FreezeReason.SYSTEM) {
            connection.closeAsync();
            entry.getAllConnections().remove(connection);
        } else {
            releaseConnection(entry, connection);
        }
        releaseConnection(entry);
    }

    protected void releaseConnection(ClientConnectionsEntry entry) {
        entry.releaseConnection();
    }

    protected void releaseConnection(ClientConnectionsEntry entry, T conn) {
        entry.releaseConnection(conn);
    }

}
