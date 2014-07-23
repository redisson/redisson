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

import org.redisson.misc.ReclosableLatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lambdaworks.redis.RedisConnection;
import com.lambdaworks.redis.RedisConnectionException;
import com.lambdaworks.redis.codec.RedisCodec;
import com.lambdaworks.redis.pubsub.RedisPubSubConnection;

abstract class BaseLoadBalancer implements LoadBalancer {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private RedisCodec codec;

    private String password;

    private final ReclosableLatch clientsEmpty = new ReclosableLatch();
    final Queue<SlaveConnectionEntry> clients = new ConcurrentLinkedQueue<SlaveConnectionEntry>();

    public void init(RedisCodec codec, String password) {
        this.codec = codec;
        this.password = password;
    }

    public synchronized void add(SlaveConnectionEntry entry) {
        clients.add(entry);
        clientsEmpty.open();
    }

    public synchronized void unfreeze(String host, int port) {
        InetSocketAddress addr = new InetSocketAddress(host, port);
        for (SlaveConnectionEntry connectionEntry : clients) {
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
        for (SlaveConnectionEntry connectionEntry : clients) {
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
                connection.close();
            }
            
            // close all pub/sub connections
            while (true) {
                RedisPubSubConnection connection = connectionEntry.pollFreeSubscribeConnection();
                if (connection == null) {
                    break;
                }
                connection.close();
            }

            
            boolean allFreezed = true;
            for (SlaveConnectionEntry entry : clients) {
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

    @SuppressWarnings("unchecked")
    public RedisPubSubConnection nextPubSubConnection() {
        clientsEmpty.awaitUninterruptibly();
        List<SlaveConnectionEntry> clientsCopy = new ArrayList<SlaveConnectionEntry>(clients);
        while (true) {
            if (clientsCopy.isEmpty()) {
                // TODO refactor
                log.warn("Slave subscribe-connection pool gets exhausted! Trying to acquire connection again...");
                return nextPubSubConnection();
//                long time = System.currentTimeMillis();
//                long endTime = System.currentTimeMillis() - time;
//                log.warn("Connection acquired, time spended: {} ms", endTime);
            }

            int index = getIndex(clientsCopy);
            SlaveConnectionEntry entry = clientsCopy.get(index);

            if (entry.isFreezed()
                    || !entry.getSubscribeConnectionsSemaphore().tryAcquire()) {
                clientsCopy.remove(index);
            } else {
                try {
                    RedisPubSubConnection conn = entry.pollFreeSubscribeConnection();
                    if (conn != null) {
                        return conn;
                    }
                    conn = entry.getClient().connectPubSub(codec);
                    if (password != null) {
                        conn.auth(password);
                    }
                    entry.registerSubscribeConnection(conn);
                    return conn;
                } catch (RedisConnectionException e) {
                    entry.getConnectionsSemaphore().release();
                    // TODO connection scoring
                    log.warn("Can't connect to {}, trying next connection!", entry.getClient().getAddr());
                    clientsCopy.remove(index);
                }
            }
        }
    }

    public RedisConnection nextConnection() {
        clientsEmpty.awaitUninterruptibly();
        List<SlaveConnectionEntry> clientsCopy = new ArrayList<SlaveConnectionEntry>(clients);
        while (true) {
            if (clientsCopy.isEmpty()) {
                // TODO refactor
                log.warn("Slave connection pool gets exhausted! Trying to acquire connection ...");
                return nextConnection();
//                long time = System.currentTimeMillis();
//                long endTime = System.currentTimeMillis() - time;
//                log.warn("Connection acquired, time spended: {} ms", endTime);
            }

            int index = getIndex(clientsCopy);
            SlaveConnectionEntry entry = clientsCopy.get(index);

            if (entry.isFreezed()
                    || !entry.getConnectionsSemaphore().tryAcquire()) {
                clientsCopy.remove(index);
            } else {
                RedisConnection conn = entry.getConnections().poll();
                if (conn != null) {
                    return conn;
                }
                try {
                    conn = entry.getClient().connect(codec);
                    if (password != null) {
                        conn.auth(password);
                    }
                    return conn;
                } catch (RedisConnectionException e) {
                    entry.getConnectionsSemaphore().release();
                    // TODO connection scoring
                    log.warn("Can't connect to {}, trying next connection!", entry.getClient().getAddr());
                    clientsCopy.remove(index);
                }
            }
        }
    }

    abstract int getIndex(List<SlaveConnectionEntry> clientsCopy);

    public void returnSubscribeConnection(RedisPubSubConnection connection) {
        for (SlaveConnectionEntry entry : clients) {
            if (entry.getClient().equals(connection.getRedisClient())) {
                if (entry.isFreezed()) {
                    connection.close();
                } else {
                    entry.offerFreeSubscribeConnection(connection);
                }
                entry.getSubscribeConnectionsSemaphore().release();
                break;
            }
        }
    }

    public void returnConnection(RedisConnection connection) {
        for (SlaveConnectionEntry entry : clients) {
            if (entry.getClient().equals(connection.getRedisClient())) {
                if (entry.isFreezed()) {
                    connection.close();
                } else {
                    entry.getConnections().add(connection);
                }
                entry.getConnectionsSemaphore().release();
                break;
            }
        }
    }

    public void shutdown() {
        for (SlaveConnectionEntry entry : clients) {
            entry.getClient().shutdown();
        }
    }
    
}
