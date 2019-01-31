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
package org.redisson.connection.pool;

import java.net.InetSocketAddress;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
import org.redisson.misc.RPromise;
import org.redisson.misc.RedissonPromise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.util.Timeout;
import io.netty.util.TimerTask;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;

/**
 * Base connection pool class 
 * 
 * @author Nikita Koksharov
 *
 * @param <T> - connection type
 */
abstract class ConnectionPool<T extends RedisConnection> {

    private final Logger log = LoggerFactory.getLogger(getClass());

    protected final List<ClientConnectionsEntry> entries = new CopyOnWriteArrayList<ClientConnectionsEntry>();

    final ConnectionManager connectionManager;

    final MasterSlaveServersConfig config;

    final MasterSlaveEntry masterSlaveEntry;

    public ConnectionPool(MasterSlaveServersConfig config, ConnectionManager connectionManager, MasterSlaveEntry masterSlaveEntry) {
        this.config = config;
        this.masterSlaveEntry = masterSlaveEntry;
        this.connectionManager = connectionManager;
    }

    public RFuture<Void> add(final ClientConnectionsEntry entry) {
        final RPromise<Void> promise = new RedissonPromise<Void>();
        promise.addListener(new FutureListener<Void>() {
            @Override
            public void operationComplete(Future<Void> future) throws Exception {
                if (future.isSuccess()) {
                    entries.add(entry);
                }
            }
        });
        initConnections(entry, promise, true);
        return promise;
    }

    public RPromise<Void> initConnections(ClientConnectionsEntry entry) {
        RPromise<Void> promise = new RedissonPromise<Void>();
        initConnections(entry, promise, false);
        return promise;
    }
    
    private void initConnections(final ClientConnectionsEntry entry, final RPromise<Void> initPromise, boolean checkFreezed) {
        final int minimumIdleSize = getMinimumIdleSize(entry);

        if (minimumIdleSize == 0 || (checkFreezed && entry.isFreezed())) {
            initPromise.trySuccess(null);
            return;
        }

        final AtomicInteger initializedConnections = new AtomicInteger(minimumIdleSize);
        int startAmount = Math.min(50, minimumIdleSize);
        final AtomicInteger requests = new AtomicInteger(startAmount);
        for (int i = 0; i < startAmount; i++) {
            createConnection(checkFreezed, requests, entry, initPromise, minimumIdleSize, initializedConnections);
        }
    }

    private void createConnection(final boolean checkFreezed, final AtomicInteger requests, final ClientConnectionsEntry entry, final RPromise<Void> initPromise,
            final int minimumIdleSize, final AtomicInteger initializedConnections) {

        if ((checkFreezed && entry.isFreezed()) || !tryAcquireConnection(entry)) {
            int totalInitializedConnections = minimumIdleSize - initializedConnections.get();
            Throwable cause = new RedisConnectionException(
                    "Unable to init enough connections amount! Only " + totalInitializedConnections + " from " + minimumIdleSize + " were initialized. Server: "
                                        + entry.getClient().getAddr());
            initPromise.tryFailure(cause);
            return;
        }
        
        acquireConnection(entry, new Runnable() {
            
            @Override
            public void run() {
                RPromise<T> promise = new RedissonPromise<T>();
                createConnection(entry, promise);
                promise.addListener(new FutureListener<T>() {
                    @Override
                    public void operationComplete(Future<T> future) throws Exception {
                        if (future.isSuccess()) {
                            T conn = future.getNow();
                            if (!initPromise.isDone()) {
                                releaseConnection(entry, conn);
                            } else {
                                conn.closeAsync();
                            }
                        }

                        releaseConnection(entry);

                        if (!future.isSuccess()) {
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
                                        + " from " + minimumIdleSize + " were initialized. Redis server: " + entry.getClient().getAddr();
                            }
                            Throwable cause = new RedisConnectionException(errorMsg, future.cause());
                            initPromise.tryFailure(cause);
                            return;
                        }

                        int value = initializedConnections.decrementAndGet();
                        if (value == 0) {
                            if (initPromise.trySuccess(null)) {
                                log.info("{} connections initialized for {}", minimumIdleSize, entry.getClient().getAddr());
                            }
                        } else if (value > 0 && !initPromise.isDone()) {
                            if (requests.incrementAndGet() <= minimumIdleSize) {
                                createConnection(checkFreezed, requests, entry, initPromise, minimumIdleSize, initializedConnections);
                            }
                        }
                    }
                });
            }
        });

    }

    protected void acquireConnection(ClientConnectionsEntry entry, Runnable runnable) {
        entry.acquireConnection(runnable);
    }

    protected abstract int getMinimumIdleSize(ClientConnectionsEntry entry);

    public RFuture<T> get(RedisCommand<?> command) {
        List<ClientConnectionsEntry> entriesCopy = new LinkedList<ClientConnectionsEntry>(entries);
        for (Iterator<ClientConnectionsEntry> iterator = entriesCopy.iterator(); iterator.hasNext();) {
            ClientConnectionsEntry entry = iterator.next();
            if (!((!entry.isFreezed() || entry.isMasterForRead()) && 
                    tryAcquireConnection(entry))) {
                iterator.remove();
            }
        }
        while (!entriesCopy.isEmpty()) {
            ClientConnectionsEntry entry = config.getLoadBalancer().getEntry(entriesCopy);
            return acquireConnection(command, entry);
        }
        
        List<InetSocketAddress> failed = new LinkedList<InetSocketAddress>();
        List<InetSocketAddress> freezed = new LinkedList<InetSocketAddress>();
        for (ClientConnectionsEntry entry : entries) {
            if (entry.isFailed()) {
                failed.add(entry.getClient().getAddr());
            } else if (entry.isFreezed()) {
                freezed.add(entry.getClient().getAddr());
            }
        }

        StringBuilder errorMsg = new StringBuilder(getClass().getSimpleName() + " no available Redis entries. ");
        if (!freezed.isEmpty()) {
            errorMsg.append(" Disconnected hosts: " + freezed);
        }
        if (!failed.isEmpty()) {
            errorMsg.append(" Hosts disconnected due to errors during `failedSlaveCheckInterval`: " + failed);
        }

        RedisConnectionException exception = new RedisConnectionException(errorMsg.toString());
        return RedissonPromise.newFailedFuture(exception);
    }

    public RFuture<T> get(RedisCommand<?> command, ClientConnectionsEntry entry) {
            return acquireConnection(command, entry);
        }

    public static abstract class AcquireCallback<T> implements Runnable, FutureListener<T> {
        
    }
    
    protected final RFuture<T> acquireConnection(RedisCommand<?> command, final ClientConnectionsEntry entry) {
        final RPromise<T> result = new RedissonPromise<T>();

            AcquireCallback<T> callback = new AcquireCallback<T>() {
                    @Override
                    public void run() {
                        result.removeListener(this);
                        connectTo(entry, result);
                    }
                
                @Override
                public void operationComplete(Future<T> future) throws Exception {
                    entry.removeConnection(this);
                }
            };
            
            result.addListener(callback);
            acquireConnection(entry, callback);
        
            return result;
        }
        
    protected boolean tryAcquireConnection(ClientConnectionsEntry entry) {
        if (entry.getNodeType() == NodeType.SLAVE && entry.isFailed()) {
            checkForReconnect(entry, null);
            return false;
        }
        return true;
    }

    protected T poll(ClientConnectionsEntry entry) {
        return (T) entry.pollConnection();
    }

    protected RFuture<T> connect(ClientConnectionsEntry entry) {
        return (RFuture<T>) entry.connect();
    }

    private void connectTo(ClientConnectionsEntry entry, RPromise<T> promise) {
        if (promise.isDone()) {
            releaseConnection(entry);
            return;
        }
        T conn = poll(entry);
        if (conn != null) {
            if (!conn.isActive() && entry.getNodeType() == NodeType.SLAVE) {
                entry.trySetupFistFail();
            }

            connectedSuccessful(entry, promise, conn);
            return;
        }

        createConnection(entry, promise);
    }

    private void createConnection(final ClientConnectionsEntry entry, final RPromise<T> promise) {
        RFuture<T> connFuture = connect(entry);
        connFuture.addListener(new FutureListener<T>() {
            @Override
            public void operationComplete(Future<T> future) throws Exception {
                if (!future.isSuccess()) {
                    promiseFailure(entry, promise, future.cause());
                    return;
                }

                T conn = future.getNow();
                if (!conn.isActive()) {
                    promiseFailure(entry, promise, conn);
                    return;
                }

                connectedSuccessful(entry, promise, conn);
            }
        });
    }

    private void connectedSuccessful(ClientConnectionsEntry entry, RPromise<T> promise, T conn) {
        if (conn.isActive() && entry.getNodeType() == NodeType.SLAVE) {
            entry.resetFirstFail();
        }

        if (!promise.trySuccess(conn)) {
            releaseConnection(entry, conn);
            releaseConnection(entry);
        }
    }

    private void promiseFailure(ClientConnectionsEntry entry, RPromise<T> promise, Throwable cause) {
        if (entry.getNodeType() == NodeType.SLAVE) {
            entry.trySetupFistFail();
            if (entry.isFailed()) {
            checkForReconnect(entry, cause);
        }
        }

        releaseConnection(entry);

        promise.tryFailure(cause);
    }

    private void promiseFailure(ClientConnectionsEntry entry, RPromise<T> promise, T conn) {
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
        promise.tryFailure(cause);
    }

    private void checkForReconnect(ClientConnectionsEntry entry, Throwable cause) {
        if (masterSlaveEntry.slaveDown(entry, FreezeReason.RECONNECT)) {
            log.error("slave " + entry.getClient().getAddr() + " has been disconnected after " 
                        + config.getFailedSlaveCheckInterval() + " ms interval since moment of the first failed connection", cause);
            scheduleCheck(entry);
            }
        }

    private void scheduleCheck(final ClientConnectionsEntry entry) {

        connectionManager.getConnectionEventsHub().fireDisconnect(entry.getClient().getAddr());

        connectionManager.newTimeout(new TimerTask() {
            @Override
            public void run(Timeout timeout) throws Exception {
                synchronized (entry) {
                    if (entry.getFreezeReason() != FreezeReason.RECONNECT
                            || !entry.isFreezed()
                            || connectionManager.isShuttingDown()) {
                        return;
                    }
                }

                RFuture<RedisConnection> connectionFuture = entry.getClient().connectAsync();
                connectionFuture.addListener(new FutureListener<RedisConnection>() {
                    @Override
                    public void operationComplete(Future<RedisConnection> future) throws Exception {
                        synchronized (entry) {
                            if (entry.getFreezeReason() != FreezeReason.RECONNECT
                                    || !entry.isFreezed()) {
                                return;
                            }
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

                        final FutureListener<String> pingListener = new FutureListener<String>() {
                            @Override
                            public void operationComplete(Future<String> future) throws Exception {
                                try {
                                    synchronized (entry) {
                                        if (entry.getFreezeReason() != FreezeReason.RECONNECT
                                                || !entry.isFreezed()) {
                                            return;
                                        }
                                    }

                                    if (future.isSuccess() && "PONG".equals(future.getNow())) {
                                        if (masterSlaveEntry.slaveUp(entry, FreezeReason.RECONNECT)) {
                                            log.info("slave {} has been successfully reconnected", entry.getClient().getAddr());
                                        }
                                    } else {
                                        scheduleCheck(entry);
                                    }
                                } finally {
                                    c.closeAsync();
                                }
                            }
                        };

                        if (entry.getConfig().getPassword() != null) {
                            RFuture<Void> temp = c.async(RedisCommands.AUTH, config.getPassword());

                            FutureListener<Void> listener = new FutureListener<Void>() {
                                @Override public void operationComplete(Future<Void> future)throws Exception {
                                    ping(c, pingListener);
                                }
                            };

                            temp.addListener(listener);
                        } else {
                            ping(c, pingListener);
                        }
                    }
                });
            }
        }, config.getFailedSlaveReconnectionInterval(), TimeUnit.MILLISECONDS);
    }

    private void ping(RedisConnection c, final FutureListener<String> pingListener) {
        RFuture<String> f = c.async(RedisCommands.PING);
        f.addListener(pingListener);
    }

    public void returnConnection(ClientConnectionsEntry entry, T connection) {
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
