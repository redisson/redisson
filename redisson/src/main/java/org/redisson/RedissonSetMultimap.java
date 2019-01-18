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
package org.redisson;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.redisson.api.RFuture;
import org.redisson.api.RSet;
import org.redisson.api.RSetMultimap;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.RedisStrictCommand;
import org.redisson.client.protocol.convertor.BooleanAmountReplayConvertor;
import org.redisson.client.protocol.convertor.BooleanReplayConvertor;
import org.redisson.command.CommandAsyncExecutor;

import io.netty.buffer.ByteBuf;

/**
 * @author Nikita Koksharov
 *
 * @param <K> key
 * @param <V> value
 */
public class RedissonSetMultimap<K, V> extends RedissonMultimap<K, V> implements RSetMultimap<K, V> {

    private static final RedisStrictCommand<Boolean> SCARD_VALUE = new RedisStrictCommand<Boolean>("SCARD", new BooleanAmountReplayConvertor());
    private static final RedisCommand<Boolean> SISMEMBER_VALUE = new RedisCommand<Boolean>("SISMEMBER", new BooleanReplayConvertor());

    public RedissonSetMultimap(CommandAsyncExecutor connectionManager, String name) {
        super(connectionManager, name);
    }

    public RedissonSetMultimap(Codec codec, CommandAsyncExecutor connectionManager, String name) {
        super(codec, connectionManager, name);
    }

    @Override
    public RFuture<Integer> sizeAsync() {
        return commandExecutor.evalReadAsync(getName(), codec, RedisCommands.EVAL_INTEGER,
                "local keys = redis.call('hgetall', KEYS[1]); " +
                "local size = 0; " +
                "for i, v in ipairs(keys) do " +
                    "if i % 2 == 0 then " +
                        "local name = ARGV[1] .. v; " +
                        "size = size + redis.call('scard', name); " +
                    "end;" +
                "end; " +
                "return size; ",
                Arrays.<Object>asList(getName()),
                prefix);
    }

    @Override
    public RFuture<Boolean> containsKeyAsync(Object key) {
        String keyHash = keyHash(key);
        String setName = getValuesName(keyHash);
        return commandExecutor.readAsync(getName(), codec, SCARD_VALUE, setName);
    }

    @Override
    public RFuture<Boolean> containsValueAsync(Object value) {
        ByteBuf valueState = encodeMapValue(value);

        return commandExecutor.evalReadAsync(getName(), codec, RedisCommands.EVAL_BOOLEAN,
                "local keys = redis.call('hgetall', KEYS[1]); " +
                "for i, v in ipairs(keys) do " +
                    "if i % 2 == 0 then " +
                        "local name = ARGV[2] .. v; " +
                        "if redis.call('sismember', name, ARGV[1]) == 1 then "
                            + "return 1; " +
                        "end;" +
                    "end;" +
                "end; " +
                "return 0; ",
                Arrays.<Object>asList(getName()), 
                valueState, prefix);
    }

    @Override
    public RFuture<Boolean> containsEntryAsync(Object key, Object value) {
        String keyHash = keyHash(key);
        ByteBuf valueState = encodeMapValue(value);

        String setName = getValuesName(keyHash);
        return commandExecutor.readAsync(getName(), codec, SISMEMBER_VALUE, setName, valueState);
    }

    @Override
    public RFuture<Boolean> putAsync(K key, V value) {
        ByteBuf keyState = encodeMapKey(key);
        String keyHash = hash(keyState);
        ByteBuf valueState = encodeMapValue(value);

        String setName = getValuesName(keyHash);
        return commandExecutor.evalWriteAsync(getName(), codec, RedisCommands.EVAL_BOOLEAN,
                "redis.call('hset', KEYS[1], ARGV[1], ARGV[2]); " +
                "return redis.call('sadd', KEYS[2], ARGV[3]); ",
            Arrays.<Object>asList(getName(), setName), keyState, keyHash, valueState);
    }

    @Override
    public RFuture<Boolean> removeAsync(Object key, Object value) {
        ByteBuf keyState = encodeMapKey(key);
        String keyHash = hash(keyState);
        ByteBuf valueState = encodeMapValue(value);

        String setName = getValuesName(keyHash);
        return commandExecutor.evalWriteAsync(getName(), codec, RedisCommands.EVAL_BOOLEAN,
                "local res = redis.call('srem', KEYS[2], ARGV[2]); "
              + "if res == 1 and redis.call('scard', KEYS[2]) == 0 then "
                  + "redis.call('hdel', KEYS[1], ARGV[1]); "
              + "end; "
              + "return res; ",
            Arrays.<Object>asList(getName(), setName), keyState, valueState);
    }

    @Override
    public RFuture<Boolean> putAllAsync(K key, Iterable<? extends V> values) {
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
        return commandExecutor.evalWriteAsync(getName(), codec, RedisCommands.EVAL_BOOLEAN_AMOUNT,
                "redis.call('hset', KEYS[1], ARGV[1], ARGV[2]); " +
                "return redis.call('sadd', KEYS[2], unpack(ARGV, 3, #ARGV)); ",
            Arrays.<Object>asList(getName(), setName), params.toArray());
    }

    @Override
    public RSet<V> get(final K key) {
        String keyHash = keyHash(key);
        final String setName = getValuesName(keyHash);

        return new RedissonSet<V>(codec, commandExecutor, setName, null) {
            
            @Override
            public RFuture<Boolean> addAsync(V value) {
                return RedissonSetMultimap.this.putAsync(key, value);
            }
            
            @Override
            public RFuture<Boolean> addAllAsync(Collection<? extends V> c) {
                return RedissonSetMultimap.this.putAllAsync(key, c);
            }
            
            @Override
            public RFuture<Boolean> removeAsync(Object value) {
                return RedissonSetMultimap.this.removeAsync(key, value);
            }
            
            @Override
            public RFuture<Boolean> removeAllAsync(Collection<?> c) {
                ByteBuf keyState = encodeMapKey(key);
                return commandExecutor.evalWriteAsync(RedissonSetMultimap.this.getName(), codec, RedisCommands.EVAL_BOOLEAN_AMOUNT,
                        "redis.call('hdel', KEYS[1], ARGV[1]); " +
                        "return redis.call('del', KEYS[2]); ",
                    Arrays.<Object>asList(RedissonSetMultimap.this.getName(), setName), keyState);
            }
            
            @Override
            public RFuture<Boolean> deleteAsync() {
                ByteBuf keyState = encodeMapKey(key);
                return RedissonSetMultimap.this.fastRemoveAsync(Arrays.<Object>asList(keyState), Arrays.<Object>asList(setName), RedisCommands.EVAL_BOOLEAN_AMOUNT);
            }
            
            @Override
            public RFuture<Boolean> clearExpireAsync() {
                throw new UnsupportedOperationException("This operation is not supported for SetMultimap values Set");
            }
            
            @Override
            public RFuture<Boolean> expireAsync(long timeToLive, TimeUnit timeUnit) {
                throw new UnsupportedOperationException("This operation is not supported for SetMultimap values Set");
            }
            
            @Override
            public RFuture<Boolean> expireAtAsync(long timestamp) {
                throw new UnsupportedOperationException("This operation is not supported for SetMultimap values Set");
            }
            
            @Override
            public RFuture<Long> remainTimeToLiveAsync() {
                throw new UnsupportedOperationException("This operation is not supported for SetMultimap values Set");
            }
            
            @Override
            public RFuture<Void> renameAsync(String newName) {
                throw new UnsupportedOperationException("This operation is not supported for SetMultimap values Set");
            }
            
            @Override
            public RFuture<Boolean> renamenxAsync(String newName) {
                throw new UnsupportedOperationException("This operation is not supported for SetMultimap values Set");
            }
            
        };
    }

    @Override
    public Set<V> getAll(K key) {
        return (Set<V>) super.getAll(key);
    }

    @Override
    public RFuture<Collection<V>> getAllAsync(K key) {
        String keyHash = keyHash(key);
        String setName = getValuesName(keyHash);

        return commandExecutor.readAsync(getName(), codec, RedisCommands.SMEMBERS, setName);
    }

    @Override
    public Set<V> removeAll(Object key) {
        return (Set<V>) get(removeAllAsync(key));
    }

    @Override
    public RFuture<Collection<V>> removeAllAsync(Object key) {
        ByteBuf keyState = encodeMapKey(key);
        String keyHash = hash(keyState);

        String setName = getValuesName(keyHash);
        return commandExecutor.evalWriteAsync(getName(), codec, RedisCommands.EVAL_SET,
                "redis.call('hdel', KEYS[1], ARGV[1]); " +
                "local members = redis.call('smembers', KEYS[2]); " +
                "redis.call('del', KEYS[2]); " +
                "return members; ",
            Arrays.<Object>asList(getName(), setName), keyState);
    }

    @Override
    public Set<Entry<K, V>> entries() {
        return (Set<Entry<K, V>>) super.entries();
    }

    @Override
    public Set<V> replaceValues(K key, Iterable<? extends V> values) {
        return (Set<V>) get(replaceValuesAsync(key, values));
    }

    @Override
    Iterator<V> valuesIterator() {
        return new RedissonSetMultimapIterator<K, V, V>(RedissonSetMultimap.this, commandExecutor, codec) {
            @Override
            V getValue(V entry) {
                return (V) entry;
            }
        };
    }

    @Override
    RedissonSetMultimapIterator<K, V, Entry<K, V>> entryIterator() {
        return new RedissonSetMultimapIterator<K, V, Map.Entry<K, V>>(RedissonSetMultimap.this, commandExecutor, codec);
    }

    @Override
    public RFuture<Collection<V>> replaceValuesAsync(K key, Iterable<? extends V> values) {
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
        return commandExecutor.evalWriteAsync(getName(), codec, RedisCommands.EVAL_SET,
                "redis.call('hset', KEYS[1], ARGV[1], ARGV[2]); " +
                "local members = redis.call('smembers', KEYS[2]); " +
                "redis.call('del', KEYS[2]); " +
                "redis.call('sadd', KEYS[2], unpack(ARGV, 3, #ARGV)); " +
                "return members; ",
            Arrays.<Object>asList(getName(), setName), params.toArray());
    }

}
