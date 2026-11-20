/**
 * Copyright (c) 2013-2026 Nikita Koksharov
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
package org.redisson.spring.data.connection;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Function;

import org.redisson.client.RedisClient;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.RedisStrictCommand;
import org.redisson.connection.MasterSlaveEntry;
import org.redisson.reactive.CommandReactiveExecutor;
import org.springframework.data.redis.connection.ReactiveClusterServerCommands;
import org.springframework.data.redis.connection.RedisClusterNode;
import org.springframework.data.redis.connection.RedisServerCommands;
import org.springframework.data.redis.connection.convert.StringToRedisClientInfoConverter;
import org.springframework.data.redis.core.types.RedisClientInfo;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonReactiveClusterServerCommands extends RedissonReactiveServerCommands implements ReactiveClusterServerCommands {

    RedissonReactiveClusterServerCommands(CommandReactiveExecutor executorService) {
        super(executorService);
    }

    @Override
    public Mono<String> bgReWriteAof(RedisClusterNode node) {
        return execute(node, BGREWRITEAOF);
    }

    @Override
    public Mono<String> bgSave(RedisClusterNode node) {
        return execute(node, BGSAVE);
    }

    @Override
    public Mono<Long> lastSave(RedisClusterNode node) {
        return execute(node, RedisCommands.LASTSAVE);
    }

    @Override
    public Mono<String> save(RedisClusterNode node) {
        return execute(node, SAVE);
    }

    @Override
    public Mono<Long> dbSize(RedisClusterNode node) {
        return execute(node, RedisCommands.DBSIZE);
    }
    
    private static final RedisStrictCommand<String> FLUSHDB = new RedisStrictCommand<String>("FLUSHDB");

    @Override
    public Mono<String> flushDb(RedisClusterNode node) {
        return execute(node, FLUSHDB);
    }
    
    private static final RedisStrictCommand<String> FLUSHALL = new RedisStrictCommand<String>("FLUSHALL");

    @Override
    public Mono<String> flushAll(RedisClusterNode node) {
        return execute(node, FLUSHALL);
    }

    @Override
    public Mono<String> flushDb(RedisClusterNode node, RedisServerCommands.FlushOption option) {
        if (option == RedisServerCommands.FlushOption.ASYNC) {
            return execute(node, FLUSHDB, option.toString());
        }
        return execute(node, FLUSHDB);
    }

    @Override
    public Mono<String> flushAll(RedisClusterNode node, RedisServerCommands.FlushOption option) {
        if (option == RedisServerCommands.FlushOption.ASYNC) {
            return execute(node, FLUSHALL, option.toString());
        }
        return execute(node, FLUSHALL);
    }

    @Override
    public Mono<Properties> info() {
        return collectFromMasters(this::info);
    }

    @Override
    public Mono<Properties> info(String section) {
        return collectFromMasters(master -> info(master, section));
    }

    @Override
    public Mono<Properties> getConfig(String pattern) {
        return collectFromMasters(master -> getConfig(master, pattern));
    }

    @Override
    public Mono<String> setConfig(String param, String value) {
        return Flux.defer(() -> Flux.fromIterable(getMasters()))
                .concatMap(master -> setConfig(master, param, value))
                .last();
    }

    @Override
    public Mono<String> resetConfigStats() {
        return Flux.defer(() -> Flux.fromIterable(getMasters()))
                .concatMap(this::resetConfigStats)
                .last();
    }

    private List<RedisClusterNode> getMasters() {
        List<RedisClusterNode> masters = new ArrayList<>();
        for (MasterSlaveEntry entry : executorService.getConnectionManager().getEntrySet()) {
            InetSocketAddress addr = entry.getClient().getAddr();
            masters.add(new RedisClusterNode(addr.getHostString(), addr.getPort()));
        }
        return masters;
    }

    private Mono<Properties> collectFromMasters(Function<RedisClusterNode, Mono<Properties>> command) {
        return Flux.defer(() -> Flux.fromIterable(getMasters()))
                .concatMap(master -> command.apply(master).map(properties -> {
                    Properties prefixed = new Properties();
                    for (Map.Entry<Object, Object> entry : properties.entrySet()) {
                        prefixed.put(master.asString() + "." + entry.getKey(), entry.getValue());
                    }
                    return prefixed;
                }))
                .collect(Properties::new, Properties::putAll);
    }


    @Override
    public Mono<Properties> info(RedisClusterNode node) {
        return execute(node, INFO_DEFAULT);
    }

    @Override
    public Mono<Properties> info(RedisClusterNode node, String section) {
        return execute(node, INFO, section);
    }

    @Override
    public Mono<Properties> getConfig(RedisClusterNode node, String pattern) {
        return execute(node, CONFIG_GET, pattern);
    }

    @Override
    public Mono<String> setConfig(RedisClusterNode node, String param, String value) {
        return execute(node, CONFIG_SET, param, value);
    }

    @Override
    public Mono<String> resetConfigStats(RedisClusterNode node) {
        return execute(node, CONFIG_RESETSTAT);
    }

    @Override
    public Mono<Long> time(RedisClusterNode node) {
        return execute(node, LongCodec.INSTANCE, TIME);
    }

    private static final StringToRedisClientInfoConverter CONVERTER = new StringToRedisClientInfoConverter();
    
    @Override
    public Flux<RedisClientInfo> getClientList(RedisClusterNode node) {
        RedisClient entry = getEntry(node);
        Mono<List<String>> m = executorService.reactive(() -> {
            return executorService.readAsync(entry, StringCodec.INSTANCE, RedisCommands.CLIENT_LIST);
        });
        return m.flatMapMany(s -> Flux.fromIterable(CONVERTER.convert(s.toArray(new String[s.size()]))));
    }

}
