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
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

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

    final Queue<ConnectionEntry> clients = new ConcurrentLinkedQueue<ConnectionEntry>();

    public void init(RedisCodec codec, String password) {
        this.codec = codec;
        this.password = password;
    }

    public void add(ConnectionEntry entry) {
        clients.add(entry);
    }

    public void remove(String host, int port) {
        InetSocketAddress addr = new InetSocketAddress(host, port);
        for (Iterator<ConnectionEntry> iterator = clients.iterator(); iterator.hasNext();) {
            ConnectionEntry entry = iterator.next();
            if (!entry.getClient().getAddr().equals(addr)) {
                continue;
            }

            log.info("slave {} removed", entry.getClient().getAddr());
            iterator.remove();
            // TODO re-attach listeners
            for (RedisPubSubConnection conn : entry.getSubscribeConnections()) {
                conn.getListeners();
            }
            entry.shutdown();
            log.info("slave {} shutdown", entry.getClient().getAddr());
            break;
        }
    }

    @SuppressWarnings("unchecked")
    public RedisPubSubConnection nextPubSubConnection() {
        List<ConnectionEntry> clientsCopy = new ArrayList<ConnectionEntry>(clients);
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
            ConnectionEntry entry = clientsCopy.get(index);

            if (!entry.getSubscribeConnectionsSemaphore().tryAcquire()) {
                clientsCopy.remove(index);
            } else {
                try {
                    RedisPubSubConnection conn = entry.getSubscribeConnections().poll();
                    if (conn != null) {
                        return conn;
                    }
                    conn = entry.getClient().connectPubSub(codec);
                    if (password != null) {
                        conn.auth(password);
                    }
                    return conn;
                } catch (RedisConnectionException e) {
                    // TODO connection scoring
                    log.warn("Can't connect to {}, trying next connection!", entry.getClient().getAddr());
                    clientsCopy.remove(index);
                }
            }
        }
    }

    public RedisConnection nextConnection() {
        List<ConnectionEntry> clientsCopy = new ArrayList<ConnectionEntry>(clients);
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
            ConnectionEntry entry = clientsCopy.get(index);

            if (!entry.getConnectionsSemaphore().tryAcquire()) {
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
                    // TODO connection scoring
                    log.warn("Can't connect to {}, trying next connection!", entry.getClient().getAddr());
                    clientsCopy.remove(index);
                }
            }
        }
    }

    abstract int getIndex(List<ConnectionEntry> clientsCopy);

    public void returnSubscribeConnection(RedisPubSubConnection connection) {
        for (ConnectionEntry entry : clients) {
            if (entry.getClient().equals(connection.getRedisClient())) {
                entry.getSubscribeConnections().add(connection);
                entry.getSubscribeConnectionsSemaphore().release();
                break;
            }
        }
    }

    public void returnConnection(RedisConnection connection) {
        for (ConnectionEntry entry : clients) {
            if (entry.getClient().equals(connection.getRedisClient())) {
                entry.getConnections().add(connection);
                entry.getConnectionsSemaphore().release();
                break;
            }
        }
    }

}
