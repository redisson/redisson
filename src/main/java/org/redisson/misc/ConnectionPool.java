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
package org.redisson.misc;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.redisson.MasterSlaveServersConfig;
import org.redisson.client.RedisConnection;
import org.redisson.client.RedisConnectionException;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.connection.ClientConnectionsEntry;
import org.redisson.connection.ClientConnectionsEntry.FreezeReason;
import org.redisson.connection.ClientConnectionsEntry.NodeType;
import org.redisson.connection.ConnectionManager;
import org.redisson.connection.MasterSlaveEntry;

import io.netty.util.Timeout;
import io.netty.util.TimerTask;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;

public class ConnectionPool<T extends RedisConnection> {

    protected final List<ClientConnectionsEntry> entries = new CopyOnWriteArrayList<ClientConnectionsEntry>();

    final ConnectionManager connectionManager;

    final MasterSlaveServersConfig config;

    final MasterSlaveEntry masterSlaveEntry;

    public ConnectionPool(MasterSlaveServersConfig config, ConnectionManager connectionManager, MasterSlaveEntry masterSlaveEntry) {
        this.config = config;
        this.masterSlaveEntry = masterSlaveEntry;
        this.connectionManager = connectionManager;
    }

    public Future<Void> add(final ClientConnectionsEntry entry) {
        final Promise<Void> promise = connectionManager.newPromise();
        initConnections(entry, new Runnable() {
            @Override
            public void run() {
                entries.add(entry);
                promise.setSuccess(null);
            }
        }, true);
        return promise;
    }

    private void initConnections(final ClientConnectionsEntry entry, final Runnable runnable, boolean checkFreezed) {
        int minimumIdleSize = getMinimumIdleSize(entry);

        if (minimumIdleSize == 0 || (checkFreezed && entry.isFreezed())) {
            runnable.run();
            return;
        }

        final AtomicInteger completedConnections = new AtomicInteger(minimumIdleSize);
        for (int i = 0; i < minimumIdleSize; i++) {
            if ((checkFreezed && entry.isFreezed()) || !tryAcquireConnection(entry)) {
                if (completedConnections.decrementAndGet() == 0) {
                    runnable.run();
                }
                continue;
            }

            Promise<T> promise = connectionManager.newPromise();
            connect(entry, promise);
            promise.addListener(new FutureListener<T>() {
                @Override
                public void operationComplete(Future<T> future) throws Exception {
                    if (future.isSuccess()) {
                        T conn = future.getNow();
                        releaseConnection(entry, conn);
                    }
                    releaseConnection(entry);

                    if (completedConnections.decrementAndGet() == 0) {
                        runnable.run();
                    }
                }
            });
        }
    }

    protected int getMinimumIdleSize(ClientConnectionsEntry entry) {
        return config.getSlaveConnectionMinimumIdleSize();
    }

    protected ClientConnectionsEntry getEntry() {
        return config.getLoadBalancer().getEntry(entries);
    }

    public Future<T> get() {
        for (int j = entries.size() - 1; j >= 0; j--) {
            ClientConnectionsEntry entry = getEntry();
            if (!entry.isFreezed() && tryAcquireConnection(entry)) {
                Promise<T> promise = connectionManager.newPromise();
                connect(entry, promise);
                return promise;
            }
        }

        RedisConnectionException exception = new RedisConnectionException(
                "Can't aquire connection from pool!");
        return connectionManager.newFailedFuture(exception);
    }

    public Future<T> get(ClientConnectionsEntry entry) {
        if (((entry.getNodeType() == NodeType.MASTER && entry.getFreezeReason() == FreezeReason.SYSTEM) || !entry.isFreezed())
                && tryAcquireConnection(entry)) {
            Promise<T> promise = connectionManager.newPromise();
            connect(entry, promise);
            return promise;
        }

        RedisConnectionException exception = new RedisConnectionException(
                "Can't aquire connection to " + entry.getClient().getAddr());
        return connectionManager.newFailedFuture(exception);
    }

    protected boolean tryAcquireConnection(ClientConnectionsEntry entry) {
        return entry.getFailedAttempts() < config.getFailedAttempts() && entry.tryAcquireConnection();
    }

    protected T poll(ClientConnectionsEntry entry) {
        return (T) entry.pollConnection();
    }

    protected Future<T> connect(ClientConnectionsEntry entry) {
        return (Future<T>) entry.connect(config);
    }

    private void connect(final ClientConnectionsEntry entry, final Promise<T> promise) {
        T conn = poll(entry);
        if (conn != null) {
            if (!conn.isActive()) {
                promiseFailure(entry, promise, conn);
                return;
            }

            promiseSuccessful(entry, promise, conn);
            return;
        }

        Future<T> connFuture = connect(entry);
        connFuture.addListener(new FutureListener<T>() {
            @Override
            public void operationComplete(Future<T> future) throws Exception {
                if (!future.isSuccess()) {
                    releaseConnection(entry);

                    promiseFailure(entry, promise, future.cause());
                    return;
                }

                T conn = future.getNow();
                if (!conn.isActive()) {
                    promiseFailure(entry, promise, conn);
                    return;
                }

                promiseSuccessful(entry, promise, conn);
            }
        });
    }

    private void promiseSuccessful(final ClientConnectionsEntry entry, final Promise<T> promise, T conn) {
        entry.resetFailedAttempts();
        if (!promise.trySuccess(conn)) {
            releaseConnection(entry, conn);
            releaseConnection(entry);
        }
    }

    private void promiseFailure(ClientConnectionsEntry entry, Promise<T> promise, Throwable cause) {
        if (entry.incFailedAttempts() == config.getFailedAttempts()) {
            if (entry.getNodeType() == NodeType.SLAVE) {
                connectionManager.slaveDown(masterSlaveEntry, entry.getClient().getAddr().getHostName(),
                        entry.getClient().getAddr().getPort(), FreezeReason.RECONNECT);
                scheduleCheck(entry);
            } else {
                freezeMaster(entry);
            }
        }

        promise.tryFailure(cause);
    }

    private void freezeMaster(ClientConnectionsEntry entry) {
        if (entry.freezeMaster(FreezeReason.RECONNECT)) {
            scheduleCheck(entry);
        }
    }


    private void promiseFailure(ClientConnectionsEntry entry, Promise<T> promise, T conn) {
        int attempts = entry.incFailedAttempts();
        if (attempts == config.getFailedAttempts()) {
            if (entry.getNodeType() == NodeType.SLAVE) {
                connectionManager.slaveDown(masterSlaveEntry, entry.getClient().getAddr().getHostName(),
                        entry.getClient().getAddr().getPort(), FreezeReason.RECONNECT);
                scheduleCheck(entry);
            } else {
                freezeMaster(entry);
            }
        } else if (attempts < config.getFailedAttempts()) {
            releaseConnection(entry, conn);
        }

        releaseConnection(entry);

        RedisConnectionException cause = new RedisConnectionException(conn + " is not active!");
        promise.tryFailure(cause);
    }

    private void scheduleCheck(final ClientConnectionsEntry entry) {
        connectionManager.newTimeout(new TimerTask() {
            @Override
            public void run(Timeout timeout) throws Exception {
                if (entry.getFreezeReason() != FreezeReason.RECONNECT
                        || !entry.isFreezed()) {
                    return;
                }

                Future<RedisConnection> connectionFuture = entry.getClient().connectAsync();
                connectionFuture.addListener(new FutureListener<RedisConnection>() {
                    @Override
                    public void operationComplete(Future<RedisConnection> future) throws Exception {
                        if (entry.getFreezeReason() != FreezeReason.RECONNECT
                                || !entry.isFreezed()) {
                            return;
                        }

                        if (!future.isSuccess()) {
                            scheduleCheck(entry);
                            return;
                        }
                        final RedisConnection c = future.getNow();
                        if (!c.isActive()) {
                            c.closeAsync();
                            scheduleCheck(entry);
                            return;
                        }

                        Future<String> f = c.asyncWithTimeout(null, RedisCommands.PING);
                        f.addListener(new FutureListener<String>() {
                            @Override
                            public void operationComplete(Future<String> future) throws Exception {
                                try {
                                    if (entry.getFreezeReason() != FreezeReason.RECONNECT
                                            || !entry.isFreezed()) {
                                        return;
                                    }

                                    if (future.isSuccess() && "PONG".equals(future.getNow())) {
                                        entry.resetFailedAttempts();
                                        initConnections(entry, new Runnable() {
                                            @Override
                                            public void run() {
                                                if (entry.getNodeType() == NodeType.SLAVE) {
                                                    masterSlaveEntry.slaveUp(entry.getClient().getAddr().getHostName(), entry.getClient().getAddr().getPort(), FreezeReason.RECONNECT);
                                                } else {
                                                    synchronized (entry) {
                                                        if (entry.getFreezeReason() == FreezeReason.RECONNECT) {
                                                            entry.setFreezed(false);
                                                            entry.setFreezeReason(null);
                                                        }
                                                    }
                                                }
                                            }
                                        }, false);

                                    } else {
                                        scheduleCheck(entry);
                                    }
                                } finally {
                                    c.closeAsync();
                                }
                            }
                        });
                    }
                });
            }
        }, config.getReconnectionTimeout(), TimeUnit.MILLISECONDS);
    }

    public void returnConnection(ClientConnectionsEntry entry, T connection) {
        if (entry.isFreezed()) {
            connection.closeAsync();
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
