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

import org.redisson.client.RedisClient;
import org.redisson.client.RedisConnection;
import org.redisson.client.RedisConnectionException;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.misc.AsyncSemaphore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Function;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class ConnectionsHolder<T extends RedisConnection> {

    final Logger log = LoggerFactory.getLogger(getClass());

    private final Queue<T> allConnections = new ConcurrentLinkedQueue<>();
    private final Queue<T> freeConnections = new ConcurrentLinkedQueue<>();
    private final AsyncSemaphore freeConnectionsCounter;

    private final RedisClient client;

    private final Function<RedisClient, CompletionStage<T>> connectionCallback;

    private final ServiceManager serviceManager;

    private final boolean changeUsage;

    public ConnectionsHolder(RedisClient client, int poolMaxSize,
                             Function<RedisClient, CompletionStage<T>> connectionCallback,
                             ServiceManager serviceManager, boolean changeUsage) {
        this.freeConnectionsCounter = new AsyncSemaphore(poolMaxSize);
        this.client = client;
        this.connectionCallback = connectionCallback;
        this.serviceManager = serviceManager;
        this.changeUsage = changeUsage;
    }

    public <R extends RedisConnection> boolean remove(R connection) {
        if (freeConnections.remove(connection)) {
            return allConnections.remove(connection);
        }
        return false;
    }

    public Queue<T> getFreeConnections() {
        return freeConnections;
    }

    public AsyncSemaphore getFreeConnectionsCounter() {
        return freeConnectionsCounter;
    }

    protected CompletableFuture<Void> acquireConnection() {
        return freeConnectionsCounter.acquire();
    }
    
    private void releaseConnection() {
        freeConnectionsCounter.release();
    }

    private void addConnection(T conn) {
        conn.setLastUsageTime(System.nanoTime());
        freeConnections.add(conn);
    }

    private T pollConnection(RedisCommand<?> command) {
        T c = freeConnections.poll();
        if (c != null) {
            c.incUsage();
        }
        return c;
    }

    private void releaseConnection(T connection) {
        if (connection.isClosed()) {
            return;
        }

        if (client != null && client != connection.getRedisClient()) {
            connection.closeAsync();
            return;
        }

        connection.setLastUsageTime(System.nanoTime());
        freeConnections.add(connection);
        connection.decUsage();
    }

    public Queue<T> getAllConnections() {
        return allConnections;
    }

    public CompletableFuture<Void> initConnections(int minimumIdleSize) {
        if (minimumIdleSize == 0) {
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<Void> f = createConnection(minimumIdleSize, 1);
        for (int i = 2; i <= minimumIdleSize; i++) {
            int k = i;
            f = f.thenCompose(r -> createConnection(minimumIdleSize, k));
        }
        return f.thenAccept(r -> {
            log.info("{} connections initialized for {}", minimumIdleSize, client.getAddr());
        });
    }

    private CompletableFuture<Void> createConnection(int minimumIdleSize, int index) {
        CompletableFuture<Void> f = acquireConnection();
        return f.thenCompose(r -> {
            CompletableFuture<T> promise = new CompletableFuture<>();
            createConnection(promise);
            return promise.handle((conn, e) -> {
                if (e == null) {
                    if (changeUsage) {
                        conn.decUsage();
                    }
                    addConnection(conn);
                }

                releaseConnection();

                if (e != null) {
                    for (RedisConnection connection : getAllConnections()) {
                        if (!connection.isClosed()) {
                            connection.closeAsync();
                        }
                    }
                    getAllConnections().clear();

                    int totalInitializedConnections = index - 1;
                    String errorMsg;
                    if (totalInitializedConnections == 0) {
                        errorMsg = "Unable to connect to Redis server: " + client.getAddr();
                    } else {
                        errorMsg = "Unable to init enough connections amount! Only " + totalInitializedConnections
                                + " of " + minimumIdleSize + " were initialized. Redis server: " + client.getAddr();
                    }
                    Exception cause = new RedisConnectionException(errorMsg, e);
                    throw new CompletionException(cause);
                }
                return null;
            });
        });
    }

    private void createConnection(CompletableFuture<T> promise) {
        CompletionStage<T> connFuture = connectionCallback.apply(client);
        connFuture.whenComplete((conn, e) -> {
            if (e != null) {
                releaseConnection();

                promise.completeExceptionally(e);
                return;
            }

            log.debug("new connection created: {}", conn);

            allConnections.add(conn);

            if (changeUsage) {
                promise.thenApply(c -> c.incUsage());
            }
            connectedSuccessful(promise, conn);
        });
    }

    private void connectedSuccessful(CompletableFuture<T> promise, T conn) {
        if (!promise.complete(conn)) {
            releaseConnection(conn);
            releaseConnection();
        }
    }

    public CompletableFuture<T> acquireConnection(RedisCommand<?> command) {
        CompletableFuture<T> result = new CompletableFuture<>();

        CompletableFuture<Void> f = acquireConnection();
        f.thenAccept(r -> {
            connectTo(result, command);
        });
        result.whenComplete((r, e) -> {
            if (e != null) {
                f.completeExceptionally(e);
            }
        });
        return result;
    }

    private void connectTo(CompletableFuture<T> promise, RedisCommand<?> command) {
        if (promise.isDone()) {
            serviceManager.getGroup().submit(() -> {
                releaseConnection();
            });
            return;
        }

        T conn = pollConnection(command);
        if (conn != null) {
            connectedSuccessful(promise, conn);
            return;
        }

        createConnection(promise);
    }

    @Override
    public String toString() {
        return "ConnectionsHolder{" +
                "allConnections=" + allConnections.size() +
                ", freeConnections=" + freeConnections.size() +
                ", freeConnectionsCounter=" + freeConnectionsCounter +
                '}';
    }

    public void releaseConnection(ClientConnectionsEntry entry, T connection) {
        if (entry.isFreezed()) {
            connection.closeAsync();
            getAllConnections().remove(connection);
        } else {
            releaseConnection(connection);
        }
        serviceManager.getGroup().submit(() -> releaseConnection());
    }

}

