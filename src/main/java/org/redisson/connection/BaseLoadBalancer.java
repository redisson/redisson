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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.redisson.MasterSlaveServersConfig;
import org.redisson.client.RedisClient;
import org.redisson.client.RedisConnection;
import org.redisson.client.RedisConnectionException;
import org.redisson.client.RedisPubSubConnection;
import org.redisson.misc.ConnectionPool;
import org.redisson.misc.PubSubConnectionPoll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.util.concurrent.Future;
import io.netty.util.internal.PlatformDependent;

abstract class BaseLoadBalancer implements LoadBalancer {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private ConnectionManager connectionManager;
    final Map<RedisClient, SubscribesConnectionEntry> client2Entry = PlatformDependent.newConcurrentHashMap();

    PubSubConnectionPoll pubSubEntries;

    ConnectionPool<RedisConnection> entries;

    public void init(MasterSlaveServersConfig config, ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
        entries = new ConnectionPool<RedisConnection>(config, this, connectionManager.getGroup());
        pubSubEntries = new PubSubConnectionPoll(config, this, connectionManager.getGroup());
    }

    public synchronized void add(SubscribesConnectionEntry entry) {
        client2Entry.put(entry.getClient(), entry);
        entries.add(entry);
        pubSubEntries.add(entry);
    }

    public int getAvailableClients() {
        int count = 0;
        for (SubscribesConnectionEntry connectionEntry : client2Entry.values()) {
            if (!connectionEntry.isFreezed()) {
                count++;
            }
        }
        return count;
    }

    public synchronized void unfreeze(String host, int port) {
        InetSocketAddress addr = new InetSocketAddress(host, port);
        for (SubscribesConnectionEntry connectionEntry : client2Entry.values()) {
            if (!connectionEntry.getClient().getAddr().equals(addr)) {
                continue;
            }
            connectionEntry.setFreezed(false);
            return;
        }
        throw new IllegalStateException("Can't find " + addr + " in slaves!");
    }

    public synchronized Collection<RedisPubSubConnection> freeze(String host, int port) {
        InetSocketAddress addr = new InetSocketAddress(host, port);
        for (SubscribesConnectionEntry connectionEntry : client2Entry.values()) {
            if (connectionEntry.isFreezed()
                    || !connectionEntry.getClient().getAddr().equals(addr)) {
                continue;
            }

            log.debug("{} freezed", addr);
            connectionEntry.setFreezed(true);

            // close all connections
            while (true) {
                RedisConnection connection = connectionEntry.pollConnection();
                if (connection == null) {
                    break;
                }
                connection.closeAsync();
            }

            // close all pub/sub connections
            while (true) {
                RedisPubSubConnection connection = connectionEntry.pollFreeSubscribeConnection();
                if (connection == null) {
                    break;
                }
                connection.closeAsync();
            }


            List<RedisPubSubConnection> list = new ArrayList<RedisPubSubConnection>(connectionEntry.getAllSubscribeConnections());
            connectionEntry.getAllSubscribeConnections().clear();
            return list;
        }

        return Collections.emptyList();
    }

    public Future<RedisPubSubConnection> nextPubSubConnection() {
        return pubSubEntries.get();
    }

    public Future<RedisConnection> getConnection(RedisClient client) {
        SubscribesConnectionEntry entry = client2Entry.get(client);
        if (entry != null) {
            return entries.get(entry);
        }
        RedisConnectionException exception = new RedisConnectionException("Can't find entry for " + client);
        return connectionManager.getGroup().next().newFailedFuture(exception);
    }

    public Future<RedisConnection> nextConnection() {
        return entries.get();
    }

    public void returnSubscribeConnection(RedisPubSubConnection connection) {
        SubscribesConnectionEntry entry = client2Entry.get(connection.getRedisClient());
        pubSubEntries.returnConnection(entry, connection);
    }

    public void returnConnection(RedisConnection connection) {
        SubscribesConnectionEntry entry = client2Entry.get(connection.getRedisClient());
        entries.returnConnection(entry, connection);
    }

    public void shutdown() {
        for (SubscribesConnectionEntry entry : client2Entry.values()) {
            entry.getClient().shutdown();
        }
    }

    public void shutdownAsync() {
        for (RedisClient client : client2Entry.keySet()) {
            connectionManager.shutdownAsync(client);
        }
    }

}
