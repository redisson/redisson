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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.redisson.MasterSlaveServersConfig;
import org.redisson.client.RedisClient;
import org.redisson.client.RedisConnection;
import org.redisson.client.RedisPubSubConnection;
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

    final MasterSlaveServersConfig config;
    final ConnectionManager connectionManager;

    final int startSlot;
    final int endSlot;

    final ConnectionPool<RedisConnection> writeConnectionHolder;

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

        writeConnectionHolder = new ConnectionPool<RedisConnection>(config, null, connectionManager.getGroup());
    }

    protected void setupMasterEntry(String host, int port) {
        RedisClient client = connectionManager.createClient(host, port);
        masterEntry = new SubscribesConnectionEntry(client, config.getMasterConnectionPoolSize(), 0);
        writeConnectionHolder.add(masterEntry);
    }

    public Collection<RedisPubSubConnection> slaveDown(String host, int port) {
        Collection<RedisPubSubConnection> conns = slaveBalancer.freeze(host, port);
        if (slaveBalancer.getAvailableClients() == 0) {
            InetSocketAddress addr = masterEntry.getClient().getAddr();
            slaveUp(addr.getHostName(), addr.getPort());
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
        InetSocketAddress addr = masterEntry.getClient().getAddr();
        if (!addr.getHostName().equals(host) && port != addr.getPort()) {
            slaveDown(addr.getHostName(), addr.getPort());
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
        SubscribesConnectionEntry oldMaster = masterEntry;
        setupMasterEntry(host, port);
        writeConnectionHolder.remove(oldMaster);
        if (slaveBalancer.getAvailableClients() > 1) {
            slaveDown(host, port);
        }
        connectionManager.shutdownAsync(oldMaster.getClient());
    }

    public void shutdownMasterAsync() {
        connectionManager.shutdownAsync(masterEntry.getClient());
        slaveBalancer.shutdownAsync();
    }

    public Future<RedisConnection> connectionWriteOp() {
        return writeConnectionHolder.get();
    }

    public Future<RedisConnection> connectionReadOp() {
        return slaveBalancer.nextConnection();
    }

    public Future<RedisConnection> connectionReadOp(RedisClient client) {
        return slaveBalancer.getConnection(client);
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
