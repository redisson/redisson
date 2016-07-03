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

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.redisson.client.codec.Codec;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.codec.ScanCodec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommand.ValueType;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.convertor.BooleanReplayConvertor;
import org.redisson.client.protocol.convertor.LongReplayConvertor;
import org.redisson.client.protocol.convertor.VoidReplayConvertor;
import org.redisson.client.protocol.decoder.ListMultiDecoder;
import org.redisson.client.protocol.decoder.LongMultiDecoder;
import org.redisson.client.protocol.decoder.MapCacheScanResult;
import org.redisson.client.protocol.decoder.MapCacheScanResultReplayDecoder;
import org.redisson.client.protocol.decoder.MapScanResult;
import org.redisson.client.protocol.decoder.ObjectListDecoder;
import org.redisson.client.protocol.decoder.ObjectListReplayDecoder;
import org.redisson.client.protocol.decoder.ObjectMapDecoder;
import org.redisson.client.protocol.decoder.ObjectMapReplayDecoder;
import org.redisson.client.protocol.decoder.ScanObjectEntry;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.connection.decoder.MapGetAllDecoder;
import org.redisson.core.RMapCache;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;

/**
 * <p>Map-based cache with ability to set TTL for each entry via
 * {@link #put(Object, Object, long, TimeUnit)} or {@link #putIfAbsent(Object, Object, long, TimeUnit)} methods.
 * And therefore has an complex lua-scripts inside.</p>
 *
 * <p>Current redis implementation doesnt have map entry eviction functionality.
 * Thus entries are checked for TTL expiration during any key/value/entry read operation.
 * If key/value/entry expired then it doesn't returns and clean task runs asynchronous.
 * Clean task deletes removes 100 expired entries at once.
 * In addition there is {@link org.redisson.EvictionScheduler}. This scheduler
 * deletes expired entries in time interval between 5 seconds to 2 hours.</p>
 *
 * <p>If eviction is not required then it's better to use {@link org.redisson.reactive.RedissonMapReactive}.</p>
 *
 * @author Nikita Koksharov
 *
 * @param <K> key
 * @param <V> value
 */
public class RedissonMapCache<K, V> extends RedissonMap<K, V> implements RMapCache<K, V> {

    static final RedisCommand<Boolean> EVAL_HSET = new RedisCommand<Boolean>("EVAL", new BooleanReplayConvertor(), 4, ValueType.MAP);
    static final RedisCommand<Object> EVAL_REPLACE = new RedisCommand<Object>("EVAL", 6, ValueType.MAP, ValueType.MAP_VALUE);
    static final RedisCommand<Boolean> EVAL_REPLACE_VALUE = new RedisCommand<Boolean>("EVAL", new BooleanReplayConvertor(), 7, Arrays.asList(ValueType.MAP_KEY, ValueType.MAP_VALUE, ValueType.MAP_VALUE));
    private static final RedisCommand<Void> EVAL_HMSET = new RedisCommand<Void>("EVAL", new VoidReplayConvertor(), 4, ValueType.MAP);
    private static final RedisCommand<MapCacheScanResult<Object, Object>> EVAL_HSCAN = new RedisCommand<MapCacheScanResult<Object, Object>>("EVAL", new ListMultiDecoder(new LongMultiDecoder(), new ObjectMapReplayDecoder(), new ObjectListReplayDecoder()), ValueType.MAP);
    private static final RedisCommand<Object> EVAL_REMOVE = new RedisCommand<Object>("EVAL", 4, ValueType.MAP_KEY, ValueType.MAP_VALUE);
    private static final RedisCommand<Long> EVAL_REMOVE_VALUE = new RedisCommand<Long>("EVAL", new LongReplayConvertor(), 5, ValueType.MAP);
    private static final RedisCommand<Object> EVAL_PUT_TTL = new RedisCommand<Object>("EVAL", 9, ValueType.MAP, ValueType.MAP_VALUE);
    private static final RedisCommand<Boolean> EVAL_FAST_PUT_TTL = new RedisCommand<Boolean>("EVAL", new BooleanReplayConvertor(), 9, ValueType.MAP, ValueType.MAP_VALUE);
    private static final RedisCommand<Object> EVAL_GET_TTL = new RedisCommand<Object>("EVAL", 7, ValueType.MAP_KEY, ValueType.MAP_VALUE);
    private static final RedisCommand<Boolean> EVAL_CONTAINS_KEY = new RedisCommand<Boolean>("EVAL", new BooleanReplayConvertor(), 7, ValueType.MAP_KEY);
    private static final RedisCommand<Boolean> EVAL_CONTAINS_VALUE = new RedisCommand<Boolean>("EVAL", new BooleanReplayConvertor(), 7, ValueType.MAP_VALUE);
    private static final RedisCommand<Long> EVAL_FAST_REMOVE = new RedisCommand<Long>("EVAL", 5, ValueType.MAP_KEY);

    protected RedissonMapCache(EvictionScheduler evictionScheduler, CommandAsyncExecutor commandExecutor, String name) {
        super(commandExecutor, name);
        evictionScheduler.schedule(getName(), getTimeoutSetName(), getIdleSetName());
    }

    public RedissonMapCache(Codec codec, EvictionScheduler evictionScheduler, CommandAsyncExecutor commandExecutor, String name) {
        super(codec, commandExecutor, name);
        evictionScheduler.schedule(getName(), getTimeoutSetName(), getIdleSetName());
    }

    @Override
    public Future<Boolean> containsKeyAsync(Object key) {
        return commandExecutor.evalWriteAsync(getName(), codec, EVAL_CONTAINS_KEY,
                "local value = redis.call('hget', KEYS[1], ARGV[2]); " +
                "local expireDate = 92233720368547758; " +
                "if value ~= false then " +
                      "local expireDateScore = redis.call('zscore', KEYS[2], ARGV[2]); "
                    + "if expireDateScore ~= false then "
                        + "expireDate = tonumber(expireDateScore) "
                    + "end; "
                    + "local t, val = struct.unpack('dLc0', value); "
                    + "if t ~= 0 then "
                        + "local expireIdle = redis.call('zscore', KEYS[3], ARGV[2]); "
                        + "if expireIdle ~= false then "
                            + "if tonumber(expireIdle) > tonumber(ARGV[1]) then "
                                 + "local value = struct.pack('dLc0', t, string.len(val), val); "
                                 + "redis.call('hset', KEYS[1], ARGV[2], value); "
                                 + "redis.call('zadd', KEYS[3], t + tonumber(ARGV[1]), ARGV[2]); "
                            + "end; "
                            + "expireDate = math.min(expireDate, tonumber(expireIdle)) "
                        + "end; "
                    + "end; "
                    + "if expireDate <= tonumber(ARGV[1]) then "
                        + "return 0;"
                    + "end; "
                    + "return 1;" +
                "end;" +
                "return 0; ",
               Arrays.<Object>asList(getName(), getTimeoutSetName(), getIdleSetName()), System.currentTimeMillis(), key);
    }

    @Override
    public Future<Boolean> containsValueAsync(Object value) {
        return commandExecutor.evalWriteAsync(getName(), codec, EVAL_CONTAINS_VALUE,
                        "local s = redis.call('hgetall', KEYS[1]); "
                        + "for i, v in ipairs(s) do "
                            + "if i % 2 == 0 then "
                              + "local t, val = struct.unpack('dLc0', v); "
                              + "if ARGV[2] == val then "
                                  + "local key = s[i-1];" +
                                    "local expireDate = 92233720368547758; " +
                                    "local expireDateScore = redis.call('zscore', KEYS[2], key); "
                                    + "if expireDateScore ~= false then "
                                        + "expireDate = tonumber(expireDateScore) "
                                    + "end; "
                                    + "if t ~= 0 then "
                                        + "local expireIdle = redis.call('zscore', KEYS[3], key); "
                                        + "if expireIdle ~= false then "
                                            + "if tonumber(expireIdle) > tonumber(ARGV[1]) then "
                                                + "local value = struct.pack('dLc0', t, string.len(val), val); "
                                                + "redis.call('hset', KEYS[1], key, value); "
                                                + "redis.call('zadd', KEYS[3], t + tonumber(ARGV[1]), key); "
                                            + "end; "
                                            + "expireDate = math.min(expireDate, tonumber(expireIdle)) "
                                        + "end; "
                                    + "end; "
                                    + "if expireDate <= tonumber(ARGV[1]) then "
                                        + "return 0;"
                                    + "end; "
                                    + "return 1; "
                              + "end; "
                            + "end; "
                       + "end;" +
                     "return 0;",
                 Arrays.<Object>asList(getName(), getTimeoutSetName(), getIdleSetName()), System.currentTimeMillis(), value);
    }

    @Override
    public Future<Map<K, V>> getAllAsync(Set<K> keys) {
        if (keys.isEmpty()) {
            return newSucceededFuture(Collections.<K, V>emptyMap());
        }

        List<Object> args = new ArrayList<Object>(keys.size() + 1);
        args.add(System.currentTimeMillis());
        args.addAll(keys);

        return commandExecutor.evalWriteAsync(getName(), codec, new RedisCommand<Map<Object, Object>>("EVAL", new MapGetAllDecoder(args, 1), 7, ValueType.MAP_KEY, ValueType.MAP_VALUE),
                        "local expireHead = redis.call('zrange', KEYS[2], 0, 0, 'withscores');" +
                        "local currentTime = tonumber(table.remove(ARGV, 1)); " // index is the first parameter
                      + "local hasExpire = #expireHead == 2 and tonumber(expireHead[2]) <= currentTime; "
                      + "local map = redis.call('hmget', KEYS[1], unpack(ARGV)); "
                      + "for i = #map, 1, -1 do "
                          + "local value = map[i]; "
                          + "if value ~= false then "
                              + "local key = ARGV[i]; "
                              + "local t, val = struct.unpack('dLc0', value); "
                              + "map[i] = val; "

                              + "if hasExpire then "
                                  + "local expireDate = redis.call('zscore', KEYS[2], key); "
                                  + "if expireDate ~= false and tonumber(expireDate) <= currentTime then "
                                      + "map[i] = false; "
                                  + "end; "
                              + "end; "

                              + "if t ~= 0 then "
                                  + "local expireIdle = redis.call('zscore', KEYS[3], key); "
                                  + "if expireIdle ~= false then "
                                      + "if tonumber(expireIdle) > currentTime then "
                                          + "local value = struct.pack('dLc0', t, string.len(val), val); "
                                          + "redis.call('hset', KEYS[1], key, value); "
                                          + "redis.call('zadd', KEYS[3], t + currentTime, key); "
                                      + "else "
                                          + "map[i] = false; "
                                      + "end; "
                                  + "end; "
                              + "end; "

                          + "end; "
                      + "end; "
                      + "return map;",
                Arrays.<Object>asList(getName(), getTimeoutSetName(), getIdleSetName()), args.toArray());

    }

    @Override
    public V putIfAbsent(K key, V value, long ttl, TimeUnit ttlUnit) {
        return get(putIfAbsentAsync(key, value, ttl, ttlUnit));
    }

    @Override
    public Future<V> putIfAbsentAsync(K key, V value, long ttl, TimeUnit ttlUnit) {
        return putIfAbsentAsync(key, value, ttl, ttlUnit, 0, null);
    }

    @Override
    public V putIfAbsent(K key, V value, long ttl, TimeUnit ttlUnit, long maxIdleTime, TimeUnit maxIdleUnit) {
        return get(putIfAbsentAsync(key, value, ttl, ttlUnit, maxIdleTime, maxIdleUnit));
    }

    @Override
    public Future<V> putIfAbsentAsync(K key, V value, long ttl, TimeUnit ttlUnit, long maxIdleTime, TimeUnit maxIdleUnit) {
        if (ttl < 0) {
            throw new IllegalArgumentException("ttl can't be negative");
        }
        if (maxIdleTime < 0) {
            throw new IllegalArgumentException("maxIdleTime can't be negative");
        }
        if (ttl == 0 && maxIdleTime == 0) {
            return putIfAbsentAsync(key, value);
        }

        if (ttl > 0 && ttlUnit == null) {
            throw new NullPointerException("ttlUnit param can't be null");
        }

        if (maxIdleTime > 0 && maxIdleUnit == null) {
            throw new NullPointerException("maxIdleUnit param can't be null");
        }

        long ttlTimeout = 0;
        if (ttl > 0) {
            ttlTimeout = System.currentTimeMillis() + ttlUnit.toMillis(ttl);
        }

        long maxIdleTimeout = 0;
        long maxIdleDelta = 0;
        if (maxIdleTime > 0) {
            maxIdleDelta = maxIdleUnit.toMillis(maxIdleTime);
            maxIdleTimeout = System.currentTimeMillis() + maxIdleDelta;
        }

        return commandExecutor.evalWriteAsync(getName(), codec, EVAL_PUT_TTL,
                      "if redis.call('hexists', KEYS[1], ARGV[4]) == 0 then "
                        + "if tonumber(ARGV[1]) > 0 then "
                            + "redis.call('zadd', KEYS[2], ARGV[1], ARGV[4]); "
                        + "end; "
                        + "if tonumber(ARGV[2]) > 0 then "
                            + "redis.call('zadd', KEYS[3], ARGV[2], ARGV[4]); "
                        + "end; "
                        + "local value = struct.pack('dLc0', ARGV[3], string.len(ARGV[5]), ARGV[5]); "
                        + "redis.call('hset', KEYS[1], ARGV[4], value); "
                        + "return nil; "
                    + "else "
                        + "local value = redis.call('hget', KEYS[1], ARGV[4]); "
                        + "if value == false then "
                            + "return nil; "
                        + "end;"
                        + "local t, val = struct.unpack('dLc0', value); "
                        + "return val; "
                    + "end",
                Arrays.<Object>asList(getName(), getTimeoutSetName(), getIdleSetName()), ttlTimeout, maxIdleTimeout, maxIdleDelta, key, value);
    }

    @Override
    public Future<Long> removeAsync(Object key, Object value) {
        return commandExecutor.evalWriteAsync(getName(), codec, EVAL_REMOVE_VALUE,
                  "local value = redis.call('hget', KEYS[1], ARGV[1]); "
                + "if value == false then "
                    + "return 0; "
                + "end;"
                + "local t, val = struct.unpack('dLc0', value); "
                + "if val == ARGV[2] then "
                    + "redis.call('zrem', KEYS[2], ARGV[1]); "
                    + "redis.call('zrem', KEYS[3], ARGV[1]); "
                    + "return redis.call('hdel', KEYS[1], ARGV[1]); "
                + "else "
                    + "return 0 "
                + "end",
                Arrays.<Object>asList(getName(), getTimeoutSetName(), getIdleSetName()), key, value);
    }

    @Override
    public Future<V> getAsync(K key) {
        return commandExecutor.evalWriteAsync(getName(), codec, EVAL_GET_TTL,
                   "local value = redis.call('hget', KEYS[1], ARGV[2]); "
                 + "if value == false then "
                     + "return nil; "
                 + "end; "
                 + "local t, val = struct.unpack('dLc0', value); "
                 + "local expireDate = 92233720368547758; " +
                   "local expireDateScore = redis.call('zscore', KEYS[2], ARGV[2]); "
                 + "if expireDateScore ~= false then "
                     + "expireDate = tonumber(expireDateScore) "
                 + "end; "
                 + "if t ~= 0 then "
                     + "local expireIdle = redis.call('zscore', KEYS[3], ARGV[2]); "
                     + "if expireIdle ~= false then "
                         + "if tonumber(expireIdle) > tonumber(ARGV[1]) then "
                             + "local value = struct.pack('dLc0', t, string.len(val), val); "
                             + "redis.call('hset', KEYS[1], ARGV[2], value); "
                             + "redis.call('zadd', KEYS[3], t + tonumber(ARGV[1]), ARGV[2]); "
                         + "end; "
                         + "expireDate = math.min(expireDate, tonumber(expireIdle)) "
                     + "end; "
                 + "end; "
                 + "if expireDate <= tonumber(ARGV[1]) then "
                     + "return nil; "
                 + "end; "
                 + "return val; ",
                Arrays.<Object>asList(getName(), getTimeoutSetName(), getIdleSetName()), System.currentTimeMillis(), key);
    }

    @Override
    public V put(K key, V value, long ttl, TimeUnit unit) {
        return get(putAsync(key, value, ttl, unit));
    }

    @Override
    public Future<V> putAsync(K key, V value) {
        return commandExecutor.evalWriteAsync(getName(), codec, EVAL_PUT,
                  "local v = redis.call('hget', KEYS[1], ARGV[1]); "
                + "local value = struct.pack('dLc0', 0, string.len(ARGV[2]), ARGV[2]); "
                + "redis.call('hset', KEYS[1], ARGV[1], value); "
                + "if v == false then "
                    + "return nil; "
                + "end; "
                + "local t, val = struct.unpack('dLc0', v); "
                + "return val; ",
                Collections.<Object>singletonList(getName()), key, value);
    }

    @Override
    public Future<V> putIfAbsentAsync(K key, V value) {
        return commandExecutor.evalWriteAsync(getName(), codec, EVAL_PUT,
                 "local value = struct.pack('dLc0', 0, string.len(ARGV[2]), ARGV[2]); "
                 + "if redis.call('hsetnx', KEYS[1], ARGV[1], value) == 1 then "
                    + "return nil "
                + "else "
                    + "local v = redis.call('hget', KEYS[1], ARGV[1]); "
                    + "if v == false then "
                        + "return nil; "
                    + "end; "
                    + "local t, val = struct.unpack('dLc0', v); "
                    + "return val; "
                + "end",
                Collections.<Object>singletonList(getName()), key, value);
    }

    @Override
    public boolean fastPut(K key, V value, long ttl, TimeUnit ttlUnit) {
        return get(fastPutAsync(key, value, ttl, ttlUnit));
    }

    @Override
    public Future<Boolean> fastPutAsync(K key, V value, long ttl, TimeUnit ttlUnit) {
        return fastPutAsync(key, value, ttl, ttlUnit, 0, null);
    }

    @Override
    public boolean fastPut(K key, V value, long ttl, TimeUnit ttlUnit, long maxIdleTime, TimeUnit maxIdleUnit) {
        return get(fastPutAsync(key, value, ttl, ttlUnit, maxIdleTime, maxIdleUnit));
    }

    @Override
    public Future<Boolean> fastPutAsync(K key, V value, long ttl, TimeUnit ttlUnit, long maxIdleTime, TimeUnit maxIdleUnit) {
        if (ttl < 0) {
            throw new IllegalArgumentException("ttl can't be negative");
        }
        if (maxIdleTime < 0) {
            throw new IllegalArgumentException("maxIdleTime can't be negative");
        }
        if (ttl == 0 && maxIdleTime == 0) {
            return fastPutAsync(key, value);
        }

        if (ttl > 0 && ttlUnit == null) {
            throw new NullPointerException("ttlUnit param can't be null");
        }

        if (maxIdleTime > 0 && maxIdleUnit == null) {
            throw new NullPointerException("maxIdleUnit param can't be null");
        }

        long ttlTimeout = 0;
        if (ttl > 0) {
            ttlTimeout = System.currentTimeMillis() + ttlUnit.toMillis(ttl);
        }

        long maxIdleTimeout = 0;
        long maxIdleDelta = 0;
        if (maxIdleTime > 0) {
            maxIdleDelta = maxIdleUnit.toMillis(maxIdleTime);
            maxIdleTimeout = System.currentTimeMillis() + maxIdleDelta;
        }

        return commandExecutor.evalWriteAsync(getName(), codec, EVAL_FAST_PUT_TTL,
                  "if tonumber(ARGV[1]) > 0 then "
                    + "redis.call('zadd', KEYS[2], ARGV[1], ARGV[4]); "
                + "else "
                    + "redis.call('zrem', KEYS[2], ARGV[4]); "
                + "end; "
                + "if tonumber(ARGV[2]) > 0 then "
                    + "redis.call('zadd', KEYS[3], ARGV[2], ARGV[4]); "
                + "else "
                    + "redis.call('zrem', KEYS[3], ARGV[4]); "
                + "end; "
                + "local value = struct.pack('dLc0', ARGV[3], string.len(ARGV[5]), ARGV[5]); " +
                  "return redis.call('hset', KEYS[1], ARGV[4], value); ",
                Arrays.<Object>asList(getName(), getTimeoutSetName(), getIdleSetName()), ttlTimeout, maxIdleTimeout, maxIdleDelta, key, value);
    }

    @Override
    public Future<V> putAsync(K key, V value, long ttl, TimeUnit ttlUnit) {
        return putAsync(key, value, ttl, ttlUnit, 0, null);
    }

    @Override
    public V put(K key, V value, long ttl, TimeUnit ttlUnit, long maxIdleTime, TimeUnit maxIdleUnit) {
        return get(putAsync(key, value, ttl, ttlUnit, maxIdleTime, maxIdleUnit));
    }

    @Override
    public Future<V> putAsync(K key, V value, long ttl, TimeUnit ttlUnit, long maxIdleTime, TimeUnit maxIdleUnit) {
        if (ttl < 0) {
            throw new IllegalArgumentException("ttl can't be negative");
        }
        if (maxIdleTime < 0) {
            throw new IllegalArgumentException("maxIdleTime can't be negative");
        }
        if (ttl == 0 && maxIdleTime == 0) {
            return putAsync(key, value);
        }

        if (ttl > 0 && ttlUnit == null) {
            throw new NullPointerException("ttlUnit param can't be null");
        }

        if (maxIdleTime > 0 && maxIdleUnit == null) {
            throw new NullPointerException("maxIdleUnit param can't be null");
        }

        long ttlTimeout = 0;
        if (ttl > 0) {
            ttlTimeout = System.currentTimeMillis() + ttlUnit.toMillis(ttl);
        }

        long maxIdleTimeout = 0;
        long maxIdleDelta = 0;
        if (maxIdleTime > 0) {
            maxIdleDelta = maxIdleUnit.toMillis(maxIdleTime);
            maxIdleTimeout = System.currentTimeMillis() + maxIdleDelta;
        }

        return commandExecutor.evalWriteAsync(getName(), codec, EVAL_PUT_TTL,
                  "local v = redis.call('hget', KEYS[1], ARGV[4]); "
                + "if tonumber(ARGV[1]) > 0 then "
                    + "redis.call('zadd', KEYS[2], ARGV[1], ARGV[4]); "
                + "else "
                    + "redis.call('zrem', KEYS[2], ARGV[4]); "
                + "end; "
                + "if tonumber(ARGV[2]) > 0 then "
                    + "redis.call('zadd', KEYS[3], ARGV[2], ARGV[4]); "
                + "else "
                    + "redis.call('zrem', KEYS[3], ARGV[4]); "
                + "end; "
                + "local value = struct.pack('dLc0', ARGV[3], string.len(ARGV[5]), ARGV[5]); "
                + "redis.call('hset', KEYS[1], ARGV[4], value); "
                + "if v == false then "
                    + "return nil;"
                + "end; "
                + "local t, val = struct.unpack('dLc0', v); "
                + "return val",
                Arrays.<Object>asList(getName(), getTimeoutSetName(), getIdleSetName()), ttlTimeout, maxIdleTimeout, maxIdleDelta, key, value);
    }

    String getTimeoutSetName() {
        return "redisson__timeout__set__{" + getName() + "}";
    }

    String getIdleSetName() {
        return "redisson__idle__set__{" + getName() + "}";
    }

    @Override
    public Future<V> removeAsync(K key) {
        return commandExecutor.evalWriteAsync(getName(), codec, EVAL_REMOVE,
                  "local v = redis.call('hget', KEYS[1], ARGV[1]); "
                + "redis.call('zrem', KEYS[2], ARGV[1]); "
                + "redis.call('zrem', KEYS[3], ARGV[1]); "
                + "redis.call('hdel', KEYS[1], ARGV[1]); "
                + "if v ~= false then "
                    + "local t, val = struct.unpack('dLc0', v); "
                    + "return val; "
                + "end; "
                + "return v",
                Arrays.<Object>asList(getName(), getTimeoutSetName(), getIdleSetName()), key);
    }

    @Override
    public Future<Long> fastRemoveAsync(K ... keys) {
        if (keys == null || keys.length == 0) {
            return newSucceededFuture(0L);
        }

        return commandExecutor.evalWriteAsync(getName(), codec, EVAL_FAST_REMOVE,
                "redis.call('zrem', KEYS[3], unpack(ARGV)); " +
                "redis.call('zrem', KEYS[2], unpack(ARGV)); " +
                "return redis.call('hdel', KEYS[1], unpack(ARGV)); ",
                Arrays.<Object>asList(getName(), getTimeoutSetName(), getIdleSetName()), keys);
    }

    @Override
    MapScanResult<ScanObjectEntry, ScanObjectEntry> scanIterator(String name, InetSocketAddress client, long startPos) {
        RedisCommand<MapCacheScanResult<Object, Object>> EVAL_HSCAN = new RedisCommand<MapCacheScanResult<Object, Object>>("EVAL", 
                new ListMultiDecoder(new LongMultiDecoder(), new ObjectMapDecoder(new ScanCodec(codec)), new ObjectListDecoder(codec), new MapCacheScanResultReplayDecoder()), ValueType.MAP);
        Future<MapCacheScanResult<ScanObjectEntry, ScanObjectEntry>> f = commandExecutor.evalReadAsync(client, getName(), codec, EVAL_HSCAN,
                "local result = {}; "
                + "local idleKeys = {}; "
                + "local res = redis.call('hscan', KEYS[1], ARGV[2]); "
                + "local currentTime = tonumber(ARGV[1]); "
                + "for i, value in ipairs(res[2]) do "
                    + "if i % 2 == 0 then "
                      + "local key = res[2][i-1]; " +
                        "local expireDate = 92233720368547758; " +
                        "local expireDateScore = redis.call('zscore', KEYS[2], key); "
                        + "if expireDateScore ~= false then "
                            + "expireDate = tonumber(expireDateScore) "
                        + "end; "

                        + "local t, val = struct.unpack('dLc0', value); "
                        + "if t ~= 0 then "
                            + "local expireIdle = redis.call('zscore', KEYS[3], key); "
                            + "if expireIdle ~= false then "
                                + "if tonumber(expireIdle) > currentTime and expireDate > currentTime then "
                                    + "table.insert(idleKeys, key); "
                                + "end; "
                                + "expireDate = math.min(expireDate, tonumber(expireIdle)) "
                            + "end; "
                        + "end; "

                        + "if expireDate > currentTime then "
                            + "table.insert(result, key); "
                            + "table.insert(result, val); "
                        + "end; "
                    + "end; "
                + "end;"
                + "return {res[1], result, idleKeys};", Arrays.<Object>asList(getName(), getTimeoutSetName(), getIdleSetName()), System.currentTimeMillis(), startPos);
        
        f.addListener(new FutureListener<MapCacheScanResult<ScanObjectEntry, ScanObjectEntry>>() {
            @Override
            public void operationComplete(Future<MapCacheScanResult<ScanObjectEntry, ScanObjectEntry>> future)
                    throws Exception {
                if (future.isSuccess()) {
                    MapCacheScanResult<ScanObjectEntry, ScanObjectEntry> res = future.getNow();
                    if (res.getIdleKeys().isEmpty()) {
                        return;
                    }
                    
                    List<Object> args = new ArrayList<Object>(res.getIdleKeys().size() + 1);
                    args.add(System.currentTimeMillis());
                    args.addAll(res.getIdleKeys());

                    commandExecutor.evalWriteAsync(getName(), codec, new RedisCommand<Map<Object, Object>>("EVAL", new MapGetAllDecoder(args, 1), 7, ValueType.MAP_KEY, ValueType.MAP_VALUE),
                                    "local currentTime = tonumber(table.remove(ARGV, 1)); " // index is the first parameter
                                  + "local map = redis.call('hmget', KEYS[1], unpack(ARGV)); "
                                  + "for i = #map, 1, -1 do "
                                      + "local value = map[i]; "
                                      + "if value ~= false then "
                                          + "local key = ARGV[i]; "
                                          + "local t, val = struct.unpack('dLc0', value); "
    
                                          + "if t ~= 0 then "
                                              + "local expireIdle = redis.call('zscore', KEYS[2], key); "
                                              + "if expireIdle ~= false then "
                                                  + "if tonumber(expireIdle) > currentTime then "
                                                      + "local value = struct.pack('dLc0', t, string.len(val), val); "
                                                      + "redis.call('hset', KEYS[1], key, value); "
                                                      + "redis.call('zadd', KEYS[2], t + currentTime, key); "
                                                  + "end; "
                                              + "end; "
                                          + "end; "
                                      + "end; "
                                  + "end; ",
                            Arrays.<Object>asList(getName(), getIdleSetName()), args.toArray());
                    
                }
            }
        });

        return get(f);
    }

    @Override
    public Future<Boolean> fastPutAsync(K key, V value) {
        return commandExecutor.evalWriteAsync(getName(), codec, EVAL_HSET,
                "local val = struct.pack('dLc0', 0, string.len(ARGV[2]), ARGV[2]); "
              + "return redis.call('hset', KEYS[1], ARGV[1], val); ",
          Collections.<Object>singletonList(getName()), key, value);
    }

    @Override
    public Future<Boolean> fastPutIfAbsentAsync(K key, V value) {
        return commandExecutor.evalWriteAsync(getName(), codec, EVAL_HSET,
                "local val = struct.pack('dLc0', 0, string.len(ARGV[2]), ARGV[2]); "
              + "return redis.call('hsetnx', KEYS[1], ARGV[1], val); ",
          Collections.<Object>singletonList(getName()), key, value);
    }

    @Override
    public Future<Boolean> replaceAsync(K key, V oldValue, V newValue) {
        return commandExecutor.evalWriteAsync(getName(), codec, EVAL_REPLACE_VALUE,
                  "local v = redis.call('hget', KEYS[1], ARGV[2]); "
                + "if v == false then "
                    + "return 0;"
                + "end;"
                + "local expireDate = 92233720368547758; " +
                "local expireDateScore = redis.call('zscore', KEYS[2], ARGV[2]); "
                + "if expireDateScore ~= false then "
                    + "expireDate = tonumber(expireDateScore) "
                + "end; "

                + "local t, val = struct.unpack('dLc0', v); "
                + "if t ~= 0 then "
                    + "local expireIdle = redis.call('zscore', KEYS[3], ARGV[2]); "
                    + "if expireIdle ~= false then "
                        + "if tonumber(expireIdle) > tonumber(ARGV[1]) then "
                            + "local value = struct.pack('dLc0', t, string.len(val), val); "
                            + "redis.call('hset', KEYS[1], ARGV[2], value); "
                            + "redis.call('zadd', KEYS[3], t + tonumber(ARGV[1]), ARGV[2]); "
                        + "end; "
                        + "expireDate = math.min(expireDate, tonumber(expireIdle)) "
                    + "end; "
                + "end; "
                + "if expireDate > tonumber(ARGV[1]) and val == ARGV[3] then "
                    + "local value = struct.pack('dLc0', t, string.len(ARGV[4]), ARGV[4]); "
                    + "redis.call('hset', KEYS[1], ARGV[2], value); "
                    + "return 1; "
                + "end; "
                + "return 0; ",
                Arrays.<Object>asList(getName(), getTimeoutSetName(), getIdleSetName()), System.currentTimeMillis(), key, oldValue, newValue);
    }

    @Override
    public Future<V> replaceAsync(K key, V value) {
        return commandExecutor.evalWriteAsync(getName(), codec, EVAL_REPLACE,
                  "local v = redis.call('hget', KEYS[1], ARGV[2]); "
                + "if v ~= false then "
                    + "local t, val = struct.unpack('dLc0', v); "
                    + "if t ~= 0 then "
                        + "t = t + tonumber(ARGV[1]); "
                    + "end; "
                    + "local value = struct.pack('dLc0', t, string.len(ARGV[3]), ARGV[3]); "
                    + "redis.call('hset', KEYS[1], ARGV[2], value); "
                    + "return val; "
                + "else "
                    + "return nil; "
                + "end",
                Arrays.<Object>asList(getName(), getTimeoutSetName()), System.currentTimeMillis(), key, value);
    }

    @Override
    public Future<Void> putAllAsync(Map<? extends K, ? extends V> map) {
        if (map.isEmpty()) {
            return newSucceededFuture(null);
        }

        List<Object> params = new ArrayList<Object>(map.size()*2);
        for (java.util.Map.Entry<? extends K, ? extends V> t : map.entrySet()) {
            params.add(t.getKey());
            params.add(t.getValue());
        }

        return commandExecutor.evalWriteAsync(getName(), codec, EVAL_HMSET,
                  "for i, value in ipairs(ARGV) do "
                    + "if i % 2 == 0 then "
                        + "local val = struct.pack('dLc0', 0, string.len(value), value); "
                        + "ARGV[i] = val; "
                    + "end;"
                + "end;"
                + "return redis.call('hmset', KEYS[1], unpack(ARGV)); ",
            Collections.<Object>singletonList(getName()), params.toArray());
    }

    @Override
    public Future<Boolean> deleteAsync() {
        return commandExecutor.writeAsync(getName(), RedisCommands.DEL_OBJECTS, getName(), getTimeoutSetName(), getIdleSetName());
    }

    @Override
    public Future<Boolean> expireAsync(long timeToLive, TimeUnit timeUnit) {
        return commandExecutor.evalWriteAsync(getName(), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                "redis.call('zadd', KEYS[2], 92233720368547758, 'redisson__expiretag');" +
                "redis.call('pexpire', KEYS[2], ARGV[1]); " +
                "redis.call('zadd', KEYS[3], 92233720368547758, 'redisson__expiretag');" +
                "redis.call('pexpire', KEYS[3], ARGV[1]); " +
                "return redis.call('pexpire', KEYS[1], ARGV[1]); ",
                Arrays.<Object>asList(getName(), getTimeoutSetName(), getIdleSetName()), timeUnit.toMillis(timeToLive));
    }

    @Override
    public Future<Boolean> expireAtAsync(long timestamp) {
        return commandExecutor.evalWriteAsync(getName(), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                "redis.call('zadd', KEYS[2], 92233720368547758, 'redisson__expiretag');" +
                "redis.call('pexpireat', KEYS[2], ARGV[1]); " +
                "redis.call('zadd', KEYS[3], 92233720368547758, 'redisson__expiretag');" +
                "redis.call('pexpire', KEYS[3], ARGV[1]); " +
                "return redis.call('pexpireat', KEYS[1], ARGV[1]); ",
                Arrays.<Object>asList(getName(), getTimeoutSetName(), getIdleSetName()), timestamp);
    }

    @Override
    public Future<Boolean> clearExpireAsync() {
        return commandExecutor.evalWriteAsync(getName(), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                  "redis.call('zrem', KEYS[2], 'redisson__expiretag'); " +
                  "redis.call('persist', KEYS[2]); " +
                  "redis.call('zrem', KEYS[3], 'redisson__expiretag'); " +
                  "redis.call('persist', KEYS[3]); " +
                  "return redis.call('persist', KEYS[1]); ",
                Arrays.<Object>asList(getName(), getTimeoutSetName(), getIdleSetName()));
    }

    @Override
    public Future<Set<java.util.Map.Entry<K, V>>> readAllEntrySetAsync() {
        return commandExecutor.evalWriteAsync(getName(), codec, RedisCommands.EVAL_MAP_ENTRY,
                "local s = redis.call('hgetall', KEYS[1]); "
                + "local result = {}; "
                + "for i, v in ipairs(s) do "
                    + "if i % 2 == 0 then "
                      + "local t, val = struct.unpack('dLc0', v); "
                      + "local key = s[i-1];" +
                        "local expireDate = 92233720368547758; " +
                        "local expireDateScore = redis.call('zscore', KEYS[2], key); "
                        + "if expireDateScore ~= false then "
                            + "expireDate = tonumber(expireDateScore) "
                        + "end; "
                        + "if t ~= 0 then "
                            + "local expireIdle = redis.call('zscore', KEYS[3], key); "
                            + "if expireIdle ~= false then "
                                + "if tonumber(expireIdle) > tonumber(ARGV[1]) then "
                                    + "local value = struct.pack('dLc0', t, string.len(val), val); "
                                    + "redis.call('hset', KEYS[1], key, value); "
                                    + "redis.call('zadd', KEYS[3], t + tonumber(ARGV[1]), key); "
                                + "end; "
                                + "expireDate = math.min(expireDate, tonumber(expireIdle)) "
                            + "end; "
                        + "end; "
                        + "if expireDate > tonumber(ARGV[1]) then "
                            + "table.insert(result, key); "
                            + "table.insert(result, val); "
                        + "end; "
                    + "end; "
               + "end;" +
             "return result;",
             Arrays.<Object>asList(getName(), getTimeoutSetName(), getIdleSetName()), System.currentTimeMillis());
    }
    
    @Override
    public Future<Collection<V>> readAllValuesAsync() {
        return commandExecutor.evalWriteAsync(getName(), codec, RedisCommands.EVAL_MAP_VALUE_LIST,
                "local s = redis.call('hgetall', KEYS[1]); "
                + "local result = {}; "
                + "for i, v in ipairs(s) do "
                    + "if i % 2 == 0 then "
                      + "local t, val = struct.unpack('dLc0', v); "
                      + "local key = s[i-1];" +
                        "local expireDate = 92233720368547758; " +
                        "local expireDateScore = redis.call('zscore', KEYS[2], key); "
                        + "if expireDateScore ~= false then "
                            + "expireDate = tonumber(expireDateScore) "
                        + "end; "
                        + "if t ~= 0 then "
                            + "local expireIdle = redis.call('zscore', KEYS[3], key); "
                            + "if expireIdle ~= false then "
                                + "if tonumber(expireIdle) > tonumber(ARGV[1]) then "
                                    + "local value = struct.pack('dLc0', t, string.len(val), val); "
                                    + "redis.call('hset', KEYS[1], key, value); "
                                    + "redis.call('zadd', KEYS[3], t + tonumber(ARGV[1]), key); "
                                + "end; "
                                + "expireDate = math.min(expireDate, tonumber(expireIdle)) "
                            + "end; "
                        + "end; "
                        + "if expireDate > tonumber(ARGV[1]) then "
                            + "table.insert(result, val); "
                        + "end; "
                    + "end; "
               + "end;" +
             "return result;",
             Arrays.<Object>asList(getName(), getTimeoutSetName(), getIdleSetName()), System.currentTimeMillis());
    }
    
}
