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
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.core.RListMultimapCache;

import io.netty.util.concurrent.Future;

/**
 * @author Nikita Koksharov
 *
 * @param <K> key
 * @param <V> value
 */
public class RedissonListMultimapCache<K, V> extends RedissonListMultimap<K, V> implements RListMultimapCache<K, V> {

    private final RedissonMultimapCache<K> baseCache;
    
    RedissonListMultimapCache(EvictionScheduler evictionScheduler, CommandAsyncExecutor connectionManager, String name) {
        super(connectionManager, name);
        evictionScheduler.scheduleCleanMultimap(name, getTimeoutSetName());
        baseCache = new RedissonMultimapCache<K>(connectionManager, name, codec, getTimeoutSetName());
    }

    RedissonListMultimapCache(EvictionScheduler evictionScheduler, Codec codec, CommandAsyncExecutor connectionManager, String name) {
        super(codec, connectionManager, name);
        evictionScheduler.scheduleCleanMultimap(name, getTimeoutSetName());
        baseCache = new RedissonMultimapCache<K>(connectionManager, name, codec, getTimeoutSetName());
    }

    public Future<Boolean> containsKeyAsync(Object key) {
        try {
            byte[] keyState = codec.getMapKeyEncoder().encode(key);
            String keyHash = hash(keyState);

            String valuesName = getValuesName(keyHash);
            
            return commandExecutor.evalReadAsync(getName(), codec, RedisCommands.EVAL_BOOLEAN,
                    "local value = redis.call('hget', KEYS[1], ARGV[2]); " +
                    "if value ~= false then " +
                          "local expireDate = 92233720368547758; " +
                          "local expireDateScore = redis.call('zscore', KEYS[2], ARGV[2]); "
                        + "if expireDateScore ~= false then "
                            + "expireDate = tonumber(expireDateScore) "
                        + "end; "
                        + "if expireDate <= tonumber(ARGV[1]) then "
                            + "return 0;"
                        + "end; "
                        + "return redis.call('llen', ARGV[3]) > 0 and 1 or 0;" +
                    "end;" +
                    "return 0; ",
                   Arrays.<Object>asList(getName(), getTimeoutSetName()), System.currentTimeMillis(), keyState, valuesName);
            
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }
    
    String getTimeoutSetName() {
        return "redisson_list_multimap_ttl{" + getName() + "}";
    }


    public Future<Boolean> containsValueAsync(Object value) {
        try {
            byte[] valueState = codec.getMapValueEncoder().encode(value);

            return commandExecutor.evalReadAsync(getName(), codec, RedisCommands.EVAL_BOOLEAN,
                    "local keys = redis.call('hgetall', KEYS[1]); " +
                    "for i, v in ipairs(keys) do " +
                        "if i % 2 == 0 then " +
                            "local expireDate = 92233720368547758; " +
                            "local expireDateScore = redis.call('zscore', KEYS[2], keys[i-1]); "
                          + "if expireDateScore ~= false then "
                              + "expireDate = tonumber(expireDateScore) "
                          + "end; "
                          + "if expireDate > tonumber(ARGV[2]) then " +
                                "local name = '{' .. KEYS[1] .. '}:' .. v; " +
                          
                                "local items = redis.call('lrange', name, 0, -1) " +
                                "for i=1,#items do " +
                                    "if items[i] == ARGV[1] then " +
                                        "return 1; " +
                                    "end; " +
                                "end; " +
                              
                            "end; " +
                        "end;" +
                    "end; " +
                    "return 0; ",
                    Arrays.<Object>asList(getName(), getTimeoutSetName()), valueState, System.currentTimeMillis());
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public Future<Boolean> containsEntryAsync(Object key, Object value) {
        try {
            byte[] keyState = codec.getMapKeyEncoder().encode(key);
            String keyHash = hash(keyState);
            byte[] valueState = codec.getMapValueEncoder().encode(value);

            String valuesName = getValuesName(keyHash);
            return commandExecutor.evalReadAsync(getName(), codec, RedisCommands.EVAL_BOOLEAN,
                    "local expireDate = 92233720368547758; " +
                    "local expireDateScore = redis.call('zscore', KEYS[2], ARGV[2]); "
                  + "if expireDateScore ~= false then "
                      + "expireDate = tonumber(expireDateScore) "
                  + "end; "
                  + "if expireDate > tonumber(ARGV[1]) then " +
                      "local items = redis.call('lrange', KEYS[1], 0, -1); " +
                      "for i = 1, #items do " +
                          "if items[i] == ARGV[3] then " +
                              "return 1; " +
                          "end; " +
                      "end; " +
                    "end; " +
                    "return 0; ",
                    Arrays.<Object>asList(valuesName, getTimeoutSetName()), System.currentTimeMillis(), keyState, valueState);
            
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public List<V> get(K key) {
        try {
            byte[] keyState = codec.getMapKeyEncoder().encode(key);
            String keyHash = hash(keyState);
            String valuesName = getValuesName(keyHash);

            return new RedissonListMultimapValues<V>(codec, commandExecutor, valuesName, getTimeoutSetName(), key);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    public Future<Collection<V>> getAllAsync(K key) {
        try {
            byte[] keyState = codec.getMapKeyEncoder().encode(key);
            String keyHash = hash(keyState);
            String valuesName = getValuesName(keyHash);
            
            return commandExecutor.evalReadAsync(getName(), codec, RedisCommands.EVAL_LIST,
                    "local expireDate = 92233720368547758; " +
                    "local expireDateScore = redis.call('zscore', KEYS[2], ARGV[2]); "
                  + "if expireDateScore ~= false then "
                      + "expireDate = tonumber(expireDateScore) "
                  + "end; "
                  + "if expireDate > tonumber(ARGV[1]) then " +
                       "return redis.call('lrange', KEYS[1], 0, -1); " +
                    "end; " +
                    "return {}; ",
                Arrays.<Object>asList(valuesName, getTimeoutSetName()), System.currentTimeMillis(), keyState);
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }

    }

    public Future<Collection<V>> removeAllAsync(Object key) {
        try {
            byte[] keyState = codec.getMapKeyEncoder().encode(key);
            String keyHash = hash(keyState);

            String valuesName = getValuesName(keyHash);
            return commandExecutor.evalWriteAsync(getName(), codec, RedisCommands.EVAL_SET,
                    "redis.call('hdel', KEYS[1], ARGV[1]); " +
                    "local members = redis.call('lrange', KEYS[2], 0, -1); " +
                    "redis.call('del', KEYS[2]); " +
                    "redis.call('zrem', KEYS[3], ARGV[1]); " +
                    "return members; ",
                Arrays.<Object>asList(getName(), valuesName, getTimeoutSetName()), keyState);
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public boolean expireKey(K key, long timeToLive, TimeUnit timeUnit) {
        return get(expireKeyAsync(key, timeToLive, timeUnit));
    }
    
    @Override
    public Future<Boolean> expireKeyAsync(K key, long timeToLive, TimeUnit timeUnit) {
        return baseCache.expireKeyAsync(key, timeToLive, timeUnit);
    }

    @Override
    public Future<Boolean> deleteAsync() {
        return baseCache.deleteAsync();
    }

    @Override
    public Future<Boolean> expireAsync(long timeToLive, TimeUnit timeUnit) {
        return baseCache.expireAsync(timeToLive, timeUnit);
    }

    @Override
    public Future<Boolean> expireAtAsync(long timestamp) {
        return baseCache.expireAtAsync(timestamp);
    }

    @Override
    public Future<Boolean> clearExpireAsync() {
        return baseCache.clearExpireAsync();
    }
    
}
