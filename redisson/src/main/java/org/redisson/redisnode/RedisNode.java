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
package org.redisson.redisnode;

import org.redisson.api.NodeType;
import org.redisson.api.RFuture;
import org.redisson.api.redisnode.*;
import org.redisson.client.RedisClient;
import org.redisson.client.RedisTimeoutException;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.Time;
import org.redisson.cluster.ClusterSlotRange;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.misc.CompletableFutureWrapper;
import org.redisson.misc.RedisURI;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedisNode implements RedisClusterMaster, RedisClusterSlave, RedisMaster, RedisSlave,
                                        RedisClusterMasterAsync, RedisClusterSlaveAsync,
                                        RedisMasterAsync, RedisSlaveAsync {

    final RedisClient client;
    final CommandAsyncExecutor commandExecutor;
    private final NodeType type;

    public RedisNode(RedisClient client, CommandAsyncExecutor commandExecutor, NodeType type) {
        super();
        this.client = client;
        this.commandExecutor = commandExecutor;
        this.type = type;
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
        RFuture<Boolean> f = commandExecutor.readAsync(client, null, RedisCommands.PING_BOOL);
        CompletionStage<Boolean> s = f.exceptionally(e -> false);
        commandExecutor.getServiceManager().newTimeout(t -> {
            RedisTimeoutException ex = new RedisTimeoutException("Command execution timeout (" + timeUnit.toMillis(timeout) + "ms) for command: PING, Redis client: " + client);
            s.toCompletableFuture().completeExceptionally(ex);
        }, timeout, timeUnit);
        return new CompletableFutureWrapper<>(s);
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
        RedisNode other = (RedisNode) obj;
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
    public String toString() {
        return "RedisClientEntry [client=" + client + ", type=" + type + "]";
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
    public String clusterId() {
        return commandExecutor.get(clusterIdAsync());
    }

    @Override
    public void clusterAddSlots(int... slots) {
        commandExecutor.get(clusterAddSlotsAsync(slots));
    }

    @Override
    public void clusterReplicate(String nodeId) {
        commandExecutor.get(clusterReplicateAsync(nodeId));
    }

    @Override
    public void clusterForget(String nodeId) {
        commandExecutor.get(clusterForgetAsync(nodeId));
    }

    @Override
    public void clusterDeleteSlots(int... slots) {
        commandExecutor.get(clusterDeleteSlotsAsync(slots));
    }

    @Override
    public long clusterCountKeysInSlot(int slot) {
        return commandExecutor.get(clusterCountKeysInSlotAsync(slot));
    }

    @Override
    public List<String> clusterGetKeysInSlot(int slot, int count) {
        return commandExecutor.get(clusterGetKeysInSlotAsync(slot, count));
    }

    @Override
    public void clusterSetSlot(int slot, SetSlotCommand command) {
        commandExecutor.get(clusterSetSlotAsync(slot, command));
    }

    @Override
    public void clusterSetSlot(int slot, SetSlotCommand command, String nodeId) {
        commandExecutor.get(clusterSetSlotAsync(slot, command, nodeId));
    }

    @Override
    public void clusterMeet(String address) {
        commandExecutor.get(clusterMeetAsync(address));
    }

    @Override
    public long clusterCountFailureReports(String nodeId) {
        return commandExecutor.get(clusterCountFailureReportsAsync(nodeId));
    }

    @Override
    public void clusterFlushSlots() {
        commandExecutor.get(clusterFlushSlotsAsync());
    }

    @Override
    public Map<ClusterSlotRange, Set<String>> clusterSlots() {
        return commandExecutor.get(clusterSlotsAsync());
    }

    @Override
    public Map<String, String> info(org.redisson.api.redisnode.RedisNode.InfoSection section) {
        return commandExecutor.get(infoAsync(section));
    }

    @Override
    public Map<String, String> getMemoryStatistics() {
        return commandExecutor.get(getMemoryStatisticsAsync());
    }

    @Override
    public RFuture<Map<String, String>> getMemoryStatisticsAsync() {
        return commandExecutor.readAsync(client, StringCodec.INSTANCE, RedisCommands.MEMORY_STATS);
    }

    @Override
    public RFuture<String> clusterIdAsync() {
        return commandExecutor.readAsync(client, StringCodec.INSTANCE, RedisCommands.CLUSTER_MYID);
    }

    @Override
    public RFuture<Void> clusterAddSlotsAsync(int... slots) {
        return commandExecutor.writeAsync(client, StringCodec.INSTANCE, RedisCommands.CLUSTER_ADDSLOTS, IntStream.of(slots).boxed().toArray());
    }

    @Override
    public RFuture<Void> clusterReplicateAsync(String nodeId) {
        return commandExecutor.writeAsync(client, StringCodec.INSTANCE, RedisCommands.CLUSTER_REPLICATE, nodeId);
    }

    @Override
    public RFuture<Void> clusterForgetAsync(String nodeId) {
        return commandExecutor.writeAsync(client, StringCodec.INSTANCE, RedisCommands.CLUSTER_FORGET, nodeId);
    }

    @Override
    public RFuture<Void> clusterDeleteSlotsAsync(int... slots) {
        return commandExecutor.writeAsync(client, StringCodec.INSTANCE, RedisCommands.CLUSTER_DELSLOTS, IntStream.of(slots).boxed().toArray());
    }

    @Override
    public RFuture<Long> clusterCountKeysInSlotAsync(int slot) {
        return commandExecutor.readAsync(client, StringCodec.INSTANCE, RedisCommands.CLUSTER_COUNTKEYSINSLOT, slot);
    }

    @Override
    public RFuture<List<String>> clusterGetKeysInSlotAsync(int slot, int count) {
        return commandExecutor.readAsync(client, StringCodec.INSTANCE, RedisCommands.CLUSTER_GETKEYSINSLOT, slot, count);
    }

    @Override
    public RFuture<Void> clusterSetSlotAsync(int slot, SetSlotCommand command) {
        return commandExecutor.writeAsync(client, StringCodec.INSTANCE, RedisCommands.CLUSTER_SETSLOT, slot, command);
    }

    @Override
    public RFuture<Void> clusterSetSlotAsync(int slot, SetSlotCommand command, String nodeId) {
        return commandExecutor.writeAsync(client, StringCodec.INSTANCE, RedisCommands.CLUSTER_SETSLOT, slot, command, nodeId);
    }

    @Override
    public RFuture<Void> clusterMeetAsync(String address) {
        RedisURI uri = new RedisURI(address);
        return commandExecutor.writeAsync(client, StringCodec.INSTANCE, RedisCommands.CLUSTER_MEET, uri.getHost(), uri.getPort());
    }

    @Override
    public RFuture<Long> clusterCountFailureReportsAsync(String nodeId) {
        return commandExecutor.readAsync(client, StringCodec.INSTANCE, RedisCommands.CLUSTER_COUNTFAILUREREPORTS, nodeId);
    }

    @Override
    public RFuture<Void> clusterFlushSlotsAsync() {
        return commandExecutor.readAsync(client, StringCodec.INSTANCE, RedisCommands.CLUSTER_FLUSHSLOTS);
    }

    @Override
    public RFuture<Map<ClusterSlotRange, Set<String>>> clusterSlotsAsync() {
        return commandExecutor.readAsync(client, StringCodec.INSTANCE, RedisCommands.CLUSTER_SLOTS);
    }

    @Override
    public RFuture<Map<String, String>> infoAsync(org.redisson.api.redisnode.RedisNode.InfoSection section) {
        if (section == org.redisson.api.redisnode.RedisNode.InfoSection.ALL) {
            return commandExecutor.readAsync(client, StringCodec.INSTANCE, RedisCommands.INFO_ALL);
        } else if (section == org.redisson.api.redisnode.RedisNode.InfoSection.DEFAULT) {
                return commandExecutor.readAsync(client, StringCodec.INSTANCE, RedisCommands.INFO_DEFAULT);
        } else if (section == org.redisson.api.redisnode.RedisNode.InfoSection.SERVER) {
            return commandExecutor.readAsync(client, StringCodec.INSTANCE, RedisCommands.INFO_SERVER);
        } else if (section == org.redisson.api.redisnode.RedisNode.InfoSection.CLIENTS) {
            return commandExecutor.readAsync(client, StringCodec.INSTANCE, RedisCommands.INFO_CLIENTS);
        } else if (section == org.redisson.api.redisnode.RedisNode.InfoSection.MEMORY) {
            return commandExecutor.readAsync(client, StringCodec.INSTANCE, RedisCommands.INFO_MEMORY);
        } else if (section == org.redisson.api.redisnode.RedisNode.InfoSection.PERSISTENCE) {
            return commandExecutor.readAsync(client, StringCodec.INSTANCE, RedisCommands.INFO_PERSISTENCE);
        } else if (section == org.redisson.api.redisnode.RedisNode.InfoSection.STATS) {
            return commandExecutor.readAsync(client, StringCodec.INSTANCE, RedisCommands.INFO_STATS);
        } else if (section == org.redisson.api.redisnode.RedisNode.InfoSection.REPLICATION) {
            return commandExecutor.readAsync(client, StringCodec.INSTANCE, RedisCommands.INFO_REPLICATION);
        } else if (section == org.redisson.api.redisnode.RedisNode.InfoSection.CPU) {
            return commandExecutor.readAsync(client, StringCodec.INSTANCE, RedisCommands.INFO_CPU);
        } else if (section == org.redisson.api.redisnode.RedisNode.InfoSection.COMMANDSTATS) {
            return commandExecutor.readAsync(client, StringCodec.INSTANCE, RedisCommands.INFO_COMMANDSTATS);
        } else if (section == org.redisson.api.redisnode.RedisNode.InfoSection.CLUSTER) {
            return commandExecutor.readAsync(client, StringCodec.INSTANCE, RedisCommands.INFO_CLUSTER);
        } else if (section == org.redisson.api.redisnode.RedisNode.InfoSection.KEYSPACE) {
            return commandExecutor.readAsync(client, StringCodec.INSTANCE, RedisCommands.INFO_KEYSPACE);
        }
        throw new IllegalStateException();
    }

    @Override
    public Map<String, String> getConfig(String parameter) {
        return commandExecutor.get(getConfigAsync(parameter));
    }

    @Override
    public void setConfig(String parameter, String value) {
        commandExecutor.get(setConfigAsync(parameter, value));
    }

    @Override
    public RFuture<Map<String, String>> getConfigAsync(String parameter) {
        return commandExecutor.readAsync(client, StringCodec.INSTANCE, RedisCommands.CONFIG_GET_MAP, parameter);
    }

    @Override
    public RFuture<Void> setConfigAsync(String parameter, String value) {
        return commandExecutor.writeAsync(client, StringCodec.INSTANCE, RedisCommands.CONFIG_SET, parameter, value);
    }

}
