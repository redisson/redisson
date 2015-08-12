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

import org.redisson.MasterSlaveServersConfig;
import org.redisson.client.RedisClient;
import org.redisson.client.RedisConnection;
import org.redisson.client.RedisConnectionException;
import org.redisson.client.RedisPubSubConnection;
import org.redisson.client.protocol.RedisCommands;

public class SingleEntry extends MasterSlaveEntry {

    public SingleEntry(int startSlot, int endSlot, ConnectionManager connectionManager, MasterSlaveServersConfig config) {
        super(startSlot, endSlot, connectionManager, config);
    }

    @Override
    public void setupMasterEntry(String host, int port) {
        RedisClient masterClient = connectionManager.createClient(host, port);
        masterEntry = new SubscribesConnectionEntry(masterClient, config.getMasterConnectionPoolSize(), config.getSlaveSubscriptionConnectionPoolSize());
    }

    private void acquireSubscribeConnection() {
        if (!((SubscribesConnectionEntry)masterEntry).getSubscribeConnectionsSemaphore().tryAcquire()) {
            log.warn("Subscribe connection pool gets exhausted! Trying to acquire connection ...");
            long time = System.currentTimeMillis();
            ((SubscribesConnectionEntry)masterEntry).getSubscribeConnectionsSemaphore().acquireUninterruptibly();
            long endTime = System.currentTimeMillis() - time;
            log.warn("Subscribe connection acquired, time spended: {} ms", endTime);
        }
    }

    @Override
    RedisPubSubConnection nextPubSubConnection() {
        acquireSubscribeConnection();

        RedisPubSubConnection conn = ((SubscribesConnectionEntry)masterEntry).pollFreeSubscribeConnection();
        if (conn != null) {
            return conn;
        }

        try {
            conn = masterEntry.getClient().connectPubSub();
            if (config.getPassword() != null) {
                conn.sync(RedisCommands.AUTH, config.getPassword());
            }
            if (config.getDatabase() != 0) {
                conn.sync(RedisCommands.SELECT, config.getDatabase());
            }
            if (config.getClientName() != null) {
                conn.sync(RedisCommands.CLIENT_SETNAME, config.getClientName());
            }

            return conn;
        } catch (RedisConnectionException e) {
            ((SubscribesConnectionEntry)masterEntry).getSubscribeConnectionsSemaphore().release();
            throw e;
        }
    }

    @Override
    public void returnSubscribeConnection(PubSubConnectionEntry entry) {
        ((SubscribesConnectionEntry)masterEntry).offerFreeSubscribeConnection(entry.getConnection());
        ((SubscribesConnectionEntry)masterEntry).getSubscribeConnectionsSemaphore().release();
    }

    @Override
    public RedisConnection connectionReadOp() {
        return super.connectionWriteOp();
    }

    @Override
    public void releaseRead(RedisConnection сonnection) {
        super.releaseWrite(сonnection);
    }
}
