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

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;

import org.redisson.MasterSlaveServersConfig;
import org.redisson.client.RedisClient;
import org.redisson.client.RedisConnection;
import org.redisson.client.protocol.RedisCommands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConnectionEntry {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private volatile boolean freezed;
    private final RedisClient client;

    private final Queue<RedisConnection> connections = new ConcurrentLinkedQueue<RedisConnection>();
    private final Semaphore connectionsSemaphore;

    public ConnectionEntry(RedisClient client, int poolSize) {
        this.client = client;
        this.connectionsSemaphore = new Semaphore(poolSize);
    }

    public RedisClient getClient() {
        return client;
    }

    public boolean isFreezed() {
        return freezed;
    }

    public void setFreezed(boolean freezed) {
        this.freezed = freezed;
    }

    public Semaphore getConnectionsSemaphore() {
        return connectionsSemaphore;
    }

    public Queue<RedisConnection> getConnections() {
        return connections;
    }

    public RedisConnection connect(MasterSlaveServersConfig config) {
        RedisConnection conn = client.connect();
        if (config.getPassword() != null) {
            conn.sync(RedisCommands.AUTH, config.getPassword());
        }
        if (config.getDatabase() != 0) {
            conn.sync(RedisCommands.SELECT, config.getDatabase());
        }
        if (config.getClientName() != null) {
            conn.sync(RedisCommands.CLIENT_SETNAME, config.getClientName());
        }

        log.debug("new connection created: {}", conn);

        return conn;
    }

    @Override
    public String toString() {
        return "ConnectionEntry [freezed=" + freezed + ", client=" + client + "]";
    }

}
