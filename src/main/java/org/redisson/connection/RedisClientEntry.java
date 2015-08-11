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
import java.util.Map;

import org.redisson.client.RedisClient;
import org.redisson.client.RedisConnection;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.core.ClusterNode;

public class RedisClientEntry implements ClusterNode {

    private final RedisClient client;

    public RedisClientEntry(RedisClient client) {
        super();
        this.client = client;
    }

    public RedisClient getClient() {
        return client;
    }

    @Override
    public InetSocketAddress getAddr() {
        return client.getAddr();
    }

    @Override
    public boolean ping() {
        RedisConnection c = client.connect();
        try {
            return "PONG".equals(c.sync(RedisCommands.PING));
        } catch (Exception e) {
            return false;
        } finally {
            c.closeAsync();
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
        RedisConnection c = client.connect();
        try {
            return c.sync(RedisCommands.CLUSTER_INFO);
        } catch (Exception e) {
            return null;
        } finally {
            c.closeAsync();
        }
    }

}
