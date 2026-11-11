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
import org.redisson.ScanResult;
import org.redisson.api.RFuture;
import org.redisson.client.RedisClient;
import org.redisson.client.codec.ByteArrayCodec;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.RedisStrictCommand;
import org.redisson.client.protocol.convertor.BooleanReplayConvertor;
import org.redisson.client.protocol.convertor.Convertor;
import org.redisson.connection.MasterSlaveEntry;
import org.redisson.reactive.CommandReactiveExecutor;
import org.redisson.reactive.IteratorConsumer;
import org.springframework.data.redis.connection.CompareCondition;
import org.springframework.data.redis.connection.DataType;
import org.springframework.data.redis.connection.ExpirationOptions;
import org.springframework.data.redis.connection.ReactiveKeyCommands;
import org.springframework.data.redis.connection.ReactiveRedisConnection.*;
import org.springframework.data.redis.connection.ValueEncoding;
import org.springframework.data.redis.core.KeyScanOptions;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.util.Assert;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonReactiveKeyCommands extends RedissonBaseReactive implements ReactiveKeyCommands {

    public RedissonReactiveKeyCommands(CommandReactiveExecutor executorService) {
        super(executorService);
    }

    @Override
    public Flux<BooleanResponse<KeyCommand>> exists(Publisher<KeyCommand> keys) {
        return execute(keys, key -> {

            Assert.notNull(key.getKey(), "Key must not be null!");

            byte[] keyBuf = toByteArray(key.getKey());
            Mono<Boolean> m = read(keyBuf, StringCodec.INSTANCE, RedisCommands.EXISTS, keyBuf);
            return m.map(v -> new BooleanResponse<>(key, v));
        });
    }
    
    private static final RedisStrictCommand<DataType> TYPE = new RedisStrictCommand<DataType>("TYPE", obj -> DataType.fromCode(obj.toString()));

    @Override
    public Flux<CommandResponse<KeyCommand, DataType>> type(Publisher<KeyCommand> keys) {
        return execute(keys, key -> {

            Assert.notNull(key.getKey(), "Key must not be null!");

            byte[] keyBuf = toByteArray(key.getKey());
            Mono<DataType> m = read(keyBuf, StringCodec.INSTANCE, TYPE, keyBuf);
            return m.map(v -> new CommandResponse<>(key, v));
        });
    }
    
    @Override
    public Flux<NumericResponse<Collection<ByteBuffer>, Long>> touch(Publisher<Collection<ByteBuffer>> keys) {
        return execute(keys, coll -> {

            Assert.notNull(coll, "Collection must not be null!");
            
            Object[] params = coll.stream().map(buf -> toByteArray(buf)).toArray(Object[]::new);

            Mono<Long> m = write(null, StringCodec.INSTANCE, RedisCommands.TOUCH_LONG, params);
            return m.map(v -> new NumericResponse<>(coll, v));
        });
    }

    static final RedisStrictCommand<List<byte[]>> KEYS = new RedisStrictCommand<>("KEYS");

    @Override
    public Flux<MultiValueResponse<ByteBuffer, ByteBuffer>> keys(Publisher<ByteBuffer> patterns) {
        return execute(patterns, pattern -> {

            Assert.notNull(pattern, "Pattern must not be null!");

            Mono<List<byte[]>> m = read(null, ByteArrayCodec.INSTANCE, KEYS, toByteArray(pattern));
            return m.map(v -> {
                List<ByteBuffer> values = v.stream().map(t -> ByteBuffer.wrap(t)).collect(Collectors.toList());
                return new MultiValueResponse<>(pattern, values);   
            });
        });
    }

    @Override
    public Flux<ByteBuffer> scan(ScanOptions options) {
        List<Publisher<byte[]>> publishers = new ArrayList<>();
        for (MasterSlaveEntry entry : executorService.getConnectionManager().getEntrySet()) {
            publishers.add(Flux.<byte[]>create(emitter -> emitter.onRequest(new IteratorConsumer<byte[]>(emitter) {

                @Override
                protected boolean tryAgain() {
                    return false;
                }

                @Override
                protected RFuture<ScanResult<Object>> scanIterator(RedisClient client, String nextIterPos) {
                    List<Object> args = new ArrayList<>();
                    args.add(nextIterPos);
                    if (options.getBytePattern() != null) {
                        args.add("MATCH");
                        args.add(options.getBytePattern());
                    }
                    if (options.getCount() != null) {
                        args.add("COUNT");
                        args.add(options.getCount());
                    }
                    if (options instanceof KeyScanOptions && ((KeyScanOptions) options).getType() != null) {
                        args.add("TYPE");
                        args.add(((KeyScanOptions) options).getType());
                    }
                    return executorService.readAsync(client, entry, ByteArrayCodec.INSTANCE, RedisCommands.SCAN, args.toArray());
                }
            })));
        }
        return Flux.merge(publishers).map(v -> ByteBuffer.wrap(v));
    }

    @Override
    public Mono<ByteBuffer> randomKey() {
        Mono<byte[]> m = executorService.reactive(() -> {
            return executorService.readRandomAsync(ByteArrayCodec.INSTANCE, RedisCommands.RANDOM_KEY);
        });
        return m.map(v -> ByteBuffer.wrap(v));
    }

    static final RedisStrictCommand<String> RENAME = new RedisStrictCommand<String>("RENAME");
    
    @Override
    public Flux<BooleanResponse<RenameCommand>> rename(Publisher<RenameCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");
            Assert.notNull(command.getNewKey(), "New name must not be null!");

            byte[] keyBuf = toByteArray(command.getKey());
            byte[] newKeyBuf = toByteArray(command.getNewKey());
            Mono<String> m = write(keyBuf, StringCodec.INSTANCE, RENAME, keyBuf, newKeyBuf);
            return m.map(v -> new BooleanResponse<>(command, true));
        });
    }

    @Override
    public Flux<BooleanResponse<RenameCommand>> renameNX(Publisher<RenameCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");
            Assert.notNull(command.getNewKey(), "New name must not be null!");

            byte[] keyBuf = toByteArray(command.getKey());
            byte[] newKeyBuf = toByteArray(command.getNewKey());
            Mono<Boolean> m = write(keyBuf, StringCodec.INSTANCE, RedisCommands.RENAMENX, keyBuf, newKeyBuf);
            return m.map(v -> new BooleanResponse<>(command, v));
        });
    }

    @Override
    public Flux<NumericResponse<KeyCommand, Long>> del(Publisher<KeyCommand> keys) {
        Flux<KeyCommand> s = Flux.from(keys);
        return s.concatMap(command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");

            byte[] keyBuf = toByteArray(command.getKey());
            Mono<Long> m = write(keyBuf, StringCodec.INSTANCE, RedisCommands.DEL, keyBuf);
            return m.map(v -> new NumericResponse<>(command, v));
        });
    }

    @Override
    public Flux<NumericResponse<List<ByteBuffer>, Long>> mDel(Publisher<List<ByteBuffer>> keys) {
        return execute(keys, coll -> {

            Assert.notNull(coll, "List must not be null!");
            
            Object[] params = coll.stream().map(buf -> toByteArray(buf)).toArray(Object[]::new);

            Mono<Long> m = write(null, StringCodec.INSTANCE, RedisCommands.DEL, params);
            return m.map(v -> new NumericResponse<>(coll, v));
        });
    }

    @Override
    public Flux<NumericResponse<KeyCommand, Long>> unlink(Publisher<KeyCommand> keys) {
        return execute(keys, command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");

            byte[] keyBuf = toByteArray(command.getKey());
            Mono<Long> m = write(keyBuf, StringCodec.INSTANCE, RedisCommands.UNLINK, keyBuf);
            return m.map(v -> new NumericResponse<>(command, v));
        });
   }

    @Override
    public Flux<NumericResponse<List<ByteBuffer>, Long>> mUnlink(Publisher<List<ByteBuffer>> keys) {
        return execute(keys, coll -> {

            Assert.notNull(coll, "List must not be null!");
            
            Object[] params = coll.stream().map(buf -> toByteArray(buf)).toArray(Object[]::new);

            Mono<Long> m = write(null, StringCodec.INSTANCE, RedisCommands.UNLINK, params);
            return m.map(v -> new NumericResponse<>(coll, v));
        });
    }

    private static final RedisStrictCommand<Boolean> EXPIRE = new RedisStrictCommand<Boolean>("EXPIRE", new BooleanReplayConvertor());
    
    private static final RedisStrictCommand<Boolean> EXPIREAT = new RedisStrictCommand<>("EXPIREAT", new BooleanReplayConvertor());
    
    @Override
    public Flux<BooleanResponse<ExpireAtCommand>> expireAt(Publisher<ExpireAtCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");

            byte[] keyBuf = toByteArray(command.getKey());
            List<Object> args = new ArrayList<>();
            args.add(keyBuf);
            args.add(command.getExpireAt().getEpochSecond());
            appendCondition(args, command.getOptions());

            Mono<Boolean> m = write(keyBuf, StringCodec.INSTANCE, EXPIREAT, args.toArray());
            return m.map(v -> new BooleanResponse<>(command, v));
        });
    }

    @Override
    public Flux<BooleanResponse<ExpireAtCommand>> pExpireAt(Publisher<ExpireAtCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");

            byte[] keyBuf = toByteArray(command.getKey());
            List<Object> args = new ArrayList<>();
            args.add(keyBuf);
            args.add(command.getExpireAt().toEpochMilli());
            appendCondition(args, command.getOptions());

            Mono<Boolean> m = write(keyBuf, StringCodec.INSTANCE, RedisCommands.PEXPIREAT, args.toArray());
            return m.map(v -> new BooleanResponse<>(command, v));
        });
    }

    @Override
    public Flux<BooleanResponse<KeyCommand>> persist(Publisher<KeyCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");

            byte[] keyBuf = toByteArray(command.getKey());
            Mono<Boolean> m = write(keyBuf, StringCodec.INSTANCE, RedisCommands.PERSIST, keyBuf);
            return m.map(v -> new BooleanResponse<>(command, v));
        });
    }
    
    private static final RedisStrictCommand<Long> TTL = new RedisStrictCommand<Long>("TTL");

    @Override
    public Flux<NumericResponse<KeyCommand, Long>> ttl(Publisher<KeyCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");

            byte[] keyBuf = toByteArray(command.getKey());
            Mono<Long> m = read(keyBuf, StringCodec.INSTANCE, TTL, keyBuf);
            return m.map(v -> new NumericResponse<>(command, v));
        });
    }

    @Override
    public Flux<NumericResponse<KeyCommand, Long>> pTtl(Publisher<KeyCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");

            byte[] keyBuf = toByteArray(command.getKey());
            Mono<Long> m = read(keyBuf, StringCodec.INSTANCE, RedisCommands.PTTL, keyBuf);
            return m.map(v -> new NumericResponse<>(command, v));
        });
    }

    @Override
    public Flux<BooleanResponse<MoveCommand>> move(Publisher<MoveCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");
            Assert.notNull(command.getDatabase(), "Database must not be null!");

            byte[] keyBuf = toByteArray(command.getKey());
            Mono<Boolean> m = write(keyBuf, StringCodec.INSTANCE, RedisCommands.MOVE, keyBuf, command.getDatabase());
            return m.map(v -> new BooleanResponse<>(command, v));
        });
    }
    
    private static final RedisStrictCommand<ValueEncoding> OBJECT_ENCODING = new RedisStrictCommand<ValueEncoding>("OBJECT", "ENCODING", obj -> ValueEncoding.of((String) obj));

    @Override
    public Mono<ValueEncoding> encodingOf(ByteBuffer key) {
        Assert.notNull(key, "Key must not be null!");

        byte[] keyBuf = toByteArray(key);
        return read(keyBuf, StringCodec.INSTANCE, OBJECT_ENCODING, keyBuf);
    }

    private static final RedisStrictCommand<Long> OBJECT_IDLETIME = new RedisStrictCommand<Long>("OBJECT", "IDLETIME");
    
    @Override
    public Mono<Duration> idletime(ByteBuffer key) {
        Assert.notNull(key, "Key must not be null!");

        byte[] keyBuf = toByteArray(key);
        Mono<Long> m = read(keyBuf, StringCodec.INSTANCE, OBJECT_IDLETIME, keyBuf);
        return m.map(Duration::ofSeconds);
    }
    
    private static final RedisStrictCommand<Long> OBJECT_REFCOUNT = new RedisStrictCommand<Long>("OBJECT", "REFCOUNT");

    @Override
    public Mono<Long> refcount(ByteBuffer key) {
        Assert.notNull(key, "Key must not be null!");

        byte[] keyBuf = toByteArray(key);
        return read(keyBuf, StringCodec.INSTANCE, OBJECT_REFCOUNT, keyBuf);
    }

    @Override
    public Flux<BooleanResponse<CopyCommand>> copy(Publisher<CopyCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");
            Assert.notNull(command.getTarget(), "Target must not be null!");

            List<Object> params = new ArrayList<>();
            byte[] keyBuf = toByteArray(command.getKey());
            params.add(keyBuf);
            byte[] targetBuf = toByteArray(command.getTarget());
            params.add(targetBuf);
            if (command.getDatabase() != null) {
                params.add("DB");
                params.add(command.getDatabase());
            }
            if (command.isReplace()) {
                params.add("REPLACE");
            }

            Mono<Boolean> m = write(keyBuf, StringCodec.INSTANCE, RedisCommands.COPY, params.toArray());
            return m.map(v -> new BooleanResponse<>(command, v));
        });
    }

    @Override
    public Mono<Long> exists(List<ByteBuffer> keys) {
        Assert.notEmpty(keys, "Keys must not be empty!");

        List<Object> args = new ArrayList<>(keys.size());
        for (ByteBuffer key : keys) {
            args.add(toByteArray(key));
        }

        return read((byte[]) args.get(0), LongCodec.INSTANCE, RedisCommands.EXISTS_LONG, args.toArray());
    }

    private static void appendCondition(List<Object> args, ExpirationOptions options) {
        if (options != null && options.getCondition() != ExpirationOptions.Condition.ALWAYS) {
            args.add(options.getCondition().name());
        }
    }

    @Override
    public Flux<BooleanResponse<ExpireCommand>> applyExpiration(Publisher<ExpireCommand> commands) {
        return execute(commands, command -> {
            Assert.notNull(command.getKey(), "Key must not be null!");
            Assert.notNull(command.getExpiration(), "Expiration must not be null!");

            byte[] keyBuf = toByteArray(command.getKey());
            List<Object> args = new ArrayList<>();
            args.add(keyBuf);

            Mono<Boolean> result;

            if (command.getExpiration().isPersistent()) {
                result = write(keyBuf, StringCodec.INSTANCE, RedisCommands.PERSIST, keyBuf);
            } else {
                long timestamp = command.getExpiration().getExpirationTime();
                args.add(timestamp);
                appendCondition(args, command.getOptions());

                if (command.getExpiration().isUnixTimestamp()) {

                    if (command.getExpiration().getTimeUnit() == TimeUnit.MILLISECONDS) {
                        result = write(keyBuf, StringCodec.INSTANCE, RedisCommands.PEXPIREAT, args.toArray());
                    } else {
                        long seconds = TimeUnit.SECONDS.convert(timestamp, command.getExpiration().getTimeUnit());
                        args.set(1, seconds);
                        result = write(keyBuf, StringCodec.INSTANCE, EXPIREAT, args.toArray());
                    }
                } else {
                    if (command.getExpiration().getTimeUnit() == TimeUnit.MILLISECONDS) {
                        result = write(keyBuf, StringCodec.INSTANCE, RedisCommands.PEXPIRE, args.toArray());
                    } else {
                        long seconds = TimeUnit.SECONDS.convert(timestamp, command.getExpiration().getTimeUnit());
                        args.set(1, seconds);
                        result = write(keyBuf, StringCodec.INSTANCE, EXPIRE, args.toArray());
                    }
                }
            }

            return result.map(value -> new BooleanResponse<>(command, value));
        });
    }


    @Override
    public Flux<BooleanResponse<DelexCommand>> delex(Publisher<DelexCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");
            Assert.notNull(command.getCondition(), "Condition must not be null!");

            byte[] keyBuf = toByteArray(command.getKey());
            CompareCondition condition = command.getCondition();

            Mono<Boolean> m = write(keyBuf, StringCodec.INSTANCE, RedisCommands.DELEX,
                    keyBuf, toComparisonToken(condition), condition.getValue().asBytes());
            return m.map(v -> new BooleanResponse<>(command, v));
        });
    }

    private static String toComparisonToken(CompareCondition condition) {
        boolean equals = condition.getOperator() == CompareCondition.ComparisonOperator.EQUALS;
        switch (condition.getComparison()) {
            case DIGEST:
                return equals ? "IFDEQ" : "IFDNE";
            case VALUE:
            default:
                return equals ? "IFEQ" : "IFNE";
        }
    }


    @Override
    public Flux<CommandResponse<KeyCommand, String>> digest(Publisher<KeyCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");

            byte[] keyBuf = toByteArray(command.getKey());
            Mono<String> m = read(keyBuf, StringCodec.INSTANCE, RedisCommands.DIGEST, keyBuf);
            return m.map(v -> new CommandResponse<>(command, v));
        });
    }

}
