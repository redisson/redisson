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
package org.redisson.connection;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.redisson.MasterSlaveServersConfig;
import org.redisson.ReadMode;
import org.redisson.client.RedisClient;
import org.redisson.client.RedisConnection;
import org.redisson.client.RedisPubSubConnection;
import org.redisson.cluster.ClusterSlotRange;
import org.redisson.connection.ClientConnectionsEntry.FreezeReason;
import org.redisson.connection.ClientConnectionsEntry.NodeType;
import org.redisson.connection.balancer.LoadBalancerManager;
import org.redisson.connection.balancer.LoadBalancerManagerImpl;
import org.redisson.connection.pool.MasterConnectionPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.util.concurrent.Future;

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
    final Set<ClusterSlotRange> slotRanges;

    final AtomicBoolean active = new AtomicBoolean(true);

    public MasterSlaveEntry(Set<ClusterSlotRange> slotRanges, ConnectionManager connectionManager, MasterSlaveServersConfig config) {
        this.slotRanges = slotRanges;
        this.connectionManager = connectionManager;
        this.config = config;

        slaveBalancer = new LoadBalancerManagerImpl(config, connectionManager, this);
        writeConnectionHolder = new MasterConnectionPool(config, connectionManager, this);
    }

    public List<Future<Void>> initSlaveBalancer(Collection<URI> disconnectedNodes) {
        boolean freezeMasterAsSlave = !config.getSlaveAddresses().isEmpty()
                    && config.getReadMode() == ReadMode.SLAVE
                        && disconnectedNodes.size() < config.getSlaveAddresses().size();

        List<Future<Void>> result = new LinkedList<Future<Void>>();
        Future<Void> f = addSlave(config.getMasterAddress().getHost(), config.getMasterAddress().getPort(), freezeMasterAsSlave, NodeType.MASTER);
        result.add(f);
        for (URI address : config.getSlaveAddresses()) {
            f = addSlave(address.getHost(), address.getPort(), disconnectedNodes.contains(address), NodeType.SLAVE);
            result.add(f);
        }
        return result;
    }

    public Future<Void> setupMasterEntry(String host, int port) {
        RedisClient client = connectionManager.createClient(host, port);
        masterEntry = new ClientConnectionsEntry(client, config.getMasterConnectionMinimumIdleSize(), config.getMasterConnectionPoolSize(),
                                                    0, 0, connectionManager, NodeType.MASTER, config);
        return writeConnectionHolder.add(masterEntry);
    }

    public Collection<RedisPubSubConnection> slaveDown(String host, int port, FreezeReason freezeReason) {
        Collection<RedisPubSubConnection> conns = slaveBalancer.freeze(host, port, freezeReason);

        // add master as slave if no more slaves available
        if (config.getReadMode() == ReadMode.SLAVE && slaveBalancer.getAvailableClients() == 0) {
            InetSocketAddress addr = masterEntry.getClient().getAddr();
            if (slaveUp(addr.getHostName(), addr.getPort(), FreezeReason.SYSTEM)) {
                log.info("master {}:{} used as slave", addr.getHostName(), addr.getPort());
            }
        }
        return conns;
    }

    public Future<Void> addSlave(String host, int port) {
        return addSlave(host, port, true, NodeType.SLAVE);
    }

    private Future<Void> addSlave(String host, int port, boolean freezed, NodeType mode) {
        RedisClient client = connectionManager.createClient(host, port);
        ClientConnectionsEntry entry = new ClientConnectionsEntry(client,
                this.config.getSlaveConnectionMinimumIdleSize(),
                this.config.getSlaveConnectionPoolSize(),
                this.config.getSlaveSubscriptionConnectionMinimumIdleSize(),
                this.config.getSlaveSubscriptionConnectionPoolSize(), connectionManager, mode, config);
        if (freezed) {
            entry.setFreezed(freezed);
            entry.setFreezeReason(FreezeReason.SYSTEM);
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
            connectionManager.slaveDown(this, addr.getHostName(), addr.getPort(), FreezeReason.SYSTEM);
            log.info("master {}:{} excluded from slaves", addr.getHostName(), addr.getPort());
        }
        return true;
    }

    /**
     * Freeze slave with <code>host:port</code> from slaves list.
     * Re-attach pub/sub listeners from it to other slave.
     * Shutdown old master client.
     *
     */
    public void changeMaster(String host, int port) {
        ClientConnectionsEntry oldMaster = masterEntry;
        setupMasterEntry(host, port);
        writeConnectionHolder.remove(oldMaster);
        oldMaster.freezeMaster(FreezeReason.MANAGER);

        // more than one slave available, so master can be removed from slaves
        if (config.getReadMode() == ReadMode.SLAVE
                && slaveBalancer.getAvailableClients() > 1) {
            connectionManager.slaveDown(this, host, port, FreezeReason.SYSTEM);
        }
        connectionManager.shutdownAsync(oldMaster.getClient());
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
        masterEntry.setFreezed(false);
        masterEntry.setFreezeReason(null);
    }

    public void shutdownMasterAsync() {
        if (!active.compareAndSet(true, false)) {
            return;
        }

        connectionManager.shutdownAsync(masterEntry.getClient());
        slaveBalancer.shutdownAsync();
    }

    public Future<RedisConnection> connectionWriteOp() {
        return writeConnectionHolder.get();
    }

    public Future<RedisConnection> connectionReadOp() {
        return slaveBalancer.nextConnection();
    }

    public Future<RedisConnection> connectionReadOp(InetSocketAddress addr) {
        return slaveBalancer.getConnection(addr);
    }


    Future<RedisPubSubConnection> nextPubSubConnection() {
        return slaveBalancer.nextPubSubConnection();
    }

    public void returnPubSubConnection(PubSubConnectionEntry entry) {
        slaveBalancer.returnPubSubConnection(entry.getConnection());
    }

    public void releaseWrite(RedisConnection connection) {
        writeConnectionHolder.returnConnection(masterEntry, connection);
    }

    public void releaseRead(RedisConnection сonnection) {
        slaveBalancer.returnConnection(сonnection);
    }

    public void shutdown() {
        if (!active.compareAndSet(true, false)) {
            return;
        }

        masterEntry.getClient().shutdown();
        slaveBalancer.shutdown();
    }

    public void addSlotRange(ClusterSlotRange range) {
        slotRanges.add(range);
    }

    public void removeSlotRange(ClusterSlotRange range) {
        slotRanges.remove(range);
    }

    public Set<ClusterSlotRange> getSlotRanges() {
        return slotRanges;
    }

}
