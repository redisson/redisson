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
package org.redisson.connection.balancer;

import org.redisson.api.NodeType;
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
import org.redisson.misc.RedisURI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class LoadBalancerManager {

    private final Logger log = LoggerFactory.getLogger(getClass());

    protected final ConnectionManager connectionManager;
    protected final PubSubConnectionPool pubSubConnectionPool;
    protected final SlaveConnectionPool slaveConnectionPool;
    
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
                entry.getClient().getConfig().setReadOnly(nodeType == NodeType.SLAVE && connectionManager.getServiceManager().getConfig().getReadMode() != ReadMode.MASTER);
            }
            entry.setNodeType(nodeType);
        }
    }

    public CompletableFuture<Void> add(ClientConnectionsEntry entry) {
        CompletableFuture<Void> future = CompletableFuture.completedFuture(null);
        if (!entry.isFreezed()) {
            CompletableFuture<Void> slaveFuture = slaveConnectionPool.initConnections(entry);
            CompletableFuture<Void> pubSubFuture = pubSubConnectionPool.initConnections(entry);
            future = CompletableFuture.allOf(slaveFuture, pubSubFuture);
        }
        return future.thenAccept(r -> {
            slaveConnectionPool.addEntry(entry);
            pubSubConnectionPool.addEntry(entry);
            client2Entry.put(entry.getClient(), entry);
        });
    }

    public Collection<ClientConnectionsEntry> getEntries() {
        return Collections.unmodifiableCollection(client2Entry.values());
    }

    public int getAvailableSlaves() {
        return (int) client2Entry.values().stream()
                                            .filter(e -> !e.isFreezed() && e.getNodeType() == NodeType.SLAVE)
                                            .count();
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
            log.error("Can't find {} in slaves! Available slaves: {}", address, client2Entry.keySet());
            return false;
        }

        return unfreeze(entry, freezeReason);
    }

    public CompletableFuture<Boolean> unfreezeAsync(RedisURI address, FreezeReason freezeReason) {
        ClientConnectionsEntry entry = getEntry(address);
        if (entry == null) {
            log.error("Can't find {} in slaves! Available slaves: {}", address, client2Entry.keySet());
            return CompletableFuture.completedFuture(false);
        }

        return unfreezeAsync(entry, freezeReason);
    }

    public CompletableFuture<Boolean> unfreezeAsync(InetSocketAddress address, FreezeReason freezeReason) {
        ClientConnectionsEntry entry = getEntry(address);
        if (entry == null) {
            log.error("Can't find {} in slaves! Available slaves: {}", address, client2Entry.keySet());
            return CompletableFuture.completedFuture(false);
        }

        return unfreezeAsync(entry, freezeReason);
    }

    public boolean unfreeze(InetSocketAddress address, FreezeReason freezeReason) {
        ClientConnectionsEntry entry = getEntry(address);
        if (entry == null) {
            log.error("Can't find {} in slaves! Available slaves: {}", address, client2Entry.keySet());
            return false;
        }

        return unfreeze(entry, freezeReason);
    }

    public boolean unfreeze(ClientConnectionsEntry entry, FreezeReason freezeReason) {
        synchronized (entry) {
            if (!entry.isFreezed()) {
                return false;
            }

            if (freezeReason != FreezeReason.RECONNECT
                    || entry.getFreezeReason() == FreezeReason.RECONNECT) {
                if (!entry.isInitialized()) {
                    entry.setInitialized(true);

                    List<CompletableFuture<Void>> futures = new ArrayList<>(2);
                    futures.add(slaveConnectionPool.initConnections(entry));
                    futures.add(pubSubConnectionPool.initConnections(entry));

                    CompletableFuture<Void> future = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
                    future.whenComplete((r, e) -> {
                        if (e != null) {
                            log.error("Unable to unfreeze entry: {}", entry, e);
                            entry.setInitialized(false);
                            connectionManager.getServiceManager().newTimeout(t -> {
                                unfreeze(entry, freezeReason);
                            }, 1, TimeUnit.SECONDS);
                            return;
                        }

                        entry.resetFirstFail();
                        entry.setFreezeReason(null);
                        log.debug("Unfreezed entry: {}", entry);
                    });
                    return true;
                }
            }
        }
        return false;
    }

    public CompletableFuture<Boolean> unfreezeAsync(ClientConnectionsEntry entry, FreezeReason freezeReason) {
        synchronized (entry) {
            if (!entry.isFreezed()) {
                return CompletableFuture.completedFuture(false);
            }

            if (freezeReason != FreezeReason.RECONNECT
                    || entry.getFreezeReason() == FreezeReason.RECONNECT) {
                if (!entry.isInitialized()) {
                    entry.setInitialized(true);

                    List<CompletableFuture<Void>> futures = new ArrayList<>(2);
                    futures.add(slaveConnectionPool.initConnections(entry));
                    futures.add(pubSubConnectionPool.initConnections(entry));

                    CompletableFuture<Void> future = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
                    return future.whenComplete((r, e) -> {
                        if (e != null) {
                            log.error("Unable to unfreeze entry: {}", entry, e);
                            entry.setInitialized(false);
                            return;
                        }

                        entry.resetFirstFail();
                        entry.setFreezeReason(null);
                        log.debug("Unfreezed entry: {}", entry);
                    }).thenApply(e -> true);
                }
            }
        }
        return CompletableFuture.completedFuture(false);
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
            if (connectionEntry.isFreezed()) {
                return null;
            }

            // only RECONNECT freeze reason could be replaced
            if (connectionEntry.getFreezeReason() == null
                    || connectionEntry.getFreezeReason() == FreezeReason.RECONNECT
                        || (freezeReason == FreezeReason.MANAGER 
                                && connectionEntry.getFreezeReason() != FreezeReason.MANAGER 
                                    && connectionEntry.getNodeType() == NodeType.SLAVE)) {
                connectionEntry.setFreezeReason(freezeReason);
                return connectionEntry;
            }
        }

        return connectionEntry;
    }

    public CompletableFuture<RedisPubSubConnection> nextPubSubConnection() {
        return pubSubConnectionPool.get();
    }

    public CompletableFuture<RedisPubSubConnection> nextPubSubConnection(ClientConnectionsEntry entry) {
        return pubSubConnectionPool.get(entry);
    }

    public boolean contains(InetSocketAddress addr) {
        return getEntry(addr) != null;
    }

    public boolean contains(RedisURI addr) {
        return getEntry(addr) != null;
    }

    public boolean contains(RedisClient redisClient) {
        return getEntry(redisClient) != null;
    }

    public ClientConnectionsEntry getEntry(RedisURI addr) {
        for (ClientConnectionsEntry entry : client2Entry.values()) {
            InetSocketAddress entryAddr = entry.getClient().getAddr();
            if (addr.equals(entryAddr)) {
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

    public CompletableFuture<RedisConnection> getConnection(RedisCommand<?> command, RedisURI addr) {
        ClientConnectionsEntry entry = getEntry(addr);
        if (entry != null) {
            return slaveConnectionPool.get(command, entry);
        }
        RedisConnectionException exception = new RedisConnectionException("Can't find entry for " + addr);
        CompletableFuture<RedisConnection> f = new CompletableFuture<>();
        f.completeExceptionally(exception);
        return f;
    }

    public CompletableFuture<RedisConnection> getConnection(RedisCommand<?> command, RedisClient client) {
        ClientConnectionsEntry entry = getEntry(client);
        if (entry != null) {
            return slaveConnectionPool.get(command, entry);
        }
        RedisConnectionException exception = new RedisConnectionException("Can't find entry for " + client);
        CompletableFuture<RedisConnection> f = new CompletableFuture<>();
        f.completeExceptionally(exception);
        return f;
    }

    public CompletableFuture<RedisConnection> nextConnection(RedisCommand<?> command) {
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

    public CompletableFuture<Void> shutdownAsync() {
        if (client2Entry.values().isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (ClientConnectionsEntry entry : client2Entry.values()) {
            futures.add(entry.shutdownAsync());
        }
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

}
