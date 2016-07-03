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
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.RedisStrictCommand;
import org.redisson.client.protocol.convertor.BooleanAmountReplayConvertor;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.core.RListMultimap;

import io.netty.util.concurrent.Future;

/**
 * @author Nikita Koksharov
 *
 * @param <K> key
 * @param <V> value
 */
public class RedissonListMultimap<K, V> extends RedissonMultimap<K, V> implements RListMultimap<K, V> {

    private static final RedisStrictCommand<Boolean> LLEN_VALUE = new RedisStrictCommand<Boolean>("LLEN", new BooleanAmountReplayConvertor());

    RedissonListMultimap(CommandAsyncExecutor connectionManager, String name) {
        super(connectionManager, name);
    }

    RedissonListMultimap(Codec codec, CommandAsyncExecutor connectionManager, String name) {
        super(codec, connectionManager, name);
    }

    public Future<Integer> sizeAsync() {
        return commandExecutor.evalReadAsync(getName(), codec, RedisCommands.EVAL_INTEGER,
                "local keys = redis.call('hgetall', KEYS[1]); " +
                "local size = 0; " +
                "for i, v in ipairs(keys) do " +
                    "if i % 2 == 0 then " +
                        "local name = '{' .. KEYS[1] .. '}:' .. v; " +
                        "size = size + redis.call('llen', name); " +
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
            return commandExecutor.readAsync(getName(), codec, LLEN_VALUE, setName);
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

                            "local items = redis.call('lrange', name, 0, -1) " +
                            "for i=1,#items do " +
                                "if items[i] == ARGV[1] then " +
                                    "return 1; " +
                                "end; " +
                            "end; " +
                        "end;" +
                    "end; " +
                    "return 0; ",
                    Arrays.<Object>asList(getName()), valueState);
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public boolean containsEntry(Object key, Object value) {
        return get(containsEntryAsync(key, value));
    }

    public Future<Boolean> containsEntryAsync(Object key, Object value) {
        try {
            byte[] keyState = codec.getMapKeyEncoder().encode(key);
            String keyHash = hash(keyState);
            byte[] valueState = codec.getMapValueEncoder().encode(value);

            String setName = getValuesName(keyHash);

            return commandExecutor.evalReadAsync(getName(), codec, RedisCommands.EVAL_BOOLEAN,
                    "local items = redis.call('lrange', KEYS[1], 0, -1) " +
                    "for i=1,#items do " +
                        "if items[i] == ARGV[1] then " +
                            "return 1; " +
                        "end; " +
                    "end; " +
                    "return 0; ",
                    Collections.<Object>singletonList(setName), valueState);
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public boolean put(K key, V value) {
        return get(putAsync(key, value));
    }

    public Future<Boolean> putAsync(K key, V value) {
        try {
            byte[] keyState = codec.getMapKeyEncoder().encode(key);
            String keyHash = hash(keyState);
            byte[] valueState = codec.getMapValueEncoder().encode(value);

            String setName = getValuesName(keyHash);
            return commandExecutor.evalWriteAsync(getName(), codec, RedisCommands.EVAL_BOOLEAN,
                    "redis.call('hset', KEYS[1], ARGV[1], ARGV[2]); " +
                    "redis.call('rpush', KEYS[2], ARGV[3]); " +
                    "return 1; ",
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
                    "local res = redis.call('lrem', KEYS[2], 1, ARGV[2]); "
                  + "if res == 1 and redis.call('llen', KEYS[2]) == 0 then "
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
                    "return redis.call('rpush', KEYS[2], unpack(ARGV, 3, #ARGV)); ",
                Arrays.<Object>asList(getName(), setName), params.toArray());
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }


    @Override
    public List<V> get(K key) {
        try {
            byte[] keyState = codec.getMapKeyEncoder().encode(key);
            String keyHash = hash(keyState);
            String setName = getValuesName(keyHash);

            return new RedissonList<V>(codec, commandExecutor, setName);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public List<V> getAll(K key) {
        return (List<V>) get(getAllAsync(key));
    }

    public Future<Collection<V>> getAllAsync(K key) {
        try {
            byte[] keyState = codec.getMapKeyEncoder().encode(key);
            String keyHash = hash(keyState);
            String setName = getValuesName(keyHash);

            return commandExecutor.readAsync(getName(), codec, RedisCommands.LRANGE, setName, 0, -1);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public List<V> removeAll(Object key) {
        return (List<V>) get(removeAllAsync(key));
    }

    public Future<Collection<V>> removeAllAsync(Object key) {
        try {
            byte[] keyState = codec.getMapKeyEncoder().encode(key);
            String keyHash = hash(keyState);

            String setName = getValuesName(keyHash);
            return commandExecutor.evalWriteAsync(getName(), codec, RedisCommands.EVAL_LIST,
                    "redis.call('hdel', KEYS[1], ARGV[1]); " +
                    "local members = redis.call('lrange', KEYS[2], 0, -1); " +
                    "redis.call('del', KEYS[2]); " +
                    "return members; ",
                Arrays.<Object>asList(getName(), setName), keyState);
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public List<V> replaceValues(K key, Iterable<? extends V> values) {
        return (List<V>) get(replaceValuesAsync(key, values));
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
            return commandExecutor.evalWriteAsync(getName(), codec, RedisCommands.EVAL_LIST,
                    "redis.call('hset', KEYS[1], ARGV[1], ARGV[2]); " +
                    "local members = redis.call('lrange', KEYS[2], 0, -1); " +
                    "redis.call('del', KEYS[2]); " +
                    "redis.call('rpush', KEYS[2], unpack(ARGV, 3, #ARGV)); " +
                    "return members; ",
                Arrays.<Object>asList(getName(), setName), params.toArray());
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    Iterator<V> valuesIterator() {
        return new RedissonListMultimapIterator<K, V, V>(this, commandExecutor, codec) {
            @Override
            V getValue(V entry) {
                return (V) entry;
            }
        };
    }

    RedissonMultiMapIterator<K, V, Entry<K, V>> entryIterator() {
        return new RedissonListMultimapIterator<K, V, Map.Entry<K, V>>(this, commandExecutor, codec);
    }

}
