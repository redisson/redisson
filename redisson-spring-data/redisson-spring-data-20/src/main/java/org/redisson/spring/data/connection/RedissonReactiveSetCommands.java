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
import java.util.Set;
import java.util.stream.Collectors;

import org.reactivestreams.Publisher;
import org.redisson.client.codec.ByteArrayCodec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.RedisStrictCommand;
import org.redisson.reactive.CommandReactiveExecutor;
import org.springframework.data.redis.connection.ReactiveRedisConnection.BooleanResponse;
import org.springframework.data.redis.connection.ReactiveRedisConnection.ByteBufferResponse;
import org.springframework.data.redis.connection.ReactiveRedisConnection.CommandResponse;
import org.springframework.data.redis.connection.ReactiveRedisConnection.KeyCommand;
import org.springframework.data.redis.connection.ReactiveRedisConnection.NumericResponse;
import org.springframework.data.redis.connection.ReactiveSetCommands;
import org.springframework.util.Assert;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonReactiveSetCommands extends RedissonBaseReactive implements ReactiveSetCommands {

    RedissonReactiveSetCommands(CommandReactiveExecutor executorService) {
        super(executorService);
    }

    private static final RedisCommand<Long> SADD = new RedisCommand<Long>("SADD");
    
    @Override
    public Flux<NumericResponse<SAddCommand, Long>> sAdd(Publisher<SAddCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");
            Assert.notNull(command.getValues(), "Values must not be null!");

            List<Object> args = new ArrayList<Object>(command.getValues().size() + 1);
            args.add(toByteArray(command.getKey()));
            args.addAll(command.getValues().stream().map(v -> toByteArray(v)).collect(Collectors.toList()));
            
            byte[] keyBuf = toByteArray(command.getKey());
            Mono<Long> m = write(keyBuf, StringCodec.INSTANCE, SADD, args.toArray());
            return m.map(v -> new NumericResponse<>(command, v));
        });
    }

    private static final RedisCommand<Long> SREM = new RedisCommand<Long>("SREM");
    
    @Override
    public Flux<NumericResponse<SRemCommand, Long>> sRem(Publisher<SRemCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");
            Assert.notNull(command.getValues(), "Values must not be null!");

            List<Object> args = new ArrayList<Object>(command.getValues().size() + 1);
            args.add(toByteArray(command.getKey()));
            args.addAll(command.getValues().stream().map(v -> toByteArray(v)).collect(Collectors.toList()));
            
            Mono<Long> m = write((byte[])args.get(0), StringCodec.INSTANCE, SREM, args.toArray());
            return m.map(v -> new NumericResponse<>(command, v));
        });
    }

    @Override
    public Flux<ByteBuffer> sPop(SPopCommand command) {
        Assert.notNull(command.getKey(), "Key must not be null!");
        
        byte[] keyBuf = toByteArray(command.getKey());
        Mono<Set<byte[]>> m = write(keyBuf, ByteArrayCodec.INSTANCE, RedisCommands.SPOP, keyBuf, command.getCount());
        return m.flatMapMany(v -> Flux.fromIterable(v).map(e -> ByteBuffer.wrap(e)));
    }

    @Override
    public Flux<ByteBufferResponse<KeyCommand>> sPop(Publisher<KeyCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");

            byte[] keyBuf = toByteArray(command.getKey());
            Mono<byte[]> m = write(keyBuf, ByteArrayCodec.INSTANCE, RedisCommands.SPOP_SINGLE, keyBuf);
            return m.map(v -> new ByteBufferResponse<>(command, ByteBuffer.wrap(v)));
        });
    }

    @Override
    public Flux<BooleanResponse<SMoveCommand>> sMove(Publisher<SMoveCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");
            Assert.notNull(command.getDestination(), "Destination key must not be null!");
            Assert.notNull(command.getValue(), "Value must not be null!");

            byte[] keyBuf = toByteArray(command.getKey());
            byte[] destinationBuf = toByteArray(command.getDestination());
            byte[] valueBuf = toByteArray(command.getValue());
            Mono<Boolean> m = write(keyBuf, ByteArrayCodec.INSTANCE, RedisCommands.SMOVE, keyBuf, destinationBuf, valueBuf);
            return m.map(v -> new BooleanResponse<>(command, v));
        });
    }

    private static final RedisStrictCommand<Long> SCARD = new RedisStrictCommand<Long>("SCARD");
    
    @Override
    public Flux<NumericResponse<KeyCommand, Long>> sCard(Publisher<KeyCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");

            byte[] keyBuf = toByteArray(command.getKey());
            Mono<Long> m = write(keyBuf, StringCodec.INSTANCE, SCARD, keyBuf);
            return m.map(v -> new NumericResponse<>(command, v));
        });
    }

    @Override
    public Flux<BooleanResponse<SIsMemberCommand>> sIsMember(Publisher<SIsMemberCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");
            Assert.notNull(command.getValue(), "Value must not be null!");

            byte[] keyBuf = toByteArray(command.getKey());
            byte[] valueBuf = toByteArray(command.getValue());
            Mono<Boolean> m = read(keyBuf, StringCodec.INSTANCE, RedisCommands.SISMEMBER, keyBuf, valueBuf);
            return m.map(v -> new BooleanResponse<>(command, v));
        });
    }

    @Override
    public Flux<CommandResponse<SInterCommand, Flux<ByteBuffer>>> sInter(Publisher<SInterCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getKeys(), "Key must not be null!");

            List<byte[]> list = command.getKeys().stream().map(v -> toByteArray(v)).collect(Collectors.toList());
            Mono<Set<byte[]>> m = write((byte[])list.get(0), ByteArrayCodec.INSTANCE, RedisCommands.SINTER, list.toArray());
            return m.map(v -> new CommandResponse<>(command, 
                                    Flux.fromIterable(v).map(e -> ByteBuffer.wrap(e))));
        });
    }

    @Override
    public Flux<NumericResponse<SInterStoreCommand, Long>> sInterStore(Publisher<SInterStoreCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getKeys(), "Keys must not be null!");
            Assert.notNull(command.getKey(), "Destination key must not be null!");

            List<Object> args = new ArrayList<Object>(command.getKeys().size() + 1);
            args.add(toByteArray(command.getKey()));
            args.addAll(command.getKeys().stream().map(v -> toByteArray(v)).collect(Collectors.toList()));

            Mono<Long> m = write((byte[])args.get(0), StringCodec.INSTANCE, RedisCommands.SINTERSTORE, args.toArray());
            return m.map(v -> new NumericResponse<>(command, v));
        });
    }

    @Override
    public Flux<CommandResponse<SUnionCommand, Flux<ByteBuffer>>> sUnion(Publisher<SUnionCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getKeys(), "Key must not be null!");

            List<byte[]> list = command.getKeys().stream().map(v -> toByteArray(v)).collect(Collectors.toList());
            Mono<Set<byte[]>> m = write((byte[])list.get(0), ByteArrayCodec.INSTANCE, RedisCommands.SUNION, list.toArray());
            return m.map(v -> new CommandResponse<>(command, 
                                    Flux.fromIterable(v).map(e -> ByteBuffer.wrap(e))));
        });
    }

    @Override
    public Flux<NumericResponse<SUnionStoreCommand, Long>> sUnionStore(Publisher<SUnionStoreCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getKeys(), "Keys must not be null!");
            Assert.notNull(command.getKey(), "Destination key must not be null!");

            List<Object> args = new ArrayList<Object>(command.getKeys().size() + 1);
            args.add(toByteArray(command.getKey()));
            args.addAll(command.getKeys().stream().map(v -> toByteArray(v)).collect(Collectors.toList()));

            Mono<Long> m = write((byte[])args.get(0), StringCodec.INSTANCE, RedisCommands.SUNIONSTORE, args.toArray());
            return m.map(v -> new NumericResponse<>(command, v));
        });
    }

    @Override
    public Flux<CommandResponse<SDiffCommand, Flux<ByteBuffer>>> sDiff(Publisher<SDiffCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getKeys(), "Key must not be null!");

            List<byte[]> list = command.getKeys().stream().map(v -> toByteArray(v)).collect(Collectors.toList());
            Mono<Set<byte[]>> m = write((byte[])list.get(0), ByteArrayCodec.INSTANCE, RedisCommands.SDIFF, list.toArray());
            return m.map(v -> new CommandResponse<>(command, 
                                    Flux.fromIterable(v).map(e -> ByteBuffer.wrap(e))));
        });
    }

    @Override
    public Flux<NumericResponse<SDiffStoreCommand, Long>> sDiffStore(Publisher<SDiffStoreCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getKeys(), "Keys must not be null!");
            Assert.notNull(command.getKey(), "Destination key must not be null!");

            List<Object> args = new ArrayList<Object>(command.getKeys().size() + 1);
            args.add(toByteArray(command.getKey()));
            args.addAll(command.getKeys().stream().map(v -> toByteArray(v)).collect(Collectors.toList()));

            Mono<Long> m = write((byte[])args.get(0), StringCodec.INSTANCE, RedisCommands.SDIFFSTORE, args.toArray());
            return m.map(v -> new NumericResponse<>(command, v));
        });
    }

    @Override
    public Flux<CommandResponse<KeyCommand, Flux<ByteBuffer>>> sMembers(Publisher<KeyCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");

            byte[] keyBuf = toByteArray(command.getKey());
            Mono<Set<byte[]>> m = read(keyBuf, ByteArrayCodec.INSTANCE, RedisCommands.SMEMBERS, keyBuf);
            return m.map(v -> new CommandResponse<>(command, Flux.fromIterable(v).map(e -> ByteBuffer.wrap(e))));
        });
    }

    @Override
    public Flux<CommandResponse<SRandMembersCommand, Flux<ByteBuffer>>> sRandMember(
            Publisher<SRandMembersCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");

            byte[] keyBuf = toByteArray(command.getKey());
            Mono<Set<byte[]>> m = read(keyBuf, ByteArrayCodec.INSTANCE, RedisCommands.SRANDMEMBER, keyBuf, command.getCount().orElse(1L));
            return m.map(v -> new CommandResponse<>(command, Flux.fromIterable(v).map(e -> ByteBuffer.wrap(e))));
        });
    }

}
