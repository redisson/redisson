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
package org.redisson.connection;

import io.netty.util.concurrent.Promise;
import org.redisson.client.RedisClient;
import org.redisson.client.RedisConnection;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.core.ClusterNode;
import org.redisson.core.NodeType;

import java.net.InetSocketAddress;
import java.util.Map;

public class RedisClientEntry implements ClusterNode {

    private final RedisClient client;
    private final ConnectionManager manager;
    private final NodeType type;

    public RedisClientEntry(RedisClient client, ConnectionManager manager, NodeType type) {
        super();
        this.client = client;
        this.manager = manager;
        this.type = type;
    }

    @Override
    public NodeType getType() {
        return type;
    }

    public RedisClient getClient() {
        return client;
    }

    @Override
    public InetSocketAddress getAddr() {
        return client.getAddr();
    }

    private RedisConnection connect() {
        RedisConnection c = client.connect();
        Promise<RedisConnection> future = manager.newPromise();
        manager.getConnectListener().onConnect(future, c, null, manager.getConfig());
        future.syncUninterruptibly();
        return future.getNow();
    }

    @Override
    public boolean ping() {
        RedisConnection c = null;
        try {
            c = connect();
            return "PONG".equals(c.sync(RedisCommands.PING));
        } catch (Exception e) {
            return false;
        } finally {
            if (c != null) {
                c.closeAsync();
            }
        }
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((client == null) ? 0 : client.getAddr().hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        RedisClientEntry other = (RedisClientEntry) obj;
        if (client == null) {
            if (other.client != null)
                return false;
        } else if (!client.getAddr().equals(other.client.getAddr()))
            return false;
        return true;
    }

    @Override
    public Map<String, String> info() {
        RedisConnection c = null;
        try {
            c = connect();
            return c.sync(RedisCommands.CLUSTER_INFO);
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) {
                c.closeAsync();
            }
        }
    }

}
