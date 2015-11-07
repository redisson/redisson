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
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.redisson.MasterSlaveServersConfig;
import org.redisson.client.RedisConnection;
import org.redisson.client.RedisConnectionException;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.connection.ConnectionEntry.FreezeReason;
import org.redisson.connection.ConnectionEntry.Mode;
import org.redisson.connection.ConnectionManager;
import org.redisson.connection.LoadBalancer;
import org.redisson.connection.MasterSlaveEntry;
import org.redisson.connection.SubscribesConnectionEntry;

import io.netty.util.Timeout;
import io.netty.util.TimerTask;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;

public class ConnectionPool<T extends RedisConnection> {

    final List<SubscribesConnectionEntry> entries = new CopyOnWriteArrayList<SubscribesConnectionEntry>();

    final ConcurrentLinkedQueue<Promise<T>> promises = new ConcurrentLinkedQueue<Promise<T>>();

    final ConnectionManager connectionManager;

    final MasterSlaveServersConfig config;

    final LoadBalancer loadBalancer;

    final MasterSlaveEntry masterSlaveEntry;

    public ConnectionPool(MasterSlaveServersConfig config, LoadBalancer loadBalancer,
            ConnectionManager connectionManager, MasterSlaveEntry masterSlaveEntry) {
        this.config = config;
        this.loadBalancer = loadBalancer;
        this.masterSlaveEntry = masterSlaveEntry;
        this.connectionManager = connectionManager;
    }

    public void add(SubscribesConnectionEntry entry) {
        entries.add(entry);
        handleQueue(entry);
    }

    public void remove(SubscribesConnectionEntry entry) {
        entries.remove(entry);
    }

    public Future<T> get() {
        for (int j = entries.size() - 1; j >= 0; j--) {
            SubscribesConnectionEntry entry;
            if (ConnectionPool.this.loadBalancer != null) {
                entry = ConnectionPool.this.loadBalancer.getEntry(entries);
            } else {
                entry = entries.get(0);
            }
            if (!entry.isFreezed() && tryAcquireConnection(entry)) {
                Promise<T> promise = connectionManager.newPromise();
                connect(entry, promise);
                return promise;
            }
        }

        Promise<T> promise = connectionManager.newPromise();
        promises.add(promise);
        return promise;
    }

    public Future<T> get(SubscribesConnectionEntry entry) {
        if (!entry.isFreezed() && tryAcquireConnection(entry)) {
            Promise<T> promise = connectionManager.newPromise();
            connect(entry, promise);
            return promise;
        }

        RedisConnectionException exception = new RedisConnectionException(
                "Can't aquire connection to " + entry.getClient().getAddr());
        return connectionManager.newFailedFuture(exception);
    }

    protected boolean tryAcquireConnection(SubscribesConnectionEntry entry) {
        return entry.tryAcquireConnection();
    }

    protected T poll(SubscribesConnectionEntry entry) {
        return (T) entry.pollConnection();
    }

    protected Future<T> connect(SubscribesConnectionEntry entry) {
        return (Future<T>) entry.connect(config);
    }

    private void connect(final SubscribesConnectionEntry entry, final Promise<T> promise) {
        T conn = poll(entry);
        if (conn != null) {
            if (!conn.isActive()) {
                releaseConnection(entry, conn);
                releaseConnection(entry);

                RedisConnectionException cause = new RedisConnectionException(conn + " is not active!");
                promiseFailure(entry, promise, cause);
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
                    releaseConnection(entry, conn);
                    releaseConnection(entry);

                    RedisConnectionException cause = new RedisConnectionException(conn + " is not active!");
                    promiseFailure(entry, promise, cause);
                    return;
                }

                promiseSuccessful(entry, promise, conn);
            }
        });
    }

    private void promiseSuccessful(final SubscribesConnectionEntry entry, final Promise<T> promise, T conn) {
        entry.resetFailedAttempts();
        if (!promise.trySuccess(conn)) {
            releaseConnection(entry, conn);
            releaseConnection(entry);
        }
    }

    private void promiseFailure(SubscribesConnectionEntry entry, Promise<T> promise, Throwable cause) {
        if (entry.incFailedAttempts() == config.getSlaveFailedAttempts()
                && entry.getServerMode() == Mode.SLAVE) {
            connectionManager.slaveDown(masterSlaveEntry, entry.getClient().getAddr().getHostName(),
                    entry.getClient().getAddr().getPort(), FreezeReason.RECONNECT);
            scheduleCheck(entry);
        }
        promise.setFailure(cause);
    }

    private void scheduleCheck(final SubscribesConnectionEntry entry) {
        connectionManager.getTimer().newTimeout(new TimerTask() {
            @Override
            public void run(Timeout timeout) throws Exception {
                if (entry.getFreezeReason() == FreezeReason.MANAGER
                        || !entry.isFreezed()) {
                    return;
                }

                Future<RedisConnection> connectionFuture = entry.getClient().connectAsync();
                connectionFuture.addListener(new FutureListener<RedisConnection>() {
                    @Override
                    public void operationComplete(Future<RedisConnection> future) throws Exception {
                        if (entry.getFreezeReason() == FreezeReason.MANAGER
                                || !entry.isFreezed()) {
                            return;
                        }

                        if (!future.isSuccess()) {
                            scheduleCheck(entry);
                            return;
                        }
                        final RedisConnection c = future.getNow();
                        if (c.isActive()) {
                            Future<String> f = c.asyncWithTimeout(null, RedisCommands.PING);
                            f.addListener(new FutureListener<String>() {
                                @Override
                                public void operationComplete(Future<String> future) throws Exception {
                                    try {
                                        if (entry.getFreezeReason() == FreezeReason.MANAGER
                                                || !entry.isFreezed()) {
                                            return;
                                        }

                                        if (future.isSuccess() && "PONG".equals(future.getNow())) {
                                            entry.resetFailedAttempts();
                                            masterSlaveEntry.slaveUp(entry.getClient().getAddr().getHostName(), entry.getClient().getAddr().getPort(), FreezeReason.RECONNECT);
                                        } else {
                                            scheduleCheck(entry);
                                        }
                                    } finally {
                                        c.closeAsync();
                                    }
                                }
                            });
                        } else {
                            c.closeAsync();
                            scheduleCheck(entry);
                        }
                    }
                });
            }
        }, config.getSlaveReconnectionTimeout(), TimeUnit.MILLISECONDS);
    }

    public void returnConnection(SubscribesConnectionEntry entry, T connection) {
        if (entry.isFreezed()) {
            connection.closeAsync();
        } else {
            if (connection.getFailAttempts() == config.getRefreshConnectionAfterFails()) {
                connection.forceReconnect();
            }
            releaseConnection(entry, connection);
        }
        releaseConnection(entry);
    }

    protected void releaseConnection(SubscribesConnectionEntry entry) {
        entry.releaseConnection();

        handleQueue(entry);
    }

    private void handleQueue(SubscribesConnectionEntry entry) {
        Promise<T> promise = promises.poll();
        if (promise != null) {
            if (!entry.isFreezed() && tryAcquireConnection(entry)) {
                connect(entry, promise);
            } else {
                promises.add(promise);
            }
        }
    }

    protected void releaseConnection(SubscribesConnectionEntry entry, T conn) {
        entry.releaseConnection(conn);
    }

}
