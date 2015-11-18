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
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.redisson.MasterSlaveServersConfig;
import org.redisson.client.RedisClient;
import org.redisson.client.RedisConnection;
import org.redisson.client.RedisPubSubConnection;
import org.redisson.cluster.ClusterSlotRange;
import org.redisson.connection.ConnectionEntry.FreezeReason;
import org.redisson.connection.ConnectionEntry.NodeType;
import org.redisson.misc.ConnectionPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.util.concurrent.Future;

/**
 *
 * @author Nikita Koksharov
 *
 */
//TODO ping support
public class MasterSlaveEntry<E extends ConnectionEntry> {

    final Logger log = LoggerFactory.getLogger(getClass());

    LoadBalancer slaveBalancer;
    SubscribesConnectionEntry masterEntry;

    final ConnectionListener connectListener;

    final MasterSlaveServersConfig config;
    final ConnectionManager connectionManager;

    final ConnectionPool<RedisConnection> writeConnectionHolder;
    final Set<ClusterSlotRange> slotRanges;

    final AtomicBoolean active = new AtomicBoolean(true);

    public MasterSlaveEntry(Set<ClusterSlotRange> slotRanges, ConnectionManager connectionManager, MasterSlaveServersConfig config, ConnectionListener connectListener) {
        this.slotRanges = slotRanges;
        this.connectionManager = connectionManager;
        this.config = config;
        this.connectListener = connectListener;

        slaveBalancer = config.getLoadBalancer();
        slaveBalancer.init(config, connectionManager, this);

        initSlaveBalancer(config);

        writeConnectionHolder = new ConnectionPool<RedisConnection>(config, null, connectionManager, this);
    }

    protected void initSlaveBalancer(MasterSlaveServersConfig config) {
        boolean freezeMasterAsSlave = !config.getSlaveAddresses().isEmpty();
        addSlave(config.getMasterAddress().getHost(), config.getMasterAddress().getPort(), freezeMasterAsSlave, NodeType.MASTER);
        for (URI address : config.getSlaveAddresses()) {
            addSlave(address.getHost(), address.getPort(), false, NodeType.SLAVE);
        }
    }

    public void setupMasterEntry(String host, int port) {
        RedisClient client = connectionManager.createClient(host, port);
        masterEntry = new SubscribesConnectionEntry(client, config.getMasterConnectionPoolSize(), 0, connectListener, NodeType.MASTER);
        writeConnectionHolder.add(masterEntry);
    }

    public Collection<RedisPubSubConnection> slaveDown(String host, int port, FreezeReason freezeReason) {
        Collection<RedisPubSubConnection> conns = slaveBalancer.freeze(host, port, freezeReason);
        // add master as slave if no more slaves available
        if (slaveBalancer.getAvailableClients() == 0) {
            InetSocketAddress addr = masterEntry.getClient().getAddr();
            slaveUp(addr.getHostName(), addr.getPort(), FreezeReason.MANAGER);
            log.info("master {}:{} used as slave", addr.getHostName(), addr.getPort());
        }
        return conns;
    }

    public void addSlave(String host, int port) {
        addSlave(host, port, true, NodeType.SLAVE);
    }

    private void addSlave(String host, int port, boolean freezed, NodeType mode) {
        RedisClient client = connectionManager.createClient(host, port);
        SubscribesConnectionEntry entry = new SubscribesConnectionEntry(client,
                this.config.getSlaveConnectionPoolSize(),
                this.config.getSlaveSubscriptionConnectionPoolSize(), connectListener, mode);
        entry.setFreezed(freezed);
        slaveBalancer.add(entry);
    }

    public RedisClient getClient() {
        return masterEntry.getClient();
    }

    public void slaveUp(String host, int port, FreezeReason freezeReason) {
        if (!slaveBalancer.unfreeze(host, port, freezeReason)) {
            return;
        }
        InetSocketAddress addr = masterEntry.getClient().getAddr();
        if (!addr.getHostName().equals(host) || port != addr.getPort()) {
            connectionManager.slaveDown(this, addr.getHostName(), addr.getPort(), FreezeReason.MANAGER);
        }
    }

    /**
     * Freeze slave with <code>host:port</code> from slaves list.
     * Re-attach pub/sub listeners from it to other slave.
     * Shutdown old master client.
     *
     */
    public void changeMaster(String host, int port) {
        SubscribesConnectionEntry oldMaster = masterEntry;
        setupMasterEntry(host, port);
        writeConnectionHolder.remove(oldMaster);
        if (slaveBalancer.getAvailableClients() > 1) {
            // more than one slave avaliable, so master could be removed from slaves
            connectionManager.slaveDown(this, host, port, FreezeReason.MANAGER);
        }
        connectionManager.shutdownAsync(oldMaster.getClient());
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

    public void returnSubscribeConnection(PubSubConnectionEntry entry) {
        slaveBalancer.returnSubscribeConnection(entry.getConnection());
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
