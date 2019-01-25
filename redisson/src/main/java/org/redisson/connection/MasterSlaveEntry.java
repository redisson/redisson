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
package org.redisson.connection;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.redisson.api.NodeType;
import org.redisson.api.RFuture;
import org.redisson.client.RedisClient;
import org.redisson.client.RedisConnection;
import org.redisson.client.RedisPubSubConnection;
import org.redisson.client.protocol.CommandData;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.cluster.ClusterConnectionManager;
import org.redisson.config.MasterSlaveServersConfig;
import org.redisson.config.ReadMode;
import org.redisson.config.SubscriptionMode;
import org.redisson.connection.ClientConnectionsEntry.FreezeReason;
import org.redisson.connection.balancer.LoadBalancerManager;
import org.redisson.connection.pool.MasterConnectionPool;
import org.redisson.connection.pool.MasterPubSubConnectionPool;
import org.redisson.misc.CountableListener;
import org.redisson.misc.RPromise;
import org.redisson.misc.RedissonPromise;
import org.redisson.misc.TransferListener;
import org.redisson.misc.URIBuilder;
import org.redisson.pubsub.PubSubConnectionEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;

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
    
    String sslHostname;
    
    public MasterSlaveEntry(ConnectionManager connectionManager, MasterSlaveServersConfig config) {
        this.connectionManager = connectionManager;
        this.config = config;

        slaveBalancer = new LoadBalancerManager(config, connectionManager, this);
        writeConnectionPool = new MasterConnectionPool(config, connectionManager, this);
        pubSubConnectionPool = new MasterPubSubConnectionPool(config, connectionManager, this);
        
        if (connectionManager instanceof ClusterConnectionManager) {
            sslHostname = ((ClusterConnectionManager) connectionManager).getConfigEndpointHostName();
        }
    }

    public MasterSlaveServersConfig getConfig() {
        return config;
    }

    public List<RFuture<Void>> initSlaveBalancer(Collection<URI> disconnectedNodes) {
        boolean freezeMasterAsSlave = !config.getSlaveAddresses().isEmpty()
                    && !config.checkSkipSlavesInit()
                        && disconnectedNodes.size() < config.getSlaveAddresses().size();

        List<RFuture<Void>> result = new LinkedList<RFuture<Void>>();
        RFuture<Void> f = addSlave(config.getMasterAddress(), freezeMasterAsSlave, NodeType.MASTER);
        result.add(f);
        for (URI address : config.getSlaveAddresses()) {
            f = addSlave(address, disconnectedNodes.contains(address), NodeType.SLAVE);
            result.add(f);
        }
        return result;
    }

    public RFuture<RedisClient> setupMasterEntry(InetSocketAddress address, URI uri) {
        RedisClient client = connectionManager.createClient(NodeType.MASTER, address, uri, sslHostname);
        return setupMasterEntry(client);
    }
    

    public RFuture<RedisClient> setupMasterEntry(URI address) {
        RedisClient client = connectionManager.createClient(NodeType.MASTER, address, sslHostname);
        return setupMasterEntry(client);
    }

    private RFuture<RedisClient> setupMasterEntry(final RedisClient client) {
        final RPromise<RedisClient> result = new RedissonPromise<RedisClient>();
        RFuture<InetSocketAddress> addrFuture = client.resolveAddr();
        addrFuture.addListener(new FutureListener<InetSocketAddress>() {

            @Override
            public void operationComplete(Future<InetSocketAddress> future) throws Exception {
                if (!future.isSuccess()) {
                    client.shutdownAsync();
                    result.tryFailure(future.cause());
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
                
                CountableListener<RedisClient> listener = new CountableListener<RedisClient>(result, client, counter);
                RFuture<Void> writeFuture = writeConnectionPool.add(masterEntry);
                writeFuture.addListener(listener);
                
                if (config.getSubscriptionMode() == SubscriptionMode.MASTER) {
                    RFuture<Void> pubSubFuture = pubSubConnectionPool.add(masterEntry);
                    pubSubFuture.addListener(listener);
                }
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
    
    public boolean slaveDown(URI address, FreezeReason freezeReason) {
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
        
        entry.reset();
        
        for (RedisConnection connection : entry.getAllConnections()) {
            connection.closeAsync();
            reattachBlockingQueue(connection);
        }
        while (true) {
            RedisConnection connection = entry.pollConnection();
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

    private void reattachBlockingQueue(RedisConnection connection) {
        final CommandData<?, ?> commandData = connection.getCurrentCommand();

        if (commandData == null 
                || !commandData.isBlockingCommand()
                    || commandData.getPromise().isDone()) {
            return;
        }

        RFuture<RedisConnection> newConnection = connectionWriteOp(commandData.getCommand());
        newConnection.addListener(new FutureListener<RedisConnection>() {
            @Override
            public void operationComplete(Future<RedisConnection> future) throws Exception {
                if (!future.isSuccess()) {
                    log.error("Can't resubscribe blocking queue {}", commandData);
                    return;
                }

                final RedisConnection newConnection = future.getNow();
                    
                final FutureListener<Object> listener = new FutureListener<Object>() {
                    @Override
                    public void operationComplete(Future<Object> future) throws Exception {
                        releaseWrite(newConnection);
                    }
                };
                commandData.getPromise().addListener(listener);
                if (commandData.getPromise().isDone()) {
                    return;
                }
                ChannelFuture channelFuture = newConnection.send(commandData);
                channelFuture.addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        if (!future.isSuccess()) {
                            listener.operationComplete(null);
                            commandData.getPromise().removeListener(listener);
                            releaseWrite(newConnection);
                            log.error("Can't resubscribe blocking queue {}", commandData);
                        }
                    }
                });
            }
        });
    }
    
    public boolean hasSlave(RedisClient redisClient) {
        return slaveBalancer.contains(redisClient);
    }

    public boolean hasSlave(InetSocketAddress addr) {
        return slaveBalancer.contains(addr);
    }
    
    public boolean hasSlave(URI addr) {
        return slaveBalancer.contains(addr);
    }

    public int getAvailableClients() {
        return slaveBalancer.getAvailableClients();
    }

    public RFuture<Void> addSlave(URI address) {
        return addSlave(address, false, NodeType.SLAVE);
    }
    
    public RFuture<Void> addSlave(InetSocketAddress address, URI uri) {
        return addSlave(address, uri, false, NodeType.SLAVE);
    }
        
    private RFuture<Void> addSlave(final RedisClient client, final boolean freezed, final NodeType nodeType) {
        final RPromise<Void> result = new RedissonPromise<Void>();
        RFuture<InetSocketAddress> addrFuture = client.resolveAddr();
        addrFuture.addListener(new FutureListener<InetSocketAddress>() {
            @Override
            public void operationComplete(Future<InetSocketAddress> future) throws Exception {
                if (!future.isSuccess()) {
                    result.tryFailure(future.cause());
                    return;
    }

                ClientConnectionsEntry entry = new ClientConnectionsEntry(client,
                        config.getSlaveConnectionMinimumIdleSize(),
                        config.getSlaveConnectionPoolSize(),
                        config.getSubscriptionConnectionMinimumIdleSize(),
                        config.getSubscriptionConnectionPoolSize(), connectionManager, nodeType);
                if (freezed) {
                    synchronized (entry) {
                        entry.setFreezed(freezed);
                        entry.setFreezeReason(FreezeReason.SYSTEM);
                    }
                }
                RFuture<Void> addFuture = slaveBalancer.add(entry);
                addFuture.addListener(new FutureListener<Void>() {
                    @Override
                    public void operationComplete(Future<Void> future) throws Exception {
                        if (!future.isSuccess()) {
                            client.shutdownAsync();
                        }
                    }
                });
                addFuture.addListener(new TransferListener<Void>(result));
            }
        });
        return result;
    }

    private RFuture<Void> addSlave(InetSocketAddress address, URI uri, final boolean freezed, final NodeType nodeType) {
        RedisClient client = connectionManager.createClient(NodeType.SLAVE, address, uri, sslHostname);
        return addSlave(client, freezed, nodeType);
    }
    
    private RFuture<Void> addSlave(URI address, final boolean freezed, final NodeType nodeType) {
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
    
    public boolean isSlaveUnfreezed(URI address) {
        return slaveBalancer.isUnfreezed(address);
    }
    
    public boolean slaveUp(URI address, FreezeReason freezeReason) {
        if (!slaveBalancer.unfreeze(address, freezeReason)) {
            return false;
        }

        InetSocketAddress addr = masterEntry.getClient().getAddr();
        // exclude master from slaves
        if (!config.checkSkipSlavesInit()
                && !URIBuilder.compare(addr, address)) {
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
    public RFuture<RedisClient> changeMaster(URI address) {
        final ClientConnectionsEntry oldMaster = masterEntry;
        RFuture<RedisClient> future = setupMasterEntry(address);
        changeMaster(address, oldMaster, future);
        return future;
    }
    
    public void changeMaster(InetSocketAddress address, URI uri) {
        final ClientConnectionsEntry oldMaster = masterEntry;
        RFuture<RedisClient> future = setupMasterEntry(address, uri);
        changeMaster(uri, oldMaster, future);
    }


    private void changeMaster(final URI address, final ClientConnectionsEntry oldMaster,
            RFuture<RedisClient> future) {
        future.addListener(new FutureListener<RedisClient>() {
            @Override
            public void operationComplete(Future<RedisClient> future) throws Exception {
                if (!future.isSuccess()) {
                    if (oldMaster != masterEntry) {
                        writeConnectionPool.remove(masterEntry);
                        pubSubConnectionPool.remove(masterEntry);
                        masterEntry.getClient().shutdownAsync();
                        masterEntry = oldMaster;
                    }
                    log.error("Unable to change master from: " + oldMaster.getClient().getAddr() + " to: " + address, future.cause());
                    return;
                }

                RedisClient newMasterClient = future.getNow();
                
                writeConnectionPool.remove(oldMaster);
                pubSubConnectionPool.remove(oldMaster);
                
                oldMaster.freezeMaster(FreezeReason.MANAGER);
                slaveDown(oldMaster);

                slaveBalancer.changeType(oldMaster.getClient().getAddr(), NodeType.SLAVE);
                slaveBalancer.changeType(newMasterClient.getAddr(), NodeType.MASTER);
                // freeze in slaveBalancer
                slaveDown(oldMaster.getClient().getAddr(), FreezeReason.MANAGER);

                // more than one slave available, so master can be removed from slaves
                if (!config.checkSkipSlavesInit()
                        && slaveBalancer.getAvailableClients() > 1) {
                    slaveDown(newMasterClient.getAddr(), FreezeReason.SYSTEM);
                }
                oldMaster.getClient().shutdownAsync();
                log.info("master {} has changed to {}", oldMaster.getClient().getAddr(), masterEntry.getClient().getAddr());
            }
        });
    }

    public RFuture<Void> shutdownAsync() {
        if (!active.compareAndSet(true, false)) {
            return RedissonPromise.<Void>newSucceededFuture(null);
        }

        RPromise<Void> result = new RedissonPromise<Void>();
        CountableListener<Void> listener = new CountableListener<Void>(result, null, 2);
        masterEntry.getClient().shutdownAsync().addListener(listener);
        slaveBalancer.shutdownAsync().addListener(listener);
        return result;
    }

    public RFuture<RedisConnection> connectionWriteOp(RedisCommand<?> command) {
        return writeConnectionPool.get(command);
    }

    public RFuture<RedisConnection> redirectedConnectionWriteOp(RedisCommand<?> command, URI addr) {
        return slaveBalancer.getConnection(command, addr);
    }

    public RFuture<RedisConnection> connectionReadOp(RedisCommand<?> command) {
        if (config.getReadMode() == ReadMode.MASTER) {
            return connectionWriteOp(command);
        }
        return slaveBalancer.nextConnection(command);
    }

    public RFuture<RedisConnection> connectionReadOp(RedisCommand<?> command, URI addr) {
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

    public void returnPubSubConnection(PubSubConnectionEntry entry) {
        if (config.getSubscriptionMode() == SubscriptionMode.MASTER) {
            pubSubConnectionPool.returnConnection(masterEntry, entry.getConnection());
            return;
        }
        slaveBalancer.returnPubSubConnection(entry.getConnection());
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
