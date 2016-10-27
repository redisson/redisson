/**
 * Copyright 2016 Nikita Koksharov
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
package org.redisson.connection.pool;

import org.redisson.MasterSlaveServersConfig;
import org.redisson.client.RedisPubSubConnection;
import org.redisson.connection.ConnectionManager;
import org.redisson.connection.MasterSlaveEntry;
import org.redisson.connection.ClientConnectionsEntry;

import io.netty.util.concurrent.Future;

/**
 * Connection pool for Publish / Subscribe
 * 
 * @author Nikita Koksharov
 *
 */
public class PubSubConnectionPool extends ConnectionPool<RedisPubSubConnection> {

    public PubSubConnectionPool(MasterSlaveServersConfig config, ConnectionManager connectionManager, MasterSlaveEntry masterSlaveEntry) {
        super(config, connectionManager, masterSlaveEntry);
    }

    @Override
    protected RedisPubSubConnection poll(ClientConnectionsEntry entry) {
        return entry.pollSubscribeConnection();
    }

    @Override
    protected int getMinimumIdleSize(ClientConnectionsEntry entry) {
        return config.getSlaveSubscriptionConnectionMinimumIdleSize();
    }

    @Override
    protected Future<RedisPubSubConnection> connect(ClientConnectionsEntry entry) {
        return entry.connectPubSub();
    }

    @Override
    protected void acquireConnection(ClientConnectionsEntry entry, Runnable runnable) {
        entry.acquireSubscribeConnection(runnable);
    }
    
    @Override
    protected void releaseConnection(ClientConnectionsEntry entry) {
        entry.releaseSubscribeConnection();
    }

    @Override
    protected void releaseConnection(ClientConnectionsEntry entry, RedisPubSubConnection conn) {
        entry.releaseSubscribeConnection(conn);
    }

}
