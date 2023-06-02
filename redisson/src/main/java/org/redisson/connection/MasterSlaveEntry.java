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
package org.redisson.connection;

import io.netty.channel.ChannelFuture;
import org.redisson.api.NodeType;
import org.redisson.client.RedisClient;
import org.redisson.client.RedisConnection;
import org.redisson.client.RedisPubSubConnection;
import org.redisson.client.protocol.CommandData;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.config.MasterSlaveServersConfig;
import org.redisson.config.ReadMode;
import org.redisson.config.SubscriptionMode;
import org.redisson.connection.ClientConnectionsEntry.FreezeReason;
import org.redisson.connection.balancer.LoadBalancerManager;
import org.redisson.connection.pool.MasterConnectionPool;
import org.redisson.connection.pool.MasterPubSubConnectionPool;
import org.redisson.misc.RedisURI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class MasterSlaveEntry {

    final Logger log = LoggerFactory.getLogger(getClass());

    LoadBalancerManager slaveBalancer;
    ClientConnectionsEntry masterEntry;

    int references;
    
    final MasterSlaveServersConfig config;
    final ConnectionManager connectionManager;

    final MasterConnectionPool writeConnectionPool;
    
    final MasterPubSubConnectionPool pubSubConnectionPool;

    final AtomicBoolean active = new AtomicBoolean(true);
    final IdleConnectionWatcher idleConnectionWatcher;

    final AtomicBoolean noPubSubSlaves = new AtomicBoolean();

    public MasterSlaveEntry(ConnectionManager connectionManager, IdleConnectionWatcher idleConnectionWatcher,
                            MasterSlaveServersConfig config) {
        this.connectionManager = connectionManager;
        this.config = config;
        this.idleConnectionWatcher = idleConnectionWatcher;

        slaveBalancer = new LoadBalancerManager(config, connectionManager, this);
        writeConnectionPool = new MasterConnectionPool(config, connectionManager, this);
        pubSubConnectionPool = new MasterPubSubConnectionPool(config, connectionManager, this);
    }

    public MasterSlaveServersConfig getConfig() {
        return config;
    }

    public CompletableFuture<Void> initSlaveBalancer(Collection<RedisURI> disconnectedNodes) {
        return initSlaveBalancer(disconnectedNodes, null);
    }

    public CompletableFuture<Void> initSlaveBalancer(Collection<RedisURI> disconnectedNodes, String slaveSSLHostname) {
        List<CompletableFuture<Void>> result = new ArrayList<>(config.getSlaveAddresses().size());
        for (String address : config.getSlaveAddresses()) {
            RedisURI uri = new RedisURI(address);
            CompletableFuture<Void> f = addSlave(uri, disconnectedNodes.contains(uri), NodeType.SLAVE, slaveSSLHostname);
            result.add(f);
        }

        CompletableFuture<Void> future = CompletableFuture.allOf(result.toArray(new CompletableFuture[0]));
        return future.thenAccept(v -> {
            useMasterAsSlave();
        });
    }

    private void useMasterAsSlave() {
        if (slaveBalancer.getAvailableClients() == 0) {
            slaveUp(masterEntry.getClient().getAddr(), FreezeReason.SYSTEM);
        } else {
            slaveDown(masterEntry.getClient().getAddr(), FreezeReason.SYSTEM);
        }
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
                    idleConnectionWatcher,
                    NodeType.MASTER,
                    config);

            List<CompletableFuture<Void>> futures = new ArrayList<>();
            if (!config.isSlaveNotUsed() && !slaveBalancer.contains(client.getAddr())) {
                CompletableFuture<Void> masterAsSlaveFuture = addSlave(client.getAddr(), client.getConfig().getAddress(),
                        true, NodeType.MASTER, client.getConfig().getSslHostname());
                futures.add(masterAsSlaveFuture);
            }

            if (masterEntry.isFreezed()) {
                return CompletableFuture.completedFuture(null);
            }

            CompletableFuture<Void> writeFuture = writeConnectionPool.initConnections(masterEntry);
            futures.add(writeFuture);

            if (config.getSubscriptionMode() == SubscriptionMode.MASTER) {
                CompletableFuture<Void> pubSubFuture = pubSubConnectionPool.initConnections(masterEntry);
                futures.add(pubSubFuture);
            }
            return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        }).whenComplete((r, e) -> {
            if (e != null) {
                client.shutdownAsync();
            }
        }).thenApply(r -> {
            writeConnectionPool.addEntry(masterEntry);
            pubSubConnectionPool.addEntry(masterEntry);
            return client;
        });
    }

    public boolean slaveDown(InetSocketAddress address, FreezeReason freezeReason) {
        ClientConnectionsEntry entry = slaveBalancer.freeze(address, freezeReason);
        if (entry == null) {
            return false;
        }
        
        return slaveDown(entry);
    }

    public CompletableFuture<Boolean> slaveDownAsync(InetSocketAddress address, FreezeReason freezeReason) {
        ClientConnectionsEntry entry = slaveBalancer.freeze(address, freezeReason);
        if (entry == null) {
            return CompletableFuture.completedFuture(false);
        }

        return slaveDownAsync(entry);
    }

    public CompletableFuture<Boolean> slaveDownAsync(RedisURI address, FreezeReason freezeReason) {
        ClientConnectionsEntry entry = slaveBalancer.freeze(address, freezeReason);
        if (entry == null) {
            return CompletableFuture.completedFuture(false);
        }

        return slaveDownAsync(entry);
    }

    private boolean slaveDown(ClientConnectionsEntry entry) {
        if (entry.isMasterForRead()) {
            return false;
        }

        // add master as slave if no more slaves available
        if (!config.isSlaveNotUsed()
                && !masterEntry.getClient().getAddr().equals(entry.getClient().getAddr())
                    && slaveBalancer.getAvailableClients() == 0) {
            if (slaveBalancer.unfreeze(masterEntry.getClient().getAddr(), FreezeReason.SYSTEM)) {
                log.info("master {} used as slave", masterEntry.getClient().getAddr());
            }
        }

        return nodeDown(entry);
    }

    public CompletableFuture<Boolean> slaveDownAsync(ClientConnectionsEntry entry, FreezeReason freezeReason) {
        ClientConnectionsEntry e = slaveBalancer.freeze(entry, freezeReason);
        if (e == null) {
            return CompletableFuture.completedFuture(false);
        }

        return slaveDownAsync(entry);
    }

    private CompletableFuture<Boolean> slaveDownAsync(ClientConnectionsEntry entry) {
        if (entry.isMasterForRead()) {
            return CompletableFuture.completedFuture(false);
        }

        // add master as slave if no more slaves available
        if (!config.isSlaveNotUsed()
                && !masterEntry.getClient().getAddr().equals(entry.getClient().getAddr())
                    && slaveBalancer.getAvailableClients() == 0) {
            CompletableFuture<Boolean> f = slaveBalancer.unfreezeAsync(masterEntry.getClient().getAddr(), FreezeReason.SYSTEM);
            return f.thenApply(value -> {
                if (value) {
                    log.info("master {} used as slave", masterEntry.getClient().getAddr());
                }

                return nodeDown(entry);
            });
        }

        return CompletableFuture.completedFuture(nodeDown(entry));
    }

    public void masterDown() {
        nodeDown(masterEntry);
    }

    public boolean nodeDown(ClientConnectionsEntry entry) {
        entry.reset();
        
        for (RedisConnection connection : entry.getAllConnections()) {
            connection.closeAsync();
            reattachBlockingQueue(connection.getCurrentCommand());
        }
        while (true) {
            RedisConnection connection = entry.pollConnection(null);
            if (connection == null) {
                break;
            }
        }
        entry.getAllConnections().clear();

        for (RedisPubSubConnection connection : entry.getAllSubscribeConnections()) {
            connection.closeAsync();
            connectionManager.getSubscribeService().reattachPubSub(connection);
        }
        while (true) {
            RedisConnection connection = entry.pollSubscribeConnection();
            if (connection == null) {
                break;
            }
        }
        entry.getAllSubscribeConnections().clear();
        
        return true;
    }

    private void reattachBlockingQueue(CommandData<?, ?> commandData) {
        if (commandData == null
                || !commandData.isBlockingCommand()
                    || commandData.getPromise().isDone()) {
            return;
        }

        String key = null;
        for (int i = 0; i < commandData.getParams().length; i++) {
            Object param = commandData.getParams()[i];
            if ("STREAMS".equals(param)) {
                key = (String) commandData.getParams()[i+1];
                break;
            }
        }
        if (key == null) {
            key = (String) commandData.getParams()[0];
        }

        MasterSlaveEntry entry = connectionManager.getEntry(key);
        if (entry == null) {
            connectionManager.getServiceManager().newTimeout(timeout ->
                    reattachBlockingQueue(commandData), 1, TimeUnit.SECONDS);
            return;
        }

        CompletableFuture<RedisConnection> newConnectionFuture = entry.connectionWriteOp(commandData.getCommand());
        newConnectionFuture.whenComplete((newConnection, e) -> {
            if (e != null) {
                connectionManager.getServiceManager().newTimeout(timeout ->
                        reattachBlockingQueue(commandData), 1, TimeUnit.SECONDS);
                return;
            }

            if (commandData.getPromise().isDone()) {
                entry.releaseWrite(newConnection);
                return;
            }

            ChannelFuture channelFuture = newConnection.send(commandData);
            channelFuture.addListener(future -> {
                if (!future.isSuccess()) {
                    connectionManager.getServiceManager().newTimeout(timeout ->
                            reattachBlockingQueue(commandData), 1, TimeUnit.SECONDS);
                    return;
                }
                log.info("command '{}' has been resent to '{}'", commandData, newConnection.getRedisClient());
            });
            commandData.getPromise().whenComplete((r, ex) -> {
                entry.releaseWrite(newConnection);
            });
        });
    }
    
    public boolean hasSlave(RedisClient redisClient) {
        return slaveBalancer.contains(redisClient);
    }

    public boolean hasSlave(InetSocketAddress addr) {
        return slaveBalancer.contains(addr);
    }
    
    public boolean hasSlave(RedisURI addr) {
        return slaveBalancer.contains(addr);
    }

    public int getAvailableSlaves() {
        return slaveBalancer.getAvailableSlaves();
    }

    public int getAvailableClients() {
        return slaveBalancer.getAvailableClients();
    }

    public CompletableFuture<Void> addSlave(RedisURI address) {
        return addSlave(address, false, NodeType.SLAVE, null);
    }
    
    public CompletableFuture<Void> addSlave(InetSocketAddress address, RedisURI uri) {
        return addSlave(address, uri, false, NodeType.SLAVE, null);
    }

    public CompletableFuture<Void> addSlave(InetSocketAddress address, RedisURI uri, String sslHostname) {
        return addSlave(address, uri, false, NodeType.SLAVE, sslHostname);
    }

    public CompletableFuture<Void> addSlave(RedisClient client) {
        return addSlave(client, false, NodeType.SLAVE);
    }

    private CompletableFuture<Void> addSlave(RedisClient client, boolean freezed, NodeType nodeType) {
        noPubSubSlaves.set(false);
        CompletableFuture<InetSocketAddress> addrFuture = client.resolveAddr();
        return addrFuture.thenCompose(res -> {
            ClientConnectionsEntry entry = new ClientConnectionsEntry(client,
                    config.getSlaveConnectionMinimumIdleSize(),
                    config.getSlaveConnectionPoolSize(),
                    idleConnectionWatcher,
                    nodeType,
                    config);
            if (freezed) {
                synchronized (entry) {
                    entry.setFreezeReason(FreezeReason.SYSTEM);
                }
            }
            return slaveBalancer.add(entry);
        }).whenComplete((r, ex) -> {
            if (ex != null) {
                client.shutdownAsync();
            }
        });
    }

    private CompletableFuture<Void> addSlave(InetSocketAddress address, RedisURI uri, boolean freezed, NodeType nodeType, String sslHostname) {
        RedisClient client = connectionManager.createClient(NodeType.SLAVE, address, uri, sslHostname);
        return addSlave(client, freezed, nodeType);
    }
    
    public CompletableFuture<Void> addSlave(RedisURI address, boolean freezed, NodeType nodeType, String sslHostname) {
        RedisClient client = connectionManager.createClient(nodeType, address, sslHostname);
        return addSlave(client, freezed, nodeType);
    }

    public Collection<ClientConnectionsEntry> getAllEntries() {
        return slaveBalancer.getEntries();
    }
    
    public ClientConnectionsEntry getEntry(RedisClient redisClient) {
        return slaveBalancer.getEntry(redisClient);
    }

    public ClientConnectionsEntry getEntry(RedisURI addr) {
        return slaveBalancer.getEntry(addr);
    }

    public boolean isInit() {
        return masterEntry != null;
    }

    public RedisClient getClient() {
        return masterEntry.getClient();
    }

    public boolean slaveUp(ClientConnectionsEntry entry, FreezeReason freezeReason) {
        if (!slaveBalancer.unfreeze(entry, freezeReason)) {
            return false;
        }

        InetSocketAddress addr = masterEntry.getClient().getAddr();
        // exclude master from slaves
        if (!config.isSlaveNotUsed()
                && !addr.equals(entry.getClient().getAddr())) {
            if (slaveDown(addr, FreezeReason.SYSTEM)) {
                log.info("master {} excluded from slaves", addr);
            }
        }
        noPubSubSlaves.set(false);
        return true;
    }
    
    public boolean slaveUp(RedisURI address, FreezeReason freezeReason) {
        if (!slaveBalancer.unfreeze(address, freezeReason)) {
            return false;
        }

        InetSocketAddress addr = masterEntry.getClient().getAddr();
        // exclude master from slaves
        if (!config.isSlaveNotUsed()
                && !address.equals(addr)) {
            if (slaveDown(addr, FreezeReason.SYSTEM)) {
                log.info("master {} excluded from slaves", addr);
            }
        }
        noPubSubSlaves.set(false);
        return true;
    }

    public CompletableFuture<Boolean> excludeMasterFromSlaves(RedisURI address) {
        InetSocketAddress addr = masterEntry.getClient().getAddr();
        if (address.equals(addr)) {
            return CompletableFuture.completedFuture(false);
        }
        CompletableFuture<Boolean> downFuture = slaveDownAsync(addr, FreezeReason.SYSTEM);
        return downFuture.thenApply(r -> {
            if (r) {
                log.info("master {} excluded from slaves", addr);
            }
            return r;
        });
    }

    public CompletableFuture<Boolean> excludeMasterFromSlaves(InetSocketAddress address) {
        InetSocketAddress addr = masterEntry.getClient().getAddr();
        if (config.isSlaveNotUsed() || addr.equals(address)) {
            return CompletableFuture.completedFuture(false);
        }
        CompletableFuture<Boolean> downFuture = slaveDownAsync(addr, FreezeReason.SYSTEM);
        return downFuture.thenApply(r -> {
            if (r) {
                log.info("master {} excluded from slaves", addr);
            }
            return r;
        });
    }

    public CompletableFuture<Boolean> slaveUpAsync(RedisURI address, FreezeReason freezeReason) {
        noPubSubSlaves.set(false);
        return slaveBalancer.unfreezeAsync(address, freezeReason);
    }

    public CompletableFuture<Boolean> slaveUpAsync(InetSocketAddress address, FreezeReason freezeReason) {
        noPubSubSlaves.set(false);
        CompletableFuture<Boolean> f = slaveBalancer.unfreezeAsync(address, freezeReason);
        return f.thenCompose(r -> {
            if (r) {
                return excludeMasterFromSlaves(address);
            }
            return CompletableFuture.completedFuture(r);
        });
    }

    public boolean slaveUp(InetSocketAddress address, FreezeReason freezeReason) {
        if (!slaveBalancer.unfreeze(address, freezeReason)) {
            return false;
        }

        InetSocketAddress addr = masterEntry.getClient().getAddr();
        // exclude master from slaves
        if (!config.isSlaveNotUsed()
                && !addr.equals(address)) {
            if (slaveDown(addr, FreezeReason.SYSTEM)) {
                log.info("master {} excluded from slaves", addr);
            }
        }
        noPubSubSlaves.set(false);
        return true;
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
                    writeConnectionPool.remove(masterEntry);
                    pubSubConnectionPool.remove(masterEntry);
                    masterEntry.shutdownAsync();
                    masterEntry = oldMaster;
                }
                log.error("Unable to change master from: {} to: {}", oldMaster.getClient().getAddr(), address, e);
                return;
            }
            
            writeConnectionPool.remove(oldMaster);
            pubSubConnectionPool.remove(oldMaster);

            synchronized (oldMaster) {
                oldMaster.setFreezeReason(FreezeReason.MANAGER);
            }
            nodeDown(oldMaster);

            slaveBalancer.changeType(oldMaster.getClient().getAddr(), NodeType.SLAVE);
            slaveBalancer.changeType(newMasterClient.getAddr(), NodeType.MASTER);
            // freeze in slaveBalancer
            slaveDown(oldMaster.getClient().getAddr(), FreezeReason.MANAGER);

            // check if at least one slave is available, use master as slave if false
            if (!config.isSlaveNotUsed()) {
                useMasterAsSlave();
            }
            oldMaster.shutdownAsync();
            log.info("master {} has changed to {}", oldMaster.getClient().getAddr(), masterEntry.getClient().getAddr());
        });
    }

    public CompletableFuture<Void> shutdownAsync() {
        if (!active.compareAndSet(true, false)) {
            return CompletableFuture.completedFuture(null);
        }

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        if (masterEntry != null) {
            futures.add(masterEntry.shutdownAsync());
        }
        futures.add(slaveBalancer.shutdownAsync());

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    public CompletableFuture<RedisConnection> connectionWriteOp(RedisCommand<?> command) {
        return writeConnectionPool.get(command);
    }

    public CompletableFuture<RedisConnection> redirectedConnectionWriteOp(RedisCommand<?> command, RedisURI addr) {
        return slaveBalancer.getConnection(command, addr);
    }

    public CompletableFuture<RedisConnection> connectionReadOp(RedisCommand<?> command) {
        if (config.getReadMode() == ReadMode.MASTER) {
            return connectionWriteOp(command);
        }
        return slaveBalancer.nextConnection(command);
    }

    public CompletableFuture<RedisConnection> connectionReadOp(RedisCommand<?> command, RedisURI addr) {
        return slaveBalancer.getConnection(command, addr);
    }
    
    public CompletableFuture<RedisConnection> connectionReadOp(RedisCommand<?> command, RedisClient client) {
        if (config.getReadMode() == ReadMode.MASTER) {
            return connectionWriteOp(command);
        }
        return slaveBalancer.getConnection(command, client);
    }

    public CompletableFuture<RedisPubSubConnection> nextPubSubConnection(ClientConnectionsEntry entry) {
        if (entry != null) {
            return slaveBalancer.nextPubSubConnection(entry);
        }

        if (config.getSubscriptionMode() == SubscriptionMode.MASTER) {
            return pubSubConnectionPool.get();
        }

        if (noPubSubSlaves.get()) {
            return pubSubConnectionPool.get();
        }

        CompletableFuture<RedisPubSubConnection> future = slaveBalancer.nextPubSubConnection();
        return future.handle((r, e) -> {
            if (e != null) {
                if (noPubSubSlaves.compareAndSet(false, true)) {
                    log.warn("No slaves for master: {} PubSub connections established with the master node.",
                            masterEntry.getClient().getAddr(), e);
                }
                return pubSubConnectionPool.get();
            }

            return CompletableFuture.completedFuture(r);
        }).thenCompose(f -> f);
    }

    public void returnPubSubConnection(RedisPubSubConnection connection) {
        if (config.getSubscriptionMode() == SubscriptionMode.MASTER) {
            pubSubConnectionPool.returnConnection(masterEntry, connection);
            return;
        }
        slaveBalancer.returnPubSubConnection(connection);
    }

    public void releaseWrite(RedisConnection connection) {
        writeConnectionPool.returnConnection(masterEntry, connection);
    }

    public void releaseRead(RedisConnection connection) {
        if (config.getReadMode() == ReadMode.MASTER) {
            releaseWrite(connection);
            return;
        }
        slaveBalancer.returnConnection(connection);
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

}
