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
package org.redisson.redisnode;

import org.redisson.api.NodeType;
import org.redisson.api.RFuture;
import org.redisson.api.redisnode.BaseRedisNodes;
import org.redisson.client.RedisConnection;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.connection.ClientConnectionsEntry;
import org.redisson.connection.ConnectionManager;
import org.redisson.connection.MasterSlaveEntry;
import org.redisson.misc.RedisURI;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonBaseNodes implements BaseRedisNodes {

    ConnectionManager connectionManager;
    CommandAsyncExecutor commandExecutor;

    public RedissonBaseNodes(ConnectionManager connectionManager, CommandAsyncExecutor commandExecutor) {
        this.connectionManager = connectionManager;
        this.commandExecutor = commandExecutor;
    }

    protected <T extends org.redisson.api.redisnode.RedisNode> Collection<T> getNodes(NodeType type) {
        Collection<MasterSlaveEntry> entries = connectionManager.getEntrySet();
        List<T> result = new ArrayList<>();
        for (MasterSlaveEntry masterSlaveEntry : entries) {
            if (type == NodeType.MASTER) {
                RedisNode entry = new RedisNode(masterSlaveEntry.getClient(), commandExecutor, NodeType.MASTER);
                result.add((T) entry);
                continue;
            }

            for (ClientConnectionsEntry slaveEntry : masterSlaveEntry.getAllEntries()) {
                if (slaveEntry.getFreezeReason() != ClientConnectionsEntry.FreezeReason.MANAGER
                        && slaveEntry.getNodeType() == type) {
                    RedisNode entry = new RedisNode(slaveEntry.getClient(), commandExecutor, slaveEntry.getNodeType());
                    result.add((T) entry);
                }
            }
        }
        return result;
    }

    protected RedisNode getNode(String address, NodeType nodeType) {
        Collection<MasterSlaveEntry> entries = connectionManager.getEntrySet();
        RedisURI addr = new RedisURI(address);
        for (MasterSlaveEntry masterSlaveEntry : entries) {
            if (nodeType == NodeType.MASTER
                    && addr.equals(masterSlaveEntry.getClient().getAddr())) {
                return new RedisNode(masterSlaveEntry.getClient(), commandExecutor, NodeType.MASTER);
            }

            for (ClientConnectionsEntry entry : masterSlaveEntry.getAllEntries()) {
                if (addr.equals(entry.getClient().getAddr())
                        && entry.getFreezeReason() != ClientConnectionsEntry.FreezeReason.MANAGER) {
                    return new RedisNode(entry.getClient(), commandExecutor, entry.getNodeType());
                }
            }
        }
        return null;
    }

    protected List<RedisNode> getNodes() {
        Collection<MasterSlaveEntry> entries = connectionManager.getEntrySet();
        List<RedisNode> result = new ArrayList<>();
        for (MasterSlaveEntry masterSlaveEntry : entries) {
            if (masterSlaveEntry.getAllEntries().isEmpty()) {
                RedisNode masterEntry = new RedisNode(masterSlaveEntry.getClient(), commandExecutor, NodeType.MASTER);
                result.add(masterEntry);
            }

            for (ClientConnectionsEntry slaveEntry : masterSlaveEntry.getAllEntries()) {
                if (slaveEntry.getFreezeReason() != ClientConnectionsEntry.FreezeReason.MANAGER) {
                    RedisNode entry = new RedisNode(slaveEntry.getClient(), commandExecutor, slaveEntry.getNodeType());
                    result.add(entry);
                }
            }
        }
        return result;
    }

    @Override
    public boolean pingAll(long timeout, TimeUnit timeUnit) {
        List<RedisNode> clients = getNodes();
        List<CompletableFuture<Boolean>> futures = new ArrayList<>();
        for (RedisNode entry : clients) {
            CompletionStage<RedisConnection> f = entry.getClient().connectAsync();
            CompletionStage<Boolean> ff = f.thenCompose(c -> {
                RFuture<String> r = c.async(timeUnit.toMillis(timeout), RedisCommands.PING);
                return r.whenComplete((rr, ex) -> {
                    c.closeAsync();
                });
            }).thenApply("PONG"::equals);
            futures.add(ff.toCompletableFuture());
        }

        CompletableFuture<Void> f = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        try {
            f.get(timeout, timeUnit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            return false;
        }

        return futures.stream()
                        .map(r -> r.getNow(false))
                        .filter(r -> !r).findAny()
                        .orElse(true);
    }

    @Override
    public boolean pingAll() {
        return pingAll(1, TimeUnit.SECONDS);
    }

}
