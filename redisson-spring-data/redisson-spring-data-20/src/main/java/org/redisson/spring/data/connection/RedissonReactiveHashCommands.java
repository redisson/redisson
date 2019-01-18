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
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import org.reactivestreams.Publisher;
import org.redisson.client.codec.ByteArrayCodec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.handler.State;
import org.redisson.client.protocol.Decoder;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.decoder.MultiDecoder;
import org.redisson.reactive.CommandReactiveExecutor;
import org.springframework.data.redis.connection.ReactiveHashCommands;
import org.springframework.data.redis.connection.ReactiveRedisConnection.BooleanResponse;
import org.springframework.data.redis.connection.ReactiveRedisConnection.CommandResponse;
import org.springframework.data.redis.connection.ReactiveRedisConnection.KeyCommand;
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
        public Decoder<Object> getDecoder(int paramNum, State state) {
            return null;
        }

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

}
