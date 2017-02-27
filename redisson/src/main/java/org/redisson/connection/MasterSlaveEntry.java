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
package org.redisson.connection;

import java.net.InetSocketAddress;
import java.net.URL;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.redisson.api.NodeType;
import org.redisson.api.RFuture;
import org.redisson.client.RedisClient;
import org.redisson.client.RedisConnection;
import org.redisson.client.RedisPubSubConnection;
import org.redisson.client.RedisPubSubListener;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.CommandData;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.cluster.ClusterSlotRange;
import org.redisson.config.MasterSlaveServersConfig;
import org.redisson.config.ReadMode;
import org.redisson.config.SubscriptionMode;
import org.redisson.connection.ClientConnectionsEntry.FreezeReason;
import org.redisson.connection.balancer.LoadBalancerManager;
import org.redisson.connection.pool.MasterConnectionPool;
import org.redisson.connection.pool.MasterPubSubConnectionPool;
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

    final MasterSlaveServersConfig config;
    final ConnectionManager connectionManager;

    final MasterConnectionPool writeConnectionHolder;
    final Set<Integer> slots = new HashSet<Integer>();
    
    final MasterPubSubConnectionPool pubSubConnectionHolder;

    final AtomicBoolean active = new AtomicBoolean(true);

    public MasterSlaveEntry(Set<ClusterSlotRange> slotRanges, ConnectionManager connectionManager, MasterSlaveServersConfig config) {
        for (ClusterSlotRange clusterSlotRange : slotRanges) {
            for (int i = clusterSlotRange.getStartSlot(); i < clusterSlotRange.getEndSlot() + 1; i++) {
                slots.add(i);
            }
        }
        this.connectionManager = connectionManager;
        this.config = config;

        slaveBalancer = new LoadBalancerManager(config, connectionManager, this);
        writeConnectionHolder = new MasterConnectionPool(config, connectionManager, this);
        pubSubConnectionHolder = new MasterPubSubConnectionPool(config, connectionManager, this);
    }

    public List<RFuture<Void>> initSlaveBalancer(Collection<URL> disconnectedNodes) {
        boolean freezeMasterAsSlave = !config.getSlaveAddresses().isEmpty()
                    && config.getReadMode() == ReadMode.SLAVE
                        && disconnectedNodes.size() < config.getSlaveAddresses().size();

        List<RFuture<Void>> result = new LinkedList<RFuture<Void>>();
        RFuture<Void> f = addSlave(config.getMasterAddress().getHost(), config.getMasterAddress().getPort(), freezeMasterAsSlave, NodeType.MASTER);
        result.add(f);
        for (URL address : config.getSlaveAddresses()) {
            f = addSlave(address.getHost(), address.getPort(), disconnectedNodes.contains(address), NodeType.SLAVE);
            result.add(f);
        }
        return result;
    }

    public RFuture<Void> setupMasterEntry(String host, int port) {
        RedisClient client = connectionManager.createClient(NodeType.MASTER, host, port);
        masterEntry = new ClientConnectionsEntry(
                client, 
                config.getMasterConnectionMinimumIdleSize(), 
                config.getMasterConnectionPoolSize(),
                config.getSubscriptionConnectionMinimumIdleSize(),
                config.getSubscriptionConnectionPoolSize(), 
                connectionManager, 
                NodeType.MASTER);
        
        if (config.getSubscriptionMode() == SubscriptionMode.MASTER) {
            RFuture<Void> f = writeConnectionHolder.add(masterEntry);
            RFuture<Void> s = pubSubConnectionHolder.add(masterEntry);
            return CountListener.create(s, f);
        }
        return writeConnectionHolder.add(masterEntry);
    }

    private boolean slaveDown(ClientConnectionsEntry entry, FreezeReason freezeReason) {
        ClientConnectionsEntry e = slaveBalancer.freeze(entry, freezeReason);
        if (e == null) {
            return false;
        }
        
        return slaveDown(e, freezeReason == FreezeReason.SYSTEM);
    }
    
    public boolean slaveDown(String host, int port, FreezeReason freezeReason) {
        ClientConnectionsEntry entry = slaveBalancer.freeze(host, port, freezeReason);
        if (entry == null) {
            return false;
        }
        
        return slaveDown(entry, freezeReason == FreezeReason.SYSTEM);
    }

    private boolean slaveDown(ClientConnectionsEntry entry, boolean temporaryDown) {
        // add master as slave if no more slaves available
        if (config.getReadMode() == ReadMode.SLAVE && slaveBalancer.getAvailableClients() == 0) {
            InetSocketAddress addr = masterEntry.getClient().getAddr();
            if (slaveUp(addr.getHostName(), addr.getPort(), FreezeReason.SYSTEM)) {
                log.info("master {}:{} used as slave", addr.getHostName(), addr.getPort());
            }
        }
        
        // close all connections
        while (true) {
            final RedisConnection connection = entry.pollConnection();
            if (connection == null) {
                break;
            }
           
            connection.closeAsync().addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    reattachBlockingQueue(connection);
                }
            });
        }

        // close all pub/sub connections
        while (true) {
            RedisPubSubConnection connection = entry.pollSubscribeConnection();
            if (connection == null) {
                break;
            }
            connection.closeAsync();
        }
        
        for (RedisPubSubConnection connection : entry.getAllSubscribeConnections()) {
            reattachPubSub(connection, temporaryDown);
        }
        entry.getAllSubscribeConnections().clear();
        
        return true;
    }
    
    private void reattachPubSub(RedisPubSubConnection redisPubSubConnection, boolean temporaryDown) {
        for (String channelName : redisPubSubConnection.getChannels().keySet()) {
            PubSubConnectionEntry pubSubEntry = connectionManager.getPubSubEntry(channelName);
            Collection<RedisPubSubListener<?>> listeners = pubSubEntry.getListeners(channelName);
            reattachPubSubListeners(channelName, listeners, temporaryDown);
        }

        for (String channelName : redisPubSubConnection.getPatternChannels().keySet()) {
            PubSubConnectionEntry pubSubEntry = connectionManager.getPubSubEntry(channelName);
            Collection<RedisPubSubListener<?>> listeners = pubSubEntry.getListeners(channelName);
            reattachPatternPubSubListeners(channelName, listeners, temporaryDown);
        }
    }

    private void reattachPubSubListeners(final String channelName, final Collection<RedisPubSubListener<?>> listeners, boolean temporaryDown) {
        RFuture<Codec> subscribeCodec = connectionManager.unsubscribe(channelName, temporaryDown);
        if (listeners.isEmpty()) {
            return;
        }
        
        subscribeCodec.addListener(new FutureListener<Codec>() {
            @Override
            public void operationComplete(Future<Codec> future) throws Exception {
                Codec subscribeCodec = future.get();
                subscribe(channelName, listeners, subscribeCodec);
            }

        });
    }

    private void subscribe(final String channelName, final Collection<RedisPubSubListener<?>> listeners,
            final Codec subscribeCodec) {
        RFuture<PubSubConnectionEntry> subscribeFuture = connectionManager.subscribe(subscribeCodec, channelName, null);
        subscribeFuture.addListener(new FutureListener<PubSubConnectionEntry>() {
            
            @Override
            public void operationComplete(Future<PubSubConnectionEntry> future)
                    throws Exception {
                if (!future.isSuccess()) {
                    subscribe(channelName, listeners, subscribeCodec);
                    return;
                }
                PubSubConnectionEntry newEntry = future.getNow();
                for (RedisPubSubListener<?> redisPubSubListener : listeners) {
                    newEntry.addListener(channelName, redisPubSubListener);
                }
                log.debug("resubscribed listeners of '{}' channel to {}", channelName, newEntry.getConnection().getRedisClient());
            }
        });
    }

    private void reattachPatternPubSubListeners(final String channelName, final Collection<RedisPubSubListener<?>> listeners, boolean temporaryDown) {
        RFuture<Codec> subscribeCodec = connectionManager.punsubscribe(channelName, temporaryDown);
        if (listeners.isEmpty()) {
            return;
        }
        
        subscribeCodec.addListener(new FutureListener<Codec>() {
            @Override
            public void operationComplete(Future<Codec> future) throws Exception {
                Codec subscribeCodec = future.get();
                psubscribe(channelName, listeners, subscribeCodec);
            }
        });
    }
    
    private void psubscribe(final String channelName, final Collection<RedisPubSubListener<?>> listeners,
            final Codec subscribeCodec) {
        RFuture<PubSubConnectionEntry> subscribeFuture = connectionManager.psubscribe(channelName, subscribeCodec, null);
        subscribeFuture.addListener(new FutureListener<PubSubConnectionEntry>() {
            @Override
            public void operationComplete(Future<PubSubConnectionEntry> future)
                    throws Exception {
                if (!future.isSuccess()) {
                    psubscribe(channelName, listeners, subscribeCodec);
                    return;
                }
                
                PubSubConnectionEntry newEntry = future.getNow();
                for (RedisPubSubListener<?> redisPubSubListener : listeners) {
                    newEntry.addListener(channelName, redisPubSubListener);
                }
                log.debug("resubscribed listeners for '{}' channel-pattern", channelName);
            }
        });
    }

    private void reattachBlockingQueue(RedisConnection connection) {
        final CommandData<?, ?> commandData = connection.getCurrentCommand();

        if (commandData == null 
                || !commandData.isBlockingCommand()) {
            return;
        }

        RFuture<RedisConnection> newConnection = connectionReadOp(RedisCommands.BLPOP_VALUE);
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
                        releaseRead(newConnection);
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
                            releaseRead(newConnection);
                            log.error("Can't resubscribe blocking queue {}", commandData);
                        }
                    }
                });
            }
        });
    }
    
    public boolean hasSlave(InetSocketAddress addr) {
        return slaveBalancer.contains(addr);
    }

    public RFuture<Void> addSlave(String host, int port) {
        return addSlave(host, port, true, NodeType.SLAVE);
    }
    
    private RFuture<Void> addSlave(String host, int port, boolean freezed, NodeType mode) {
        RedisClient client = connectionManager.createClient(NodeType.SLAVE, host, port);
        ClientConnectionsEntry entry = new ClientConnectionsEntry(client,
                this.config.getSlaveConnectionMinimumIdleSize(),
                this.config.getSlaveConnectionPoolSize(),
                this.config.getSubscriptionConnectionMinimumIdleSize(),
                this.config.getSubscriptionConnectionPoolSize(), connectionManager, mode);
        if (freezed) {
            synchronized (entry) {
                entry.setFreezed(freezed);
                entry.setFreezeReason(FreezeReason.SYSTEM);
            }
        }
        return slaveBalancer.add(entry);
    }

    public RedisClient getClient() {
        return masterEntry.getClient();
    }

    public boolean slaveUp(String host, int port, FreezeReason freezeReason) {
        if (!slaveBalancer.unfreeze(host, port, freezeReason)) {
            return false;
        }

        InetSocketAddress addr = masterEntry.getClient().getAddr();
        // exclude master from slaves
        if (config.getReadMode() == ReadMode.SLAVE
                && (!addr.getHostName().equals(host) || port != addr.getPort())) {
            slaveDown(addr.getHostName(), addr.getPort(), FreezeReason.SYSTEM);
            log.info("master {}:{} excluded from slaves", addr.getHostName(), addr.getPort());
        }
        return true;
    }

    /**
     * Freeze slave with <code>host:port</code> from slaves list.
     * Re-attach pub/sub listeners from it to other slave.
     * Shutdown old master client.
     * 
     * @param host of Redis
     * @param port of Redis
     */
    public void changeMaster(final String host, final int port) {
        final ClientConnectionsEntry oldMaster = masterEntry;
        RFuture<Void> future = setupMasterEntry(host, port);
        future.addListener(new FutureListener<Void>() {
            @Override
            public void operationComplete(Future<Void> future) throws Exception {
                writeConnectionHolder.remove(oldMaster);
                pubSubConnectionHolder.remove(oldMaster);
                slaveDown(oldMaster, FreezeReason.MANAGER);

                // more than one slave available, so master can be removed from slaves
                if (config.getReadMode() == ReadMode.SLAVE
                        && slaveBalancer.getAvailableClients() > 1) {
                    slaveDown(host, port, FreezeReason.SYSTEM);
                }
                connectionManager.shutdownAsync(oldMaster.getClient());
            }
        });
    }

    public boolean isFreezed() {
        return masterEntry.isFreezed();
    }

    public FreezeReason getFreezeReason() {
        return masterEntry.getFreezeReason();
    }

    public void freeze() {
        masterEntry.freezeMaster(FreezeReason.MANAGER);
    }

    public void unfreeze() {
        masterEntry.resetFailedAttempts();
        synchronized (masterEntry) {
            masterEntry.setFreezed(false);
            masterEntry.setFreezeReason(null);
        }
    }

    public void shutdownMasterAsync() {
        if (!active.compareAndSet(true, false)) {
            return;
        }

        connectionManager.shutdownAsync(masterEntry.getClient());
        slaveBalancer.shutdownAsync();
    }

    public RFuture<RedisConnection> connectionWriteOp(RedisCommand<?> command) {
        return writeConnectionHolder.get(command);
    }

    public RFuture<RedisConnection> connectionReadOp(RedisCommand<?> command) {
        return slaveBalancer.nextConnection(command);
    }

    public RFuture<RedisConnection> connectionReadOp(RedisCommand<?> command, InetSocketAddress addr) {
        return slaveBalancer.getConnection(command, addr);
    }

    RFuture<RedisPubSubConnection> nextPubSubConnection() {
        if (config.getSubscriptionMode() == SubscriptionMode.MASTER) {
            return pubSubConnectionHolder.get();
        }
        
        return slaveBalancer.nextPubSubConnection();
    }

    public void returnPubSubConnection(PubSubConnectionEntry entry) {
        if (config.getSubscriptionMode() == SubscriptionMode.MASTER) {
            pubSubConnectionHolder.returnConnection(masterEntry, entry.getConnection());
            return;
        }
        slaveBalancer.returnPubSubConnection(entry.getConnection());
    }

    public void releaseWrite(RedisConnection connection) {
        writeConnectionHolder.returnConnection(masterEntry, connection);
    }

    public void releaseRead(RedisConnection connection) {
        slaveBalancer.returnConnection(connection);
    }

    public void shutdown() {
        if (!active.compareAndSet(true, false)) {
            return;
        }

        masterEntry.getClient().shutdown();
        slaveBalancer.shutdown();
    }

    public void addSlotRange(Integer range) {
        slots.add(range);
    }

    public void removeSlotRange(Integer range) {
        slots.remove(range);
    }

    public Set<Integer> getSlotRanges() {
        return slots;
    }

}
