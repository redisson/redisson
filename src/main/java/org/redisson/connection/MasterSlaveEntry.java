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

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.redisson.MasterSlaveServersConfig;
import org.redisson.client.RedisClient;
import org.redisson.client.RedisConnection;
import org.redisson.client.RedisException;
import org.redisson.client.RedisPubSubConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Nikita Koksharov
 *
 */
//TODO ping support
public class MasterSlaveEntry {

    final Logger log = LoggerFactory.getLogger(getClass());

    LoadBalancer slaveBalancer;
    volatile ConnectionEntry masterEntry;

    final MasterSlaveServersConfig config;
    final ConnectionManager connectionManager;

    final int startSlot;
    final int endSlot;

    public MasterSlaveEntry(int startSlot, int endSlot, ConnectionManager connectionManager, MasterSlaveServersConfig config) {
        this.startSlot = startSlot;
        this.endSlot = endSlot;
        this.connectionManager = connectionManager;
        this.config = config;

        slaveBalancer = config.getLoadBalancer();
        slaveBalancer.init(config, connectionManager);

        List<URI> addresses = new ArrayList<URI>(config.getSlaveAddresses());
        addresses.add(config.getMasterAddress());
        for (URI address : addresses) {
            RedisClient client = connectionManager.createClient(address.getHost(), address.getPort());
            slaveBalancer.add(new SubscribesConnectionEntry(client,
                    this.config.getSlaveConnectionPoolSize(),
                    this.config.getSlaveSubscriptionConnectionPoolSize()));
        }
        if (config.getSlaveAddresses().size() > 1) {
            slaveDown(config.getMasterAddress().getHost(), config.getMasterAddress().getPort());
        }

        setupMasterEntry(config.getMasterAddress().getHost(), config.getMasterAddress().getPort());
    }

    public void setupMasterEntry(String host, int port) {
        RedisClient client = connectionManager.createClient(host, port);
        masterEntry = new ConnectionEntry(client, config.getMasterConnectionPoolSize());
    }

    public Collection<RedisPubSubConnection> slaveDown(String host, int port) {
        Collection<RedisPubSubConnection> conns = slaveBalancer.freeze(host, port);
        if (slaveBalancer.getAvailableClients() == 0) {
            slaveUp(masterEntry.getClient().getAddr().getHostName(), masterEntry.getClient().getAddr().getPort());
        }
        return conns;
    }

    public void addSlave(String host, int port) {
        RedisClient client = connectionManager.createClient(host, port);
        SubscribesConnectionEntry entry = new SubscribesConnectionEntry(client,
                this.config.getSlaveConnectionPoolSize(),
                this.config.getSlaveSubscriptionConnectionPoolSize());
        entry.setFreezed(true);
        slaveBalancer.add(entry);
    }

    public RedisClient getClient() {
        return masterEntry.getClient();
    }

    public void slaveUp(String host, int port) {
        if (!masterEntry.getClient().getAddr().getHostName().equals(host) && port != masterEntry.getClient().getAddr().getPort()) {
            slaveDown(masterEntry.getClient().getAddr().getHostName(), masterEntry.getClient().getAddr().getPort());
        }
        slaveBalancer.unfreeze(host, port);
    }

    /**
     * Freeze slave with <code>host:port</code> from slaves list.
     * Re-attach pub/sub listeners from it to other slave.
     * Shutdown old master client.
     *
     */
    public void changeMaster(String host, int port) {
        ConnectionEntry oldMaster = masterEntry;
        setupMasterEntry(host, port);
        if (slaveBalancer.getAvailableClients() > 1) {
            slaveDown(host, port);
        }
        connectionManager.shutdownAsync(oldMaster.getClient());
    }

    public void shutdownMasterAsync() {
        connectionManager.shutdownAsync(masterEntry.getClient());
        slaveBalancer.shutdownAsync();
    }

    public RedisConnection connectionWriteOp() {
        acquireMasterConnection();

        RedisConnection conn = masterEntry.getConnections().poll();
        if (conn != null) {
            return conn;
        }

        try {
            return masterEntry.connect(config);
        } catch (RedisException e) {
            masterEntry.getConnectionsSemaphore().release();
            throw e;
        }
    }

    public RedisConnection connectionReadOp() {
        return slaveBalancer.nextConnection();
    }

    RedisPubSubConnection nextPubSubConnection() {
        return slaveBalancer.nextPubSubConnection();
    }

    void acquireMasterConnection() {
        if (!masterEntry.getConnectionsSemaphore().tryAcquire()) {
            log.warn("Master connection pool gets exhausted! Trying to acquire connection ...");
            long time = System.currentTimeMillis();
            masterEntry.getConnectionsSemaphore().acquireUninterruptibly();
            long endTime = System.currentTimeMillis() - time;
            log.warn("Master connection acquired, time spended: {} ms", endTime);
        }
    }

    public void returnSubscribeConnection(PubSubConnectionEntry entry) {
        slaveBalancer.returnSubscribeConnection(entry.getConnection());
    }

    public void releaseWrite(RedisConnection connection) {
        // may changed during changeMaster call
        if (!masterEntry.getClient().equals(connection.getRedisClient())) {
            connection.closeAsync();
            return;
        }

        masterEntry.getConnections().add(connection);
        masterEntry.getConnectionsSemaphore().release();
    }

    public void releaseRead(RedisConnection сonnection) {
        slaveBalancer.returnConnection(сonnection);
    }

    public void shutdown() {
        masterEntry.getClient().shutdown();
        slaveBalancer.shutdown();
    }

    public int getEndSlot() {
        return endSlot;
    }

    public int getStartSlot() {
        return startSlot;
    }

    public boolean isOwn(int slot) {
        return slot >= startSlot && slot <= endSlot;
    }

}
