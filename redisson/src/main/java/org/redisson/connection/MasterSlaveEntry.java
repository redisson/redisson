/**
 * Copyright (c) 2013-2021 Nikita Koksharov
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
import org.redisson.api.RFuture;
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
import org.redisson.misc.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
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
    
    final String sslHostname;
    
    public MasterSlaveEntry(ConnectionManager connectionManager, MasterSlaveServersConfig config, String sslHostname) {
        this.connectionManager = connectionManager;
        this.config = config;

        slaveBalancer = new LoadBalancerManager(config, connectionManager, this);
        writeConnectionPool = new MasterConnectionPool(config, connectionManager, this);
        pubSubConnectionPool = new MasterPubSubConnectionPool(config, connectionManager, this);

        this.sslHostname = sslHostname;
    }

    public MasterSlaveServersConfig getConfig() {
        return config;
    }

    public List<RFuture<Void>> initSlaveBalancer(Collection<RedisURI> disconnectedNodes, RedisClient master) {
        boolean freezeMasterAsSlave = !config.getSlaveAddresses().isEmpty()
                    && !config.checkSkipSlavesInit()
                        && disconnectedNodes.size() < config.getSlaveAddresses().size();

        List<RFuture<Void>> result = new LinkedList<RFuture<Void>>();
        RFuture<Void> f = addSlave(master.getAddr(), master.getConfig().getAddress(), freezeMasterAsSlave, NodeType.MASTER);
        result.add(f);
        for (String address : config.getSlaveAddresses()) {
            RedisURI uri = new RedisURI(address);
            f = addSlave(uri, disconnectedNodes.contains(uri), NodeType.SLAVE);
            result.add(f);
        }
        return result;
    }

    public RFuture<RedisClient> setupMasterEntry(InetSocketAddress address, RedisURI uri) {
        RedisClient client = connectionManager.createClient(NodeType.MASTER, address, uri, sslHostname);
        return setupMasterEntry(client);
    }
    

    public RFuture<RedisClient> setupMasterEntry(RedisURI address) {
        RedisClient client = connectionManager.createClient(NodeType.MASTER, address, sslHostname);
        return setupMasterEntry(client);
    }

    private RFuture<RedisClient> setupMasterEntry(RedisClient client) {
        RPromise<RedisClient> result = new RedissonPromise<RedisClient>();
        result.onComplete((res, e) -> {
            if (e != null) {
                client.shutdownAsync();
            }
        });
        RFuture<InetSocketAddress> addrFuture = client.resolveAddr();
        addrFuture.onComplete((res, e) -> {
            if (e != null) {
                result.tryFailure(e);
                return;
            }
            
            masterEntry = new ClientConnectionsEntry(
                    client,
                    config.getMasterConnectionMinimumIdleSize(),
                    config.getMasterConnectionPoolSize(),
                    config.getSubscriptionConnectionMinimumIdleSize(),
                    config.getSubscriptionConnectionPoolSize(),
                    connectionManager,
                    NodeType.MASTER);
    
            int counter = 1;
            if (config.getSubscriptionMode() == SubscriptionMode.MASTER) {
                counter++;
            }
            
            CountableListener<RedisClient> listener = new CountableListener<>(result, client, counter);
            RFuture<Void> writeFuture = writeConnectionPool.add(masterEntry);
            writeFuture.onComplete(listener);
            
            if (config.getSubscriptionMode() == SubscriptionMode.MASTER) {
                RFuture<Void> pubSubFuture = pubSubConnectionPool.add(masterEntry);
                pubSubFuture.onComplete(listener);
            }
        });
        
        return result;
    }

    public boolean slaveDown(ClientConnectionsEntry entry, FreezeReason freezeReason) {
        ClientConnectionsEntry e = slaveBalancer.freeze(entry, freezeReason);
        if (e == null) {
            return false;
        }
        
        return slaveDown(entry);
    }
    
    public boolean slaveDown(InetSocketAddress address, FreezeReason freezeReason) {
        ClientConnectionsEntry entry = slaveBalancer.freeze(address, freezeReason);
        if (entry == null) {
            return false;
        }
        
        return slaveDown(entry);
    }
    
    public boolean slaveDown(RedisURI address, FreezeReason freezeReason) {
        ClientConnectionsEntry entry = slaveBalancer.freeze(address, freezeReason);
        if (entry == null) {
            return false;
        }
        
        return slaveDown(entry);
    }

    private boolean slaveDown(ClientConnectionsEntry entry) {
        if (entry.isMasterForRead()) {
            return false;
        }

        // add master as slave if no more slaves available
        if (!config.checkSkipSlavesInit() && slaveBalancer.getAvailableClients() == 0) {
            if (slaveBalancer.unfreeze(masterEntry.getClient().getAddr(), FreezeReason.SYSTEM)) {
                log.info("master {} used as slave", masterEntry.getClient().getAddr());
            }
        }

        return nodeDown(entry);
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
            connectionManager.newTimeout(timeout ->
                    reattachBlockingQueue(commandData), 1, TimeUnit.SECONDS);
            return;
        }

        RFuture<RedisConnection> newConnectionFuture = entry.connectionWriteOp(commandData.getCommand());
        newConnectionFuture.onComplete((newConnection, e) -> {
            if (e != null) {
                connectionManager.newTimeout(timeout ->
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
                    connectionManager.newTimeout(timeout ->
                            reattachBlockingQueue(commandData), 1, TimeUnit.SECONDS);
                }
            });
            commandData.getPromise().onComplete((r, ex) -> {
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

    public RFuture<Void> addSlave(RedisURI address) {
        return addSlave(address, false, NodeType.SLAVE);
    }
    
    public RFuture<Void> addSlave(InetSocketAddress address, RedisURI uri) {
        return addSlave(address, uri, false, NodeType.SLAVE);
    }
        
    private RFuture<Void> addSlave(RedisClient client, boolean freezed, NodeType nodeType) {
        RPromise<Void> result = new RedissonPromise<Void>();
        RFuture<InetSocketAddress> addrFuture = client.resolveAddr();
        addrFuture.onComplete((res, e) -> {
            if (e != null) {
                result.tryFailure(e);
                return;
            }

            ClientConnectionsEntry entry = new ClientConnectionsEntry(client,
                    config.getSlaveConnectionMinimumIdleSize(),
                    config.getSlaveConnectionPoolSize(),
                    config.getSubscriptionConnectionMinimumIdleSize(),
                    config.getSubscriptionConnectionPoolSize(), connectionManager, nodeType);
            if (freezed) {
                synchronized (entry) {
                    entry.setFreezeReason(FreezeReason.SYSTEM);
                }
            }
            RFuture<Void> addFuture = slaveBalancer.add(entry);
            addFuture.onComplete((r, ex) -> {
                if (ex != null) {
                    client.shutdownAsync();
                }
            });
            addFuture.onComplete(new TransferListener<Void>(result));
        });
        return result;
    }

    private RFuture<Void> addSlave(InetSocketAddress address, RedisURI uri, boolean freezed, NodeType nodeType) {
        RedisClient client = connectionManager.createClient(NodeType.SLAVE, address, uri, sslHostname);
        return addSlave(client, freezed, nodeType);
    }
    
    private RFuture<Void> addSlave(RedisURI address, boolean freezed, NodeType nodeType) {
        RedisClient client = connectionManager.createClient(nodeType, address, sslHostname);
        return addSlave(client, freezed, nodeType);
    }

    public Collection<ClientConnectionsEntry> getAllEntries() {
        return slaveBalancer.getEntries();
    }
    
    public ClientConnectionsEntry getEntry(RedisClient redisClient) {
        return slaveBalancer.getEntry(redisClient);
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
        if (!config.checkSkipSlavesInit()
                && !addr.equals(entry.getClient().getAddr())) {
            if (slaveDown(addr, FreezeReason.SYSTEM)) {
                log.info("master {} excluded from slaves", addr);
            }
        }
        return true;
    }
    
    public boolean isSlaveUnfreezed(RedisURI address) {
        return slaveBalancer.isUnfreezed(address);
    }
    
    public boolean slaveUp(RedisURI address, FreezeReason freezeReason) {
        if (!slaveBalancer.unfreeze(address, freezeReason)) {
            return false;
        }

        InetSocketAddress addr = masterEntry.getClient().getAddr();
        // exclude master from slaves
        if (!config.checkSkipSlavesInit()
                && !RedisURI.compare(addr, address)) {
            if (slaveDown(addr, FreezeReason.SYSTEM)) {
                log.info("master {} excluded from slaves", addr);
            }
        }
        return true;
    }
    
    public boolean slaveUp(InetSocketAddress address, FreezeReason freezeReason) {
        if (!slaveBalancer.unfreeze(address, freezeReason)) {
            return false;
        }

        InetSocketAddress addr = masterEntry.getClient().getAddr();
        // exclude master from slaves
        if (!config.checkSkipSlavesInit()
                && !addr.equals(address)) {
            if (slaveDown(addr, FreezeReason.SYSTEM)) {
                log.info("master {} excluded from slaves", addr);
            }
        }
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
    public RFuture<RedisClient> changeMaster(RedisURI address) {
        ClientConnectionsEntry oldMaster = masterEntry;
        RFuture<RedisClient> future = setupMasterEntry(address);
        changeMaster(address, oldMaster, future);
        return future;
    }
    
    public RFuture<RedisClient> changeMaster(InetSocketAddress address, RedisURI uri) {
        ClientConnectionsEntry oldMaster = masterEntry;
        RFuture<RedisClient> future = setupMasterEntry(address, uri);
        changeMaster(uri, oldMaster, future);
        return future;
    }


    private void changeMaster(RedisURI address, ClientConnectionsEntry oldMaster,
            RFuture<RedisClient> future) {
        future.onComplete((newMasterClient, e) -> {
            if (e != null) {
                if (oldMaster != masterEntry) {
                    writeConnectionPool.remove(masterEntry);
                    pubSubConnectionPool.remove(masterEntry);
                    masterEntry.shutdownAsync();
                    masterEntry = oldMaster;
                }
                log.error("Unable to change master from: " + oldMaster.getClient().getAddr() + " to: " + address, e);
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

            // more than one slave available, so master can be removed from slaves
            if (!config.checkSkipSlavesInit()
                    && slaveBalancer.getAvailableClients() > 1) {
                slaveDown(newMasterClient.getAddr(), FreezeReason.SYSTEM);
            }
            oldMaster.shutdownAsync();
            log.info("master {} has changed to {}", oldMaster.getClient().getAddr(), masterEntry.getClient().getAddr());
        });
    }

    public RFuture<Void> shutdownAsync() {
        if (!active.compareAndSet(true, false)) {
            return RedissonPromise.<Void>newSucceededFuture(null);
        }

        RPromise<Void> result = new RedissonPromise<Void>();
        CountableListener<Void> listener = new CountableListener<Void>(result, null, 2);
        if (masterEntry != null) {
            masterEntry.shutdownAsync().onComplete(listener);
        }
        slaveBalancer.shutdownAsync().onComplete(listener);
        return result;
    }

    public RFuture<RedisConnection> connectionWriteOp(RedisCommand<?> command) {
        return writeConnectionPool.get(command);
    }

    public RFuture<RedisConnection> redirectedConnectionWriteOp(RedisCommand<?> command, RedisURI addr) {
        return slaveBalancer.getConnection(command, addr);
    }

    public RFuture<RedisConnection> connectionReadOp(RedisCommand<?> command) {
        if (config.getReadMode() == ReadMode.MASTER) {
            return connectionWriteOp(command);
        }
        return slaveBalancer.nextConnection(command);
    }

    public RFuture<RedisConnection> connectionReadOp(RedisCommand<?> command, RedisURI addr) {
        return slaveBalancer.getConnection(command, addr);
    }
    
    public RFuture<RedisConnection> connectionReadOp(RedisCommand<?> command, RedisClient client) {
        if (config.getReadMode() == ReadMode.MASTER) {
            return connectionWriteOp(command);
        }
        return slaveBalancer.getConnection(command, client);
    }

    public RFuture<RedisPubSubConnection> nextPubSubConnection() {
        if (config.getSubscriptionMode() == SubscriptionMode.MASTER) {
            return pubSubConnectionPool.get();
        }
        
        return slaveBalancer.nextPubSubConnection();
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
