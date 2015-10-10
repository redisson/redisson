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

import org.redisson.MasterSlaveServersConfig;
import org.redisson.client.RedisConnection;
import org.redisson.client.RedisConnectionException;
import org.redisson.client.RedisException;
import org.redisson.connection.LoadBalancer;
import org.redisson.connection.SubscribesConnectionEntry;

import io.netty.channel.EventLoopGroup;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.OneTimeTask;

public class ConnectionPool<T extends RedisConnection> {

    final List<SubscribesConnectionEntry> entries = new CopyOnWriteArrayList<SubscribesConnectionEntry>();

    EventExecutor executor;

    MasterSlaveServersConfig config;

    LoadBalancer loadBalancer;

    public ConnectionPool(MasterSlaveServersConfig config, LoadBalancer loadBalancer, EventLoopGroup eventLoopGroup) {
        this.config = config;
        this.loadBalancer = loadBalancer;
        this.executor = eventLoopGroup.next();
    }

    public void add(SubscribesConnectionEntry entry) {
        entries.add(entry);
    }

    public void remove(SubscribesConnectionEntry entry) {
        entries.remove(entry);
    }

    public Future<T> get() {
        for (int j = entries.size()-1; j >= 0 ; j--) {
            SubscribesConnectionEntry entry;
            if (ConnectionPool.this.loadBalancer != null) {
                entry = ConnectionPool.this.loadBalancer.getEntry(entries);
            } else {
                entry = entries.get(0);
            }
            if (!entry.isFreezed() && tryAcquireConnection(entry)) {
                Promise<T> promise = executor.newPromise();
                connect(entry, promise);
                return promise;
            }
        }

        RedisConnectionException exception = new RedisConnectionException("Connection pool exhausted!");
        return executor.newFailedFuture(exception);
    }

    public Future<T> get(SubscribesConnectionEntry entry) {
        if (!entry.isFreezed() && tryAcquireConnection(entry)) {
            Promise<T> promise = executor.newPromise();
            connect(entry, promise);
            return promise;
        }

        RedisConnectionException exception = new RedisConnectionException("Can't aquire connection for " + entry.getClient());
        return executor.newFailedFuture(exception);
    }

    protected boolean tryAcquireConnection(SubscribesConnectionEntry entry) {
        return entry.tryAcquireConnection();
    }

    protected T poll(SubscribesConnectionEntry entry) {
        return (T) entry.pollConnection();
    }

    protected T connect(SubscribesConnectionEntry entry) {
        return (T) entry.connect(config);
    }

    private Future<T> connect(final SubscribesConnectionEntry entry, final Promise<T> promise) {
        T conn = poll(entry);
        if (conn != null) {
            if (!promise.trySuccess(conn)) {
                releaseConnection(entry, conn);
                releaseConnection(entry);
            }
        } else {
            executor.execute(new OneTimeTask() {
                @Override
                public void run() {
                    try {
                        T conn = connect(entry);
                        if (!promise.trySuccess(conn)) {
                            releaseConnection(entry, conn);
                            releaseConnection(entry);
                        }
                    } catch (RedisException e) {
                        releaseConnection(entry);
                        promise.setFailure(e);
                    }
                }
            });
        }
        return promise;
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
    }

    protected void releaseConnection(SubscribesConnectionEntry entry, T conn) {
        entry.releaseConnection(conn);
    }

}
