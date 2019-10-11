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
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.reactivestreams.Publisher;
import org.redisson.client.codec.ByteArrayCodec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.RedisStrictCommand;
import org.redisson.reactive.CommandReactiveExecutor;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.redis.connection.ReactiveListCommands;
import org.springframework.data.redis.connection.ReactiveRedisConnection.BooleanResponse;
import org.springframework.data.redis.connection.ReactiveRedisConnection.ByteBufferResponse;
import org.springframework.data.redis.connection.ReactiveRedisConnection.CommandResponse;
import org.springframework.data.redis.connection.ReactiveRedisConnection.KeyCommand;
import org.springframework.data.redis.connection.ReactiveRedisConnection.NumericResponse;
import org.springframework.data.redis.connection.ReactiveRedisConnection.RangeCommand;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonReactiveListCommands extends RedissonBaseReactive implements ReactiveListCommands {

    private static final RedisStrictCommand<Long> RPUSH = new RedisStrictCommand<Long>("RPUSH");
    private static final RedisStrictCommand<Long> LPUSH = new RedisStrictCommand<Long>("LPUSH");
    private static final RedisStrictCommand<Long> RPUSHX = new RedisStrictCommand<Long>("RPUSHX");
    private static final RedisStrictCommand<Long> LPUSHX = new RedisStrictCommand<Long>("LPUSHX");

    RedissonReactiveListCommands(CommandReactiveExecutor executorService) {
        super(executorService);
    }

    @Override
    public Flux<NumericResponse<PushCommand, Long>> push(Publisher<PushCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");
            Assert.notEmpty(command.getValues(), "Values must not be null or empty!");

            if (!command.getUpsert() && command.getValues().size() > 1) {
                throw new InvalidDataAccessApiUsageException(
                        String.format("%s PUSHX only allows one value!", command.getDirection()));
            }

            RedisStrictCommand<Long> redisCommand;
            
            List<Object> params = new ArrayList<Object>();
            params.add(toByteArray(command.getKey()));
            params.addAll(command.getValues().stream().map(v -> toByteArray(v)).collect(Collectors.toList()));

            if (ObjectUtils.nullSafeEquals(Direction.RIGHT, command.getDirection())) {
                if (command.getUpsert()) {
                    redisCommand = RPUSH;
                } else {
                    redisCommand = RPUSHX;
                }
            } else {
                if (command.getUpsert()) {
                    redisCommand = LPUSH;
                } else {
                    redisCommand = LPUSHX;
                }
            }

            Mono<Long> m = write((byte[])params.get(0), StringCodec.INSTANCE, redisCommand, params.toArray());
            return m.map(v -> new NumericResponse<>(command, v));
        });
    }
    
    private static final RedisStrictCommand<Long> LLEN = new RedisStrictCommand<Long>("LLEN");

    @Override
    public Flux<NumericResponse<KeyCommand, Long>> lLen(Publisher<KeyCommand> commands) {
        return execute(commands, key -> {

            Assert.notNull(key.getKey(), "Key must not be null!");

            byte[] keyBuf = toByteArray(key.getKey());
            Mono<Long> m = read(keyBuf, StringCodec.INSTANCE, LLEN, keyBuf);
            return m.map(v -> new NumericResponse<>(key, v));
        });
    }

    @Override
    public Flux<CommandResponse<RangeCommand, Flux<ByteBuffer>>> lRange(Publisher<RangeCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");
            Assert.notNull(command.getRange(), "Range must not be null!");

            byte[] keyBuf = toByteArray(command.getKey());
            Mono<List<byte[]>> m = read(keyBuf, ByteArrayCodec.INSTANCE, RedisCommands.LRANGE, 
                    keyBuf, command.getRange().getLowerBound().getValue().orElse(0L), 
                            command.getRange().getUpperBound().getValue().orElse(-1L));
            return m.map(v -> new CommandResponse<>(command, Flux.fromIterable(v).map(e -> ByteBuffer.wrap(e))));
        });
    }

    private static final RedisStrictCommand<String> LTRIM = new RedisStrictCommand<String>("LTRIM");
    
    @Override
    public Flux<BooleanResponse<RangeCommand>> lTrim(Publisher<RangeCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");
            Assert.notNull(command.getRange(), "Range must not be null!");

            byte[] keyBuf = toByteArray(command.getKey());
            Mono<String> m = write(keyBuf, StringCodec.INSTANCE, LTRIM, 
                    keyBuf, command.getRange().getLowerBound().getValue().orElse(0L), 
                            command.getRange().getUpperBound().getValue().orElse(-1L));
            return m.map(v -> new BooleanResponse<>(command, true));
        });
    }

    @Override
    public Flux<ByteBufferResponse<LIndexCommand>> lIndex(Publisher<LIndexCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");
            Assert.notNull(command.getIndex(), "Index value must not be null!");

            byte[] keyBuf = toByteArray(command.getKey());
            Mono<byte[]> m = read(keyBuf, ByteArrayCodec.INSTANCE, RedisCommands.LINDEX, 
                                            keyBuf, command.getIndex());
            return m.map(v -> new ByteBufferResponse<>(command, ByteBuffer.wrap(v)));
        });
    }

    private static final RedisStrictCommand<Long> LINSERT = new RedisStrictCommand<Long>("LINSERT");
    
    @Override
    public Flux<NumericResponse<LInsertCommand, Long>> lInsert(Publisher<LInsertCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");
            Assert.notNull(command.getValue(), "Value must not be null!");
            Assert.notNull(command.getPivot(), "Pivot must not be null!");
            Assert.notNull(command.getPosition(), "Position must not be null!");

            byte[] keyBuf = toByteArray(command.getKey());
            byte[] valueBuf = toByteArray(command.getValue());
            byte[] pivotBuf = toByteArray(command.getPivot());
            Mono<Long> m = write(keyBuf, StringCodec.INSTANCE, LINSERT, keyBuf, command.getPosition(), pivotBuf, valueBuf);
            return m.map(v -> new NumericResponse<>(command, v));
        });
    }

    private static final RedisStrictCommand<String> LSET = new RedisStrictCommand<String>("LSET");
    
    @Override
    public Flux<BooleanResponse<LSetCommand>> lSet(Publisher<LSetCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");
            Assert.notNull(command.getValue(), "value must not be null!");
            Assert.notNull(command.getIndex(), "Index must not be null!");

            byte[] keyBuf = toByteArray(command.getKey());
            byte[] valueBuf = toByteArray(command.getValue());
            Mono<String> m = write(keyBuf, StringCodec.INSTANCE, LSET, 
                    keyBuf, command.getIndex(), valueBuf);
            return m.map(v -> new BooleanResponse<>(command, true));
        });
    }

    private static final RedisStrictCommand<Long> LREM = new RedisStrictCommand<Long>("LREM");
    
    @Override
    public Flux<NumericResponse<LRemCommand, Long>> lRem(Publisher<LRemCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");
            Assert.notNull(command.getValue(), "Value must not be null!");
            Assert.notNull(command.getCount(), "Count must not be null!");

            byte[] keyBuf = toByteArray(command.getKey());
            byte[] valueBuf = toByteArray(command.getValue());
            Mono<Long> m = write(keyBuf, StringCodec.INSTANCE, LREM, keyBuf, command.getCount(), valueBuf);
            return m.map(v -> new NumericResponse<>(command, v));
        });
    }

    @Override
    public Flux<ByteBufferResponse<PopCommand>> pop(Publisher<PopCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");
            Assert.notNull(command.getDirection(), "Direction must not be null!");
            
            byte[] keyBuf = toByteArray(command.getKey());
            RedisCommand<Object> redisCommand = RedisCommands.LPOP;
            if (command.getDirection() == Direction.RIGHT) {
                redisCommand = RedisCommands.RPOP;
            }
            
            Mono<byte[]> m = write(keyBuf, ByteArrayCodec.INSTANCE, redisCommand, keyBuf);
            return m.map(v -> new ByteBufferResponse<>(command, ByteBuffer.wrap(v)));
        });
    }

    @Override
    public Flux<PopResponse> bPop(Publisher<BPopCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getKeys(), "Keys must not be null!");
            Assert.notNull(command.getDirection(), "Direction must not be null!");
            Assert.notNull(command.getTimeout(), "Timeout must not be null!");
            
            RedisCommand<List<Object>> redisCommand = RedisCommands.BLPOP;
            if (command.getDirection() == Direction.RIGHT) {
                redisCommand = RedisCommands.BRPOP;
            }
            
            List<Object> params = new ArrayList<Object>(command.getKeys().size() + 1);
            params.addAll(command.getKeys().stream().map(v -> toByteArray(v)).collect(Collectors.toList()));
            params.add(command.getTimeout().getSeconds());
            
            Mono<List<byte[]>> m = write((byte[])params.get(0), ByteArrayCodec.INSTANCE, redisCommand, params.toArray());
            return m.map(v -> new PopResponse(command, 
                    new PopResult(v.stream().map(e -> ByteBuffer.wrap(e)).collect(Collectors.toList()))));
        });
    }

    @Override
    public Flux<ByteBufferResponse<RPopLPushCommand>> rPopLPush(Publisher<RPopLPushCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");
            Assert.notNull(command.getDestination(), "Destination key must not be null!");
            
            byte[] keyBuf = toByteArray(command.getKey());
            byte[] destinationBuf = toByteArray(command.getDestination());
            
            Mono<byte[]> m = write(keyBuf, ByteArrayCodec.INSTANCE, RedisCommands.RPOPLPUSH, keyBuf, destinationBuf);
            return m.map(v -> new ByteBufferResponse<>(command, ByteBuffer.wrap(v)));
        });
    }

    @Override
    public Flux<ByteBufferResponse<BRPopLPushCommand>> bRPopLPush(Publisher<BRPopLPushCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");
            Assert.notNull(command.getDestination(), "Destination key must not be null!");
            Assert.notNull(command.getTimeout(), "Timeout must not be null!");
            
            byte[] keyBuf = toByteArray(command.getKey());
            byte[] destinationBuf = toByteArray(command.getDestination());
            
            Mono<byte[]> m = write(keyBuf, ByteArrayCodec.INSTANCE, RedisCommands.BRPOPLPUSH, 
                                    keyBuf, destinationBuf, command.getTimeout().getSeconds());
            return m.map(v -> new ByteBufferResponse<>(command, ByteBuffer.wrap(v)));
        });
    }

}
