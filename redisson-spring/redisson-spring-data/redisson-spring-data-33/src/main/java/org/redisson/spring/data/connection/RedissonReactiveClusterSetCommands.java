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
import org.springframework.data.redis.connection.ReactiveClusterSetCommands;
import org.springframework.data.redis.connection.ReactiveRedisConnection.BooleanResponse;
import org.springframework.data.redis.connection.ReactiveRedisConnection.CommandResponse;
import org.springframework.data.redis.connection.ReactiveRedisConnection.NumericResponse;
import org.springframework.util.Assert;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonReactiveClusterSetCommands extends RedissonReactiveSetCommands implements ReactiveClusterSetCommands {

    RedissonReactiveClusterSetCommands(CommandReactiveExecutor executorService) {
        super(executorService);
    }

    private Mono<List<Set<ByteBuffer>>> members(List<ByteBuffer> keys) {
        List<Mono<Set<byte[]>>> sets = new ArrayList<>(keys.size());
        for (ByteBuffer key : keys) {
            byte[] keyBuf = toByteArray(key);
            sets.add(write(keyBuf, ByteArrayCodec.INSTANCE, RedisCommands.SMEMBERS, keyBuf));
        }
        return Flux.mergeSequential(sets).map(values -> {
            Set<ByteBuffer> set = new LinkedHashSet<>();
            for (byte[] value : values) {
                set.add(ByteBuffer.wrap(value));
            }
            return set;
        }).collectList();
    }

    private Mono<Set<ByteBuffer>> intersection(List<ByteBuffer> keys) {
        return members(keys).map(sets -> {
            Set<ByteBuffer> result = sets.get(0);
            for (Set<ByteBuffer> set : sets.subList(1, sets.size())) {
                result.retainAll(set);
            }
            return result;
        });
    }

    private Mono<Set<ByteBuffer>> union(List<ByteBuffer> keys) {
        return members(keys).map(sets -> {
            Set<ByteBuffer> result = new LinkedHashSet<>();
            for (Set<ByteBuffer> set : sets) {
                result.addAll(set);
            }
            return result;
        });
    }

    private Mono<Set<ByteBuffer>> difference(List<ByteBuffer> keys) {
        return members(keys).map(sets -> {
            Set<ByteBuffer> result = sets.get(0);
            for (Set<ByteBuffer> set : sets.subList(1, sets.size())) {
                result.removeAll(set);
            }
            return result;
        });
    }

    private Mono<Long> store(ByteBuffer destKey, Set<ByteBuffer> members) {
        byte[] destBuf = toByteArray(destKey);
        Mono<Long> deleted = write(destBuf, StringCodec.INSTANCE, RedisCommands.DEL, destBuf);
        if (members.isEmpty()) {
            return deleted.thenReturn(0L);
        }

        List<Object> args = new ArrayList<>(members.size() + 1);
        args.add(destBuf);
        for (ByteBuffer member : members) {
            args.add(toByteArray(member));
        }
        Mono<Object> added = write(destBuf, StringCodec.INSTANCE, RedisCommands.SADD, args.toArray());
        return deleted.then(added).thenReturn((long) members.size());
    }

    private static List<ByteBuffer> withDestination(List<ByteBuffer> keys, ByteBuffer destKey) {
        List<ByteBuffer> result = new ArrayList<>(keys);
        result.add(destKey);
        return result;
    }

    @Override
    public Flux<CommandResponse<SInterCommand, Flux<ByteBuffer>>> sInter(Publisher<SInterCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getKeys(), "Key must not be null!");

            if (ClusterSlotHashUtil.isSameSlotForAllKeys(command.getKeys())) {
                return super.sInter(Mono.just(command));
            }
            return intersection(command.getKeys()).map(v -> new CommandResponse<>(command, Flux.fromIterable(v)));
        });
    }

    @Override
    public Flux<NumericResponse<SInterStoreCommand, Long>> sInterStore(Publisher<SInterStoreCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getKeys(), "Keys must not be null!");
            Assert.notNull(command.getKey(), "Destination key must not be null!");

            if (ClusterSlotHashUtil.isSameSlotForAllKeys(withDestination(command.getKeys(), command.getKey()))) {
                return super.sInterStore(Mono.just(command));
            }
            return intersection(command.getKeys())
                    .flatMap(members -> store(command.getKey(), members))
                    .map(v -> new NumericResponse<>(command, v));
        });
    }

    @Override
    public Flux<CommandResponse<SUnionCommand, Flux<ByteBuffer>>> sUnion(Publisher<SUnionCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getKeys(), "Key must not be null!");

            if (ClusterSlotHashUtil.isSameSlotForAllKeys(command.getKeys())) {
                return super.sUnion(Mono.just(command));
            }
            return union(command.getKeys()).map(v -> new CommandResponse<>(command, Flux.fromIterable(v)));
        });
    }

    @Override
    public Flux<NumericResponse<SUnionStoreCommand, Long>> sUnionStore(Publisher<SUnionStoreCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getKeys(), "Keys must not be null!");
            Assert.notNull(command.getKey(), "Destination key must not be null!");

            if (ClusterSlotHashUtil.isSameSlotForAllKeys(withDestination(command.getKeys(), command.getKey()))) {
                return super.sUnionStore(Mono.just(command));
            }
            return union(command.getKeys())
                    .flatMap(members -> store(command.getKey(), members))
                    .map(v -> new NumericResponse<>(command, v));
        });
    }

    @Override
    public Flux<CommandResponse<SDiffCommand, Flux<ByteBuffer>>> sDiff(Publisher<SDiffCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getKeys(), "Key must not be null!");

            if (ClusterSlotHashUtil.isSameSlotForAllKeys(command.getKeys())) {
                return super.sDiff(Mono.just(command));
            }
            return difference(command.getKeys()).map(v -> new CommandResponse<>(command, Flux.fromIterable(v)));
        });
    }

    @Override
    public Flux<NumericResponse<SDiffStoreCommand, Long>> sDiffStore(Publisher<SDiffStoreCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getKeys(), "Keys must not be null!");
            Assert.notNull(command.getKey(), "Destination key must not be null!");

            if (ClusterSlotHashUtil.isSameSlotForAllKeys(withDestination(command.getKeys(), command.getKey()))) {
                return super.sDiffStore(Mono.just(command));
            }
            return difference(command.getKeys())
                    .flatMap(members -> store(command.getKey(), members))
                    .map(v -> new NumericResponse<>(command, v));
        });
    }

    @Override
    public Flux<BooleanResponse<SMoveCommand>> sMove(Publisher<SMoveCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");
            Assert.notNull(command.getDestination(), "Destination key must not be null!");
            Assert.notNull(command.getValue(), "Value must not be null!");

            if (ClusterSlotHashUtil.isSameSlotForAllKeys(command.getKey(), command.getDestination())) {
                return super.sMove(Mono.just(command));
            }

            byte[] keyBuf = toByteArray(command.getKey());
            byte[] destinationBuf = toByteArray(command.getDestination());
            byte[] valueBuf = toByteArray(command.getValue());
            Mono<Integer> removed = write(keyBuf, StringCodec.INSTANCE, RedisCommands.SREM, keyBuf, valueBuf);
            Mono<Boolean> moved = removed.flatMap(count -> {
                if (count == 0) {
                    return Mono.just(false);
                }
                Mono<Object> added = write(destinationBuf, StringCodec.INSTANCE, RedisCommands.SADD, destinationBuf, valueBuf);
                return added.thenReturn(true);
            });
            return moved.map(v -> new BooleanResponse<>(command, v));
        });
    }

}
