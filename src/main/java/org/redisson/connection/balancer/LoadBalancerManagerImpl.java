/**
 * Copyright 2016 Nikita Koksharov
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
package org.redisson.connection.balancer;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.redisson.MasterSlaveServersConfig;
import org.redisson.client.RedisConnection;
import org.redisson.client.RedisConnectionException;
import org.redisson.client.RedisPubSubConnection;
import org.redisson.connection.ClientConnectionsEntry;
import org.redisson.connection.ClientConnectionsEntry.FreezeReason;
import org.redisson.connection.ConnectionManager;
import org.redisson.connection.MasterSlaveEntry;
import org.redisson.connection.pool.PubSubConnectionPool;
import org.redisson.connection.pool.SlaveConnectionPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.PlatformDependent;

public class LoadBalancerManagerImpl implements LoadBalancerManager {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final ConnectionManager connectionManager;
    private final Map<InetSocketAddress, ClientConnectionsEntry> addr2Entry = PlatformDependent.newConcurrentHashMap();
    private final PubSubConnectionPool pubSubConnectionPool;
    private final SlaveConnectionPool slaveConnectionPool;

    public LoadBalancerManagerImpl(MasterSlaveServersConfig config, ConnectionManager connectionManager, MasterSlaveEntry entry) {
        this.connectionManager = connectionManager;
        slaveConnectionPool = new SlaveConnectionPool(config, connectionManager, entry);
        pubSubConnectionPool = new PubSubConnectionPool(config, connectionManager, entry);
    }

    public Future<Void> add(final ClientConnectionsEntry entry) {
        final Promise<Void> result = connectionManager.newPromise();
        FutureListener<Void> listener = new FutureListener<Void>() {
            AtomicInteger counter = new AtomicInteger(2);
            @Override
            public void operationComplete(Future<Void> future) throws Exception {
                if (!future.isSuccess()) {
                    result.tryFailure(future.cause());
                    return;
                }
                if (counter.decrementAndGet() == 0) {
                    addr2Entry.put(entry.getClient().getAddr(), entry);
                    result.setSuccess(null);
                }
            }
        };

        Future<Void> slaveFuture = slaveConnectionPool.add(entry);
        slaveFuture.addListener(listener);
        Future<Void> pubSubFuture = pubSubConnectionPool.add(entry);
        pubSubFuture.addListener(listener);
        return result;
    }

    public int getAvailableClients() {
        int count = 0;
        for (ClientConnectionsEntry connectionEntry : addr2Entry.values()) {
            if (!connectionEntry.isFreezed()) {
                count++;
            }
        }
        return count;
    }

    public boolean unfreeze(String host, int port, FreezeReason freezeReason) {
        InetSocketAddress addr = new InetSocketAddress(host, port);
        ClientConnectionsEntry entry = addr2Entry.get(addr);
        if (entry == null) {
            throw new IllegalStateException("Can't find " + addr + " in slaves!");
        }

        synchronized (entry) {
            if (!entry.isFreezed()) {
                return false;
            }
            if ((freezeReason == FreezeReason.RECONNECT
                    && entry.getFreezeReason() == FreezeReason.RECONNECT)
                        || freezeReason != FreezeReason.RECONNECT) {
                entry.resetFailedAttempts();
                entry.setFreezed(false);
                entry.setFreezeReason(null);
                return true;
            }
        }
        return false;
    }
    
    public ClientConnectionsEntry freeze(String host, int port, FreezeReason freezeReason) {
        InetSocketAddress addr = new InetSocketAddress(host, port);
        ClientConnectionsEntry connectionEntry = addr2Entry.get(addr);
        return freeze(connectionEntry, freezeReason);
    }

    public ClientConnectionsEntry freeze(ClientConnectionsEntry connectionEntry, FreezeReason freezeReason) {
        if (connectionEntry == null) {
            return null;
        }

        synchronized (connectionEntry) {
            // only RECONNECT freeze reason could be replaced
            if (connectionEntry.getFreezeReason() == null
                    || connectionEntry.getFreezeReason() == FreezeReason.RECONNECT) {
                connectionEntry.setFreezed(true);
                connectionEntry.setFreezeReason(freezeReason);
                return connectionEntry;
            }
            if (connectionEntry.isFreezed()) {
                return null;
            }
        }

        return connectionEntry;
    }

    public Future<RedisPubSubConnection> nextPubSubConnection() {
        return pubSubConnectionPool.get();
    }

    public Future<RedisConnection> getConnection(InetSocketAddress addr) {
        ClientConnectionsEntry entry = addr2Entry.get(addr);
        if (entry != null) {
            return slaveConnectionPool.get(entry);
        }
        RedisConnectionException exception = new RedisConnectionException("Can't find entry for " + addr);
        return connectionManager.newFailedFuture(exception);
    }

    public Future<RedisConnection> nextConnection() {
        return slaveConnectionPool.get();
    }

    public void returnPubSubConnection(RedisPubSubConnection connection) {
        ClientConnectionsEntry entry = addr2Entry.get(connection.getRedisClient().getAddr());
        pubSubConnectionPool.returnConnection(entry, connection);
    }

    public void returnConnection(RedisConnection connection) {
        ClientConnectionsEntry entry = addr2Entry.get(connection.getRedisClient().getAddr());
        slaveConnectionPool.returnConnection(entry, connection);
    }

    public void shutdown() {
        for (ClientConnectionsEntry entry : addr2Entry.values()) {
            entry.getClient().shutdown();
        }
    }

    public void shutdownAsync() {
        for (ClientConnectionsEntry entry : addr2Entry.values()) {
            connectionManager.shutdownAsync(entry.getClient());
        }
    }

}
