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
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.redisson.MasterSlaveServersConfig;
import org.redisson.client.RedisClient;
import org.redisson.client.RedisConnection;
import org.redisson.client.RedisPubSubConnection;
import org.redisson.cluster.ClusterSlotRange;
import org.redisson.connection.ClientConnectionsEntry.NodeType;
import org.redisson.misc.ConnectionPool;
import org.redisson.misc.PubSubConnectionPoll;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;

public class SingleEntry extends MasterSlaveEntry {

    final ConnectionPool<RedisPubSubConnection> pubSubConnectionHolder;

    public SingleEntry(Set<ClusterSlotRange> slotRanges, ConnectionManager connectionManager, MasterSlaveServersConfig config, ConnectionListener connectListener) {
        super(slotRanges, connectionManager, config, connectListener);
        pubSubConnectionHolder = new PubSubConnectionPoll(config, connectionManager, this) {
            protected ClientConnectionsEntry getEntry() {
                return entries.get(0);
            }
        };
    }

    @Override
    public Future<Void> setupMasterEntry(String host, int port) {
        RedisClient masterClient = connectionManager.createClient(host, port);
        masterEntry = new ClientConnectionsEntry(masterClient,
                config.getMasterConnectionMinimumIdleSize(),
                config.getMasterConnectionPoolSize(),
                config.getSlaveConnectionMinimumIdleSize(),
                config.getSlaveSubscriptionConnectionPoolSize(), connectListener, NodeType.MASTER, connectionManager.getConnectionWatcher(), config);
        final Promise<Void> res = connectionManager.newPromise();
        Future<Void> f = writeConnectionHolder.add(masterEntry);
        Future<Void> s = pubSubConnectionHolder.add(masterEntry);
        FutureListener<Void> listener = new FutureListener<Void>() {
            AtomicInteger counter = new AtomicInteger(2);
            @Override
            public void operationComplete(Future<Void> future) throws Exception {
                if (counter.decrementAndGet() == 0) {
                    res.setSuccess(null);
                }
            }
        };
        f.addListener(listener);
        s.addListener(listener);
        return res;
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
    public Future<RedisConnection> connectionReadOp(InetSocketAddress addr) {
        return super.connectionWriteOp();
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
