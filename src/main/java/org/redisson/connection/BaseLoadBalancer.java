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
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.redisson.MasterSlaveServersConfig;
import org.redisson.client.RedisConnection;
import org.redisson.client.RedisConnectionException;
import org.redisson.client.RedisException;
import org.redisson.client.RedisPubSubConnection;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.misc.ReclosableLatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class BaseLoadBalancer implements LoadBalancer {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private MasterSlaveServersConfig config;

    private ConnectionManager connectionManager;
    private final ReclosableLatch clientsEmpty = new ReclosableLatch();
    final Queue<SubscribesConnectionEntry> clients = new ConcurrentLinkedQueue<SubscribesConnectionEntry>();

    public void init(MasterSlaveServersConfig config, ConnectionManager connectionManager) {
        this.config = config;
        this.connectionManager = connectionManager;
    }

    public synchronized void add(SubscribesConnectionEntry entry) {
        clients.add(entry);
        if (!entry.isFreezed()) {
            clientsEmpty.open();
        }
    }

    public int getAvailableClients() {
        int count = 0;
        for (SubscribesConnectionEntry connectionEntry : clients) {
            if (!connectionEntry.isFreezed()) {
                count++;
            }
        }
        return count;
    }

    public synchronized void unfreeze(String host, int port) {
        InetSocketAddress addr = new InetSocketAddress(host, port);
        for (SubscribesConnectionEntry connectionEntry : clients) {
            if (!connectionEntry.getClient().getAddr().equals(addr)) {
                continue;
            }
            connectionEntry.setFreezed(false);
            clientsEmpty.open();
            return;
        }
        throw new IllegalStateException("Can't find " + addr + " in slaves!");
    }

    public synchronized Collection<RedisPubSubConnection> freeze(String host, int port) {
        InetSocketAddress addr = new InetSocketAddress(host, port);
        for (SubscribesConnectionEntry connectionEntry : clients) {
            if (connectionEntry.isFreezed()
                    || !connectionEntry.getClient().getAddr().equals(addr)) {
                continue;
            }

            log.debug("{} freezed", addr);
            connectionEntry.setFreezed(true);

            // close all connections
            while (true) {
                RedisConnection connection = connectionEntry.getConnections().poll();
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


            boolean allFreezed = true;
            for (SubscribesConnectionEntry entry : clients) {
                if (!entry.isFreezed()) {
                    allFreezed = false;
                    break;
                }
            }
            if (allFreezed) {
                clientsEmpty.close();
            }

            List<RedisPubSubConnection> list = new ArrayList<RedisPubSubConnection>(connectionEntry.getAllSubscribeConnections());
            connectionEntry.getAllSubscribeConnections().clear();
            return list;
        }

        return Collections.emptyList();
    }

    public RedisPubSubConnection nextPubSubConnection() {
        clientsEmpty.awaitUninterruptibly();
        List<SubscribesConnectionEntry> clientsCopy = new ArrayList<SubscribesConnectionEntry>(clients);
        while (true) {
            if (clientsCopy.isEmpty()) {
                throw new RedisConnectionException("Slave subscribe-connection pool gets exhausted!");
            }

            int index = getIndex(clientsCopy);
            SubscribesConnectionEntry entry = clientsCopy.get(index);

            if (entry.isFreezed()
                    || !entry.getSubscribeConnectionsSemaphore().tryAcquire()) {
                clientsCopy.remove(index);
            } else {
                try {
                    RedisPubSubConnection conn = entry.pollFreeSubscribeConnection();
                    if (conn != null) {
                        return conn;
                    }
                    conn = entry.getClient().connectPubSub();
                    if (config.getPassword() != null) {
                        conn.sync(RedisCommands.AUTH, config.getPassword());
                    }
                    if (config.getDatabase() != 0) {
                        conn.sync(RedisCommands.SELECT, config.getDatabase());
                    }
                    if (config.getClientName() != null) {
                        conn.sync(RedisCommands.CLIENT_SETNAME, config.getClientName());
                    }

                    entry.registerSubscribeConnection(conn);
                    return conn;
                } catch (RedisConnectionException e) {
                    entry.getSubscribeConnectionsSemaphore().release();
                    // TODO connection scoring
                    log.warn("Can't connect to {}, trying next connection!", entry.getClient().getAddr());
                    clientsCopy.remove(index);
                }
            }
        }
    }

    public RedisConnection nextConnection() {
        clientsEmpty.awaitUninterruptibly();
        List<SubscribesConnectionEntry> clientsCopy = new ArrayList<SubscribesConnectionEntry>(clients);
        while (true) {
            if (clientsCopy.isEmpty()) {
                throw new RedisConnectionException("Slave connection pool gets exhausted!");
            }

            int index = getIndex(clientsCopy);
            SubscribesConnectionEntry entry = clientsCopy.get(index);

            if (entry.isFreezed()
                    || !entry.getConnectionsSemaphore().tryAcquire()) {
                clientsCopy.remove(index);
            } else {
                RedisConnection conn = entry.getConnections().poll();
                if (conn != null) {
                    return conn;
                }
                try {
                    return entry.connect(config);
                } catch (RedisException e) {
                    entry.getConnectionsSemaphore().release();
                    // TODO connection scoring
                    log.warn("Can't connect to {}, trying next connection!", entry.getClient().getAddr());
                    clientsCopy.remove(index);
                }
            }
        }
    }

    abstract int getIndex(List<SubscribesConnectionEntry> clientsCopy);

    public void returnSubscribeConnection(RedisPubSubConnection connection) {
        for (SubscribesConnectionEntry entry : clients) {
            if (entry.getClient().equals(connection.getRedisClient())) {
                if (entry.isFreezed()) {
                    connection.closeAsync();
                } else {
                    entry.offerFreeSubscribeConnection(connection);
                }
                entry.getSubscribeConnectionsSemaphore().release();
                break;
            }
        }
    }

    public void returnConnection(RedisConnection connection) {
        for (SubscribesConnectionEntry entry : clients) {
            if (entry.getClient().equals(connection.getRedisClient())) {
                if (entry.isFreezed()) {
                    connection.closeAsync();
                } else {
                    entry.getConnections().add(connection);
                }
                entry.getConnectionsSemaphore().release();
                break;
            }
        }
    }

    public void shutdown() {
        for (SubscribesConnectionEntry entry : clients) {
            entry.getClient().shutdown();
        }
    }

    public void shutdownAsync() {
        for (SubscribesConnectionEntry entry : clients) {
            connectionManager.shutdownAsync(entry.getClient());
        }
    }

}
