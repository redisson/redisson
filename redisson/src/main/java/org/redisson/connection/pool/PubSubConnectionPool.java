/**
 * Copyright (c) 2013-2022 Nikita Koksharov
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

import org.redisson.client.RedisPubSubConnection;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.config.MasterSlaveServersConfig;
import org.redisson.connection.ClientConnectionsEntry;
import org.redisson.connection.ConnectionManager;
import org.redisson.connection.MasterSlaveEntry;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

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

    public CompletableFuture<RedisPubSubConnection> get() {
        return get(RedisCommands.PUBLISH);
    }
    
    @Override
    protected RedisPubSubConnection poll(ClientConnectionsEntry entry, RedisCommand<?> command) {
        return entry.pollSubscribeConnection();
    }

    @Override
    protected int getMinimumIdleSize(ClientConnectionsEntry entry) {
        return config.getSubscriptionConnectionMinimumIdleSize();
    }

    @Override
    protected CompletionStage<RedisPubSubConnection> connect(ClientConnectionsEntry entry) {
        return entry.connectPubSub();
    }

    @Override
    protected CompletableFuture<Void> acquireConnection(ClientConnectionsEntry entry, RedisCommand<?> command) {
        return entry.acquireSubscribeConnection();
    }
    
    @Override
    protected void releaseConnection(ClientConnectionsEntry entry) {
        entry.releaseSubscribeConnection();
    }

    @Override
    protected void releaseConnection(ClientConnectionsEntry entry, RedisPubSubConnection conn) {
        entry.releaseSubscribeConnection(conn);
    }

    @Override
    protected boolean changeUsage() {
        return false;
    }
}
