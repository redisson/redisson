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
package org.redisson.connection.pool;

import org.redisson.api.NodeType;
import org.redisson.client.RedisConnection;
import org.redisson.client.RedisConnectionException;
import org.redisson.client.protocol.RedisCommand;
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

    public void addEntry(ClientConnectionsEntry entry) {
        entries.add(entry);
    }

    public CompletableFuture<Void> initConnections(ClientConnectionsEntry entry) {
        int minimumIdleSize = getMinimumIdleSize(entry);
        if (minimumIdleSize == 0) {
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<Void> initPromise = new CompletableFuture<>();
        AtomicInteger initializedConnections = new AtomicInteger(minimumIdleSize);
        createConnection(entry, initPromise, minimumIdleSize, initializedConnections);
        return initPromise;
    }

    private void createConnection(ClientConnectionsEntry entry,
                                  CompletableFuture<Void> initPromise, int minimumIdleSize, AtomicInteger initializedConnections) {

        CompletableFuture<Void> f = acquireConnection(entry, null);
        f.thenAccept(r -> {
            CompletableFuture<T> promise = new CompletableFuture<T>();
            createConnection(entry, promise);
            promise.whenComplete((conn, e) -> {
                if (e == null) {
                    if (changeUsage()) {
                        conn.decUsage();
                    }
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
                    Exception cause = new RedisConnectionException(errorMsg, e);
                    initPromise.completeExceptionally(cause);
                    return;
                }

                int value = initializedConnections.decrementAndGet();
                if (value == 0) {
                    if (initPromise.complete(null)) {
                        log.info("{} connections initialized for {}", minimumIdleSize, entry.getClient().getAddr());
                    }
                } else if (value > 0 && !initPromise.isDone()) {
                    createConnection(entry, initPromise, minimumIdleSize, initializedConnections);
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
                    && isHealthy(entry))) {
                iterator.remove();
            }
        }
        if (!entriesCopy.isEmpty()) {
            ClientConnectionsEntry entry = config.getLoadBalancer().getEntry(entriesCopy, command);
            return acquireConnection(command, entry);
        }
        
        List<InetSocketAddress> failed = new LinkedList<>();
        List<InetSocketAddress> freezed = new LinkedList<>();
        for (ClientConnectionsEntry entry : entries) {
            if (entry.getClient().getConfig().getFailedNodeDetector().isNodeFailed()) {
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
        
    private boolean isHealthy(ClientConnectionsEntry entry) {
        if (entry.getNodeType() == NodeType.SLAVE && entry.getClient().getConfig().getFailedNodeDetector().isNodeFailed()) {
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
            connectionManager.getServiceManager().getGroup().submit(() -> {
                releaseConnection(entry);
            });
            return;
        }

        T conn = poll(entry, command);
        if (conn != null) {
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

            if (changeUsage()) {
                promise.thenApply(c -> c.incUsage());
            }
            connectedSuccessful(entry, promise, conn);
        });
    }

    protected boolean changeUsage() {
        return true;
    }

    private void connectedSuccessful(ClientConnectionsEntry entry, CompletableFuture<T> promise, T conn) {
        if (entry.getNodeType() == NodeType.SLAVE) {
            entry.getClient().getConfig().getFailedNodeDetector().onConnectSuccessful();
        }

        if (!promise.complete(conn)) {
            releaseConnection(entry, conn);
            releaseConnection(entry);
        }
    }

    private void promiseFailure(ClientConnectionsEntry entry, CompletableFuture<T> promise, Throwable cause) {
        if (entry.getNodeType() == NodeType.SLAVE) {
            entry.getClient().getConfig().getFailedNodeDetector().onConnectFailed();
            if (entry.getClient().getConfig().getFailedNodeDetector().isNodeFailed()) {
                masterSlaveEntry.shutdownAndReconnectAsync(entry.getClient(), cause);
            }
        }

        releaseConnection(entry);

        promise.completeExceptionally(cause);
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
