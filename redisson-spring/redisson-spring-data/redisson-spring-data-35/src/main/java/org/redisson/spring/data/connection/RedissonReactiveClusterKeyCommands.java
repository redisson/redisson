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
import org.redisson.client.RedisClient;
import org.redisson.client.codec.ByteArrayCodec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandBatchService;
import org.redisson.misc.CompletableFutureWrapper;
import org.redisson.reactive.CommandReactiveExecutor;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.ClusterSlotHashUtil;
import org.springframework.data.redis.connection.ReactiveClusterKeyCommands;
import org.springframework.data.redis.connection.ReactiveRedisConnection;
import org.springframework.data.redis.connection.ReactiveRedisConnection.BooleanResponse;
import org.springframework.data.redis.connection.ReactiveRedisConnection.MultiValueResponse;
import org.springframework.data.redis.connection.ReactiveRedisConnection.NumericResponse;
import org.springframework.data.redis.connection.RedisClusterNode;
import org.springframework.util.Assert;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonReactiveClusterKeyCommands extends RedissonReactiveKeyCommands implements ReactiveClusterKeyCommands {

    public RedissonReactiveClusterKeyCommands(CommandReactiveExecutor executorService) {
        super(executorService);
    }

    private Mono<Long> sumPerKey(RedisCommand<Long> command, Collection<ByteBuffer> keys) {
        return Flux.fromIterable(keys).concatMap(key -> {
            byte[] keyBuf = toByteArray(key);
            Mono<Long> count = write(keyBuf, StringCodec.INSTANCE, command, keyBuf);
            return count;
        }).reduce(0L, Long::sum);
    }

    @Override
    public Mono<Long> exists(List<ByteBuffer> keys) {
        Assert.notEmpty(keys, "Keys must not be empty!");

        if (ClusterSlotHashUtil.isSameSlotForAllKeys(keys)) {
            return super.exists(keys);
        }
        return sumPerKey(RedisCommands.EXISTS_LONG, keys);
    }

    @Override
    public Flux<NumericResponse<Collection<ByteBuffer>, Long>> touch(Publisher<Collection<ByteBuffer>> keys) {
        return execute(keys, coll -> {

            Assert.notNull(coll, "Collection must not be null!");

            if (ClusterSlotHashUtil.isSameSlotForAllKeys(coll)) {
                return super.touch(Mono.just(coll));
            }
            return sumPerKey(RedisCommands.TOUCH_LONG, coll).map(v -> new NumericResponse<>(coll, v));
        });
    }

    @Override
    public Mono<List<ByteBuffer>> keys(RedisClusterNode node, ByteBuffer pattern) {
        RedisClient entry = getEntry(node);
        Mono<List<byte[]>> m = executorService.reactive(() -> {
            return executorService.readAsync(entry, ByteArrayCodec.INSTANCE, KEYS, toByteArray(pattern));
        });
        return m.map(v -> v.stream().map(t -> ByteBuffer.wrap(t)).collect(Collectors.toList()));
    }

    @Override
    public Flux<MultiValueResponse<ByteBuffer, ByteBuffer>> keys(Publisher<ByteBuffer> patterns) {
        return execute(patterns, pattern -> {
            Mono<List<byte[]>> m = executorService.reactive(() -> {
                List<CompletableFuture<List<byte[]>>> futures = executorService.readAllAsync(ByteArrayCodec.INSTANCE, KEYS, toByteArray(pattern));
                CompletableFuture<Void> ff = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
                CompletableFuture<List<byte[]>> future = ff.thenApply(r -> {
                    return futures.stream().flatMap(f -> f.getNow(new ArrayList<>()).stream()).collect(Collectors.toList());
                }).toCompletableFuture();
                return new CompletableFutureWrapper<>(future);
            });
            return m.map(v -> {
                List<ByteBuffer> values = v.stream().map(t -> ByteBuffer.wrap(t)).collect(Collectors.toList());
                return new MultiValueResponse<>(pattern, values);
            });
        });
    }

    @Override
    public Flux<NumericResponse<List<ByteBuffer>, Long>> mDel(Publisher<List<ByteBuffer>> keys) {
        return execute(keys, coll -> {

            Assert.notNull(coll, "List must not be null!");

            if (ClusterSlotHashUtil.isSameSlotForAllKeys(coll)) {
                return super.mDel(Mono.just(coll));
            }
            return sumPerKey(RedisCommands.DEL, coll).map(v -> new NumericResponse<>(coll, v));
        });
    }

    @Override
    public Flux<NumericResponse<List<ByteBuffer>, Long>> mUnlink(Publisher<List<ByteBuffer>> keys) {
        return execute(keys, coll -> {
            CommandBatchService es = new CommandBatchService(executorService);
            coll.stream().map(buf -> toByteArray(buf)).forEach(buf -> es.writeAsync(buf, StringCodec.INSTANCE, RedisCommands.UNLINK, buf));

            Mono<BatchResult<?>> m = executorService.reactive(es::executeAsync);
            return m.map(r -> new NumericResponse<>(coll, r.getResponses().stream().collect(Collectors.summarizingLong(v -> (long) v)).getSum()));
        });
    }

    @Override
    public Mono<ByteBuffer> randomKey(RedisClusterNode node) {
        RedisClient entry = getEntry(node);
        Mono<byte[]> m = executorService.reactive(() -> {
            return executorService.readRandomAsync(entry, ByteArrayCodec.INSTANCE, RedisCommands.RANDOM_KEY);
        });
        return m.map(v -> ByteBuffer.wrap(v));
    }

    @Override
    public Flux<BooleanResponse<RenameCommand>> rename(Publisher<RenameCommand> commands) {

        return execute(commands, command -> {
            Assert.notNull(command.getKey(), "Key must not be null!");
            Assert.notNull(command.getNewKey(), "New name must not be null!");

            byte[] keyBuf = toByteArray(command.getKey());
            byte[] newKeyBuf = toByteArray(command.getNewKey());

            if (executorService.getConnectionManager().calcSlot(keyBuf) == executorService.getConnectionManager().calcSlot(newKeyBuf)) {
                return super.rename(Mono.just(command));
            }

            return read(keyBuf, ByteArrayCodec.INSTANCE, RedisCommands.DUMP, keyBuf)
                    .switchIfEmpty(Mono.error(() -> new RedisSystemException("ERR no such key", null)))
                    .zipWith(
                            Mono.defer(() -> pTtl(command.getKey())
                                    .filter(Objects::nonNull)
                                    .map(ttl -> Math.max(0, ttl))
                                    .switchIfEmpty(Mono.just(0L))
                            )
                    )
                    .flatMap(valueAndTtl -> {
                        return write(newKeyBuf, StringCodec.INSTANCE, RedisCommands.RESTORE, newKeyBuf, valueAndTtl.getT2(), valueAndTtl.getT1(), "REPLACE");
                    })
                    .then(del(command.getKey()))
                    .thenReturn(new BooleanResponse<>(command, true));
        });
    }

    @Override
    public Flux<ReactiveRedisConnection.BooleanResponse<RenameCommand>> renameNX(Publisher<RenameCommand> commands) {
        return execute(commands, command -> {
            Assert.notNull(command.getKey(), "Key must not be null!");
            Assert.notNull(command.getNewKey(), "New name must not be null!");

            byte[] keyBuf = toByteArray(command.getKey());
            byte[] newKeyBuf = toByteArray(command.getNewKey());

            if (executorService.getConnectionManager().calcSlot(keyBuf) == executorService.getConnectionManager().calcSlot(newKeyBuf)) {
                return super.renameNX(Mono.just(command));
            }

            return exists(command.getNewKey())
                    .flatMap(newKeyExists -> {
                        if (newKeyExists) {
                            return Mono.just(false);
                        }

                        return read(keyBuf, ByteArrayCodec.INSTANCE, RedisCommands.DUMP, keyBuf)
                                .switchIfEmpty(Mono.error(() -> new RedisSystemException("ERR no such key", null)))
                                .zipWith(
                                        Mono.defer(() -> pTtl(command.getKey())
                                                .filter(Objects::nonNull)
                                                .map(ttl -> Math.max(0, ttl))
                                                .switchIfEmpty(Mono.just(0L))
                                        )
                                )
                                .flatMap(valueAndTtl -> {
                                    return write(newKeyBuf, StringCodec.INSTANCE, RedisCommands.RESTORE, newKeyBuf, valueAndTtl.getT2(), valueAndTtl.getT1());
                                })
                                .then(del(command.getKey()))
                                .thenReturn(true);
                    })
                    .map(didRename -> new BooleanResponse<>(command, didRename));
        });
    }

}
