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
package org.redisson.connection.balancer;

import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.redisson.api.NodeType;
import org.redisson.api.RFuture;
import org.redisson.client.RedisClient;
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
import org.redisson.misc.CountableListener;
import org.redisson.misc.RPromise;
import org.redisson.misc.RedisURI;
import org.redisson.misc.RedissonPromise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    
    private final Map<RedisClient, ClientConnectionsEntry> client2Entry = new ConcurrentHashMap<>();

    public LoadBalancerManager(MasterSlaveServersConfig config, ConnectionManager connectionManager, MasterSlaveEntry entry) {
        this.connectionManager = connectionManager;
        slaveConnectionPool = new SlaveConnectionPool(config, connectionManager, entry);
        pubSubConnectionPool = new PubSubConnectionPool(config, connectionManager, entry);
    }

    public void changeType(InetSocketAddress address, NodeType nodeType) {
        ClientConnectionsEntry entry = getEntry(address);
        if (entry != null) {
            if (connectionManager.isClusterMode()) {
                entry.getClient().getConfig().setReadOnly(nodeType == NodeType.SLAVE && connectionManager.getConfig().getReadMode() != ReadMode.MASTER);
            }
            entry.setNodeType(nodeType);
        }
    }

    public RFuture<Void> add(final ClientConnectionsEntry entry) {
        RPromise<Void> result = new RedissonPromise<Void>();
        
        CountableListener<Void> listener = new CountableListener<Void>(result, null, 2) {
            @Override
            protected void onSuccess(Void value) {
                client2Entry.put(entry.getClient(), entry);
            }
        };

        RFuture<Void> slaveFuture = slaveConnectionPool.add(entry);
        slaveFuture.onComplete(listener);
        
        RFuture<Void> pubSubFuture = pubSubConnectionPool.add(entry);
        pubSubFuture.onComplete(listener);
        return result;
    }

    public Collection<ClientConnectionsEntry> getEntries() {
        return Collections.unmodifiableCollection(client2Entry.values());
    }
    
    public int getAvailableClients() {
        int count = 0;
        for (ClientConnectionsEntry connectionEntry : client2Entry.values()) {
            if (!connectionEntry.isFreezed()) {
                count++;
            }
        }
        return count;
    }

    public boolean unfreeze(RedisURI address, FreezeReason freezeReason) {
        ClientConnectionsEntry entry = getEntry(address);
        if (entry == null) {
            throw new IllegalStateException("Can't find " + address + " in slaves!");
        }

        return unfreeze(entry, freezeReason);
    }
    
    public boolean unfreeze(InetSocketAddress address, FreezeReason freezeReason) {
        ClientConnectionsEntry entry = getEntry(address);
        if (entry == null) {
            throw new IllegalStateException("Can't find " + address + " in slaves!");
        }

        return unfreeze(entry, freezeReason);
    }

    public boolean unfreeze(ClientConnectionsEntry entry, FreezeReason freezeReason) {
        synchronized (entry) {
            if (!entry.isFreezed()) {
                return false;
            }
            if ((freezeReason == FreezeReason.RECONNECT
                    && entry.getFreezeReason() == FreezeReason.RECONNECT)
                        || freezeReason != FreezeReason.RECONNECT) {
                entry.resetFirstFail();
                entry.setFreezed(false);
                entry.setFreezeReason(null);
                
                slaveConnectionPool.initConnections(entry);
                pubSubConnectionPool.initConnections(entry);
                return true;
            }
        }
        return false;
    }
    
    public ClientConnectionsEntry freeze(RedisURI address, FreezeReason freezeReason) {
        ClientConnectionsEntry connectionEntry = getEntry(address);
        return freeze(connectionEntry, freezeReason);
    }

    public ClientConnectionsEntry freeze(InetSocketAddress address, FreezeReason freezeReason) {
        ClientConnectionsEntry connectionEntry = getEntry(address);
        return freeze(connectionEntry, freezeReason);
    }

    @SuppressWarnings("BooleanExpressionComplexity")
    public ClientConnectionsEntry freeze(ClientConnectionsEntry connectionEntry, FreezeReason freezeReason) {
        if (connectionEntry == null || (connectionEntry.isFailed() 
                && connectionEntry.getFreezeReason() == FreezeReason.RECONNECT
                    && freezeReason == FreezeReason.RECONNECT)) {
            return null;
        }

        synchronized (connectionEntry) {
            // only RECONNECT freeze reason could be replaced
            if (connectionEntry.getFreezeReason() == null
                    || connectionEntry.getFreezeReason() == FreezeReason.RECONNECT
                        || (freezeReason == FreezeReason.MANAGER 
                                && connectionEntry.getFreezeReason() != FreezeReason.MANAGER 
                                    && connectionEntry.getNodeType() == NodeType.SLAVE)) {
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
        return getEntry(addr) != null;
    }

    public boolean isUnfreezed(RedisURI addr) {
        ClientConnectionsEntry entry = getEntry(addr);
        return !entry.isFreezed();
    }
    
    public boolean contains(RedisURI addr) {
        return getEntry(addr) != null;
    }

    public boolean contains(RedisClient redisClient) {
        return getEntry(redisClient) != null;
    }

    private ClientConnectionsEntry getEntry(RedisURI addr) {
        for (ClientConnectionsEntry entry : client2Entry.values()) {
            InetSocketAddress entryAddr = entry.getClient().getAddr();
            if (RedisURI.compare(entryAddr, addr)) {
                return entry;
            }
        }
        return null;
    }
    
    private ClientConnectionsEntry getEntry(InetSocketAddress address) {
        for (ClientConnectionsEntry entry : client2Entry.values()) {
            InetSocketAddress addr = entry.getClient().getAddr();
            if (addr.getAddress().equals(address.getAddress()) && addr.getPort() == address.getPort()) {
                return entry;
            }
        }
        return null;
    }

    public ClientConnectionsEntry getEntry(RedisClient redisClient) {
        return client2Entry.get(redisClient);
    }

    public RFuture<RedisConnection> getConnection(RedisCommand<?> command, RedisURI addr) {
        ClientConnectionsEntry entry = getEntry(addr);
        if (entry != null) {
            return slaveConnectionPool.get(command, entry);
        }
        RedisConnectionException exception = new RedisConnectionException("Can't find entry for " + addr);
        return RedissonPromise.newFailedFuture(exception);
    }
    
    public RFuture<RedisConnection> getConnection(RedisCommand<?> command, RedisClient client) {
        ClientConnectionsEntry entry = getEntry(client);
        if (entry != null) {
            return slaveConnectionPool.get(command, entry);
        }
        RedisConnectionException exception = new RedisConnectionException("Can't find entry for " + client);
        return RedissonPromise.newFailedFuture(exception);
    }

    public RFuture<RedisConnection> nextConnection(RedisCommand<?> command) {
        return slaveConnectionPool.get(command);
    }

    public void returnPubSubConnection(RedisPubSubConnection connection) {
        ClientConnectionsEntry entry = getEntry(connection.getRedisClient());
        pubSubConnectionPool.returnConnection(entry, connection);
    }

    public void returnConnection(RedisConnection connection) {
        ClientConnectionsEntry entry = getEntry(connection.getRedisClient());
        slaveConnectionPool.returnConnection(entry, connection);
    }

    public RFuture<Void> shutdownAsync() {
        if (client2Entry.values().isEmpty()) {
            return RedissonPromise.<Void>newSucceededFuture(null);
        }
        RPromise<Void> result = new RedissonPromise<Void>();
        CountableListener<Void> listener = new CountableListener<Void>(result, null, client2Entry.values().size());
        for (ClientConnectionsEntry entry : client2Entry.values()) {
            entry.getClient().shutdownAsync().onComplete(listener);
        }
        return result;
    }

}
