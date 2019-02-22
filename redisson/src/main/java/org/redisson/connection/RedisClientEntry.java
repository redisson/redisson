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
package org.redisson.connection;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.redisson.api.ClusterNode;
import org.redisson.api.NodeType;
import org.redisson.api.RFuture;
import org.redisson.client.RedisClient;
import org.redisson.client.RedisTimeoutException;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.Time;
import org.redisson.command.CommandSyncService;
import org.redisson.misc.RPromise;
import org.redisson.misc.RedissonPromise;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedisClientEntry implements ClusterNode {

    private final RedisClient client;
    private final CommandSyncService commandExecutor;
    private final NodeType type;

    public RedisClientEntry(RedisClient client, CommandSyncService commandExecutor, NodeType type) {
        super();
        this.client = client;
        this.commandExecutor = commandExecutor;
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

    @Override
    public RFuture<Boolean> pingAsync() {
        return pingAsync(1, TimeUnit.SECONDS);
    }
    
    @Override
    public RFuture<Boolean> pingAsync(long timeout, TimeUnit timeUnit) {
        RPromise<Boolean> result = new RedissonPromise<>();
        RFuture<Boolean> f = commandExecutor.readAsync(client, null, RedisCommands.PING_BOOL);
        f.onComplete((res, e) -> {
            if (e != null) {
                result.trySuccess(false);
                return;
            }
            
            result.trySuccess(res);
        });
        commandExecutor.getConnectionManager().newTimeout(t -> {
            RedisTimeoutException ex = new RedisTimeoutException("Command execution timeout for command: PING, Redis client: " + client);
            result.tryFailure(ex);
        }, timeout, timeUnit);
        return result;
    }
    
    @Override
    public boolean ping() {
        return commandExecutor.get(pingAsync());
    }
    
    @Override
    public boolean ping(long timeout, TimeUnit timeUnit) {
        return commandExecutor.get(pingAsync(timeout, timeUnit));
    }

    @Override
    @SuppressWarnings("AvoidInlineConditionals")
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
    public RFuture<Time> timeAsync() {
        return commandExecutor.readAsync(client, LongCodec.INSTANCE, RedisCommands.TIME);
    }
    
    @Override
    public Time time() {
        return commandExecutor.get(timeAsync());
    }
    
    @Override
    public RFuture<Map<String, String>> clusterInfoAsync() {
        return commandExecutor.readAsync(client, StringCodec.INSTANCE, RedisCommands.CLUSTER_INFO);
    }
    
    @Override
    public Map<String, String> clusterInfo() {
        return commandExecutor.get(clusterInfoAsync());
    }
    
    @Override
    public Map<String, String> info(InfoSection section) {
        return commandExecutor.get(infoAsync(section));
    }
    
    @Override
    public RFuture<Map<String, String>> infoAsync(InfoSection section) {
        if (section == InfoSection.ALL) {
            return commandExecutor.readAsync(client, StringCodec.INSTANCE, RedisCommands.INFO_ALL);
        } else if (section == InfoSection.DEFAULT) {
                return commandExecutor.readAsync(client, StringCodec.INSTANCE, RedisCommands.INFO_DEFAULT);
        } else if (section == InfoSection.SERVER) {
            return commandExecutor.readAsync(client, StringCodec.INSTANCE, RedisCommands.INFO_SERVER);
        } else if (section == InfoSection.CLIENTS) {
            return commandExecutor.readAsync(client, StringCodec.INSTANCE, RedisCommands.INFO_CLIENTS);
        } else if (section == InfoSection.MEMORY) {
            return commandExecutor.readAsync(client, StringCodec.INSTANCE, RedisCommands.INFO_MEMORY);
        } else if (section == InfoSection.PERSISTENCE) {
            return commandExecutor.readAsync(client, StringCodec.INSTANCE, RedisCommands.INFO_PERSISTENCE);
        } else if (section == InfoSection.STATS) {
            return commandExecutor.readAsync(client, StringCodec.INSTANCE, RedisCommands.INFO_STATS);
        } else if (section == InfoSection.REPLICATION) {
            return commandExecutor.readAsync(client, StringCodec.INSTANCE, RedisCommands.INFO_REPLICATION);
        } else if (section == InfoSection.CPU) {
            return commandExecutor.readAsync(client, StringCodec.INSTANCE, RedisCommands.INFO_CPU);
        } else if (section == InfoSection.COMMANDSTATS) {
            return commandExecutor.readAsync(client, StringCodec.INSTANCE, RedisCommands.INFO_COMMANDSTATS);
        } else if (section == InfoSection.CLUSTER) {
            return commandExecutor.readAsync(client, StringCodec.INSTANCE, RedisCommands.INFO_CLUSTER);
        } else if (section == InfoSection.KEYSPACE) {
            return commandExecutor.readAsync(client, StringCodec.INSTANCE, RedisCommands.INFO_KEYSPACE);
        }
        throw new IllegalStateException();
    }

    @Override
    public String toString() {
        return "RedisClientEntry [client=" + client + ", type=" + type + "]";
    }
    
}
