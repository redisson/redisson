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

import org.redisson.api.NodeType;
import org.redisson.api.RFuture;
import org.redisson.client.RedisClient;
import org.redisson.client.RedisConnection;
import org.redisson.client.RedisConnectionException;
import org.redisson.client.RedisPubSubConnection;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.config.MasterSlaveServersConfig;
import org.redisson.config.ReadMode;
import org.redisson.config.SubscriptionMode;
import org.redisson.connection.ClientConnectionsEntry.FreezeReason;
import org.redisson.connection.pool.MasterConnectionPool;
import org.redisson.connection.pool.MasterPubSubConnectionPool;
import org.redisson.connection.pool.PubSubConnectionPool;
import org.redisson.connection.pool.SlaveConnectionPool;
import org.redisson.misc.RedisURI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class MasterSlaveEntry {

    final Logger log = LoggerFactory.getLogger(getClass());

    volatile ClientConnectionsEntry masterEntry;

    int references;
    
    final MasterSlaveServersConfig config;
    final ConnectionManager connectionManager;

    final MasterConnectionPool masterConnectionPool;
    
    final MasterPubSubConnectionPool masterPubSubConnectionPool;

    final PubSubConnectionPool slavePubSubConnectionPool;

    final SlaveConnectionPool slaveConnectionPool;

    final Map<RedisClient, ClientConnectionsEntry> client2Entry = new ConcurrentHashMap<>();

    final AtomicBoolean active = new AtomicBoolean(true);

    final AtomicBoolean noPubSubSlaves = new AtomicBoolean();

    volatile int availableSlaves = -1;

    public MasterSlaveEntry(ConnectionManager connectionManager, MasterSlaveServersConfig config) {
        this.connectionManager = connectionManager;
        this.config = config;

        slaveConnectionPool = new SlaveConnectionPool(config, connectionManager, this);
        slavePubSubConnectionPool = new PubSubConnectionPool(config, connectionManager, this);
        masterConnectionPool = new MasterConnectionPool(config, connectionManager, this);
        masterPubSubConnectionPool = new MasterPubSubConnectionPool(config, connectionManager, this);
    }

    public MasterSlaveServersConfig getConfig() {
        return config;
    }

    public CompletableFuture<Void> initSlaveBalancer(Function<RedisURI, String> hostnameMapper) {
        List<CompletableFuture<Void>> result = new ArrayList<>(config.getSlaveAddresses().size());
        for (String address : config.getSlaveAddresses()) {
            RedisURI uri = new RedisURI(address);
            String hostname = hostnameMapper.apply(uri);
            CompletableFuture<Void> f = addSlave(uri, false, hostname);
            result.add(f);
        }

        CompletableFuture<Void> future = CompletableFuture.allOf(result.toArray(new CompletableFuture[0]));
        return future.thenAccept(v -> {
            useMasterAsSlave();
        });
    }

    private void useMasterAsSlave() {
        if (hasNoSlaves()
                || config.getReadMode() == ReadMode.MASTER_SLAVE) {
            addSlaveEntry(masterEntry);
        } else {
            removeSlaveEntry(masterEntry);
        }
    }

    private void removeSlaveEntry(ClientConnectionsEntry entry) {
        slaveConnectionPool.removeEntry(entry);
        slavePubSubConnectionPool.removeEntry(entry);
        client2Entry.remove(entry.getClient());

        if (config.getSubscriptionMode() == SubscriptionMode.SLAVE) {
            entry.reattachPubSub();
        }
    }

    private void addSlaveEntry(ClientConnectionsEntry entry) {
        if (client2Entry.get(entry.getClient()) != null) {
            return;
        }
        slaveConnectionPool.addEntry(entry);
        slavePubSubConnectionPool.addEntry(entry);
        client2Entry.put(entry.getClient(), entry);
    }

    private boolean hasNoSlaves() {
        int count = (int) client2Entry.values().stream()
                .filter(e -> !e.isFreezed() && e.getNodeType() == NodeType.SLAVE)
                .count();
        return count == 0;
    }

    public CompletableFuture<RedisClient> setupMasterEntry(InetSocketAddress address, RedisURI uri) {
        RedisClient client = connectionManager.createClient(NodeType.MASTER, address, uri, null);
        return setupMasterEntry(client);
    }

    public CompletableFuture<RedisClient> setupMasterEntry(RedisURI address) {
        return setupMasterEntry(address, null);
    }

    public CompletableFuture<RedisClient> setupMasterEntry(RedisURI address, String sslHostname) {
        RedisClient client = connectionManager.createClient(NodeType.MASTER, address, sslHostname);
        return setupMasterEntry(client);
    }

    private CompletableFuture<RedisClient> setupMasterEntry(RedisClient client) {
        CompletableFuture<InetSocketAddress> addrFuture = client.resolveAddr();
        return addrFuture.thenCompose(res -> {
            masterEntry = new ClientConnectionsEntry(
                    client,
                    config.getMasterConnectionMinimumIdleSize(),
                    config.getMasterConnectionPoolSize(),
                    connectionManager,
                    NodeType.MASTER,
                    config);

            if (!config.isSlaveNotUsed()) {
                addSlaveEntry(masterEntry);
            }

            if (masterEntry.isFreezed()) {
                return CompletableFuture.completedFuture(null);
            }

            List<CompletableFuture<Void>> futures = new ArrayList<>();
            CompletableFuture<Void> writeFuture = masterEntry.initConnections(config.getMasterConnectionMinimumIdleSize());
            futures.add(writeFuture);

            if (config.getSubscriptionMode() == SubscriptionMode.MASTER) {
                CompletableFuture<Void> pubSubFuture = masterEntry.initPubSubConnections(config.getSubscriptionConnectionMinimumIdleSize());
                futures.add(pubSubFuture);
            }
            return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        }).whenComplete((r, e) -> {
            if (e != null) {
                client.shutdownAsync();
            }
        }).thenApply(r -> {
            masterConnectionPool.addEntry(masterEntry);
            masterPubSubConnectionPool.addEntry(masterEntry);
            return client;
        });
    }

    public boolean slaveDown(InetSocketAddress address, FreezeReason freezeReason) {
        ClientConnectionsEntry connectionEntry = getEntry(address);
        ClientConnectionsEntry entry = freeze(connectionEntry, freezeReason);
        if (entry == null) {
            return false;
        }
        
        return slaveDown(entry);
    }

    public boolean slaveDown(RedisURI address, FreezeReason freezeReason) {
        ClientConnectionsEntry connectionEntry = getEntry(address);
        ClientConnectionsEntry entry = freeze(connectionEntry, freezeReason);
        if (entry == null) {
            return false;
        }

        return slaveDown(entry);
    }

    private boolean slaveDown(ClientConnectionsEntry entry) {
        // add master as slave if no more slaves available
        if (!config.isSlaveNotUsed()
                && !masterEntry.getClient().getAddr().equals(entry.getClient().getAddr())
                    && hasNoSlaves()) {
            addSlaveEntry(masterEntry);
            log.info("master {} is used as slave", masterEntry.getClient().getAddr());
        }

        entry.nodeDown();
        return true;
    }

    public void shutdownAndReconnectAsync(RedisClient client, Throwable cause) {
        ClientConnectionsEntry entry = getEntry(client);
        if (slaveDown(entry, FreezeReason.RECONNECT)) {
            log.error("Redis node {} has been disconnected", entry.getClient().getAddr(), cause);
            scheduleCheck(entry);
        }
    }

    private void scheduleCheck(ClientConnectionsEntry entry) {
        connectionManager.getServiceManager().newTimeout(timeout -> {
            boolean res = entry.getLock().execute(() -> {
                if (entry.getFreezeReason() != FreezeReason.RECONNECT
                        || connectionManager.getServiceManager().isShuttingDown()) {
                    return false;
                }
                return true;
            });
            if (!res) {
                return;
            }

            CompletionStage<RedisConnection> connectionFuture = entry.getClient().connectAsync();
            connectionFuture.whenComplete((c, e) -> {
                boolean res2 = entry.getLock().execute(() -> {
                    if (entry.getFreezeReason() != FreezeReason.RECONNECT) {
                        return false;
                    }
                    return true;
                });
                if (!res2) {
                    return;
                }

                if (e != null) {
                    scheduleCheck(entry);
                    return;
                }
                if (!c.isActive()) {
                    c.closeAsync();
                    scheduleCheck(entry);
                    return;
                }

                RFuture<String> f = c.async(RedisCommands.PING);
                f.whenComplete((t, ex) -> {
                    try {
                        boolean res3 = entry.getLock().execute(() -> {
                            if (entry.getFreezeReason() != FreezeReason.RECONNECT) {
                                return false;
                            }
                            return true;
                        });
                        if (!res3) {
                            return;
                        }

                        if ("PONG".equals(t)) {
                            CompletableFuture<Boolean> ff = slaveUpAsync(entry, FreezeReason.RECONNECT);
                            ff.thenAccept(r -> {
                                if (r) {
                                    log.info("slave {} has been successfully reconnected", entry.getClient().getAddr());
                                }
                            });
                        } else {
                            scheduleCheck(entry);
                        }
                    } finally {
                        c.closeAsync();
                    }
                });
            });
        }, config.getFailedSlaveReconnectionInterval(), TimeUnit.MILLISECONDS);
    }
    
    private boolean slaveDown(ClientConnectionsEntry entry, FreezeReason freezeReason) {
        ClientConnectionsEntry e = freeze(entry, freezeReason);
        if (e == null) {
            return false;
        }

        return slaveDown(entry);
    }

    public void masterDown() {
        masterEntry.nodeDown();
    }

    public boolean hasSlave(RedisClient redisClient) {
        return getEntry(redisClient) != null;
    }

    public boolean hasSlave(InetSocketAddress addr) {
        return getEntry(addr) != null;
    }
    
    public boolean hasSlave(RedisURI addr) {
        return getEntry(addr) != null;
    }

    public CompletableFuture<Void> addSlave(RedisURI address) {
        return addSlave(address, false, null);
    }
    
    public CompletableFuture<Void> addSlave(InetSocketAddress address, RedisURI uri) {
        return addSlave(address, uri, false, null);
    }

    public CompletableFuture<Void> addSlave(InetSocketAddress address, RedisURI uri, String sslHostname) {
        return addSlave(address, uri, false, sslHostname);
    }

    public CompletableFuture<Void> addSlave(RedisClient client) {
        return addSlave(client, false);
    }

    private CompletableFuture<Void> addSlave(RedisClient client, boolean freezed) {
        noPubSubSlaves.set(false);
        CompletableFuture<InetSocketAddress> addrFuture = client.resolveAddr();
        return addrFuture.thenCompose(res -> {
            ClientConnectionsEntry entry = new ClientConnectionsEntry(client,
                    config.getSlaveConnectionMinimumIdleSize(),
                    config.getSlaveConnectionPoolSize(),
                    connectionManager,
                    NodeType.SLAVE,
                    config);

            if (freezed) {
                entry.getLock().execute(() -> {
                    entry.setFreezeReason(FreezeReason.SYSTEM);
                });
            }

            CompletableFuture<Void> future = CompletableFuture.completedFuture(null);
            if (!entry.isFreezed()) {
                CompletableFuture<Void> slaveFuture = entry.initConnections(config.getSlaveConnectionMinimumIdleSize());
                CompletableFuture<Void> pubSubFuture = entry.initPubSubConnections(config.getSubscriptionConnectionMinimumIdleSize());
                future = CompletableFuture.allOf(slaveFuture, pubSubFuture);
            }
            return future.thenAccept(r -> {
                addSlaveEntry(entry);
            });
        }).whenComplete((r, ex) -> {
            if (ex != null) {
                client.shutdownAsync();
            }
        });
    }

    private CompletableFuture<Void> addSlave(InetSocketAddress address, RedisURI uri, boolean freezed, String sslHostname) {
        RedisClient client = connectionManager.createClient(NodeType.SLAVE, address, uri, sslHostname);
        return addSlave(client, freezed);
    }
    
    public CompletableFuture<Void> addSlave(RedisURI address, boolean freezed, String sslHostname) {
        RedisClient client = connectionManager.createClient(NodeType.SLAVE, address, sslHostname);
        return addSlave(client, freezed);
    }

    public Collection<ClientConnectionsEntry> getAllEntries() {
        return Collections.unmodifiableCollection(client2Entry.values());
    }

    public ClientConnectionsEntry getEntry(InetSocketAddress address) {
        InetSocketAddress masterAddr = masterEntry.getClient().getAddr();
        if (masterAddr.getAddress().equals(address.getAddress()) && masterAddr.getPort() == address.getPort()) {
            return masterEntry;
        }
        for (ClientConnectionsEntry entry : client2Entry.values()) {
            InetSocketAddress addr = entry.getClient().getAddr();
            if (addr.getAddress().equals(address.getAddress()) && addr.getPort() == address.getPort()) {
                return entry;
            }
        }
        return null;
    }

    public ClientConnectionsEntry getEntry(RedisClient redisClient) {
        if (masterEntry.getClient().equals(redisClient)) {
            return masterEntry;
        }
        return client2Entry.get(redisClient);
    }

    public ClientConnectionsEntry getEntry(RedisURI addr) {
        if (addr.equals(masterEntry.getClient().getAddr())) {
            return masterEntry;
        }
        for (ClientConnectionsEntry entry : client2Entry.values()) {
            InetSocketAddress entryAddr = entry.getClient().getAddr();
            if (addr.equals(entryAddr)) {
                return entry;
            }
        }
        return null;
    }

    public boolean isInit() {
        return masterEntry != null;
    }

    public RedisClient getClient() {
        return masterEntry.getClient();
    }

    public CompletableFuture<Boolean> slaveUpAsync(ClientConnectionsEntry entry, FreezeReason freezeReason) {
        noPubSubSlaves.set(false);
        CompletableFuture<Boolean> f = unfreezeAsync(entry, freezeReason);
        return f.thenApply(r -> {
            if (r) {
                excludeMasterFromSlaves(entry.getClient().getAddr());
                return r;
            }
            return r;
        });
    }
    
    public CompletableFuture<Boolean> slaveUpAsync(RedisURI address, FreezeReason freezeReason) {
        noPubSubSlaves.set(false);
        CompletableFuture<Boolean> f = unfreezeAsync(address, freezeReason);
        return f.thenApply(r -> {
            if (r) {
                excludeMasterFromSlaves(address);
                return r;
            }
            return r;
        });
    }

    public boolean excludeMasterFromSlaves(RedisURI address) {
        InetSocketAddress addr = masterEntry.getClient().getAddr();
        if (address.equals(addr)
                || config.getReadMode() == ReadMode.MASTER_SLAVE) {
            return false;
        }

        removeSlaveEntry(masterEntry);
        log.info("master {} excluded from slaves", addr);
        return true;
    }

    public boolean excludeMasterFromSlaves(InetSocketAddress address) {
        InetSocketAddress addr = masterEntry.getClient().getAddr();
        if (config.isSlaveNotUsed() || addr.equals(address)
                || config.getReadMode() == ReadMode.MASTER_SLAVE) {
            return false;
        }

        removeSlaveEntry(masterEntry);
        log.info("master {} excluded from slaves", addr);
        return true;
    }

    public CompletableFuture<Boolean> slaveUpNoMasterExclusionAsync(RedisURI address, FreezeReason freezeReason) {
        noPubSubSlaves.set(false);
        return unfreezeAsync(address, freezeReason);
    }

    public CompletableFuture<Boolean> slaveUpNoMasterExclusionAsync(InetSocketAddress address, FreezeReason freezeReason) {
        noPubSubSlaves.set(false);
        return unfreezeAsync(address, freezeReason);
    }

    public CompletableFuture<Boolean> slaveUpAsync(InetSocketAddress address, FreezeReason freezeReason) {
        noPubSubSlaves.set(false);
        CompletableFuture<Boolean> f = unfreezeAsync(address, freezeReason);
        return f.thenApply(r -> {
            if (r) {
                excludeMasterFromSlaves(address);
                return r;
            }
            return r;
        });
    }

    /**
     * Freeze slave with <code>redis(s)://host:port</code> from slaves list.
     * Re-attach pub/sub listeners from it to other slave.
     * Shutdown old master client.
     * 
     * @param address of Redis
     * @return client 
     */
    public CompletableFuture<RedisClient> changeMaster(RedisURI address) {
        ClientConnectionsEntry oldMaster = masterEntry;
        CompletableFuture<RedisClient> future = setupMasterEntry(address);
        return changeMaster(address, oldMaster, future);
    }
    
    public CompletableFuture<RedisClient> changeMaster(InetSocketAddress address, RedisURI uri) {
        ClientConnectionsEntry oldMaster = masterEntry;
        CompletableFuture<RedisClient> future = setupMasterEntry(address, uri);
        return changeMaster(uri, oldMaster, future);
    }


    private CompletableFuture<RedisClient> changeMaster(RedisURI address, ClientConnectionsEntry oldMaster,
                              CompletableFuture<RedisClient> future) {
        return future.whenComplete((newMasterClient, e) -> {
            if (e != null) {
                if (oldMaster != masterEntry) {
                    removeMaster(masterEntry);

                    masterEntry = oldMaster;
                }
                log.error("Unable to change master from: {} to: {}", oldMaster.getClient().getAddr(), address, e);
                return;
            }

            ClientConnectionsEntry entry = getAllEntries().stream().filter(node -> node.getNodeType() == NodeType.SLAVE
                                                                                        && node.getClient().getAddr().equals(masterEntry.getClient().getAddr()))
                                                                    .findAny().orElse(null);
            // remove new master entry if it is already present as slave
            if (entry != null) {
                removeSlaveEntry(entry);
                entry.nodeDown();
                entry.shutdownAsync();
                log.info("new master {} excluded from slaves", masterEntry.getClient().getAddr());
            }

            removeMaster(oldMaster);

            // check if at least one slave is available, use master as slave if false
            if (!config.isSlaveNotUsed()) {
                useMasterAsSlave();
            }
            log.info("master {} has been changed to {}", oldMaster.getClient().getAddr(), masterEntry.getClient().getAddr());
        });
    }

    private void removeMaster(ClientConnectionsEntry masterEntry) {
        masterConnectionPool.removeEntry(masterEntry);
        masterPubSubConnectionPool.removeEntry(masterEntry);
        removeSlaveEntry(masterEntry);
        masterEntry.nodeDown();
        masterEntry.shutdownAsync();
    }

    public CompletableFuture<Void> shutdownAsync() {
        if (!active.compareAndSet(true, false)) {
            return CompletableFuture.completedFuture(null);
        }

        List<CompletableFuture<Void>> futures = new ArrayList<>(client2Entry.size() + 1);
        if (masterEntry != null) {
            futures.add(masterEntry.shutdownAsync());
        }
        for (ClientConnectionsEntry entry : client2Entry.values()) {
            futures.add(entry.shutdownAsync());
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    public CompletableFuture<RedisConnection> connectionWriteOp(RedisCommand<?> command) {
        return masterConnectionPool.get(command, false);
    }

    public CompletableFuture<RedisConnection> trackedConnectionWriteOp(RedisCommand<?> command) {
        return masterConnectionPool.get(command, true);
    }

    public CompletableFuture<RedisConnection> redirectedConnectionWriteOp(RedisCommand<?> command, RedisURI addr) {
        return connectionReadOp(command, addr);
    }

    public CompletableFuture<RedisConnection> connectionReadOp(RedisCommand<?> command, boolean trackChanges) {
        if (config.getReadMode() == ReadMode.MASTER) {
            if (trackChanges) {
                return trackedConnectionWriteOp(command);
            }
            return connectionWriteOp(command);
        }
        return slaveConnectionPool.get(command, trackChanges);
    }

    public CompletableFuture<RedisConnection> connectionReadOp(RedisCommand<?> command, RedisURI addr) {
        ClientConnectionsEntry entry = getEntry(addr);
        if (entry != null) {
            return slaveConnectionPool.get(command, entry, false);
        }
        RedisConnectionException exception = new RedisConnectionException("Can't find entry for " + addr + " command " + command);
        CompletableFuture<RedisConnection> f = new CompletableFuture<>();
        f.completeExceptionally(exception);
        return f;
    }
    
    public CompletableFuture<RedisConnection> connectionReadOp(RedisCommand<?> command, RedisClient client, boolean trackChanges) {
        if (config.getReadMode() == ReadMode.MASTER) {
            if (trackChanges) {
                return trackedConnectionWriteOp(command);
            }
            return connectionWriteOp(command);
        }

        ClientConnectionsEntry entry = getEntry(client);
        if (entry != null) {
            return slaveConnectionPool.get(command, entry, trackChanges);
        }
        RedisConnectionException exception = new RedisConnectionException("Can't find entry for " + client + " command " + command);
        CompletableFuture<RedisConnection> f = new CompletableFuture<>();
        f.completeExceptionally(exception);
        return f;
    }

    public CompletableFuture<RedisPubSubConnection> nextPubSubConnection(ClientConnectionsEntry entry) {
        if (entry != null) {
            return slavePubSubConnectionPool.get(entry);
        }

        if (config.getSubscriptionMode() == SubscriptionMode.MASTER) {
            return masterPubSubConnectionPool.get();
        }

        if (noPubSubSlaves.get()) {
            return masterPubSubConnectionPool.get();
        }

        CompletableFuture<RedisPubSubConnection> future = slavePubSubConnectionPool.get();
        return future.handle((r, e) -> {
            if (e != null) {
                if (noPubSubSlaves.compareAndSet(false, true)) {
                    log.warn("No slaves for master: {} PubSub connections established with the master node.",
                            masterEntry.getClient().getAddr(), e);
                }
                return masterPubSubConnectionPool.get();
            }

            return CompletableFuture.completedFuture(r);
        }).thenCompose(f -> f);
    }

    public void returnPubSubConnection(RedisPubSubConnection connection) {
        ClientConnectionsEntry entry = getEntry(connection.getRedisClient());
        if (entry == null) {
            connection.closeAsync();
            return;
        }
        entry.returnConnection(connection);
    }

    public void releaseWrite(RedisConnection connection) {
        masterEntry.returnConnection(connection);
    }

    @Deprecated
    public void releaseTrackedWrite(RedisConnection connection) {
        masterEntry.returnConnection(connection);
    }

    public void releaseRead(RedisConnection connection, boolean trackChanges) {
        if (config.getReadMode() == ReadMode.MASTER) {
            if (trackChanges) {
                releaseTrackedWrite(connection);
                return;
            }
            releaseWrite(connection);
            return;
        }

        ClientConnectionsEntry entry = getEntry(connection.getRedisClient());
        if (entry == null) {
            connection.closeAsync();
            return;
        }
        entry.returnConnection(connection);
    }

    public void incReference() {
        references++;
    }

    public int decReference() {
        return --references;
    }

    public int getReferences() {
        return references;
    }

    @Override
    public String toString() {
        return "MasterSlaveEntry [masterEntry=" + masterEntry + "]";
    }

    @SuppressWarnings("BooleanExpressionComplexity")
    private ClientConnectionsEntry freeze(ClientConnectionsEntry connectionEntry, FreezeReason freezeReason) {
        if (connectionEntry == null || (connectionEntry.getClient().getConfig().getFailedNodeDetector().isNodeFailed()
                && connectionEntry.getFreezeReason() == FreezeReason.RECONNECT
                && freezeReason == FreezeReason.RECONNECT)) {
            return null;
        }

        return connectionEntry.getLock().execute(() -> {
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
            return connectionEntry;
        });
    }

    private CompletableFuture<Boolean> unfreezeAsync(RedisURI address, FreezeReason freezeReason) {
        ClientConnectionsEntry entry = getEntry(address);
        if (entry == null) {
            log.error("Can't find {} in slaves! Available slaves: {}", address, client2Entry.keySet());
            return CompletableFuture.completedFuture(false);
        }

        return unfreezeAsync(entry, freezeReason);
    }

    private CompletableFuture<Boolean> unfreezeAsync(InetSocketAddress address, FreezeReason freezeReason) {
        ClientConnectionsEntry entry = getEntry(address);
        if (entry == null) {
            log.error("Can't find {} in slaves! Available slaves: {}", address, client2Entry.keySet());
            return CompletableFuture.completedFuture(false);
        }

        return unfreezeAsync(entry, freezeReason);
    }

    private CompletableFuture<Boolean> unfreezeAsync(ClientConnectionsEntry entry, FreezeReason freezeReason) {
        return unfreezeAsync(entry, freezeReason, 0);
    }

    private CompletableFuture<Boolean> unfreezeAsync(ClientConnectionsEntry entry, FreezeReason freezeReason, int retry) {
        return entry.getLock().execute(() -> {
            if (!entry.isFreezed()) {
                return CompletableFuture.completedFuture(false);
            }

            if (freezeReason != FreezeReason.RECONNECT
                    || entry.getFreezeReason() == FreezeReason.RECONNECT) {
                if (!entry.isInitialized()) {
                    entry.setInitialized(true);

                    List<CompletableFuture<Void>> futures = new ArrayList<>(2);
                    futures.add(entry.initConnections(config.getSlaveConnectionMinimumIdleSize()));
                    futures.add(entry.initPubSubConnections(config.getSubscriptionConnectionMinimumIdleSize()));

                    CompletableFuture<Void> future = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
                    CompletableFuture<Boolean> f = new CompletableFuture<>();
                    future.whenComplete((r, e) -> {
                        if (e != null) {
                            int maxAttempts = connectionManager.getServiceManager().getConfig().getRetryAttempts();
                            int retryInterval = connectionManager.getServiceManager().getConfig().getRetryInterval();
                            log.error("Unable to unfreeze entry: {} attempt: {} of {}", entry, retry, maxAttempts, e);
                            entry.setInitialized(false);
                            if (retry < maxAttempts) {
                                connectionManager.getServiceManager().newTimeout(t -> {
                                    CompletableFuture<Boolean> ff = unfreezeAsync(entry, freezeReason, retry + 1);
                                    connectionManager.getServiceManager().transfer(ff, f);
                                }, retryInterval, TimeUnit.MILLISECONDS);
                            } else {
                                f.complete(false);
                            }
                            return;
                        }

                        entry.getClient().getConfig().getFailedNodeDetector().onConnectSuccessful();
                        entry.setFreezeReason(null);
                        log.debug("Unfreezed entry: {} after {} attempts", entry, retry);
                        f.complete(true);
                    });
                    return f;
                }
            }
            return CompletableFuture.completedFuture(false);
        });
    }

    public ClientConnectionsEntry getEntry() {
        return masterEntry;
    }

    public int getAvailableSlaves() {
        return availableSlaves;
    }

    public void setAvailableSlaves(int slaves) {
        availableSlaves = slaves;
    }

}
