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
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.reactivestreams.Publisher;
import org.redisson.client.codec.ByteArrayCodec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.RedisStrictCommand;
import org.redisson.client.protocol.convertor.BooleanReplayConvertor;
import org.redisson.client.protocol.convertor.Convertor;
import org.redisson.reactive.CommandReactiveExecutor;
import org.redisson.reactive.RedissonKeysReactive;
import org.springframework.data.redis.connection.DataType;
import org.springframework.data.redis.connection.ReactiveKeyCommands;
import org.springframework.data.redis.connection.ReactiveRedisConnection.BooleanResponse;
import org.springframework.data.redis.connection.ReactiveRedisConnection.CommandResponse;
import org.springframework.data.redis.connection.ReactiveRedisConnection.KeyCommand;
import org.springframework.data.redis.connection.ReactiveRedisConnection.MultiValueResponse;
import org.springframework.data.redis.connection.ReactiveRedisConnection.NumericResponse;
import org.springframework.data.redis.connection.ValueEncoding;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.util.Assert;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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
    
    private static final RedisStrictCommand<DataType> TYPE = new RedisStrictCommand<DataType>("TYPE", new Convertor<DataType>() {
        @Override
        public DataType convert(Object obj) {
            return DataType.fromCode(obj.toString());
        }
    });

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

            Mono<Long> m = read(null, StringCodec.INSTANCE, RedisCommands.TOUCH_LONG, params);
            return m.map(v -> new NumericResponse<>(coll, v));
        });
    }

    @Override
    public Flux<MultiValueResponse<ByteBuffer, ByteBuffer>> keys(Publisher<ByteBuffer> patterns) {
        return execute(patterns, pattern -> {

            Assert.notNull(pattern, "Pattern must not be null!");

            Mono<List<String>> m = read(null, StringCodec.INSTANCE, RedisCommands.KEYS, toByteArray(pattern));
            return m.map(v -> {
                List<ByteBuffer> values = v.stream().map(t -> ByteBuffer.wrap(t.getBytes())).collect(Collectors.toList());
                return new MultiValueResponse<>(pattern, values);   
            });
        });
    }

    @Override
    public Flux<ByteBuffer> scan(ScanOptions options) {
        RedissonKeysReactive reactive = new RedissonKeysReactive(executorService);
        return reactive.getKeysByPattern(options.getPattern(), options.getCount().intValue()).map(t -> ByteBuffer.wrap(t.getBytes()));
    }

    @Override
    public Mono<ByteBuffer> randomKey() {
        return executorService.reactive(() -> {
            return executorService.readRandomAsync(ByteArrayCodec.INSTANCE, RedisCommands.RANDOM_KEY);
        });
    }

    static final RedisStrictCommand<String> RENAME = new RedisStrictCommand<String>("RENAME");
    
    @Override
    public Flux<BooleanResponse<RenameCommand>> rename(Publisher<RenameCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");
            Assert.notNull(command.getNewName(), "New name must not be null!");

            byte[] keyBuf = toByteArray(command.getKey());
            byte[] newKeyBuf = toByteArray(command.getNewName());
            Mono<String> m = write(keyBuf, StringCodec.INSTANCE, RENAME, keyBuf, newKeyBuf);
            return m.map(v -> new BooleanResponse<>(command, true));
        });
    }

    @Override
    public Flux<BooleanResponse<RenameCommand>> renameNX(Publisher<RenameCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");
            Assert.notNull(command.getNewName(), "New name must not be null!");

            byte[] keyBuf = toByteArray(command.getKey());
            byte[] newKeyBuf = toByteArray(command.getNewName());
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

            Mono<Long> m = read(null, StringCodec.INSTANCE, RedisCommands.DEL, params);
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

            Mono<Long> m = read(null, StringCodec.INSTANCE, RedisCommands.UNLINK, params);
            return m.map(v -> new NumericResponse<>(coll, v));
        });
    }

    private static final RedisStrictCommand<Boolean> EXPIRE = new RedisStrictCommand<Boolean>("EXPIRE", new BooleanReplayConvertor());
    
    @Override
    public Flux<BooleanResponse<ExpireCommand>> expire(Publisher<ExpireCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");

            byte[] keyBuf = toByteArray(command.getKey());
            Mono<Boolean> m = read(keyBuf, StringCodec.INSTANCE, EXPIRE, keyBuf, command.getTimeout().getSeconds());
            return m.map(v -> new BooleanResponse<>(command, v));
        });
    }

    @Override
    public Flux<BooleanResponse<ExpireCommand>> pExpire(Publisher<ExpireCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");

            byte[] keyBuf = toByteArray(command.getKey());
            Mono<Boolean> m = read(keyBuf, StringCodec.INSTANCE, RedisCommands.PEXPIRE, keyBuf);
            return m.map(v -> new BooleanResponse<>(command, v));
        });
    }

    private static final RedisStrictCommand<Boolean> EXPIREAT = new RedisStrictCommand<Boolean>("EXPIREAT", new BooleanReplayConvertor());
    
    @Override
    public Flux<BooleanResponse<ExpireAtCommand>> expireAt(Publisher<ExpireAtCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");

            byte[] keyBuf = toByteArray(command.getKey());
            Mono<Boolean> m = read(keyBuf, StringCodec.INSTANCE, EXPIREAT, keyBuf, command.getExpireAt().getEpochSecond());
            return m.map(v -> new BooleanResponse<>(command, v));
        });
    }

    @Override
    public Flux<BooleanResponse<ExpireAtCommand>> pExpireAt(Publisher<ExpireAtCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");

            byte[] keyBuf = toByteArray(command.getKey());
            Mono<Boolean> m = read(keyBuf, StringCodec.INSTANCE, RedisCommands.PEXPIREAT, keyBuf, command.getExpireAt().toEpochMilli());
            return m.map(v -> new BooleanResponse<>(command, v));
        });
    }

    @Override
    public Flux<BooleanResponse<KeyCommand>> persist(Publisher<KeyCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");

            byte[] keyBuf = toByteArray(command.getKey());
            Mono<Boolean> m = read(keyBuf, StringCodec.INSTANCE, RedisCommands.PERSIST, keyBuf);
            return m.map(v -> new BooleanResponse<>(command, v));
        });
    }
    
    private static final RedisStrictCommand<Long> TTL = new RedisStrictCommand<Long>("TTL");

    @Override
    public Flux<NumericResponse<KeyCommand, Long>> ttl(Publisher<KeyCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");

            byte[] keyBuf = toByteArray(command.getKey());
            Mono<Long> m = write(keyBuf, StringCodec.INSTANCE, TTL, keyBuf);
            return m.map(v -> new NumericResponse<>(command, v));
        });
    }

    @Override
    public Flux<NumericResponse<KeyCommand, Long>> pTtl(Publisher<KeyCommand> commands) {
        return execute(commands, command -> {

            Assert.notNull(command.getKey(), "Key must not be null!");

            byte[] keyBuf = toByteArray(command.getKey());
            Mono<Long> m = write(keyBuf, StringCodec.INSTANCE, RedisCommands.PTTL, keyBuf);
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
    
    private static final RedisStrictCommand<ValueEncoding> OBJECT_ENCODING = new RedisStrictCommand<ValueEncoding>("OBJECT", "ENCODING", new Convertor<ValueEncoding>() {
        @Override
        public ValueEncoding convert(Object obj) {
            return ValueEncoding.of((String) obj);
        }
    });

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

}
