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
import org.redisson.client.codec.ByteArrayCodec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.reactive.CommandReactiveExecutor;
import org.springframework.data.redis.connection.ClusterSlotHashUtil;
import org.springframework.data.redis.connection.ReactiveClusterListCommands;
import org.springframework.data.redis.connection.ReactiveRedisConnection.ByteBufferResponse;
import org.springframework.util.Assert;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.ByteBuffer;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonReactiveClusterListCommands extends RedissonReactiveListCommands implements ReactiveClusterListCommands {

    RedissonReactiveClusterListCommands(CommandReactiveExecutor executorService) {
        super(executorService);
    }

    @Override
    public Flux<ByteBufferResponse<RPopLPushCommand>> rPopLPush(Publisher<RPopLPushCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");
            Assert.notNull(command.getDestination(), "Destination key must not be null!");

            if (ClusterSlotHashUtil.isSameSlotForAllKeys(command.getKey(), command.getDestination())) {
                return super.rPopLPush(Mono.just(command));
            }

            byte[] keyBuf = toByteArray(command.getKey());
            byte[] destinationBuf = toByteArray(command.getDestination());
            Mono<byte[]> popped = write(keyBuf, ByteArrayCodec.INSTANCE, RedisCommands.RPOP, keyBuf);
            return popped.flatMap(value -> {
                Mono<Object> pushed = write(destinationBuf, StringCodec.INSTANCE, RedisCommands.LPUSH, destinationBuf, value);
                return pushed.thenReturn(new ByteBufferResponse<>(command, ByteBuffer.wrap(value)));
            });
        });
    }

}
