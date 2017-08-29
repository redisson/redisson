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
import java.net.URI;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.redisson.api.NodeType;
import org.redisson.api.RFuture;
import org.redisson.client.RedisConnection;
import org.redisson.client.RedisConnectionException;
import org.redisson.client.RedisPubSubConnection;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.config.MasterSlaveServersConfig;
import org.redisson.config.ReadMode;
import org.redisson.connection.ClientConnectionsEntry;
import org.redisson.connection.ClientConnectionsEntry.FreezeReason;
import org.redisson.connection.ConnectionManager;
import org.redisson.connection.MasterSlaveEntry;
import org.redisson.connection.pool.PubSubConnectionPool;
import org.redisson.connection.pool.SlaveConnectionPool;
import org.redisson.misc.RPromise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.internal.PlatformDependent;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class LoadBalancerManager {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final ConnectionManager connectionManager;
    private final PubSubConnectionPool pubSubConnectionPool;
    private final SlaveConnectionPool slaveConnectionPool;
    
    private final Map<String, ClientConnectionsEntry> ip2Entry = PlatformDependent.newConcurrentHashMap();

    public LoadBalancerManager(MasterSlaveServersConfig config, ConnectionManager connectionManager, MasterSlaveEntry entry) {
        this.connectionManager = connectionManager;
        slaveConnectionPool = new SlaveConnectionPool(config, connectionManager, entry);
        pubSubConnectionPool = new PubSubConnectionPool(config, connectionManager, entry);
    }

    public void changeType(InetSocketAddress addr, NodeType nodeType) {
        ClientConnectionsEntry entry = ip2Entry.get(addr.getAddress().getHostAddress() + ":" + addr.getPort());
        changeType(addr, nodeType, entry);
    }

    protected void changeType(Object addr, NodeType nodeType, ClientConnectionsEntry entry) {
        if (entry != null) {
            if (connectionManager.isClusterMode()) {
                entry.getClient().getConfig().setReadOnly(nodeType == NodeType.SLAVE && connectionManager.getConfig().getReadMode() != ReadMode.MASTER);
            }
            entry.setNodeType(nodeType);
        }
    }
    
    public void changeType(URI address, NodeType nodeType) {
        ClientConnectionsEntry entry = getEntry(address);
        changeType(address, nodeType, entry);
    }
    
    public RFuture<Void> add(final ClientConnectionsEntry entry) {
        final RPromise<Void> result = connectionManager.newPromise();
        FutureListener<Void> listener = new FutureListener<Void>() {
            AtomicInteger counter = new AtomicInteger(2);
            @Override
            public void operationComplete(Future<Void> future) throws Exception {
                if (!future.isSuccess()) {
                    result.tryFailure(future.cause());
                    return;
                }
                if (counter.decrementAndGet() == 0) {
                    String addr = entry.getClient().getIpAddr();
                    ip2Entry.put(addr, entry);
                    result.trySuccess(null);
                }
            }
        };

        RFuture<Void> slaveFuture = slaveConnectionPool.add(entry);
        slaveFuture.addListener(listener);
        RFuture<Void> pubSubFuture = pubSubConnectionPool.add(entry);
        pubSubFuture.addListener(listener);
        return result;
    }

    public int getAvailableClients() {
        int count = 0;
        for (ClientConnectionsEntry connectionEntry : ip2Entry.values()) {
            if (!connectionEntry.isFreezed()) {
                count++;
            }
        }
        return count;
    }

    public boolean unfreeze(URI address, FreezeReason freezeReason) {
        ClientConnectionsEntry entry = getEntry(address);
        if (entry == null) {
            throw new IllegalStateException("Can't find " + address + " in slaves!");
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
    
    private String convert(URI address) {
        InetSocketAddress addr = new InetSocketAddress(address.getHost(), address.getPort());
        return addr.getAddress().getHostAddress() + ":" + addr.getPort();
    }
    
    public ClientConnectionsEntry freeze(URI address, FreezeReason freezeReason) {
        ClientConnectionsEntry connectionEntry = getEntry(address);
        return freeze(connectionEntry, freezeReason);
    }

    private ClientConnectionsEntry getEntry(URI address) {
        String addr = convert(address);
        return ip2Entry.get(addr);
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

    public RFuture<RedisPubSubConnection> nextPubSubConnection() {
        return pubSubConnectionPool.get();
    }

    public boolean contains(InetSocketAddress addr) {
        return ip2Entry.containsKey(addr.getAddress().getHostAddress() + ":" + addr.getPort());
    }
    
    public boolean contains(String addr) {
        return ip2Entry.containsKey(addr);
    }
    
    public RFuture<RedisConnection> getConnection(RedisCommand<?> command, InetSocketAddress addr) {
        ClientConnectionsEntry entry = ip2Entry.get(addr.getAddress().getHostAddress());
        if (entry != null) {
            return slaveConnectionPool.get(command, entry);
        }
        RedisConnectionException exception = new RedisConnectionException("Can't find entry for " + addr);
        return connectionManager.newFailedFuture(exception);
    }

    public RFuture<RedisConnection> nextConnection(RedisCommand<?> command) {
        return slaveConnectionPool.get(command);
    }

    public void returnPubSubConnection(RedisPubSubConnection connection) {
        ClientConnectionsEntry entry = ip2Entry.get(connection.getRedisClient().getAddr().getAddress().getHostAddress());
        pubSubConnectionPool.returnConnection(entry, connection);
    }

    public void returnConnection(RedisConnection connection) {
        ClientConnectionsEntry entry = ip2Entry.get(connection.getRedisClient().getAddr().getAddress().getHostAddress());
        slaveConnectionPool.returnConnection(entry, connection);
    }

    public void shutdown() {
        for (ClientConnectionsEntry entry : ip2Entry.values()) {
            entry.getClient().shutdown();
        }
    }

    public void shutdownAsync() {
        for (ClientConnectionsEntry entry : ip2Entry.values()) {
            connectionManager.shutdownAsync(entry.getClient());
        }
    }

}
