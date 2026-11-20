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

import org.reactivestreams.Publisher;
import org.redisson.api.BatchResult;
import org.redisson.client.codec.ByteArrayCodec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandBatchService;
import org.redisson.reactive.CommandReactiveExecutor;
import org.springframework.data.redis.connection.ClusterSlotHashUtil;
import org.springframework.data.redis.connection.ReactiveClusterStringCommands;
import org.springframework.data.redis.connection.ReactiveRedisConnection.BooleanResponse;
import org.springframework.data.redis.connection.ReactiveRedisConnection.MultiValueResponse;
import org.springframework.util.Assert;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonReactiveClusterStringCommands extends RedissonReactiveStringCommands implements ReactiveClusterStringCommands {

    RedissonReactiveClusterStringCommands(CommandReactiveExecutor executorService) {
        super(executorService);
    }

    @Override
    public Flux<MultiValueResponse<List<ByteBuffer>, ByteBuffer>> mGet(Publisher<List<ByteBuffer>> keysets) {
        return execute(keysets, coll -> {

            Assert.notNull(coll, "List must not be null!");

            if (ClusterSlotHashUtil.isSameSlotForAllKeys(coll)) {
                return super.mGet(Mono.just(coll));
            }

            CommandBatchService es = new CommandBatchService(executorService);
            for (ByteBuffer key : coll) {
                byte[] keyBuf = toByteArray(key);
                es.readAsync(keyBuf, ByteArrayCodec.INSTANCE, RedisCommands.GET, keyBuf);
            }

            Mono<BatchResult<?>> m = executorService.reactive(es::executeAsync);
            return m.map(r -> {
                List<ByteBuffer> values = new ArrayList<>(r.getResponses().size());
                for (Object value : r.getResponses()) {
                    if (value == null) {
                        values.add(null);
                    } else {
                        values.add(ByteBuffer.wrap((byte[]) value));
                    }
                }
                return new MultiValueResponse<>(coll, values);
            });
        });
    }

    @Override
    public Flux<BooleanResponse<MSetCommand>> mSet(Publisher<MSetCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getKeyValuePairs(), "KeyValuePairs must not be null!");

            if (ClusterSlotHashUtil.isSameSlotForAllKeys(command.getKeyValuePairs().keySet())) {
                return super.mSet(Mono.just(command));
            }

            CommandBatchService es = new CommandBatchService(executorService);
            for (Map.Entry<ByteBuffer, ByteBuffer> entry : command.getKeyValuePairs().entrySet()) {
                byte[] keyBuf = toByteArray(entry.getKey());
                es.writeAsync(keyBuf, StringCodec.INSTANCE, RedisCommands.SET, keyBuf, toByteArray(entry.getValue()));
            }

            Mono<BatchResult<?>> m = executorService.reactive(es::executeAsync);
            return m.map(r -> new BooleanResponse<>(command, true));
        });
    }

}
