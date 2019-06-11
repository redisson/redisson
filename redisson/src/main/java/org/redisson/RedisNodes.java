/**
 * Copyright (c) 2013-2019 Nikita Koksharov
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
package org.redisson;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.redisson.api.Node;
import org.redisson.api.NodeType;
import org.redisson.api.NodesGroup;
import org.redisson.api.RFuture;
import org.redisson.client.RedisConnection;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.connection.ClientConnectionsEntry;
import org.redisson.connection.ClientConnectionsEntry.FreezeReason;
import org.redisson.connection.ConnectionListener;
import org.redisson.connection.ConnectionManager;
import org.redisson.connection.MasterSlaveEntry;
import org.redisson.connection.RedisClientEntry;
import org.redisson.misc.RedisURI;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <N> node type
 */
public class RedisNodes<N extends Node> implements NodesGroup<N> {

    final ConnectionManager connectionManager;

    public RedisNodes(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    @Override
    public N getNode(String address) {
        Collection<MasterSlaveEntry> entries = connectionManager.getEntrySet();
        RedisURI addr = new RedisURI(address);
        for (MasterSlaveEntry masterSlaveEntry : entries) {
            if (masterSlaveEntry.getAllEntries().isEmpty() 
                    && RedisURI.compare(masterSlaveEntry.getClient().getAddr(), addr)) {
                return (N) new RedisClientEntry(masterSlaveEntry.getClient(), connectionManager.getCommandExecutor(), NodeType.MASTER);
            }

            for (ClientConnectionsEntry entry : masterSlaveEntry.getAllEntries()) {
                if (RedisURI.compare(entry.getClient().getAddr(), addr) 
                        && entry.getFreezeReason() != FreezeReason.MANAGER) {
                    return (N) new RedisClientEntry(entry.getClient(), connectionManager.getCommandExecutor(), entry.getNodeType());
                }
            }
        }
        return null;
    }
    
    @Override
    public Collection<N> getNodes(NodeType type) {
        Collection<MasterSlaveEntry> entries = connectionManager.getEntrySet();
        List<N> result = new ArrayList<N>();
        for (MasterSlaveEntry masterSlaveEntry : entries) {
            if (masterSlaveEntry.getAllEntries().isEmpty() 
                    && type == NodeType.MASTER) {
                RedisClientEntry entry = new RedisClientEntry(masterSlaveEntry.getClient(), connectionManager.getCommandExecutor(), NodeType.MASTER);
                result.add((N) entry);
            }
            
            for (ClientConnectionsEntry slaveEntry : masterSlaveEntry.getAllEntries()) {
                if (slaveEntry.getFreezeReason() != FreezeReason.MANAGER 
                        && slaveEntry.getNodeType() == type) {
                    RedisClientEntry entry = new RedisClientEntry(slaveEntry.getClient(), connectionManager.getCommandExecutor(), slaveEntry.getNodeType());
                    result.add((N) entry);
                }
            }
        }
        return result;
    }


    @Override
    public Collection<N> getNodes() {
        Collection<MasterSlaveEntry> entries = connectionManager.getEntrySet();
        List<N> result = new ArrayList<N>();
        for (MasterSlaveEntry masterSlaveEntry : entries) {
            if (masterSlaveEntry.getAllEntries().isEmpty()) {
                RedisClientEntry masterEntry = new RedisClientEntry(masterSlaveEntry.getClient(), connectionManager.getCommandExecutor(), NodeType.MASTER);
                result.add((N) masterEntry);
            }
            
            for (ClientConnectionsEntry slaveEntry : masterSlaveEntry.getAllEntries()) {
                if (slaveEntry.getFreezeReason() != FreezeReason.MANAGER) {
                    RedisClientEntry entry = new RedisClientEntry(slaveEntry.getClient(), connectionManager.getCommandExecutor(), slaveEntry.getNodeType());
                    result.add((N) entry);
                }
            }
        }
        return result;
    }

    @Override
    public boolean pingAll(long timeout, TimeUnit timeUnit) {
        List<RedisClientEntry> clients = new ArrayList<>((Collection<RedisClientEntry>) getNodes());
        Map<RedisConnection, RFuture<String>> result = new ConcurrentHashMap<>(clients.size());
        CountDownLatch latch = new CountDownLatch(clients.size());
        for (RedisClientEntry entry : clients) {
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
            for (Entry<RedisConnection, RFuture<String>> entry : result.entrySet()) {
                entry.getKey().closeAsync();
            }
            return false;
        }

        time = System.currentTimeMillis();
        boolean res = true;
        for (Entry<RedisConnection, RFuture<String>> entry : result.entrySet()) {
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

    @Override
    public int addConnectionListener(ConnectionListener connectionListener) {
        return connectionManager.getConnectionEventsHub().addListener(connectionListener);
    }

    @Override
    public void removeConnectionListener(int listenerId) {
        connectionManager.getConnectionEventsHub().removeListener(listenerId);
    }

}
