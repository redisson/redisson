/**
 * Copyright (c) 2013-2024 Nikita Koksharov
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

import org.redisson.api.NodeType;
import org.redisson.client.RedisConnection;
import org.redisson.client.RedisConnectionException;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.config.MasterSlaveServersConfig;
import org.redisson.connection.ClientConnectionsEntry;
import org.redisson.connection.ConnectionManager;
import org.redisson.connection.ConnectionsHolder;
import org.redisson.connection.MasterSlaveEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Base connection pool class 
 * 
 * @author Nikita Koksharov
 *
 * @param <T> - connection type
 */
abstract class ConnectionPool<T extends RedisConnection> {

    private final Logger log = LoggerFactory.getLogger(getClass());

    protected final Queue<ClientConnectionsEntry> entries = new ConcurrentLinkedQueue<>();

    final ConnectionManager connectionManager;

    final MasterSlaveServersConfig config;

    final MasterSlaveEntry masterSlaveEntry;

    ConnectionPool(MasterSlaveServersConfig config, ConnectionManager connectionManager, MasterSlaveEntry masterSlaveEntry) {
        this.config = config;
        this.masterSlaveEntry = masterSlaveEntry;
        this.connectionManager = connectionManager;
    }

    public final void addEntry(ClientConnectionsEntry entry) {
        entries.add(entry);
    }

    public final void removeEntry(ClientConnectionsEntry entry) {
        entries.remove(entry);
    }

    protected abstract ConnectionsHolder<T> getConnectionHolder(ClientConnectionsEntry entry, boolean trackChanges);

    public CompletableFuture<T> get(RedisCommand<?> command, boolean trackChanges) {
        List<ClientConnectionsEntry> entriesCopy = new LinkedList<>(entries);
        entriesCopy.removeIf(n -> n.isFreezed() || !isHealthy(n));
        if (!entriesCopy.isEmpty()) {
            ClientConnectionsEntry entry = config.getLoadBalancer().getEntry(entriesCopy, command);
            log.debug("Entry {} selected as connection source", entry);
            return acquireConnection(command, entry, trackChanges);
        }
        
        List<InetSocketAddress> failed = new LinkedList<>();
        List<InetSocketAddress> freezed = new LinkedList<>();
        for (ClientConnectionsEntry entry : entries) {
            if (entry.getClient().getConfig().getFailedNodeDetector().isNodeFailed()) {
                failed.add(entry.getClient().getAddr());
            } else if (entry.isFreezed()) {
                freezed.add(entry.getClient().getAddr());
            }
        }

        StringBuilder errorMsg = new StringBuilder(getClass().getSimpleName() + " no available Redis entries. " +
                "Master entry host: " + masterSlaveEntry.getClient().getAddr() + " entries " + entries);
        if (!freezed.isEmpty()) {
            errorMsg.append(" Disconnected hosts: ").append(freezed);
        }
        if (!failed.isEmpty()) {
            errorMsg.append(" Hosts disconnected due to errors during `failedSlaveCheckInterval`: ").append(failed);
        }

        RedisConnectionException exception = new RedisConnectionException(errorMsg.toString());
        CompletableFuture<T> result = new CompletableFuture<>();
        result.completeExceptionally(exception);
        return result;
    }

    public CompletableFuture<T> get(RedisCommand<?> command, ClientConnectionsEntry entry, boolean trackChanges) {
        return acquireConnection(command, entry, trackChanges);
    }

    protected final CompletableFuture<T> acquireConnection(RedisCommand<?> command, ClientConnectionsEntry entry, boolean trackChanges) {
        ConnectionsHolder<T> handler = getConnectionHolder(entry, trackChanges);
        CompletableFuture<T> result = handler.acquireConnection(command);
        return result.whenComplete((r, e) -> {
            if (e != null) {
                if (entry.getNodeType() == NodeType.SLAVE) {
                    entry.getClient().getConfig().getFailedNodeDetector().onConnectFailed();
                    if (entry.getClient().getConfig().getFailedNodeDetector().isNodeFailed()) {
                        masterSlaveEntry.shutdownAndReconnectAsync(entry.getClient(), e);
                    }
                }
                return;
            }

            entry.addHandler(r, handler);

            if (entry.getNodeType() == NodeType.SLAVE) {
                entry.getClient().getConfig().getFailedNodeDetector().onConnectSuccessful();
            }
        });
    }
        
    private boolean isHealthy(ClientConnectionsEntry entry) {
        if (entry.getNodeType() == NodeType.SLAVE
                && entry.getClient().getConfig().getFailedNodeDetector().isNodeFailed()) {
            return false;
        }
        return true;
    }

}
