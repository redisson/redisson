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
package org.redisson;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CountDownLatch;

import org.redisson.client.RedisConnection;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.connection.ConnectionListener;
import org.redisson.connection.ConnectionManager;
import org.redisson.connection.RedisClientEntry;
import org.redisson.core.Node;
import org.redisson.core.NodeType;
import org.redisson.core.NodesGroup;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;

public class RedisNodes<N extends Node> implements NodesGroup<N> {

    final ConnectionManager connectionManager;

    public RedisNodes(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    @Override
    public Collection<N> getNodes(NodeType type) {
        Collection<N> clients = (Collection<N>) connectionManager.getClients();
        List<N> result = new ArrayList<N>();
        for (N node : clients) {
            if (node.getType().equals(type)) {
                result.add(node);
            }
        }
        return result;
    }


    @Override
    public Collection<N> getNodes() {
        return (Collection<N>) connectionManager.getClients();
    }

    @Override
    public boolean pingAll() {
        List<RedisClientEntry> clients = new ArrayList<RedisClientEntry>(connectionManager.getClients());
        final Map<RedisConnection, Future<String>> result = new HashMap<RedisConnection, Future<String>>(clients.size());
        final CountDownLatch latch = new CountDownLatch(clients.size());
        for (RedisClientEntry entry : clients) {
            Future<RedisConnection> f = entry.getClient().connectAsync();
            f.addListener(new FutureListener<RedisConnection>() {
                @Override
                public void operationComplete(Future<RedisConnection> future) throws Exception {
                    if (future.isSuccess()) {
                        final RedisConnection c = future.getNow();
                        Promise<RedisConnection> connectionFuture = connectionManager.newPromise();
                        connectionManager.getConnectListener().onConnect(connectionFuture, c, null, connectionManager.getConfig());
                        connectionFuture.addListener(new FutureListener<RedisConnection>() {
                            @Override
                            public void operationComplete(Future<RedisConnection> future) throws Exception {
                                Future<String> r = c.async(connectionManager.getConfig().getPingTimeout(), RedisCommands.PING);
                                result.put(c, r);
                                latch.countDown();
                            }
                        });
                    } else {
                        latch.countDown();
                    }
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
            for (Entry<RedisConnection, Future<String>> entry : result.entrySet()) {
                entry.getKey().closeAsync();
            }
            return false;
        }

        time = System.currentTimeMillis();
        boolean res = true;
        for (Entry<RedisConnection, Future<String>> entry : result.entrySet()) {
            Future<String> f = entry.getValue();
            f.awaitUninterruptibly();
            if (!"PONG".equals(f.getNow())) {
                res = false;
            }
            entry.getKey().closeAsync();
        }

        // true and no futures missed during client connection
        return res && result.size() == clients.size();
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
