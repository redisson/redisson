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
import org.redisson.client.RedisPubSubConnection;
import org.redisson.misc.ConnectionPool;
import org.redisson.misc.PubSubConnectionPoll;

import io.netty.util.concurrent.Future;

public class SingleEntry extends MasterSlaveEntry<SubscribesConnectionEntry> {

    final ConnectionPool<RedisPubSubConnection> pubSubConnectionHolder;

    public SingleEntry(int startSlot, int endSlot, ConnectionManager connectionManager, MasterSlaveServersConfig config) {
        super(startSlot, endSlot, connectionManager, config);
        pubSubConnectionHolder = new PubSubConnectionPoll(config, null, connectionManager.getGroup());
    }

    @Override
    public void setupMasterEntry(String host, int port) {
        RedisClient masterClient = connectionManager.createClient(host, port);
        masterEntry = new SubscribesConnectionEntry(masterClient, config.getMasterConnectionPoolSize(), config.getSlaveSubscriptionConnectionPoolSize());
        writeConnectionHolder.add(masterEntry);
        pubSubConnectionHolder.add(masterEntry);
    }

    @Override
    Future<RedisPubSubConnection> nextPubSubConnection() {
        return pubSubConnectionHolder.get();
    }

    @Override
    public void returnSubscribeConnection(PubSubConnectionEntry entry) {
        pubSubConnectionHolder.returnConnection(masterEntry, entry.getConnection());
    }

    @Override
    public Future<RedisConnection> connectionReadOp() {
        return super.connectionWriteOp();
    }

    @Override
    public void releaseRead(RedisConnection сonnection) {
        super.releaseWrite(сonnection);
    }

}
