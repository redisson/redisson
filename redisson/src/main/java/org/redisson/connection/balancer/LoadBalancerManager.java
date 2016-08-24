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
package org.redisson.connection.balancer;

import java.net.InetSocketAddress;

import org.redisson.api.RFuture;
import org.redisson.client.RedisConnection;
import org.redisson.client.RedisPubSubConnection;
import org.redisson.connection.ClientConnectionsEntry;
import org.redisson.connection.ClientConnectionsEntry.FreezeReason;

public interface LoadBalancerManager {

    RFuture<RedisConnection> getConnection(InetSocketAddress addr);

    int getAvailableClients();

    void shutdownAsync();

    void shutdown();

    boolean unfreeze(String host, int port, FreezeReason freezeReason);

    ClientConnectionsEntry freeze(ClientConnectionsEntry connectionEntry, FreezeReason freezeReason);
    
    ClientConnectionsEntry freeze(String host, int port, FreezeReason freezeReason);

    RFuture<Void> add(ClientConnectionsEntry entry);

    RFuture<RedisConnection> nextConnection();

    RFuture<RedisPubSubConnection> nextPubSubConnection();

    void returnConnection(RedisConnection connection);

    void returnPubSubConnection(RedisPubSubConnection connection);

}
