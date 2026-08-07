/**
 * Copyright (c) 2013-2026 Nikita Koksharov
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

import java.util.Deque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Function;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class ConnectionsHolder<T extends RedisConnection> {

    final Logger log = LoggerFactory.getLogger(getClass());

    private final Queue<T> allConnections = new ConcurrentLinkedQueue<>();
    private final Deque<T> freeConnections = new ConcurrentLinkedDeque<>();
    private final Object warmUpLock = new Object();
    private final Set<CompletableFuture<Void>> warmUpFutures = new HashSet<>();
    private final Set<CompletableFuture<Void>> warmUpPermits = new HashSet<>();
    private CompletableFuture<Void> lastWarmUp = CompletableFuture.completedFuture(null);
    private long warmUpGeneration;
    private boolean warmUpEnabled = true;
    private final AsyncSemaphore freeConnectionsCounter;
    private final int poolMaxSize;

    private final RedisClient client;

    private final Function<RedisClient, CompletionStage<T>> connectionCallback;

    private final ServiceManager serviceManager;

    private final boolean changeUsage;

    public ConnectionsHolder(RedisClient client, int poolMaxSize,
                             Function<RedisClient, CompletionStage<T>> connectionCallback,
                             ServiceManager serviceManager, boolean changeUsage) {
        this.freeConnectionsCounter = new AsyncSemaphore(poolMaxSize, serviceManager.getGroup());
        this.poolMaxSize = poolMaxSize;
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
        int size = freeConnections.size();
        for (int i = 0; i < size; i++) {
            T conn = freeConnections.poll();
            if (conn == null) {
                return null;
            }
            if (conn.isActive()) {
                if (i > 0) {
                    log.debug("skipped connections with inactive channel: {}", i);
                }
                if (changeUsage) {
                    conn.incUsage();
                }
                return conn;
            }

            freeConnections.addLast(conn);
        }
        return null;
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
        if (connection.isActive()) {
            freeConnections.addFirst(connection);
        } else {
            freeConnections.addLast(connection);
        }
        if (changeUsage) {
            connection.decUsage();
        }
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

    public CompletableFuture<Void> warmUp(int connectionAmount) {
        if (connectionAmount < 0) {
            return failedFuture(new IllegalArgumentException("connectionAmount can't be negative"));
        }
        if (connectionAmount > poolMaxSize) {
            return failedFuture(new IllegalArgumentException(
                    "connectionAmount can't be greater than connection pool size"));
        }
        CompletableFuture<Void> warmUpFuture;
        synchronized (warmUpLock) {
            if (!warmUpEnabled) {
                return failedWarmUpFuture();
            }
            long generation = warmUpGeneration;
            lastWarmUp = lastWarmUp.handle((r, e) -> null)
                                  .thenCompose(r -> warmUpConnection(connectionAmount, generation));
            warmUpFuture = lastWarmUp;
            warmUpFutures.add(warmUpFuture);
        }
        warmUpFuture.whenComplete((r, e) -> removeWarmUpFuture(warmUpFuture));
        return warmUpFuture.thenApply(r -> null);
    }

    private CompletableFuture<Void> failedFuture(Exception e) {
        CompletableFuture<Void> f = new CompletableFuture<>();
        f.completeExceptionally(e);
        return f;
    }

    private CompletableFuture<Void> failedWarmUpFuture() {
        return failedFuture(new RedisConnectionException("Redis node isn't available: " + client));
    }

    private void removeWarmUpFuture(CompletableFuture<Void> future) {
        synchronized (warmUpLock) {
            warmUpFutures.remove(future);
        }
    }

    private CompletableFuture<Void> warmUpConnection(int connectionAmount, long generation) {
        synchronized (warmUpLock) {
            if (!isWarmUpValid(generation)) {
                return failedWarmUpFuture();
            }
            if (allConnections.size() >= connectionAmount) {
                return CompletableFuture.completedFuture(null);
            }
        }

        return createWarmUpConnection(connectionAmount, generation)
                .thenCompose(r -> warmUpConnection(connectionAmount, generation));
    }

    private CompletableFuture<Void> createWarmUpConnection(int connectionAmount, long generation) {
        CompletableFuture<Void> f;
        synchronized (warmUpLock) {
            if (!isWarmUpValid(generation)) {
                return failedWarmUpFuture();
            }
            f = acquireConnection();
            warmUpPermits.add(f);
        }
        f.whenComplete((r, e) -> removeWarmUpPermit(f));
        return f.thenCompose(r -> {
            synchronized (warmUpLock) {
                if (!isWarmUpValid(generation)) {
                    releaseConnection();
                    return failedWarmUpFuture();
                }
                if (allConnections.size() >= connectionAmount) {
                    releaseConnection();
                    return CompletableFuture.completedFuture(null);
                }
            }

            CompletableFuture<T> promise = new CompletableFuture<>();
            createConnection(promise);
            return promise.handle((conn, e) -> {
                if (e != null) {
                    throw new CompletionException(e);
                }

                synchronized (warmUpLock) {
                    if (!isWarmUpValid(generation)) {
                        allConnections.remove(conn);
                        conn.closeAsync();
                        releaseConnection();
                        throw new CompletionException(new RedisConnectionException(
                                "Redis node isn't available: " + client));
                    }
                    if (changeUsage) {
                        conn.decUsage();
                    }
                    addConnection(conn);
                    releaseConnection();
                }
                return null;
            });
        });
    }

    private void removeWarmUpPermit(CompletableFuture<Void> future) {
        synchronized (warmUpLock) {
            warmUpPermits.remove(future);
        }
    }

    private boolean isWarmUpValid(long generation) {
        return warmUpEnabled && warmUpGeneration == generation;
    }

    void failWarmUp(Throwable cause) {
        Set<CompletableFuture<Void>> futures;
        Set<CompletableFuture<Void>> permits;
        synchronized (warmUpLock) {
            warmUpEnabled = false;
            warmUpGeneration++;
            futures = new HashSet<>(warmUpFutures);
            permits = new HashSet<>(warmUpPermits);
            warmUpFutures.clear();
            warmUpPermits.clear();
            lastWarmUp = CompletableFuture.completedFuture(null);
        }

        futures.forEach(f -> f.completeExceptionally(cause));
        permits.forEach(f -> f.cancel(false));
    }

    void enableWarmUp() {
        synchronized (warmUpLock) {
            warmUpEnabled = true;
        }
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
                    // release only on success; the failure path already releases in createConnection(promise),
                    // so an unconditional release here double-releases per failed init and lifts the counter above pool max
                    releaseConnection();
                }

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
            releaseConnection();
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
        releaseConnection();
    }

    public ServiceManager getServiceManager() {
        return serviceManager;
    }
}
