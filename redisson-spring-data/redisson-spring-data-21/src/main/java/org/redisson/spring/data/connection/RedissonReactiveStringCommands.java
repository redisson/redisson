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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.reactivestreams.Publisher;
import org.redisson.client.codec.ByteArrayCodec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.handler.State;
import org.redisson.client.protocol.Decoder;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.RedisStrictCommand;
import org.redisson.client.protocol.convertor.BooleanReplayConvertor;
import org.redisson.reactive.CommandReactiveExecutor;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.ReactiveRedisConnection.AbsentByteBufferResponse;
import org.springframework.data.redis.connection.ReactiveRedisConnection.BooleanResponse;
import org.springframework.data.redis.connection.ReactiveRedisConnection.ByteBufferResponse;
import org.springframework.data.redis.connection.ReactiveRedisConnection.KeyCommand;
import org.springframework.data.redis.connection.ReactiveRedisConnection.MultiValueResponse;
import org.springframework.data.redis.connection.ReactiveRedisConnection.NumericResponse;
import org.springframework.data.redis.connection.ReactiveRedisConnection.RangeCommand;
import org.springframework.data.redis.connection.ReactiveStringCommands;
import org.springframework.data.redis.connection.RedisStringCommands.BitOperation;
import org.springframework.data.redis.connection.RedisStringCommands.SetOption;
import org.springframework.util.Assert;

import io.netty.buffer.ByteBuf;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonReactiveStringCommands extends RedissonBaseReactive implements ReactiveStringCommands {

    RedissonReactiveStringCommands(CommandReactiveExecutor executorService) {
        super(executorService);
    }

    private static final RedisCommand<Boolean> SET = new RedisCommand<Boolean>("SET", new BooleanReplayConvertor());
    
    @Override
    public Flux<BooleanResponse<SetCommand>> set(Publisher<SetCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");
            Assert.notNull(command.getValue(), "Value must not be null!");

            byte[] key = toByteArray(command.getKey());
            byte[] value = toByteArray(command.getValue());
            
            if (!command.getExpiration().isPresent()) {
                Mono<Boolean> m = write(key, StringCodec.INSTANCE, SET, key, value);
                return m.map(v -> new BooleanResponse<>(command, v));
            } else if (command.getExpiration().get().isPersistent()) {
                if (!command.getOption().isPresent() || command.getOption().get() == SetOption.UPSERT) {
                    Mono<Boolean> m = write(key, StringCodec.INSTANCE, SET, key, value);
                    return m.map(v -> new BooleanResponse<>(command, v));
                }
                if (command.getOption().get() == SetOption.SET_IF_ABSENT) {
                    Mono<Boolean> m = write(key, StringCodec.INSTANCE, SET, key, value, "NX");
                    return m.map(v -> new BooleanResponse<>(command, v));
                }
                if (command.getOption().get() == SetOption.SET_IF_PRESENT) {
                    Mono<Boolean> m = write(key, StringCodec.INSTANCE, SET, key, value, "XX");
                    return m.map(v -> new BooleanResponse<>(command, v));
                }
            } else {
                if (!command.getOption().isPresent() || command.getOption().get() == SetOption.UPSERT) {
                    Mono<Boolean> m = write(key, StringCodec.INSTANCE, RedisCommands.SET, key, value, "PX", command.getExpiration().get().getExpirationTimeInMilliseconds());
                    return m.map(v -> new BooleanResponse<>(command, v));
                }
                if (command.getOption().get() == SetOption.SET_IF_ABSENT) {
                    Mono<Boolean> m = write(key, StringCodec.INSTANCE, RedisCommands.SET, key, value, "PX", command.getExpiration().get().getExpirationTimeInMilliseconds(), "NX");
                    return m.map(v -> new BooleanResponse<>(command, v));
                }
                if (command.getOption().get() == SetOption.SET_IF_PRESENT) {
                    Mono<Boolean> m = write(key, StringCodec.INSTANCE, RedisCommands.SET, key, value, "PX", command.getExpiration().get().getExpirationTimeInMilliseconds(), "XX");
                    return m.map(v -> new BooleanResponse<>(command, v));
                }
            }
            throw new IllegalArgumentException();
        });
    }

    private static final RedisCommand<Object> GET = new RedisCommand<Object>("GET", new Decoder<Object>() {
        @Override
        public Object decode(ByteBuf buf, State state) throws IOException {
            if (buf.readableBytes() == 0) {
                System.out.println("null:");
                return null;
            }
            
            byte[] result = new byte[buf.readableBytes()];
            buf.readBytes(result);
            return result;
        }
        
    });
    
    @Override
    public Flux<ByteBufferResponse<KeyCommand>> get(Publisher<KeyCommand> keys) {
        return execute(keys, command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");

            byte[] keyBuf = toByteArray(command.getKey());
            Mono<byte[]> m = read(keyBuf, ByteArrayCodec.INSTANCE, GET, keyBuf);
            return m.map(v -> new ByteBufferResponse<>(command, ByteBuffer.wrap(v)))
                    .defaultIfEmpty(new AbsentByteBufferResponse<>(command));
        });
    }

    @Override
    public Flux<ByteBufferResponse<SetCommand>> getSet(Publisher<SetCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");
            Assert.notNull(command.getValue(), "Value must not be null!");

            if (command.getExpiration().isPresent() || command.getOption().isPresent()) {
                throw new IllegalArgumentException("Command must not define expiration nor option for GETSET.");
            }

            byte[] keyBuf = toByteArray(command.getKey());
            byte[] valueBuf = toByteArray(command.getValue());
            
            Mono<byte[]> m = write(keyBuf, ByteArrayCodec.INSTANCE, RedisCommands.GETSET, keyBuf, valueBuf);
            return m.map(v -> new ByteBufferResponse<>(command, ByteBuffer.wrap(v)));
        });
    }

    @Override
    public Flux<MultiValueResponse<List<ByteBuffer>, ByteBuffer>> mGet(Publisher<List<ByteBuffer>> keysets) {
        return execute(keysets, coll -> {

            Assert.notNull(coll, "List must not be null!");
            
            Object[] params = coll.stream().map(buf -> toByteArray(buf)).toArray(Object[]::new);

            Mono<List<byte[]>> m = read(null, ByteArrayCodec.INSTANCE, RedisCommands.MGET, params);
            return m.map(v -> {
                List<ByteBuffer> values = v.stream().map(array -> {
                    if (array == null) {
                        return ByteBuffer.allocate(0);
                    }
                    return ByteBuffer.wrap(array);
                }).collect(Collectors.toList());
                return new MultiValueResponse<>(coll, values);
            });
        });
    }

    @Override
    public Flux<BooleanResponse<SetCommand>> setNX(Publisher<SetCommand> values) {
        return execute(values, command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");
            Assert.notNull(command.getValue(), "Value must not be null!");

            if (command.getExpiration().isPresent() || command.getOption().isPresent()) {
                throw new IllegalArgumentException("Command must not define expiration nor option for GETSET.");
            }

            byte[] keyBuf = toByteArray(command.getKey());
            byte[] valueBuf = toByteArray(command.getValue());
            
            Mono<Boolean> m = write(keyBuf, StringCodec.INSTANCE, RedisCommands.SETNX, keyBuf, valueBuf);
            return m.map(v -> new BooleanResponse<>(command, v));
        });
    }

    private static final RedisCommand<Boolean> SETEX = new RedisCommand<Boolean>("SETEX", new BooleanReplayConvertor());
    
    @Override
    public Flux<BooleanResponse<SetCommand>> setEX(Publisher<SetCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");
            Assert.notNull(command.getValue(), "Value must not be null!");

            if (!command.getExpiration().isPresent()) {
                throw new IllegalArgumentException("Expiration must not be null!");
            }

            byte[] keyBuf = toByteArray(command.getKey());
            byte[] valueBuf = toByteArray(command.getValue());
            
            Mono<Boolean> m = write(keyBuf, StringCodec.INSTANCE, SETEX, 
                    keyBuf, command.getExpiration().get().getExpirationTimeInSeconds(), valueBuf);
            return m.map(v -> new BooleanResponse<>(command, v));
        });
    }

    private static final RedisCommand<String> PSETEX = new RedisCommand<String>("PSETEX");
    
    @Override
    public Flux<BooleanResponse<SetCommand>> pSetEX(Publisher<SetCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");
            Assert.notNull(command.getValue(), "Value must not be null!");

            if (!command.getExpiration().isPresent()) {
                throw new IllegalArgumentException("Expiration must not be null!");
            }

            byte[] keyBuf = toByteArray(command.getKey());
            byte[] valueBuf = toByteArray(command.getValue());
            
            Mono<String> m = write(keyBuf, StringCodec.INSTANCE, PSETEX, 
                    keyBuf, command.getExpiration().get().getExpirationTimeInMilliseconds(), valueBuf);
            return m.map(v -> new BooleanResponse<>(command, true));
        });
    }

    private static final RedisCommand<Boolean> MSET = new RedisCommand<Boolean>("MSET", new BooleanReplayConvertor());
    
    @Override
    public Flux<BooleanResponse<MSetCommand>> mSet(Publisher<MSetCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getKeyValuePairs(), "KeyValuePairs must not be null!");

            List<byte[]> params = convert(command);
            
            Mono<Boolean> m = write(params.get(0), StringCodec.INSTANCE, MSET, params.toArray());
            return m.map(v -> new BooleanResponse<>(command, v));
        });
    }

    protected List<byte[]> convert(MSetCommand command) {
        List<byte[]> params = new ArrayList<byte[]>(command.getKeyValuePairs().size());
        command.getKeyValuePairs().entrySet().forEach(e -> {
            byte[] keyBuf = toByteArray(e.getKey());
            byte[] valueBuf = toByteArray(e.getValue());
            params.add(keyBuf);
            params.add(valueBuf);
        });
        return params;
    }

    @Override
    public Flux<BooleanResponse<MSetCommand>> mSetNX(Publisher<MSetCommand> source) {
        return execute(source, command -> {

            Assert.notNull(command.getKeyValuePairs(), "KeyValuePairs must not be null!");

            List<byte[]> params = convert(command);
            
            Mono<Boolean> m = write(params.get(0), StringCodec.INSTANCE, RedisCommands.MSETNX, params.toArray());
            return m.map(v -> new BooleanResponse<>(command, v));
        });
    }

    private static final RedisStrictCommand<Long> APPEND = new RedisStrictCommand<Long>("APPEND");
    
    @Override
    public Flux<NumericResponse<AppendCommand, Long>> append(Publisher<AppendCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");
            Assert.notNull(command.getValue(), "Value must not be null!");

            byte[] keyBuf = toByteArray(command.getKey());
            byte[] valueBuf = toByteArray(command.getValue());
            
            Mono<Long> m = write(keyBuf, StringCodec.INSTANCE, APPEND, keyBuf, valueBuf);
            return m.map(v -> new NumericResponse<>(command, v));
        });
    }

    private static final RedisCommand<Object> GETRANGE = new RedisCommand<Object>("GETRANGE");
    
    @Override
    public Flux<ByteBufferResponse<RangeCommand>> getRange(Publisher<RangeCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");
            Assert.notNull(command.getRange(), "Range must not be null!");

            byte[] keyBuf = toByteArray(command.getKey());
            Mono<byte[]> m = read(keyBuf, ByteArrayCodec.INSTANCE, GETRANGE, 
                    keyBuf, command.getRange().getLowerBound().getValue().orElse(0L), 
                            command.getRange().getUpperBound().getValue().orElse(-1L));
            return m.map(v -> new ByteBufferResponse<>(command, ByteBuffer.wrap(v)));
        });
    }

    private static final RedisCommand<Long> SETRANGE = new RedisCommand<Long>("SETRANGE");
    
    @Override
    public Flux<NumericResponse<SetRangeCommand, Long>> setRange(Publisher<SetRangeCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");
            Assert.notNull(command.getValue(), "Value must not be null!");
            Assert.notNull(command.getOffset(), "Offset must not be null!");

            byte[] keyBuf = toByteArray(command.getKey());
            byte[] valueBuf = toByteArray(command.getValue());
            Mono<Long> m = write(keyBuf, StringCodec.INSTANCE, SETRANGE, keyBuf, command.getOffset(), valueBuf);
            return m.map(v -> new NumericResponse<>(command, v));
        });
    }

    @Override
    public Flux<BooleanResponse<GetBitCommand>> getBit(Publisher<GetBitCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");
            Assert.notNull(command.getOffset(), "Offset must not be null!");

            byte[] keyBuf = toByteArray(command.getKey());
            Mono<Boolean> m = read(keyBuf, StringCodec.INSTANCE, RedisCommands.GETBIT, keyBuf, command.getOffset());
            return m.map(v -> new BooleanResponse<>(command, v));
        });
    }

    @Override
    public Flux<BooleanResponse<SetBitCommand>> setBit(Publisher<SetBitCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");
            Assert.notNull(command.getOffset(), "Offset must not be null!");

            byte[] keyBuf = toByteArray(command.getKey());
            Mono<Boolean> m = write(keyBuf, StringCodec.INSTANCE, RedisCommands.SETBIT, keyBuf, command.getOffset(), command.getValue() ? 1 : 0);
            return m.map(v -> new BooleanResponse<>(command, v));
        });
    }

    @Override
    
    public Flux<NumericResponse<BitCountCommand, Long>> bitCount(Publisher<BitCountCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");

            Range<Long> range = command.getRange();
            if (range == null) {
                range = Range.unbounded();
            }
            
            byte[] keyBuf = toByteArray(command.getKey());
            Mono<Long> m;
            if (range == Range.<Long>unbounded()) {
                m = write(keyBuf, StringCodec.INSTANCE, RedisCommands.BITCOUNT, keyBuf); 
            } else {
                m = write(keyBuf, StringCodec.INSTANCE, RedisCommands.BITCOUNT, 
                        keyBuf, range.getLowerBound().getValue().orElse(0L), 
                        range.getUpperBound().getValue().get());
            }
            return m.map(v -> new NumericResponse<>(command, v));
        });
    }

    @Override
    public Flux<MultiValueResponse<BitFieldCommand, Long>> bitField(Publisher<BitFieldCommand> commands) {
        return null;
    }

    private static final RedisStrictCommand<Long> BITOP = new RedisStrictCommand<Long>("BITOP");
    
    @Override
    public Flux<NumericResponse<BitOpCommand, Long>> bitOp(Publisher<BitOpCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getDestinationKey(), "DestinationKey must not be null!");
            Assert.notEmpty(command.getKeys(), "Keys must not be null or empty");
            
            if (command.getBitOp() == BitOperation.NOT && command.getKeys().size() > 1) {
                throw new UnsupportedOperationException("NOT operation doesn't support more than single source key");
            }

            List<Object> params = new ArrayList<Object>(command.getKeys().size() + 2);
            params.add(command.getBitOp());
            params.add(toByteArray(command.getDestinationKey()));
            params.addAll(command.getKeys().stream().map(v -> toByteArray(v)).collect(Collectors.toList()));

            Mono<Long> m = write(toByteArray(command.getDestinationKey()), StringCodec.INSTANCE, BITOP, params.toArray());
            return m.map(v -> new NumericResponse<>(command, v));
        });
    }
    
    private static final RedisStrictCommand<Long> BITPOS = new RedisStrictCommand<Long>("BITPOS");

    @Override
    public Flux<NumericResponse<BitPosCommand, Long>> bitPos(Publisher<BitPosCommand> commands) {
        return execute(commands, command -> {

            List<Object> params = new ArrayList<>();
            params.add(toByteArray(command.getKey()));
            params.add(command.getBit() ? 1 : 0);
            
            if (command.getRange() != null) {
                if (command.getRange().getLowerBound().getValue().isPresent()) {
                    params.add(command.getRange().getLowerBound().getValue().get());
                }
                if (command.getRange().getUpperBound().getValue().isPresent()) {
                    if (!command.getRange().getLowerBound().getValue().isPresent()) {
                        throw new IllegalArgumentException("LowerBound must not be null");
                    }
                    params.add(command.getRange().getUpperBound().getValue().get());
                }
            }

            Mono<Long> m = read((byte[])params.get(0), StringCodec.INSTANCE, BITPOS, params.toArray());
            return m.map(v -> new NumericResponse<>(command, v));
        });
    }

    @Override
    public Flux<NumericResponse<KeyCommand, Long>> strLen(Publisher<KeyCommand> keys) {
        return execute(keys, command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");

            byte[] keyBuf = toByteArray(command.getKey());
            Mono<Long> m = read(keyBuf, StringCodec.INSTANCE, RedisCommands.STRLEN, keyBuf);
            return m.map(v -> new NumericResponse<>(command, v));
        });
    }

}
