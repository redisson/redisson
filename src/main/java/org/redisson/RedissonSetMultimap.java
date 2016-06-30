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
package org.redisson;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.RedisStrictCommand;
import org.redisson.client.protocol.convertor.BooleanAmountReplayConvertor;
import org.redisson.client.protocol.convertor.BooleanReplayConvertor;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.core.RSetMultimap;

import io.netty.util.concurrent.Future;

/**
 * @author Nikita Koksharov
 *
 * @param <K> key
 * @param <V> value
 */
public class RedissonSetMultimap<K, V> extends RedissonMultimap<K, V> implements RSetMultimap<K, V> {

    private static final RedisStrictCommand<Boolean> SCARD_VALUE = new RedisStrictCommand<Boolean>("SCARD", new BooleanAmountReplayConvertor());
    private static final RedisCommand<Boolean> SISMEMBER_VALUE = new RedisCommand<Boolean>("SISMEMBER", new BooleanReplayConvertor());

    RedissonSetMultimap(CommandAsyncExecutor connectionManager, String name) {
        super(connectionManager, name);
    }

    RedissonSetMultimap(Codec codec, CommandAsyncExecutor connectionManager, String name) {
        super(codec, connectionManager, name);
    }

    public Future<Integer> sizeAsync() {
        return commandExecutor.evalReadAsync(getName(), codec, RedisCommands.EVAL_INTEGER,
                "local keys = redis.call('hgetall', KEYS[1]); " +
                "local size = 0; " +
                "for i, v in ipairs(keys) do " +
                    "if i % 2 == 0 then " +
                        "local name = '{' .. KEYS[1] .. '}:' .. v; " +
                        "size = size + redis.call('scard', name); " +
                    "end;" +
                "end; " +
                "return size; ",
                Arrays.<Object>asList(getName()));
    }

    
    
    public Future<Boolean> containsKeyAsync(Object key) {
        try {
            byte[] keyState = codec.getMapKeyEncoder().encode(key);
            String keyHash = hash(keyState);

            String setName = getValuesName(keyHash);
            return commandExecutor.readAsync(getName(), codec, SCARD_VALUE, setName);
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public Future<Boolean> containsValueAsync(Object value) {
        try {
            byte[] valueState = codec.getMapValueEncoder().encode(value);

            return commandExecutor.evalReadAsync(getName(), codec, RedisCommands.EVAL_BOOLEAN,
                    "local keys = redis.call('hgetall', KEYS[1]); " +
                    "for i, v in ipairs(keys) do " +
                        "if i % 2 == 0 then " +
                            "local name = '{' .. KEYS[1] .. '}:' .. v; " +
                            "if redis.call('sismember', name, ARGV[1]) == 1 then "
                                + "return 1; " +
                            "end;" +
                        "end;" +
                    "end; " +
                    "return 0; ",
                    Arrays.<Object>asList(getName()), valueState);
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public Future<Boolean> containsEntryAsync(Object key, Object value) {
        try {
            byte[] keyState = codec.getMapKeyEncoder().encode(key);
            String keyHash = hash(keyState);
            byte[] valueState = codec.getMapValueEncoder().encode(value);

            String setName = getValuesName(keyHash);
            return commandExecutor.readAsync(getName(), codec, SISMEMBER_VALUE, setName, valueState);
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public Future<Boolean> putAsync(K key, V value) {
        try {
            byte[] keyState = codec.getMapKeyEncoder().encode(key);
            String keyHash = hash(keyState);
            byte[] valueState = codec.getMapValueEncoder().encode(value);

            String setName = getValuesName(keyHash);
            return commandExecutor.evalWriteAsync(getName(), codec, RedisCommands.EVAL_BOOLEAN,
                    "redis.call('hset', KEYS[1], ARGV[1], ARGV[2]); " +
                    "return redis.call('sadd', KEYS[2], ARGV[3]); ",
                Arrays.<Object>asList(getName(), setName), keyState, keyHash, valueState);
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public Future<Boolean> removeAsync(Object key, Object value) {
        try {
            byte[] keyState = codec.getMapKeyEncoder().encode(key);
            String keyHash = hash(keyState);
            byte[] valueState = codec.getMapValueEncoder().encode(value);

            String setName = getValuesName(keyHash);
            return commandExecutor.evalWriteAsync(getName(), codec, RedisCommands.EVAL_BOOLEAN,
                    "local res = redis.call('srem', KEYS[2], ARGV[2]); "
                  + "if res == 1 and redis.call('scard', KEYS[2]) == 0 then "
                      + "redis.call('hdel', KEYS[1], ARGV[1]); "
                  + "end; "
                  + "return res; ",
                Arrays.<Object>asList(getName(), setName), keyState, valueState);
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public Future<Boolean> putAllAsync(K key, Iterable<? extends V> values) {
        try {
            List<Object> params = new ArrayList<Object>();
            byte[] keyState = codec.getMapKeyEncoder().encode(key);
            params.add(keyState);
            String keyHash = hash(keyState);
            params.add(keyHash);
            for (Object value : values) {
                byte[] valueState = codec.getMapValueEncoder().encode(value);
                params.add(valueState);
            }

            String setName = getValuesName(keyHash);
            return commandExecutor.evalWriteAsync(getName(), codec, RedisCommands.EVAL_BOOLEAN_AMOUNT,
                    "redis.call('hset', KEYS[1], ARGV[1], ARGV[2]); " +
                    "return redis.call('sadd', KEYS[2], unpack(ARGV, 3, #ARGV)); ",
                Arrays.<Object>asList(getName(), setName), params.toArray());
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public Set<V> get(K key) {
        try {
            byte[] keyState = codec.getMapKeyEncoder().encode(key);
            String keyHash = hash(keyState);
            String setName = getValuesName(keyHash);

            return new RedissonSet<V>(codec, commandExecutor, setName);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public Set<V> getAll(K key) {
        return (Set<V>) super.getAll(key);
    }

    public Future<Collection<V>> getAllAsync(K key) {
        try {
            byte[] keyState = codec.getMapKeyEncoder().encode(key);
            String keyHash = hash(keyState);
            String setName = getValuesName(keyHash);

            return commandExecutor.readAsync(getName(), codec, RedisCommands.SMEMBERS, setName);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public Set<V> removeAll(Object key) {
        return (Set<V>) get(removeAllAsync(key));
    }

    public Future<Collection<V>> removeAllAsync(Object key) {
        try {
            byte[] keyState = codec.getMapKeyEncoder().encode(key);
            String keyHash = hash(keyState);

            String setName = getValuesName(keyHash);
            return commandExecutor.evalWriteAsync(getName(), codec, RedisCommands.EVAL_SET,
                    "redis.call('hdel', KEYS[1], ARGV[1]); " +
                    "local members = redis.call('smembers', KEYS[2]); " +
                    "redis.call('del', KEYS[2]); " +
                    "return members; ",
                Arrays.<Object>asList(getName(), setName), keyState);
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public Set<Entry<K, V>> entries() {
        return (Set<Entry<K, V>>) super.entries();
    }

    @Override
    public Set<V> replaceValues(K key, Iterable<? extends V> values) {
        return (Set<V>) get(replaceValuesAsync(key, values));
    }

    Iterator<V> valuesIterator() {
        return new RedissonSetMultimapIterator<K, V, V>(RedissonSetMultimap.this, commandExecutor, codec) {
            @Override
            V getValue(V entry) {
                return (V) entry;
            }
        };
    }

    RedissonSetMultimapIterator<K, V, Entry<K, V>> entryIterator() {
        return new RedissonSetMultimapIterator<K, V, Map.Entry<K, V>>(RedissonSetMultimap.this, commandExecutor, codec);
    }

    public Future<Collection<V>> replaceValuesAsync(K key, Iterable<? extends V> values) {
        try {
            List<Object> params = new ArrayList<Object>();
            byte[] keyState = codec.getMapKeyEncoder().encode(key);
            params.add(keyState);
            String keyHash = hash(keyState);
            params.add(keyHash);
            for (Object value : values) {
                byte[] valueState = codec.getMapValueEncoder().encode(value);
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
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

}
