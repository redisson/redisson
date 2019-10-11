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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.reactivestreams.Publisher;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.RedisStrictCommand;
import org.redisson.reactive.CommandReactiveExecutor;
import org.springframework.data.redis.connection.ReactiveHyperLogLogCommands;
import org.springframework.data.redis.connection.ReactiveRedisConnection.BooleanResponse;
import org.springframework.data.redis.connection.ReactiveRedisConnection.NumericResponse;
import org.springframework.util.Assert;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonReactiveHyperLogLogCommands extends RedissonBaseReactive implements ReactiveHyperLogLogCommands {

    RedissonReactiveHyperLogLogCommands(CommandReactiveExecutor executorService) {
        super(executorService);
    }

    private static final RedisCommand<Long> PFADD = new RedisCommand<Long>("PFADD");
    
    @Override
    public Flux<NumericResponse<PfAddCommand, Long>> pfAdd(Publisher<PfAddCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");
            Assert.notEmpty(command.getValues(), "Values must not be empty!");

            byte[] keyBuf = toByteArray(command.getKey());

            List<Object> params = new ArrayList<Object>(command.getValues().size() + 1);
            params.add(keyBuf);
            params.addAll(command.getValues().stream().map(v -> toByteArray(v)).collect(Collectors.toList()));

            Mono<Long> m = write(keyBuf, StringCodec.INSTANCE, PFADD, params.toArray());
            return m.map(v -> new NumericResponse<>(command, v));
        });
    }

    @Override
    public Flux<NumericResponse<PfCountCommand, Long>> pfCount(Publisher<PfCountCommand> commands) {
        return execute(commands, command -> {

            Assert.notEmpty(command.getKeys(), "Keys must not be empty!");

            Object[] args = command.getKeys().stream().map(v -> toByteArray(v)).toArray();

            Mono<Long> m = write((byte[])args[0], StringCodec.INSTANCE, RedisCommands.PFCOUNT, args);
            return m.map(v -> new NumericResponse<>(command, v));
        });
    }
    
    private static final RedisStrictCommand<String> PFMERGE = new RedisStrictCommand<String>("PFMERGE");

    @Override
    public Flux<BooleanResponse<PfMergeCommand>> pfMerge(Publisher<PfMergeCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getKey(), "Destination key must not be null!");
            Assert.notEmpty(command.getSourceKeys(), "Source keys must not be null!");
            
            byte[] keyBuf = toByteArray(command.getKey());
            List<Object> args = new ArrayList<Object>(command.getSourceKeys().size() + 1);
            args.add(keyBuf);
            args.addAll(command.getSourceKeys().stream().map(v -> toByteArray(v)).collect(Collectors.toList()));
            Mono<String> m = write(keyBuf, StringCodec.INSTANCE, PFMERGE, args.toArray());
            return m.map(v -> new BooleanResponse<>(command, true));
        });
    }

}
