/**
 * Copyright (c) 2013-2020 Nikita Koksharov
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
import org.redisson.connection.ClientConnectionsEntry;
import org.redisson.connection.ConnectionManager;
import org.redisson.connection.MasterSlaveEntry;
import org.redisson.misc.RedisURI;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonBaseNodes implements BaseRedisNodes {

    ConnectionManager connectionManager;

    public RedissonBaseNodes(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    protected <T extends org.redisson.api.redisnode.RedisNode> Collection<T> getNodes(NodeType type) {
        Collection<MasterSlaveEntry> entries = connectionManager.getEntrySet();
        List<T> result = new ArrayList<>();
        for (MasterSlaveEntry masterSlaveEntry : entries) {
            if (masterSlaveEntry.getAllEntries().isEmpty()
                    && type == NodeType.MASTER) {
                RedisNode entry = new RedisNode(masterSlaveEntry.getClient(), connectionManager.getCommandExecutor(), NodeType.MASTER);
                result.add((T) entry);
            }

            for (ClientConnectionsEntry slaveEntry : masterSlaveEntry.getAllEntries()) {
                if (slaveEntry.getFreezeReason() != ClientConnectionsEntry.FreezeReason.MANAGER
                        && slaveEntry.getNodeType() == type) {
                    RedisNode entry = new RedisNode(slaveEntry.getClient(), connectionManager.getCommandExecutor(), slaveEntry.getNodeType());
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
                    && masterSlaveEntry.getAllEntries().isEmpty()
                        && RedisURI.compare(masterSlaveEntry.getClient().getAddr(), addr)) {
                return new RedisNode(masterSlaveEntry.getClient(), connectionManager.getCommandExecutor(), NodeType.MASTER);
            }

            for (ClientConnectionsEntry entry : masterSlaveEntry.getAllEntries()) {
                if (RedisURI.compare(entry.getClient().getAddr(), addr)
                        && entry.getFreezeReason() != ClientConnectionsEntry.FreezeReason.MANAGER) {
                    return new RedisNode(entry.getClient(), connectionManager.getCommandExecutor(), entry.getNodeType());
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
                RedisNode masterEntry = new RedisNode(masterSlaveEntry.getClient(), connectionManager.getCommandExecutor(), NodeType.MASTER);
                result.add(masterEntry);
            }

            for (ClientConnectionsEntry slaveEntry : masterSlaveEntry.getAllEntries()) {
                if (slaveEntry.getFreezeReason() != ClientConnectionsEntry.FreezeReason.MANAGER) {
                    RedisNode entry = new RedisNode(slaveEntry.getClient(), connectionManager.getCommandExecutor(), slaveEntry.getNodeType());
                    result.add(entry);
                }
            }
        }
        return result;
    }

    @Override
    public boolean pingAll(long timeout, TimeUnit timeUnit) {
        List<RedisNode> clients = getNodes();
        Map<RedisConnection, RFuture<String>> result = new ConcurrentHashMap<>(clients.size());
        CountDownLatch latch = new CountDownLatch(clients.size());
        for (RedisNode entry : clients) {
            RFuture<RedisConnection> f = entry.getClient().connectAsync();
            f.onComplete((c, e) -> {
                if (c != null) {
                    RFuture<String> r = c.async(timeUnit.toMillis(timeout), RedisCommands.PING);
                    result.put(c, r);
                    latch.countDown();
                } else {
                    latch.countDown();
                }
            });
        }

        long time = System.currentTimeMillis();
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        if (System.currentTimeMillis() - time >= connectionManager.getConfig().getConnectTimeout()) {
            for (Map.Entry<RedisConnection, RFuture<String>> entry : result.entrySet()) {
                entry.getKey().closeAsync();
            }
            return false;
        }

        time = System.currentTimeMillis();
        boolean res = true;
        for (Map.Entry<RedisConnection, RFuture<String>> entry : result.entrySet()) {
            RFuture<String> f = entry.getValue();
            f.awaitUninterruptibly();
            if (!"PONG".equals(f.getNow())) {
                res = false;
            }
            entry.getKey().closeAsync();
        }

        // true and no futures were missed during client connection
        return res && result.size() == clients.size();
    }

    @Override
    public boolean pingAll() {
        return pingAll(1, TimeUnit.SECONDS);
    }

}
