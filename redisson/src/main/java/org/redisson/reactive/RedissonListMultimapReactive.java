/**
 * Copyright 2016 Nikita Koksharov
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
package org.redisson.reactive;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.reactivestreams.Publisher;
import org.redisson.RedissonListMultimap;
import org.redisson.api.RListMultimapReactive;
import org.redisson.api.RListReactive;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandReactiveExecutor;

import io.netty.buffer.ByteBuf;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <K> key type
 * @param <V> value type
 */
public class RedissonListMultimapReactive<K, V> extends RedissonBaseMultimapReactive<K, V> implements RListMultimapReactive<K, V> {

    public RedissonListMultimapReactive(UUID id, CommandReactiveExecutor commandExecutor, String name) {
        super(new RedissonListMultimap<K, V>(id, commandExecutor, name), commandExecutor, name);
    }

    public RedissonListMultimapReactive(UUID id, Codec codec, CommandReactiveExecutor commandExecutor, String name) {
        super(new RedissonListMultimap<K, V>(id, codec, commandExecutor, name), codec, commandExecutor, name);
    }

    @Override
    public RListReactive<V> get(final K key) {
        final ByteBuf keyState = encodeMapKey(key);
        final String keyHash = hashAndRelease(keyState);
        final String setName = getValuesName(keyHash);

        return new RedissonListReactive<V>(codec, commandExecutor, setName) {
            
            @Override
            public Publisher<Boolean> delete() {
                ByteBuf keyState = encodeMapKey(key);
                return RedissonListMultimapReactive.this.fastRemove(Arrays.<Object>asList(keyState), Arrays.<Object>asList(setName), RedisCommands.EVAL_BOOLEAN_AMOUNT);
            }
            
            @Override
            public Publisher<Boolean> clearExpire() {
                throw new UnsupportedOperationException("This operation is not supported for SetMultimap values Set");
            }
            
            @Override
            public Publisher<Boolean> expire(long timeToLive, TimeUnit timeUnit) {
                throw new UnsupportedOperationException("This operation is not supported for SetMultimap values Set");
            }
            
            @Override
            public Publisher<Boolean> expireAt(long timestamp) {
                throw new UnsupportedOperationException("This operation is not supported for SetMultimap values Set");
            }
            
            @Override
            public Publisher<Long> remainTimeToLive() {
                throw new UnsupportedOperationException("This operation is not supported for SetMultimap values Set");
            }
            
            @Override
            public Publisher<Void> rename(String newName) {
                throw new UnsupportedOperationException("This operation is not supported for SetMultimap values Set");
            }
            
            @Override
            public Publisher<Boolean> renamenx(String newName) {
                throw new UnsupportedOperationException("This operation is not supported for SetMultimap values Set");
            }
            
        };
    }

    @Override
    public Publisher<List<V>> getAll(K key) {
        ByteBuf keyState = encodeMapKey(key);
        String keyHash = hashAndRelease(keyState);
        String setName = getValuesName(keyHash);

        return commandExecutor.readReactive(getName(), codec, RedisCommands.LRANGE, setName, 0, -1);
    }

    @Override
    public Publisher<List<V>> removeAll(Object key) {
        ByteBuf keyState = encodeMapKey(key);
        String keyHash = hash(keyState);

        String setName = getValuesName(keyHash);
        return commandExecutor.evalWriteReactive(getName(), codec, RedisCommands.EVAL_LIST,
                "redis.call('hdel', KEYS[1], ARGV[1]); " +
                "local members = redis.call('lrange', KEYS[2], 0, -1); " +
                "redis.call('del', KEYS[2]); " +
                "return members; ",
            Arrays.<Object>asList(getName(), setName), keyState);
    }

    @Override
    public Publisher<List<V>> replaceValues(K key, Iterable<? extends V> values) {
        List<Object> params = new ArrayList<Object>();
        ByteBuf keyState = encodeMapKey(key);
        params.add(keyState);
        String keyHash = hash(keyState);
        params.add(keyHash);
        for (Object value : values) {
            ByteBuf valueState = encodeMapValue(value);
            params.add(valueState);
        }

        String setName = getValuesName(keyHash);
        return commandExecutor.evalWriteReactive(getName(), codec, RedisCommands.EVAL_LIST,
                "redis.call('hset', KEYS[1], ARGV[1], ARGV[2]); " +
                "local members = redis.call('lrange', KEYS[2], 0, -1); " +
                "redis.call('del', KEYS[2]); " +
                "redis.call('rpush', KEYS[2], unpack(ARGV, 3, #ARGV)); " +
                "return members; ",
            Arrays.<Object>asList(getName(), setName), params.toArray());
    }

}
