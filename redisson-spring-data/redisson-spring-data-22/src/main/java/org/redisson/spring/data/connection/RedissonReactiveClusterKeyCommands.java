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
package org.redisson.spring.data.connection;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.stream.Collectors;

import org.redisson.api.RFuture;
import org.redisson.client.codec.ByteArrayCodec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.connection.MasterSlaveEntry;
import org.redisson.reactive.CommandReactiveExecutor;
import org.springframework.data.redis.connection.ReactiveClusterKeyCommands;
import org.springframework.data.redis.connection.RedisClusterNode;

import io.netty.util.CharsetUtil;
import reactor.core.publisher.Mono;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonReactiveClusterKeyCommands extends RedissonReactiveKeyCommands implements ReactiveClusterKeyCommands {

    public RedissonReactiveClusterKeyCommands(CommandReactiveExecutor executorService) {
        super(executorService);
    }

    @Override
    public Mono<List<ByteBuffer>> keys(RedisClusterNode node, ByteBuffer pattern) {
        Mono<List<String>> m = executorService.reactive(() -> {
            return (RFuture<List<String>>)(Object) executorService.readAllAsync(StringCodec.INSTANCE, RedisCommands.KEYS, toByteArray(pattern));
        });
        return m.map(v -> v.stream().map(t -> ByteBuffer.wrap(t.getBytes(CharsetUtil.UTF_8))).collect(Collectors.toList()));
    }

    @Override
    public Mono<ByteBuffer> randomKey(RedisClusterNode node) {
        MasterSlaveEntry entry = getEntry(node);
        Mono<byte[]> m = executorService.reactive(() -> {
            return executorService.readRandomAsync(entry, ByteArrayCodec.INSTANCE, RedisCommands.RANDOM_KEY);
        });
        return m.map(v -> ByteBuffer.wrap(v));
    }

}
