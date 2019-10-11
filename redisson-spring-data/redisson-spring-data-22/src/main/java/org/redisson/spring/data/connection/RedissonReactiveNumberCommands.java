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

import java.math.BigDecimal;

import org.reactivestreams.Publisher;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.convertor.NumberConvertor;
import org.redisson.reactive.CommandReactiveExecutor;
import org.springframework.data.redis.connection.ReactiveNumberCommands;
import org.springframework.data.redis.connection.ReactiveRedisConnection.KeyCommand;
import org.springframework.data.redis.connection.ReactiveRedisConnection.NumericResponse;
import org.springframework.util.Assert;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonReactiveNumberCommands extends RedissonBaseReactive implements ReactiveNumberCommands {

    public RedissonReactiveNumberCommands(CommandReactiveExecutor executorService) {
        super(executorService);
    }

    @Override
    public Flux<NumericResponse<KeyCommand, Long>> incr(Publisher<KeyCommand> keys) {
        return execute(keys, key -> {

            Assert.notNull(key.getKey(), "Key must not be null!");

            byte[] keyBuf = toByteArray(key.getKey());
            Mono<Long> m = write(keyBuf, StringCodec.INSTANCE, RedisCommands.INCR, keyBuf);
            return m.map(v -> new NumericResponse<>(key, v));
        });
    }

    @Override
    public <T extends Number> Flux<NumericResponse<IncrByCommand<T>, T>> incrBy(Publisher<IncrByCommand<T>> commands) {
        return execute(commands, key -> {

            Assert.notNull(key.getKey(), "Key must not be null!");
            Assert.notNull(key.getValue(), "Value must not be null!");

            byte[] keyBuf = toByteArray(key.getKey());
            
            Mono<T> m = write(keyBuf, StringCodec.INSTANCE,
                    new RedisCommand<Object>("INCRBYFLOAT", new NumberConvertor(key.getValue().getClass())),
                    keyBuf, new BigDecimal(key.getValue().toString()).toPlainString());
            return m.map(v -> new NumericResponse<>(key, v));
        });
    }

    @Override
    public Flux<NumericResponse<KeyCommand, Long>> decr(Publisher<KeyCommand> keys) {
        return execute(keys, key -> {

            Assert.notNull(key.getKey(), "Key must not be null!");

            byte[] keyBuf = toByteArray(key.getKey());
            Mono<Long> m = write(keyBuf, StringCodec.INSTANCE, RedisCommands.DECR, keyBuf);
            return m.map(v -> new NumericResponse<>(key, v));
        });
    }

    @Override
    public <T extends Number> Flux<NumericResponse<DecrByCommand<T>, T>> decrBy(Publisher<DecrByCommand<T>> commands) {
        return execute(commands, key -> {

            Assert.notNull(key.getKey(), "Key must not be null!");
            Assert.notNull(key.getValue(), "Value must not be null!");

            byte[] keyBuf = toByteArray(key.getKey());
            
            Mono<T> m = write(keyBuf, StringCodec.INSTANCE,
                    new RedisCommand<Object>("INCRBYFLOAT", new NumberConvertor(key.getValue().getClass())),
                    keyBuf, "-" + new BigDecimal(key.getValue().toString()).toPlainString());
            return m.map(v -> new NumericResponse<>(key, v));
        });
    }

    @Override
    public <T extends Number> Flux<NumericResponse<HIncrByCommand<T>, T>> hIncrBy(
            Publisher<HIncrByCommand<T>> commands) {
        return execute(commands, key -> {

            Assert.notNull(key.getKey(), "Key must not be null!");
            Assert.notNull(key.getValue(), "Value must not be null!");

            byte[] keyBuf = toByteArray(key.getKey());
            byte[] fieldBuf = toByteArray(key.getField());
            
            Mono<T> m = write(keyBuf, StringCodec.INSTANCE,
                    new RedisCommand<Object>("HINCRBYFLOAT", new NumberConvertor(key.getValue().getClass())),
                    keyBuf, fieldBuf, new BigDecimal(key.getValue().toString()).toPlainString());
            return m.map(v -> new NumericResponse<>(key, v));
        });
    }

}
