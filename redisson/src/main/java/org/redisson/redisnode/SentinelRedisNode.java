/**
 * Copyright (c) 2013-2021 Nikita Koksharov
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

import org.redisson.api.RFuture;
import org.redisson.api.redisnode.RedisSentinel;
import org.redisson.api.redisnode.RedisSentinelAsync;
import org.redisson.client.RedisClient;
import org.redisson.client.RedisConnection;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.Time;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.misc.RPromise;
import org.redisson.misc.RedissonPromise;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class SentinelRedisNode implements RedisSentinel, RedisSentinelAsync {

    private final RedisClient client;
    private final CommandAsyncExecutor commandAsyncService;

    public SentinelRedisNode(RedisClient client, CommandAsyncExecutor commandAsyncService) {
        super();
        this.client = client;
        this.commandAsyncService = commandAsyncService;
    }

    public RedisClient getClient() {
        return client;
    }

    @Override
    public InetSocketAddress getAddr() {
        return client.getAddr();
    }

    @Override
    public Map<String, String> getMemoryStatistics() {
        return getMemoryStatisticsAsync().syncUninterruptibly().getNow();
    }

    @Override
    public RFuture<Map<String, String>> getMemoryStatisticsAsync() {
        return executeAsync(null, StringCodec.INSTANCE, -1, RedisCommands.MEMORY_STATS);
    }

    @Override
    public RFuture<Boolean> pingAsync() {
        return pingAsync(1, TimeUnit.SECONDS);
    }

    @Override
    public RFuture<Boolean> pingAsync(long timeout, TimeUnit timeUnit) {
        return executeAsync(false, null, timeUnit.toMillis(timeout), RedisCommands.PING_BOOL);
    }

    @Override
    public boolean ping() {
        return pingAsync().syncUninterruptibly().getNow();
    }

    @Override
    public boolean ping(long timeout, TimeUnit timeUnit) {
        return pingAsync(timeout, timeUnit).syncUninterruptibly().getNow();
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
        SentinelRedisNode other = (SentinelRedisNode) obj;
        if (client == null) {
            if (other.client != null)
                return false;
        } else if (!client.getAddr().equals(other.client.getAddr()))
            return false;
        return true;
    }

    private <T> RFuture<T> executeAsync(T defaultValue, Codec codec, long timeout, RedisCommand<T> command, Object... params) {
        RPromise<T> result = new RedissonPromise<>();
        RFuture<RedisConnection> connectionFuture = client.connectAsync();
        connectionFuture.onComplete((connection, ex) -> {
            if (ex != null) {
                if (defaultValue != null) {
                    result.trySuccess(defaultValue);
                } else {
                    result.tryFailure(ex);
                }
                return;
            }

            RFuture<T> future = connection.async(timeout, codec, command, params);
            future.onComplete((r, e) -> {
                connection.closeAsync();

                if (e != null) {
                    if (defaultValue != null) {
                        result.trySuccess(defaultValue);
                    } else {
                        result.tryFailure(e);
                    }
                    return;
                }

                result.trySuccess(r);
            });
        });
        return result;
    }

    @Override
    public RFuture<Time> timeAsync() {
        return executeAsync(null, LongCodec.INSTANCE, -1, RedisCommands.TIME);
    }

    @Override
    public Time time() {
        return timeAsync().syncUninterruptibly().getNow();
    }

    @Override
    public String toString() {
        return this.getClass().toString() + " [client=" + client + "]";
    }

    @Override
    public Map<String, String> info(InfoSection section) {
        return infoAsync(section).syncUninterruptibly().getNow();
    }

    @Override
    public RFuture<Map<String, String>> infoAsync(InfoSection section) {
        if (section == InfoSection.ALL) {
            return executeAsync(null, StringCodec.INSTANCE, -1, RedisCommands.INFO_ALL);
        } else if (section == InfoSection.DEFAULT) {
            return executeAsync(null, StringCodec.INSTANCE, -1, RedisCommands.INFO_DEFAULT);
        } else if (section == InfoSection.SERVER) {
            return executeAsync(null, StringCodec.INSTANCE, -1, RedisCommands.INFO_SERVER);
        } else if (section == InfoSection.CLIENTS) {
            return executeAsync(null, StringCodec.INSTANCE, -1, RedisCommands.INFO_CLIENTS);
        } else if (section == InfoSection.MEMORY) {
            return executeAsync(null, StringCodec.INSTANCE, -1, RedisCommands.INFO_MEMORY);
        } else if (section == InfoSection.PERSISTENCE) {
            return executeAsync(null, StringCodec.INSTANCE, -1, RedisCommands.INFO_PERSISTENCE);
        } else if (section == InfoSection.STATS) {
            return executeAsync(null, StringCodec.INSTANCE, -1, RedisCommands.INFO_STATS);
        } else if (section == InfoSection.REPLICATION) {
            return executeAsync(null, StringCodec.INSTANCE, -1, RedisCommands.INFO_REPLICATION);
        } else if (section == InfoSection.CPU) {
            return executeAsync(null, StringCodec.INSTANCE, -1, RedisCommands.INFO_CPU);
        } else if (section == InfoSection.COMMANDSTATS) {
            return executeAsync(null, StringCodec.INSTANCE, -1, RedisCommands.INFO_COMMANDSTATS);
        } else if (section == InfoSection.CLUSTER) {
            return executeAsync(null, StringCodec.INSTANCE, -1, RedisCommands.INFO_CLUSTER);
        } else if (section == InfoSection.KEYSPACE) {
            return executeAsync(null, StringCodec.INSTANCE, -1, RedisCommands.INFO_KEYSPACE);
        }
        throw new IllegalStateException();
    }

    @Override
    public InetSocketAddress getMasterAddr(String masterName) {
        return commandAsyncService.get(getMasterAddrAsync(masterName));
    }

    @Override
    public List<Map<String, String>> getSentinels(String masterName) {
        return commandAsyncService.get(getSentinelsAsync(masterName));
    }

    @Override
    public List<Map<String, String>> getMasters() {
        return commandAsyncService.get(getMastersAsync());
    }

    @Override
    public List<Map<String, String>> getSlaves(String masterName) {
        return commandAsyncService.get(getSlavesAsync(masterName));
    }

    @Override
    public Map<String, String> getMaster(String masterName) {
        return commandAsyncService.get(getMasterAsync(masterName));
    }

    @Override
    public void failover(String masterName) {
        commandAsyncService.get(failoverAsync(masterName));
    }

    @Override
    public RFuture<InetSocketAddress> getMasterAddrAsync(String masterName) {
        return executeAsync(null, StringCodec.INSTANCE, -1, RedisCommands.SENTINEL_GET_MASTER_ADDR_BY_NAME, masterName);
    }

    @Override
    public RFuture<List<Map<String, String>>> getSentinelsAsync(String masterName) {
        return executeAsync(null, StringCodec.INSTANCE, -1, RedisCommands.SENTINEL_SENTINELS, masterName);
    }

    @Override
    public RFuture<List<Map<String, String>>> getMastersAsync() {
        return executeAsync(null, StringCodec.INSTANCE, -1, RedisCommands.SENTINEL_MASTERS);
    }

    @Override
    public RFuture<List<Map<String, String>>> getSlavesAsync(String masterName) {
        return executeAsync(null, StringCodec.INSTANCE, -1, RedisCommands.SENTINEL_SLAVES, masterName);
    }

    @Override
    public RFuture<Map<String, String>> getMasterAsync(String masterName) {
        return executeAsync(null, StringCodec.INSTANCE, -1, RedisCommands.SENTINEL_MASTER, masterName);
    }

    @Override
    public RFuture<Void> failoverAsync(String masterName) {
        return executeAsync(null, StringCodec.INSTANCE, -1, RedisCommands.SENTINEL_FAILOVER, masterName);
    }

    @Override
    public Map<String, String> getConfig(String parameter) {
        return getConfigAsync(parameter).syncUninterruptibly().getNow();
    }

    @Override
    public void setConfig(String parameter, String value) {
        setConfigAsync(parameter, value).syncUninterruptibly().getNow();
    }

    @Override
    public RFuture<Map<String, String>> getConfigAsync(String parameter) {
        return executeAsync(null, StringCodec.INSTANCE, -1, RedisCommands.CONFIG_GET_MAP, parameter);
    }

    @Override
    public RFuture<Void> setConfigAsync(String parameter, String value) {
        return executeAsync(null, StringCodec.INSTANCE, -1, RedisCommands.CONFIG_SET, parameter, value);
    }

}
