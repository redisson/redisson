/**
 * Copyright (c) 2013-2024 Nikita Koksharov
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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.reactivestreams.Publisher;
import org.redisson.ScanResult;
import org.redisson.api.RFuture;
import org.redisson.client.RedisClient;
import org.redisson.client.codec.ByteArrayCodec;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.handler.State;
import org.redisson.client.protocol.Decoder;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.RedisStrictCommand;
import org.redisson.client.protocol.decoder.MapScanResult;
import org.redisson.client.protocol.decoder.MultiDecoder;
import org.redisson.client.protocol.decoder.ObjectListReplayDecoder;
import org.redisson.reactive.CommandReactiveExecutor;
import org.redisson.reactive.MapReactiveIterator;
import org.springframework.data.redis.connection.ExpirationOptions;
import org.springframework.data.redis.connection.ReactiveHashCommands;
import org.springframework.data.redis.connection.ReactiveRedisConnection.BooleanResponse;
import org.springframework.data.redis.connection.ReactiveRedisConnection.CommandResponse;
import org.springframework.data.redis.connection.ReactiveRedisConnection.KeyCommand;
import org.springframework.data.redis.connection.ReactiveRedisConnection.KeyScanCommand;
import org.springframework.data.redis.connection.ReactiveRedisConnection.MultiValueResponse;
import org.springframework.data.redis.connection.ReactiveRedisConnection.NumericResponse;
import org.springframework.util.Assert;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonReactiveHashCommands extends RedissonBaseReactive implements ReactiveHashCommands {

    RedissonReactiveHashCommands(CommandReactiveExecutor executorService) {
        super(executorService);
    }
    
    private static final RedisCommand<String> HMSET = new RedisCommand<String>("HMSET");

    @Override
    public Flux<BooleanResponse<HSetCommand>> hSet(Publisher<HSetCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");
            Assert.notNull(command.getFieldValueMap(), "FieldValueMap must not be null!");

            byte[] keyBuf = toByteArray(command.getKey());
            if (command.getFieldValueMap().size() == 1) {
                Entry<ByteBuffer, ByteBuffer> entry = command.getFieldValueMap().entrySet().iterator().next();
                byte[] mapKeyBuf = toByteArray(entry.getKey());
                byte[] mapValueBuf = toByteArray(entry.getValue());
                RedisCommand<Boolean> cmd = RedisCommands.HSETNX;
                if (command.isUpsert()) {
                    cmd = RedisCommands.HSET;
                }
                Mono<Boolean> m = write(keyBuf, StringCodec.INSTANCE, cmd, keyBuf, mapKeyBuf, mapValueBuf);
                return m.map(v -> new BooleanResponse<>(command, v));
            } else {
                List<Object> params = new ArrayList<Object>(command.getFieldValueMap().size()*2 + 1);
                params.add(keyBuf);
                for (Entry<ByteBuffer, ByteBuffer> entry : command.getFieldValueMap().entrySet()) {
                    params.add(toByteArray(entry.getKey()));
                    params.add(toByteArray(entry.getValue()));
                }

                Mono<String> m = write(keyBuf, StringCodec.INSTANCE, HMSET, params.toArray());
                return m.map(v -> new BooleanResponse<>(command, true));
            }
        });
    }
    
    private static final RedisCommand<List<Object>> HMGET = new RedisCommand<List<Object>>("HMGET", new MultiDecoder<List<Object>>() {

        @Override
        public List<Object> decode(List<Object> parts, State state) {
            List<Object> list = parts.stream().filter(e -> e != null).collect(Collectors.toList());
            if (list.isEmpty()) {
                return null;
            }
            return parts;
        }
        
    });

    @Override
    public Flux<MultiValueResponse<HGetCommand, ByteBuffer>> hMGet(Publisher<HGetCommand> commands) {
        return execute(commands, command -> {
            
            Assert.notNull(command.getKey(), "Key must not be null!");
            Assert.notNull(command.getFields(), "Fields must not be null!");
            
            byte[] keyBuf = toByteArray(command.getKey());
            List<Object> args = new ArrayList<Object>(command.getFields().size() + 1);
            args.add(keyBuf);
            args.addAll(command.getFields().stream().map(buf -> toByteArray(buf)).collect(Collectors.toList()));
            Mono<List<byte[]>> m = read(keyBuf, ByteArrayCodec.INSTANCE, HMGET, args.toArray());
            return m.map(v -> {
                List<ByteBuffer> values = v.stream().map(array -> {
                    if (array != null) {
                        return ByteBuffer.wrap(array);
                    }
                    return null;
                }).collect(Collectors.toList());
                return new MultiValueResponse<>(command, values);
            });
        });
    }

    @Override
    public Flux<BooleanResponse<HExistsCommand>> hExists(Publisher<HExistsCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");
            Assert.notNull(command.getField(), "Field must not be null!");

            byte[] keyBuf = toByteArray(command.getKey());
            byte[] fieldBuf = toByteArray(command.getField());
            
            Mono<Boolean> m =read(keyBuf, StringCodec.INSTANCE, RedisCommands.HEXISTS, keyBuf, fieldBuf);
            return m.map(v -> new BooleanResponse<>(command, v));
        });
    }

    @Override
    public Flux<NumericResponse<HDelCommand, Long>> hDel(Publisher<HDelCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");
            Assert.notNull(command.getFields(), "Fields must not be null!");

            List<Object> args = new ArrayList<Object>(command.getFields().size() + 1);
            args.add(toByteArray(command.getKey()));
            args.addAll(command.getFields().stream().map(v -> toByteArray(v)).collect(Collectors.toList()));
            
            Mono<Long> m = write((byte[])args.get(0), StringCodec.INSTANCE, RedisCommands.HDEL, args.toArray());
            return m.map(v -> new NumericResponse<>(command, v));
        });
    }

    @Override
    public Flux<NumericResponse<KeyCommand, Long>> hLen(Publisher<KeyCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");
            
            byte[] keyBuf = toByteArray(command.getKey());
            Mono<Long> m = read(keyBuf, StringCodec.INSTANCE, RedisCommands.HLEN_LONG, keyBuf);
            return m.map(v -> new NumericResponse<>(command, v));
        });
    }

    @Override
    public Flux<CommandResponse<KeyCommand, Flux<ByteBuffer>>> hKeys(Publisher<KeyCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");

            byte[] keyBuf = toByteArray(command.getKey());
            
            Mono<Set<byte[]>> m = read(keyBuf, ByteArrayCodec.INSTANCE, RedisCommands.HKEYS, keyBuf);
            return m.map(v -> new CommandResponse<>(command, Flux.fromIterable(v).map(e -> ByteBuffer.wrap(e))));
        });
    }

    @Override
    public Flux<CommandResponse<KeyCommand, Flux<ByteBuffer>>> hVals(Publisher<KeyCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");

            byte[] keyBuf = toByteArray(command.getKey());
            
            Mono<List<byte[]>> m = read(keyBuf, ByteArrayCodec.INSTANCE, RedisCommands.HVALS, keyBuf);
            return m.map(v -> new CommandResponse<>(command, Flux.fromIterable(v).map(e -> ByteBuffer.wrap(e))));
        });
    }

    @Override
    public Flux<CommandResponse<KeyCommand, Flux<Entry<ByteBuffer, ByteBuffer>>>> hGetAll(
            Publisher<KeyCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");

            byte[] keyBuf = toByteArray(command.getKey());
            
            Mono<Map<byte[], byte[]>> m = read(keyBuf, ByteArrayCodec.INSTANCE, RedisCommands.HGETALL, keyBuf);
            Mono<Map<ByteBuffer, ByteBuffer>> f = m.map(v -> v.entrySet().stream().collect(Collectors.toMap(e -> ByteBuffer.wrap(e.getKey()), e -> ByteBuffer.wrap(e.getValue()))));
            return f.map(v -> new CommandResponse<>(command, Flux.fromIterable(v.entrySet())));
        });
    }

    @Override
    public Flux<CommandResponse<KeyCommand, Flux<Entry<ByteBuffer, ByteBuffer>>>> hScan(
            Publisher<KeyScanCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");
            Assert.notNull(command.getOptions(), "ScanOptions must not be null!");
            
            byte[] keyBuf = toByteArray(command.getKey());
            Flux<Entry<Object, Object>> flux = Flux.create(new MapReactiveIterator<Object, Object, Entry<Object, Object>>(null, null, 0) {
                @Override
                public RFuture<ScanResult<Object>> scanIterator(RedisClient client, String nextIterPos) {
                    if (command.getOptions().getPattern() == null) {
                        return executorService.readAsync(client, keyBuf, ByteArrayCodec.INSTANCE, RedisCommands.HSCAN, 
                                keyBuf, nextIterPos, "COUNT", Optional.ofNullable(command.getOptions().getCount()).orElse(10L));
                    }

                    return executorService.readAsync(client, keyBuf, ByteArrayCodec.INSTANCE, RedisCommands.HSCAN, 
                                keyBuf, nextIterPos, "MATCH", command.getOptions().getPattern(), 
                                                    "COUNT", Optional.ofNullable(command.getOptions().getCount()).orElse(10L));
                }
            });
            Flux<Entry<ByteBuffer, ByteBuffer>> f = flux.map(v -> Collections.singletonMap(ByteBuffer.wrap((byte[])v.getKey()), ByteBuffer.wrap((byte[])v.getValue())).entrySet().iterator().next());
            return Mono.just(new CommandResponse<>(command, f));
        });
    }

    private static final RedisCommand<Long> HSTRLEN = new RedisCommand<Long>("HSTRLEN");
    
    @Override
    public Flux<NumericResponse<HStrLenCommand, Long>> hStrLen(Publisher<HStrLenCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");
            Assert.notNull(command.getField(), "Field must not be null!");

            byte[] keyBuf = toByteArray(command.getKey());
            byte[] fieldBuf = toByteArray(command.getField());
            
            Mono<Long> m = read(keyBuf, StringCodec.INSTANCE, HSTRLEN, keyBuf, fieldBuf);
            return m.map(v -> new NumericResponse<>(command, v));
        });
    }

    @Override
    public Flux<CommandResponse<HRandFieldCommand, Flux<ByteBuffer>>> hRandField(Publisher<HRandFieldCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");

            byte[] keyBuf = toByteArray(command.getKey());

            Mono<Set<byte[]>> m;
            if (command.getCount() > 0) {
                m = read(keyBuf, ByteArrayCodec.INSTANCE, RedisCommands.HRANDFIELD_KEYS, keyBuf, command.getCount());
            } else {
                m = read(keyBuf, ByteArrayCodec.INSTANCE, RedisCommands.HRANDFIELD_KEYS, keyBuf);
            }
            return m.map(v -> new CommandResponse<>(command, Flux.fromIterable(v).map(e -> ByteBuffer.wrap(e))));
        });
    }

    @Override
    public Flux<CommandResponse<HRandFieldCommand, Flux<Entry<ByteBuffer, ByteBuffer>>>> hRandFieldWithValues(Publisher<HRandFieldCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");

            byte[] keyBuf = toByteArray(command.getKey());

            Mono<Map<byte[], byte[]>> m;
            if (command.getCount() > 0) {
                m = read(keyBuf, ByteArrayCodec.INSTANCE, RedisCommands.HRANDFIELD, keyBuf, command.getCount());
            } else {
                m = read(keyBuf, ByteArrayCodec.INSTANCE, RedisCommands.HRANDFIELD, keyBuf);
            }

            Mono<Map<ByteBuffer, ByteBuffer>> f = m.map(v -> v.entrySet().stream().collect(Collectors.toMap(e -> ByteBuffer.wrap(e.getKey()), e -> ByteBuffer.wrap(e.getValue()))));
            return f.map(v -> new CommandResponse<>(command, Flux.fromIterable(v.entrySet())));
        });
    }

    private static final RedisCommand<List<Long>> HEXPIRE = new RedisCommand<>("HEXPIRE", new ObjectListReplayDecoder<>());

    @Override
    public Flux<NumericResponse<HashExpireCommand, Long>> applyHashFieldExpiration(Publisher<HashExpireCommand> commands) {
        return execute(commands, command -> {
            Assert.notNull(command.getKey(), "Key must not be null!");
            Assert.notNull(command.getExpiration(), "Expiration must not be null!");
            Assert.notEmpty(command.getFields(), "Fields must not be empty!");

            byte[] keyBuf = toByteArray(command.getKey());

            List<Object> args = new ArrayList<>();
            args.add(keyBuf);
            args.add(command.getExpiration().getExpirationTimeInSeconds());

            if (command.getOptions() != null
                    && command.getOptions().getCondition() != ExpirationOptions.Condition.ALWAYS) {
                args.add(command.getOptions().getCondition().name());
            }

            args.add("FIELDS");
            args.add(command.getFields().size());
            args.addAll(command.getFields().stream().map(buf -> toByteArray(buf)).collect(Collectors.toList()));

            Mono<List<Long>> result = write(keyBuf, LongCodec.INSTANCE, HEXPIRE, args.toArray());
            return result.flatMapMany(Flux::fromIterable)
                    .map(value -> new NumericResponse<>(command, value));
        });
    }

    private static final RedisStrictCommand<List<Long>> HPERSIST = new RedisStrictCommand<>("HPERSIST", new ObjectListReplayDecoder<>());

    @Override
    public Flux<NumericResponse<HashFieldsCommand, Long>> hPersist(Publisher<HashFieldsCommand> commands) {
        return execute(commands, command -> {
            Assert.notNull(command.getKey(), "Key must not be null!");
            Assert.notEmpty(command.getFields(), "Fields must not be empty!");

            byte[] keyBuf = toByteArray(command.getKey());

            List<Object> args = new ArrayList<>();
            args.add(keyBuf);
            args.add("FIELDS");
            args.add(command.getFields().size());
            args.addAll(command.getFields().stream().map(buf -> toByteArray(buf)).collect(Collectors.toList()));

            Mono<List<Long>> result = write(keyBuf, LongCodec.INSTANCE, HPERSIST, args.toArray());
            return result.flatMapMany(Flux::fromIterable)
                    .map(value -> new NumericResponse<>(command, value));
        });
    }

    private static final RedisCommand<List<Long>> HTTL = new RedisCommand<>("HTTL", new ObjectListReplayDecoder<>());

    @Override
    public Flux<NumericResponse<HashFieldsCommand, Long>> hTtl(Publisher<HashFieldsCommand> commands) {
        return execute(commands, command -> {
            Assert.notNull(command.getKey(), "Key must not be null!");
            Assert.notEmpty(command.getFields(), "Fields must not be empty!");

            byte[] keyBuf = toByteArray(command.getKey());

            List<Object> args = new ArrayList<>();
            args.add(keyBuf);
            args.add("FIELDS");
            args.add(command.getFields().size());
            args.addAll(command.getFields().stream().map(buf -> toByteArray(buf)).collect(Collectors.toList()));

            Mono<List<Long>> result = read(keyBuf, LongCodec.INSTANCE, HTTL, args.toArray());
            return result.flatMapMany(Flux::fromIterable)
                    .map(value -> new NumericResponse<>(command, value));
        });
    }

    private static final RedisCommand<List<Long>> HPTTL = new RedisCommand<>("HPTTL", new ObjectListReplayDecoder<>());

    @Override
    public Flux<NumericResponse<HashFieldsCommand, Long>> hpTtl(Publisher<HashFieldsCommand> commands) {
        return execute(commands, command -> {
            Assert.notNull(command.getKey(), "Key must not be null!");
            Assert.notEmpty(command.getFields(), "Fields must not be empty!");

            byte[] keyBuf = toByteArray(command.getKey());

            List<Object> args = new ArrayList<>();
            args.add(keyBuf);
            args.add("FIELDS");
            args.add(command.getFields().size());
            args.addAll(command.getFields().stream().map(buf -> toByteArray(buf)).collect(Collectors.toList()));

            Mono<List<Long>> result = read(keyBuf, LongCodec.INSTANCE, HPTTL, args.toArray());
            return result.flatMapMany(Flux::fromIterable)
                    .map(value -> new NumericResponse<>(command, value));
        });
    }
}
