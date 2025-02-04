/**
 * Copyright (c) 2013-2024 Nikita Koksharov
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

import io.netty.buffer.ByteBuf;
import org.redisson.api.*;
import org.redisson.api.listener.MessageListener;
import org.redisson.api.map.event.*;
import org.redisson.client.RedisClient;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.IntegerCodec;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.convertor.NumberConvertor;
import org.redisson.client.protocol.decoder.*;
import org.redisson.codec.BaseEventCodec;
import org.redisson.codec.MapCacheEventCodec;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.connection.decoder.MapGetAllDecoder;
import org.redisson.eviction.EvictionScheduler;
import org.redisson.misc.CompletableFutureWrapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * <p>Map-based cache with ability to set TTL for each entry via
 * {@link #put(Object, Object, long, TimeUnit)} or {@link #putIfAbsent(Object, Object, long, TimeUnit)} methods.
 * And therefore has an complex lua-scripts inside.</p>
 *
 * <p>Current redis implementation doesnt have map entry eviction functionality.
 * Thus entries are checked for TTL expiration during any key/value/entry read operation.
 * If key/value/entry expired then it doesn't returns and clean task runs asynchronous.
 * Clean task deletes removes 100 expired entries at once.
 * In addition there is {@link EvictionScheduler}. This scheduler
 * deletes expired entries in time interval between 5 seconds to 2 hours.</p>
 *
 * <p>If eviction is not required then it's better to use {@link RedissonMap} object.</p>
 *
 * @author Nikita Koksharov
 *
 * @param <K> key
 * @param <V> value
 */
public class RedissonMapCache<K, V> extends RedissonMap<K, V> implements RMapCache<K, V> {

    private final EvictionScheduler evictionScheduler;
    protected String publishCommand;
    private String timeoutSetName;
    private String idleSetName;
    private String lastAccessTimeSetName;
    private String optionsName;
    
    public RedissonMapCache(EvictionScheduler evictionScheduler, CommandAsyncExecutor commandExecutor,
                            String name, RedissonClient redisson, MapCacheOptions<K, V> options, WriteBehindService writeBehindService) {
        super(commandExecutor, name, redisson, options, writeBehindService);
        this.timeoutSetName = getTimeoutSetName(getRawName());
        this.idleSetName = getIdleSetName(getRawName());
        this.lastAccessTimeSetName = getLastAccessTimeSetName(getRawName());
        this.optionsName = getOptionsName(getRawName());
        if (evictionScheduler != null) {
            evictionScheduler.schedule(getRawName(), timeoutSetName, idleSetName,
                    getExpiredChannelName(), lastAccessTimeSetName, options,
                    commandExecutor.getConnectionManager().getSubscribeService().getPublishCommand());
        }
        this.evictionScheduler = evictionScheduler;
        this.publishCommand = commandExecutor.getConnectionManager().getSubscribeService().getPublishCommand();
    }

    public RedissonMapCache(Codec codec, EvictionScheduler evictionScheduler, CommandAsyncExecutor commandExecutor,
                            String name, RedissonClient redisson, MapCacheOptions<K, V> options, WriteBehindService writeBehindService) {
        super(codec, commandExecutor, name, redisson, options, writeBehindService);
        this.timeoutSetName = getTimeoutSetName(getRawName());
        this.idleSetName = getIdleSetName(getRawName());
        this.lastAccessTimeSetName = getLastAccessTimeSetName(getRawName());
        this.optionsName = getOptionsName(getRawName());
        if (evictionScheduler != null) {
            evictionScheduler.schedule(getRawName(), timeoutSetName, idleSetName,
                    getExpiredChannelName(), lastAccessTimeSetName, options,
                    commandExecutor.getConnectionManager().getSubscribeService().getPublishCommand());
        }
        this.evictionScheduler = evictionScheduler;
        this.publishCommand = commandExecutor.getConnectionManager().getSubscribeService().getPublishCommand();
    }

    @Override
    public boolean trySetMaxSize(long maxSize) {
        return get(trySetMaxSizeAsync(maxSize));
    }

    public boolean trySetMaxSize(long maxSize, EvictionMode mode) {
        return get(trySetMaxSizeAsync(maxSize, mode));
    }

    @Override
    public RFuture<Boolean> trySetMaxSizeAsync(long maxSize) {
        return trySetMaxSizeAsync(maxSize, EvictionMode.LRU);
    }

    public RFuture<Boolean> trySetMaxSizeAsync(long maxSize, EvictionMode mode) {
        if (maxSize < 0) {
            throw new IllegalArgumentException("maxSize should be greater than zero");
        }

        return commandExecutor.evalWriteNoRetryAsync(getRawName(), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                "redis.call('hsetnx', KEYS[1], 'max-size', ARGV[1]);"
              + "return redis.call('hsetnx', KEYS[1], 'mode', ARGV[2]);",
                Collections.singletonList(optionsName), maxSize, mode);
    }

    @Override
    public V computeIfAbsent(K key, Duration ttl, Function<? super K, ? extends V> mappingFunction) {
        checkNotBatch();

        checkKey(key);
        Objects.requireNonNull(mappingFunction);

        V value = get(key);
        if (value != null) {
            return value;
        }
        RLock lock = getLock(key);
        lock.lock();
        try {
            value = get(key);
            if (value == null) {
                V newValue = mappingFunction.apply(key);
                if (newValue != null) {
                    V r = putIfAbsent(key, newValue, ttl.toMillis(), TimeUnit.MILLISECONDS);
                    if (r != null) {
                        return r;
                    }
                    return newValue;
                }
                return null;
            }
            return value;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public RFuture<V> computeIfAbsentAsync(K key, Duration ttl, Function<? super K, ? extends V> mappingFunction) {
        checkNotBatch();

        checkKey(key);
        Objects.requireNonNull(mappingFunction);

        RLock lock = getLock(key);
        long threadId = Thread.currentThread().getId();
        CompletionStage<V> f = lock.lockAsync(threadId)
                .thenCompose(r -> {
                    RFuture<V> oldValueFuture = getAsync(key, threadId);
                    return oldValueFuture.thenCompose(oldValue -> {
                        if (oldValue != null) {
                            return CompletableFuture.completedFuture(oldValue);
                        }

                        return CompletableFuture.supplyAsync(() -> mappingFunction.apply(key), getServiceManager().getExecutor())
                                .thenCompose(newValue -> {
                                    if (newValue != null) {
                                        return putIfAbsentAsync(key, newValue, ttl.toMillis(), TimeUnit.MILLISECONDS).thenApply(rr -> {
                                            if (rr != null) {
                                                return rr;
                                            }
                                            return newValue;
                                        });
                                    }
                                    return CompletableFuture.completedFuture(null);
                                });
                    }).whenComplete((c, e) -> {
                        lock.unlockAsync(threadId);
                    });
                });

        return new CompletableFutureWrapper<>(f);
    }

    @Override
    public void setMaxSize(long maxSize) {
        get(setMaxSizeAsync(maxSize));
    }

    public void setMaxSize(long maxSize, EvictionMode mode) {
        get(setMaxSizeAsync(maxSize, mode));
    }

    @Override
    public RFuture<Void> setMaxSizeAsync(long maxSize) {
        return setMaxSizeAsync(maxSize, EvictionMode.LRU);
    }

    public RFuture<Void> setMaxSizeAsync(long maxSize, EvictionMode mode) {
        if (maxSize < 0) {
            throw new IllegalArgumentException("maxSize should be greater than zero");
        }

        List<Object> params = new ArrayList<>(3);
        params.add(optionsName);
        params.add("max-size");
        params.add(maxSize);
        params.add("mode");
        params.add(mode);

        return commandExecutor.writeAsync(getRawName(), LongCodec.INSTANCE, RedisCommands.HMSET, params.toArray());
    }

    @Override
    protected RFuture<Boolean> containsKeyOperationAsync(String name, Object key) {
        return commandExecutor.evalWriteAsync(name, codec, RedisCommands.EVAL_BOOLEAN,
        "local value = redis.call('hget', KEYS[1], ARGV[2]); " +
                "local expireDate = 92233720368547758; " +
                "if value ~= false then " +

                "    local maxSize = tonumber(redis.call('hget', KEYS[5], 'max-size')); " +
                "    if maxSize ~= nil and maxSize ~= 0 then " +
                "        local mode = redis.call('hget', KEYS[5], 'mode'); " +
                "        if mode == false or mode == 'LRU' then " +
                "               redis.call('zadd', KEYS[4], tonumber(ARGV[1]), ARGV[2]); " +
                "        else " +
                "               redis.call('zincrby', KEYS[4], 1, ARGV[2]); " +
                "        end; " +
                "    end;" +

                "    local expireDateScore = redis.call('zscore', KEYS[2], ARGV[2]); " +
                "    if expireDateScore ~= false then " +
                "        expireDate = tonumber(expireDateScore) " +
                "    end; " +
                "    local t, val = struct.unpack('dLc0', value); " +
                "    if t ~= 0 then " +
                "        local expireIdle = redis.call('zscore', KEYS[3], ARGV[2]); " +
                "        if expireIdle ~= false then " +
                "            if tonumber(expireIdle) > tonumber(ARGV[1]) then " +
                "                redis.call('zadd', KEYS[3], t + tonumber(ARGV[1]), ARGV[2]); " +
                "            end ;" +
                "            expireDate = math.min(expireDate, tonumber(expireIdle)) " +
                "        end; " +
                "    end; " +
                "    if expireDate <= tonumber(ARGV[1]) then " +
                "        return 0; " +
                "    end; " +
                "    return 1;" +
                "end;" +
                "return 0; ",
                Arrays.<Object>asList(name, getTimeoutSetName(name), getIdleSetName(name), getLastAccessTimeSetName(name), getOptionsName(name)),
                System.currentTimeMillis(), encodeMapKey(key));
    }

    @Override
    public RFuture<Boolean> containsValueAsync(Object value) {
        checkValue(value);

        return commandExecutor.evalWriteAsync(getRawName(), codec, RedisCommands.EVAL_BOOLEAN,
        "local s = redis.call('hgetall', KEYS[1]); " +
                "for i, v in ipairs(s) do " +
                "    if i % 2 == 0 then " +
                "        local t, val = struct.unpack('dLc0', v); " +
                "        if ARGV[2] == val then " +
                "            local key = s[i - 1]; " +

                "            local maxSize = tonumber(redis.call('hget', KEYS[5], 'max-size')); " +
                "            if maxSize ~= nil and maxSize ~= 0 then " +
                "                local mode = redis.call('hget', KEYS[5], 'mode'); " +
                "                if mode == false or mode == 'LRU' then " +
                "                    redis.call('zadd', KEYS[4], tonumber(ARGV[1]), key); " +
                "                else " +
                "                    redis.call('zincrby', KEYS[4], 1, key); " +
                "                end; " +
                "            end; " +

                "            local expireDate = 92233720368547758; " +
                "            local expireDateScore = redis.call('zscore', KEYS[2], key); " +
                "            if expireDateScore ~= false then " +
                "                expireDate = tonumber(expireDateScore) " +
                "            end; " +
                "            if t ~= 0 then " +
                "                local expireIdle = redis.call('zscore', KEYS[3], key); " +
                "                if expireIdle ~= false then " +
                "                    if tonumber(expireIdle) > tonumber(ARGV[1]) then " +
                "                        redis.call('zadd', KEYS[3], t + tonumber(ARGV[1]), key); " +
                "                    end; " +
                "                    expireDate = math.min(expireDate, tonumber(expireIdle)) " +
                "                end; " +
                "            end; " +
                "            if expireDate <= tonumber(ARGV[1]) then " +
                "                return 0; " +
                "            end; " +
                "            return 1; " +
                "        end;" +
                "    end;" +
                "end;" +
                "return 0;",
                Arrays.<Object>asList(getRawName(), timeoutSetName, idleSetName, lastAccessTimeSetName, optionsName),
                System.currentTimeMillis(), encodeMapValue(value));
    }

    @Override
    public RFuture<Map<K, V>> getAllOperationAsync(Set<K> keys) {
        List<Object> args = new ArrayList<>(keys.size() + 1);
        List<Object> plainKeys = new ArrayList<>(keys);

        args.add(System.currentTimeMillis());
        encodeMapKeys(args, keys);

        return commandExecutor.evalWriteAsync(getRawName(), codec, new RedisCommand<Map<Object, Object>>("EVAL",
                        new MapValueDecoder(new MapGetAllDecoder(plainKeys, 0))),
            "local expireHead = redis.call('zrange', KEYS[2], 0, 0, 'withscores'); " +
            "local currentTime = tonumber(table.remove(ARGV, 1)); " + // index is the first parameter
            "local hasExpire = #expireHead == 2 and tonumber(expireHead[2]) <= currentTime; " +
            "local maxSize = tonumber(redis.call('hget', KEYS[5], 'max-size'));" +
            "local map = {}; " +
            "for i = 1, #ARGV, 1 do " +
            "    local value = redis.call('hget', KEYS[1], ARGV[i]); " +
            "    map[i] = false;" +
            "    if value ~= false then " +
            "        local key = ARGV[i]; " +
            "        local t, val = struct.unpack('dLc0', value); " +
            "        map[i] = val; " +
            "        if maxSize ~= nil and maxSize ~= 0 then " +
            "                local mode = redis.call('hget', KEYS[5], 'mode'); " +
            "                if mode == false or mode == 'LRU' then " +
            "                    redis.call('zadd', KEYS[4], currentTime, key); " +
            "                else " +
            "                    redis.call('zincrby', KEYS[4], 1, key); " +
            "                end; " +
            "        end; " +
            "        if hasExpire then " +
            "            local expireDate = redis.call('zscore', KEYS[2], key); " +
            "            if expireDate ~= false and tonumber(expireDate) <= currentTime then " +
            "                map[i] = false; " +
            "            end; " +
            "        end; " +
            "        if t ~= 0 then " +
            "            local expireIdle = redis.call('zscore', KEYS[3], key); " +
            "            if expireIdle ~= false then " +
            "                if tonumber(expireIdle) > currentTime then " +
            "                    redis.call('zadd', KEYS[3], t + currentTime, key); " +
            "                else " +
            "                    map[i] = false; " +
            "                end; " +
            "            end; " +
            "        end; " +
            "    end; " +
            "end; " +
            "return map;",
            Arrays.<Object>asList(getRawName(), timeoutSetName, idleSetName, lastAccessTimeSetName, optionsName),
            args.toArray());
    }

    @Override
    public V putIfAbsent(K key, V value, long ttl, TimeUnit ttlUnit) {
        return get(putIfAbsentAsync(key, value, ttl, ttlUnit));
    }

    @Override
    public RFuture<V> putIfAbsentAsync(K key, V value, long ttl, TimeUnit ttlUnit) {
        return putIfAbsentAsync(key, value, ttl, ttlUnit, 0, null);
    }

    @Override
    public V putIfAbsent(K key, V value, long ttl, TimeUnit ttlUnit, long maxIdleTime, TimeUnit maxIdleUnit) {
        return get(putIfAbsentAsync(key, value, ttl, ttlUnit, maxIdleTime, maxIdleUnit));
    }

    @Override
    public RFuture<V> putIfAbsentAsync(K key, V value, long ttl, TimeUnit ttlUnit, long maxIdleTime, TimeUnit maxIdleUnit) {
        checkKey(key);
        checkValue(value);

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

        String name = getRawName(key);
        RFuture<V> future = commandExecutor.evalWriteAsync(name, codec, RedisCommands.EVAL_MAP_VALUE,
                "local insertable = false; "
                        + "local value = redis.call('hget', KEYS[1], ARGV[5]); "
                            + "if value == false then "
                            + "insertable = true; "
                        + "else "
                            + "local t, val = struct.unpack('dLc0', value); "
                            + "local expireDate = 92233720368547758; "
                            + "local expireDateScore = redis.call('zscore', KEYS[2], ARGV[5]); "
                            + "if expireDateScore ~= false then "
                                + "expireDate = tonumber(expireDateScore) "
                            + "end; "
                            + "if t ~= 0 then "
                                + "local expireIdle = redis.call('zscore', KEYS[3], ARGV[5]); "
                                + "if expireIdle ~= false then "
                                    + "expireDate = math.min(expireDate, tonumber(expireIdle)) "
                                + "end; "
                            + "end; "
                            + "if expireDate <= tonumber(ARGV[1]) then "
                                + "insertable = true; "
                            + "end; "
                        + "end; "

                        + "if insertable == true then "
                            // ttl
                            + "if tonumber(ARGV[2]) > 0 then "
                                + "redis.call('zadd', KEYS[2], ARGV[2], ARGV[5]); "
                            + "else "
                                + "redis.call('zrem', KEYS[2], ARGV[5]); "
                            + "end; "

                            // idle
                            + "if tonumber(ARGV[3]) > 0 then "
                                + "redis.call('zadd', KEYS[3], ARGV[3], ARGV[5]); "
                            + "else "
                                + "redis.call('zrem', KEYS[3], ARGV[5]); "
                            + "end; "

                            // last access time
                            + "local maxSize = tonumber(redis.call('hget', KEYS[7], 'max-size')); " +
                              "local hasListeners = redis.call('hget', KEYS[7], 'has-listeners'); " +
                            "if maxSize ~= nil and maxSize ~= 0 then " +
                            "    local currentTime = tonumber(ARGV[1]); " +
                            "    local lastAccessTimeSetName = KEYS[5]; " +

                                "local mode = redis.call('hget', KEYS[7], 'mode'); " +
                                "if mode == false or mode == 'LRU' then " +
                                    "redis.call('zadd', lastAccessTimeSetName, currentTime, ARGV[5]); " +
                                "end; " +

                            "    local cacheSize = tonumber(redis.call('hlen', KEYS[1])); " +
                            "    if cacheSize >= maxSize then " +
                            "        local lruItems = redis.call('zrange', lastAccessTimeSetName, 0, cacheSize - maxSize); " +
                            "        for index, lruItem in ipairs(lruItems) do " +
                            "            if lruItem and lruItem ~= ARGV[5] then " +
                            "                local lruItemValue = redis.call('hget', KEYS[1], lruItem); " +
                            "                redis.call('hdel', KEYS[1], lruItem); " +
                            "                redis.call('zrem', KEYS[2], lruItem); " +
                            "                redis.call('zrem', KEYS[3], lruItem); " +
                            "                redis.call('zrem', lastAccessTimeSetName, lruItem); " +
                            "                if lruItemValue ~= false and hasListeners ~= false then " +
                                "                local removedChannelName = KEYS[6]; " +
                                                "local ttl, obj = struct.unpack('dLc0', lruItemValue);" +
                            "                    local msg = struct.pack('Lc0Lc0', string.len(lruItem), lruItem, string.len(obj), obj);" +
                                "                redis.call(ARGV[7], removedChannelName, msg); " +
                                             "end; " +
                            "            end; " +
                            "        end; " +
                            "    end; " +

                                "if mode == 'LFU' then " +
                                    "redis.call('zincrby', lastAccessTimeSetName, 1, ARGV[5]); " +
                                "end; " +

                            "end; "

                            // value
                            + "local val = struct.pack('dLc0', tonumber(ARGV[4]), string.len(ARGV[6]), ARGV[6]); "
                            + "redis.call('hset', KEYS[1], ARGV[5], val); "

                            + "if hasListeners ~= false then "
                                + "local msg = struct.pack('Lc0Lc0', string.len(ARGV[5]), ARGV[5], string.len(ARGV[6]), ARGV[6]); "
                                + "redis.call(ARGV[7], KEYS[4], msg); "
                            + "end; "

                            + "return nil; "
                        + "else "
                            + "local t, val = struct.unpack('dLc0', value); "
                            + "redis.call('zadd', KEYS[3], t + ARGV[1], ARGV[5]); "
                            + "return val; "
                        + "end; ",
                Arrays.<Object>asList(name, getTimeoutSetName(name), getIdleSetName(name), getCreatedChannelName(name),
                        getLastAccessTimeSetName(name), getRemovedChannelName(name), getOptionsName(name)),
                System.currentTimeMillis(), ttlTimeout, maxIdleTimeout, maxIdleDelta, encodeMapKey(key), encodeMapValue(value), publishCommand);
        if (hasNoWriter()) {
            return future;
        }
        MapWriterTask.Add task = new MapWriterTask.Add(key, value);
        return mapWriterFuture(future, task, r -> r == null);
    }

    @Override
    protected RFuture<Boolean> removeOperationAsync(Object key, Object value) {
        String name = getRawName(key);
        return commandExecutor.evalWriteAsync(name, codec, RedisCommands.EVAL_BOOLEAN,
                "local value = redis.call('hget', KEYS[1], ARGV[2]); "
                        + "if value == false then "
                            + "return 0; "
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
                                + "expireDate = math.min(expireDate, tonumber(expireIdle)) "
                            + "end; "
                        + "end; "
                        + "if expireDate <= tonumber(ARGV[1]) then "
                            + "return 0; "
                        + "end; "

                        + "local hasListeners = redis.call('hget', KEYS[6], 'has-listeners'); "
                        + "if val == ARGV[3] then "
                            + "redis.call('zrem', KEYS[2], ARGV[2]); "
                            + "redis.call('zrem', KEYS[3], ARGV[2]); "
                            + "local maxSize = tonumber(redis.call('hget', KEYS[6], 'max-size')); " +
                                "if maxSize ~= nil and maxSize ~= 0 then " +
                                "   redis.call('zrem', KEYS[5], ARGV[2]); " +
                                "end; "
                            + "redis.call('hdel', KEYS[1], ARGV[2]); "

                            + "if hasListeners ~= false then "
                                + "local msg = struct.pack('Lc0Lc0', string.len(ARGV[2]), ARGV[2], string.len(val), val); "
                                + "redis.call(ARGV[4], KEYS[4], msg); "
                            + "end; "
                            + "return 1; "
                        + "else "
                            + "return 0; "
                        + "end",
                Arrays.asList(name, getTimeoutSetName(name), getIdleSetName(name), getRemovedChannelName(name),
                        getLastAccessTimeSetName(name), getOptionsName(name)),
                System.currentTimeMillis(), encodeMapKey(key), encodeMapValue(value), publishCommand);
    }

    @Override
    public RFuture<V> getOperationAsync(K key) {
        String name = getRawName(key);
        return commandExecutor.evalWriteAsync(name, codec, RedisCommands.EVAL_MAP_VALUE,
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
                                    + "redis.call('zadd', KEYS[3], t + tonumber(ARGV[1]), ARGV[2]); "
                                + "end; "
                                + "expireDate = math.min(expireDate, tonumber(expireIdle)) "
                            + "end; "
                        + "end; "
                        + "if expireDate <= tonumber(ARGV[1]) then "
                            + "return nil; "
                        + "end; "
                        + "local maxSize = tonumber(redis.call('hget', KEYS[5], 'max-size')); " +
                        "if maxSize ~= nil and maxSize ~= 0 then " +
                            "local mode = redis.call('hget', KEYS[5], 'mode'); " +
                            "if mode == false or mode == 'LRU' then " +
                                "redis.call('zadd', KEYS[4], tonumber(ARGV[1]), ARGV[2]); " +
                            "else " +
                                "redis.call('zincrby', KEYS[4], 1, ARGV[2]); " +
                            "end; " +
                        "end; "
                        + "return val; ",
                Arrays.asList(name, getTimeoutSetName(name), getIdleSetName(name), getLastAccessTimeSetName(name), getOptionsName(name)),
                System.currentTimeMillis(), encodeMapKey(key));
    }

    @Override
    public V put(K key, V value, long ttl, TimeUnit unit) {
        return get(putAsync(key, value, ttl, unit));
    }

    @Override
    protected RFuture<V> putOperationAsync(K key, V value) {
        String name = getRawName(key);
        return commandExecutor.evalWriteAsync(name, codec, RedisCommands.EVAL_MAP_VALUE,
                "local v = redis.call('hget', KEYS[1], ARGV[2]);" +
                "local exists = false;" +
                "if v ~= false then" +
                "    local t, val = struct.unpack('dLc0', v);" +
                "    local expireDate = 92233720368547758;" +
                "    local expireDateScore = redis.call('zscore', KEYS[2], ARGV[2]);" +
                "    if expireDateScore ~= false then" +
                "        expireDate = tonumber(expireDateScore)" +
                "    end;" +
                "    if t ~= 0 then" +
                "        local expireIdle = redis.call('zscore', KEYS[3], ARGV[2]);" +
                "        if expireIdle ~= false then" +
                "            expireDate = math.min(expireDate, tonumber(expireIdle))" +
                "        end;" +
                "    end;" +
                "    if expireDate > tonumber(ARGV[1]) then" +
                "        exists = true;" +
                "    end;" +
                "end;" +

                "redis.call('zrem', KEYS[2], ARGV[2]); " +
                "redis.call('zrem', KEYS[3], ARGV[2]); " +

                "local value = struct.pack('dLc0', 0, string.len(ARGV[3]), ARGV[3]);" +
                "redis.call('hset', KEYS[1], ARGV[2], value);" +
                "local currentTime = tonumber(ARGV[1]);" +
                "local lastAccessTimeSetName = KEYS[6];" +
                "local maxSize = tonumber(redis.call('hget', KEYS[8], 'max-size'));" +
                "local mode = redis.call('hget', KEYS[8], 'mode'); " +
                "local hasListeners = redis.call('hget', KEYS[8], 'has-listeners'); " +
                "if exists == false then" +
                "    if maxSize ~= nil and maxSize ~= 0 then " +
                        "if mode == false or mode == 'LRU' then " +
                            "redis.call('zadd', lastAccessTimeSetName, currentTime, ARGV[2]); " +
                        "end; " +
                "        local cacheSize = tonumber(redis.call('hlen', KEYS[1]));" +
                "        if cacheSize > maxSize then" +
                "            local lruItems = redis.call('zrange', lastAccessTimeSetName, 0, cacheSize - maxSize - 1);" +
                "            for index, lruItem in ipairs(lruItems) do" +
                "                if lruItem and lruItem ~= ARGV[2] then" +
                "                    local lruItemValue = redis.call('hget', KEYS[1], lruItem);" +
                "                    redis.call('hdel', KEYS[1], lruItem);" +
                "                    redis.call('zrem', KEYS[2], lruItem);" +
                "                    redis.call('zrem', KEYS[3], lruItem);" +
                "                    redis.call('zrem', lastAccessTimeSetName, lruItem);" +
                "                    if lruItemValue ~= false and hasListeners ~= false then " +
                    "                    local removedChannelName = KEYS[7];" +
                                        "local ttl, obj = struct.unpack('dLc0', lruItemValue);" +
                    "                    local msg = struct.pack('Lc0Lc0', string.len(lruItem), lruItem, string.len(obj), obj);" +
                    "                    redis.call(ARGV[4], removedChannelName, msg);" +
                                    "end; " +
                "                end;" +
                "            end" +
                "        end;" +
                        "if mode == 'LFU' then " +
                            "redis.call('zincrby', lastAccessTimeSetName, 1, ARGV[2]); " +
                        "end; " +
                "    end;" +
                    "if hasListeners ~= false then " +
                       "local msg = struct.pack('Lc0Lc0', string.len(ARGV[2]), ARGV[2], string.len(ARGV[3]), ARGV[3]);" +
                       "redis.call(ARGV[4], KEYS[4], msg);" +
                    "end; " +
                "    return nil;" +
                "else" +
                "    if maxSize ~= nil and maxSize ~= 0 then " +
                        "if mode == false or mode == 'LRU' then " +
                            "redis.call('zadd', lastAccessTimeSetName, currentTime, ARGV[2]); " +
                        "else " +
                            "redis.call('zincrby', lastAccessTimeSetName, 1, ARGV[2]); " +
                        "end; " +
                "    end;" +
                "end;" +
                "" +
                "local t, val = struct.unpack('dLc0', v);" +
                "if hasListeners ~= false then " +
                    "local msg = struct.pack('Lc0Lc0Lc0', string.len(ARGV[2]), ARGV[2], string.len(ARGV[3]), ARGV[3], string.len(val), val);" +
                    "redis.call(ARGV[4], KEYS[5], msg);" +
                "end; " +
                "return val;",
                Arrays.asList(name, getTimeoutSetName(name), getIdleSetName(name), getCreatedChannelName(name),
                        getUpdatedChannelName(name), getLastAccessTimeSetName(name), getRemovedChannelName(name), getOptionsName(name)),
                System.currentTimeMillis(), encodeMapKey(key), encodeMapValue(value), publishCommand);
    }

    @Override
    protected RFuture<V> putIfExistsOperationAsync(K key, V value) {
        String name = getRawName(key);
        return commandExecutor.evalWriteAsync(name, codec, RedisCommands.EVAL_MAP_VALUE,
                    "local value = redis.call('hget', KEYS[1], ARGV[2]); "
                        + "if value == false then "
                            + "return nil;"
                        + "end; "
                        + "local maxSize = tonumber(redis.call('hget', KEYS[7], 'max-size'));"
                        + "local lastAccessTimeSetName = KEYS[5]; "
                        + "local currentTime = tonumber(ARGV[1]); "
                        + "local t, val;"
                        + "if value ~= false then "
                            + "t, val = struct.unpack('dLc0', value); "
                            + "local expireDate = 92233720368547758; "
                            + "local expireDateScore = redis.call('zscore', KEYS[2], ARGV[2]); "
                            + "if expireDateScore ~= false then "
                                + "expireDate = tonumber(expireDateScore) "
                            + "end; "
                            + "if t ~= 0 then "
                                + "local expireIdle = redis.call('zscore', KEYS[3], ARGV[2]); "
                                + "if expireIdle ~= false then "
                                    + "expireDate = math.min(expireDate, tonumber(expireIdle)) "
                                + "end; "
                            + "end; "
                            + "if expireDate > tonumber(ARGV[1]) then "
                                + "if maxSize ~= nil and maxSize ~= 0 then " +
                                    "local mode = redis.call('hget', KEYS[7], 'mode'); " +
                                    "if mode == false or mode == 'LRU' then " +
                                        "redis.call('zadd', lastAccessTimeSetName, currentTime, ARGV[2]); " +
                                    "else " +
                                        "redis.call('zincrby', lastAccessTimeSetName, 1, ARGV[2]); " +
                                    "end; "
                                + "end; "
                            + "else "
                                + "return nil; "
                            + "end; "
                        + "end; "

                        + "local newValue = struct.pack('dLc0', 0, string.len(ARGV[3]), ARGV[3]); "
                        + "redis.call('hset', KEYS[1], ARGV[2], newValue); "

                        + "local hasListeners = redis.call('hget', KEYS[7], 'has-listeners'); "
                        // last access time
                        + "if maxSize ~= nil and maxSize ~= 0 then " +
                            "local mode = redis.call('hget', KEYS[7], 'mode'); " +

                            "if mode == false or mode == 'LRU' then " +
                                "redis.call('zadd', lastAccessTimeSetName, currentTime, ARGV[2]); " +
                            "end; " +

                        "    local cacheSize = tonumber(redis.call('hlen', KEYS[1])); " +
                        "    if cacheSize > maxSize then " +
                        "        local lruItems = redis.call('zrange', lastAccessTimeSetName, 0, cacheSize - maxSize - 1); " +
                        "        for index, lruItem in ipairs(lruItems) do " +
                        "            if lruItem and lruItem ~= ARGV[2] then " +
                        "                local lruItemValue = redis.call('hget', KEYS[1], lruItem); " +
                        "                redis.call('hdel', KEYS[1], lruItem); " +
                        "                redis.call('zrem', KEYS[2], lruItem); " +
                        "                redis.call('zrem', KEYS[3], lruItem); " +
                        "                redis.call('zrem', lastAccessTimeSetName, lruItem); " +
                        "                if lruItemValue ~= false and hasListeners ~= false then " +
                            "                local removedChannelName = KEYS[6]; " +
                                            "local ttl, obj = struct.unpack('dLc0', lruItemValue);" +
                            "                local msg = struct.pack('Lc0Lc0', string.len(lruItem), lruItem, string.len(obj), obj);" +
                            "                redis.call(ARGV[4], removedChannelName, msg); " +
                                        "end; " +
                        "            end; " +
                        "        end; " +
                        "    end; " +

                            "if mode == 'LFU' then " +
                                "redis.call('zincrby', lastAccessTimeSetName, 1, ARGV[2]); " +
                            "end; " +

                        "end; "

                        + "if hasListeners ~= false then "
                            + "local msg = struct.pack('Lc0Lc0Lc0', string.len(ARGV[2]), ARGV[2], string.len(ARGV[3]), ARGV[3], string.len(val), val); "
                            + "redis.call(ARGV[4], KEYS[4], msg); "
                        + "end; "
                        + "return val;",
                Arrays.<Object>asList(name, getTimeoutSetName(name), getIdleSetName(name), getUpdatedChannelName(name),
                        getLastAccessTimeSetName(name), getRemovedChannelName(name), getOptionsName(name)),
                System.currentTimeMillis(), encodeMapKey(key), encodeMapValue(value), publishCommand);
    }

    @Override
    protected RFuture<V> putIfAbsentOperationAsync(K key, V value) {
        String name = getRawName(key);
        return commandExecutor.evalWriteAsync(name, codec, RedisCommands.EVAL_MAP_VALUE,
                "local value = redis.call('hget', KEYS[1], ARGV[2]); "
                        + "local maxSize = tonumber(redis.call('hget', KEYS[7], 'max-size'));"
                        + "local lastAccessTimeSetName = KEYS[5]; "
                        + "local currentTime = tonumber(ARGV[1]); "
                        + "if value ~= false then "
                            + "local t, val = struct.unpack('dLc0', value); "
                            + "local expireDate = 92233720368547758; "
                            + "local expireDateScore = redis.call('zscore', KEYS[2], ARGV[2]); "
                            + "if expireDateScore ~= false then "
                                + "expireDate = tonumber(expireDateScore) "
                            + "end; "
                            + "if t ~= 0 then "
                                + "local expireIdle = redis.call('zscore', KEYS[3], ARGV[2]); "
                                + "if expireIdle ~= false then "
                                    + "expireDate = math.min(expireDate, tonumber(expireIdle)) "
                                + "end; "
                            + "end; "
                            + "if expireDate > tonumber(ARGV[1]) then "
                                + "if maxSize ~= nil and maxSize ~= 0 then " +
                                    "local mode = redis.call('hget', KEYS[7], 'mode'); " +
                                    "if mode == false or mode == 'LRU' then " +
                                        "redis.call('zadd', lastAccessTimeSetName, currentTime, ARGV[2]); " +
                                    "else " +
                                        "redis.call('zincrby', lastAccessTimeSetName, 1, ARGV[2]); " +
                                    "end; "
                                + "end; "
                                + "return val; "
                            + "end; "
                        + "end; "

                        + "local value = struct.pack('dLc0', 0, string.len(ARGV[3]), ARGV[3]); "
                        + "redis.call('hset', KEYS[1], ARGV[2], value); "

                        + "local hasListeners = redis.call('hget', KEYS[7], 'has-listeners'); "
                        // last access time
                        + "if maxSize ~= nil and maxSize ~= 0 then " +
                            "local mode = redis.call('hget', KEYS[7], 'mode'); " +

                            "if mode == false or mode == 'LRU' then " +
                                "redis.call('zadd', lastAccessTimeSetName, currentTime, ARGV[2]); " +
                            "end; " +

                        "    local cacheSize = tonumber(redis.call('hlen', KEYS[1])); " +
                        "    if cacheSize > maxSize then " +
                        "        local lruItems = redis.call('zrange', lastAccessTimeSetName, 0, cacheSize - maxSize - 1); " +
                        "        for index, lruItem in ipairs(lruItems) do " +
                        "            if lruItem and lruItem ~= ARGV[2] then " +
                        "                local lruItemValue = redis.call('hget', KEYS[1], lruItem); " +
                        "                redis.call('hdel', KEYS[1], lruItem); " +
                        "                redis.call('zrem', KEYS[2], lruItem); " +
                        "                redis.call('zrem', KEYS[3], lruItem); " +
                        "                redis.call('zrem', lastAccessTimeSetName, lruItem); " +
                        "                if lruItemValue ~= false and hasListeners ~= false then " +
                            "                local removedChannelName = KEYS[6]; " +
                                            "local ttl, obj = struct.unpack('dLc0', lruItemValue);" +
                            "                local msg = struct.pack('Lc0Lc0', string.len(lruItem), lruItem, string.len(obj), obj);" +
                            "                redis.call(ARGV[4], removedChannelName, msg); " +
                                        "end; " +
                        "            end; " +
                        "        end; " +
                        "    end; " +

                            "if mode == 'LFU' then " +
                                "redis.call('zincrby', lastAccessTimeSetName, 1, ARGV[2]); " +
                            "end; " +

                        "end; "

                        + "if hasListeners ~= false then "
                            + "local msg = struct.pack('Lc0Lc0', string.len(ARGV[2]), ARGV[2], string.len(ARGV[3]), ARGV[3]); "
                            + "redis.call(ARGV[4], KEYS[4], msg); "
                        + "end; "
                        + "return nil;",
                Arrays.<Object>asList(name, getTimeoutSetName(name), getIdleSetName(name), getCreatedChannelName(name),
                        getLastAccessTimeSetName(name), getRemovedChannelName(name), getOptionsName(name)),
                System.currentTimeMillis(), encodeMapKey(key), encodeMapValue(value), publishCommand);
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> map, long ttl, TimeUnit ttlUnit) {
        get(putAllAsync(map, ttl, ttlUnit));
    }

    @Override
    public RFuture<Void> putAllAsync(Map<? extends K, ? extends V> map, long ttl, TimeUnit ttlUnit) {
        if (map.isEmpty()) {
            return new CompletableFutureWrapper<>((Void) null);
        }

        RFuture<Void> future = putAllOperationAsync(map, ttl, ttlUnit);
        if (hasNoWriter()) {
            return future;
        }

        MapWriterTask listener = new MapWriterTask.Add(map);
        return mapWriterFuture(future, listener);
    }

    @Override
    public V addAndGet(K key, Number value) {
        return get(addAndGetAsync(key, value));
    }

    @Override
    protected RFuture<V> addAndGetOperationAsync(K key, Number value) {
        ByteBuf keyState = encodeMapKey(key);
        String name = getRawName(key);
        return commandExecutor.evalWriteAsync(name, StringCodec.INSTANCE,
                new RedisCommand<Object>("EVAL", new NumberConvertor(value.getClass())),
                "local value = redis.call('hget', KEYS[1], ARGV[2]); "
                        + "local expireDate = 92233720368547758; "
                        + "local t = 0; "
                        + "local val = 0; "
                        + "if value ~= false then "
                            + "t, val = struct.unpack('dLc0', value); "
                            + "local expireDateScore = redis.call('zscore', KEYS[2], ARGV[2]); "
                            + "if expireDateScore ~= false then "
                                + "expireDate = tonumber(expireDateScore) "
                            + "end; "
                            + "if t ~= 0 then "
                                + "local expireIdle = redis.call('zscore', KEYS[3], ARGV[2]); "
                                + "if expireIdle ~= false then "
                                    + "if tonumber(expireIdle) > tonumber(ARGV[1]) then "
                                        + "redis.call('zadd', KEYS[3], t + tonumber(ARGV[1]), ARGV[2]); "
                                    + "end; "
                                    + "expireDate = math.min(expireDate, tonumber(expireIdle)) "
                                + "end; "
                            + "end; "
                        + "end; "

                        + "local hasListeners = redis.call('hget', KEYS[8], 'has-listeners'); "
                        + "local newValue; "
                        + "if value ~= false and expireDate > tonumber(ARGV[1]) then "
                            + "redis.call('hset', KEYS[1], 'temp_val__redisson', val); "
                            + "newValue = redis.call('hincrbyfloat', KEYS[1], 'temp_val__redisson', ARGV[3]); "
                            + "redis.call('hdel', KEYS[1], 'temp_val__redisson'); "

                            + "if hasListeners ~= false then "
                                + "local msg = struct.pack('Lc0Lc0Lc0', string.len(ARGV[2]), ARGV[2], string.len(newValue), newValue, string.len(val), val); "
                                + "redis.call(ARGV[4], KEYS[5], msg); "
                            + "end;"
                        + "else "
                            + "newValue = ARGV[3]; "
                            + "if hasListeners ~= false then "
                                + "local msg = struct.pack('Lc0Lc0', string.len(ARGV[2]), ARGV[2], string.len(ARGV[3]), ARGV[3]); "
                                + "redis.call(ARGV[4], KEYS[4], msg); "
                            + "end;"
                        + "end; "
                        + "local newValuePack = struct.pack('dLc0', t, string.len(newValue), newValue); "
                        + "redis.call('hset', KEYS[1], ARGV[2], newValuePack); "

                        // last access time
                        + "local maxSize = tonumber(redis.call('hget', KEYS[8], 'max-size')); " +
                        "if maxSize ~= nil and maxSize ~= 0 then " +
                        "    local currentTime = tonumber(ARGV[1]); " +
                        "    local lastAccessTimeSetName = KEYS[6]; " +

                            "local mode = redis.call('hget', KEYS[8], 'mode'); " +

                            "if mode == false or mode == 'LRU' then " +
                                "redis.call('zadd', lastAccessTimeSetName, currentTime, ARGV[2]); " +
                            "end; " +

                        "    local cacheSize = tonumber(redis.call('hlen', KEYS[1])); " +
                        "    if cacheSize > maxSize then " +
                        "        local lruItems = redis.call('zrange', lastAccessTimeSetName, 0, cacheSize - maxSize - 1); " +
                        "        for index, lruItem in ipairs(lruItems) do " +
                        "            if lruItem and lruItem ~= ARGV[2] then " +
                        "                local lruItemValue = redis.call('hget', KEYS[1], lruItem); " +
                        "                redis.call('hdel', KEYS[1], lruItem); " +
                        "                redis.call('zrem', KEYS[2], lruItem); " +
                        "                redis.call('zrem', KEYS[3], lruItem); " +
                        "                redis.call('zrem', lastAccessTimeSetName, lruItem); " +
                        "                if lruItemValue ~= false and hasListeners ~= false then " +
                            "                local removedChannelName = KEYS[7]; " +
                                            "local ttl, obj = struct.unpack('dLc0', lruItemValue);" +
                            "                local msg = struct.pack('Lc0Lc0', string.len(lruItem), lruItem, string.len(obj), obj);" +
                            "                redis.call(ARGV[4], removedChannelName, msg); " +
                                        "end; " +
                        "            end; " +
                        "        end; " +
                        "    end; " +

                            "if mode == 'LFU' then " +
                                "redis.call('zincrby', lastAccessTimeSetName, 1, ARGV[2]); " +
                            "end; " +

                        "end; "

                      + "return newValue;",
                Arrays.<Object>asList(name, getTimeoutSetName(name), getIdleSetName(name), getCreatedChannelName(name),
                        getUpdatedChannelName(name), getLastAccessTimeSetName(name), getRemovedChannelName(name), getOptionsName(name)),
                System.currentTimeMillis(), keyState, new BigDecimal(value.toString()).toPlainString(), publishCommand);
    }

    @Override
    public boolean fastPut(K key, V value, long ttl, TimeUnit ttlUnit) {
        return get(fastPutAsync(key, value, ttl, ttlUnit));
    }

    @Override
    public RFuture<Boolean> fastPutAsync(K key, V value, long ttl, TimeUnit ttlUnit) {
        return fastPutAsync(key, value, ttl, ttlUnit, 0, null);
    }

    @Override
    public boolean fastPut(K key, V value, long ttl, TimeUnit ttlUnit, long maxIdleTime, TimeUnit maxIdleUnit) {
        return get(fastPutAsync(key, value, ttl, ttlUnit, maxIdleTime, maxIdleUnit));
    }

    @Override
    public RFuture<Boolean> fastPutAsync(K key, V value, long ttl, TimeUnit ttlUnit, long maxIdleTime, TimeUnit maxIdleUnit) {
        checkKey(key);
        checkValue(value);

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

        RFuture<Boolean> future = fastPutOperationAsync(key, value, ttl, ttlUnit, maxIdleTime, maxIdleUnit);

        if (hasNoWriter()) {
            return future;
        }

        return mapWriterFuture(future, new MapWriterTask.Add(key, value));
    }

    protected RFuture<Boolean> fastPutOperationAsync(K key, V value, long ttl, TimeUnit ttlUnit, long maxIdleTime, TimeUnit maxIdleUnit) {
        long currentTime = System.currentTimeMillis();
        long ttlTimeout = 0;
        if (ttl > 0) {
            ttlTimeout = currentTime + ttlUnit.toMillis(ttl);
        }

        long maxIdleTimeout = 0;
        long maxIdleDelta = 0;
        if (maxIdleTime > 0) {
            maxIdleDelta = maxIdleUnit.toMillis(maxIdleTime);
            maxIdleTimeout = currentTime + maxIdleDelta;
        }

        String name = getRawName(key);
        RFuture<Boolean> future = commandExecutor.evalWriteAsync(name, codec, RedisCommands.EVAL_BOOLEAN,
                "local insertable = false; "
                        + "local value = redis.call('hget', KEYS[1], ARGV[5]); "
                        + "local t, val;"
                        + "if value == false then "
                            + "insertable = true; "
                        + "else "
                            + "t, val = struct.unpack('dLc0', value); "
                            + "local expireDate = 92233720368547758; "
                            + "local expireDateScore = redis.call('zscore', KEYS[2], ARGV[5]); "
                            + "if expireDateScore ~= false then "
                                + "expireDate = tonumber(expireDateScore) "
                            + "end; "
                            + "if t ~= 0 then "
                                + "local expireIdle = redis.call('zscore', KEYS[3], ARGV[5]); "
                                + "if expireIdle ~= false then "
                                    + "expireDate = math.min(expireDate, tonumber(expireIdle)) "
                                + "end; "
                            + "end; "
                            + "if expireDate <= tonumber(ARGV[1]) then "
                                + "insertable = true; "
                            + "end; "
                        + "end; " +

                        "if tonumber(ARGV[2]) > 0 then "
                            + "redis.call('zadd', KEYS[2], ARGV[2], ARGV[5]); "
                        + "else "
                            + "redis.call('zrem', KEYS[2], ARGV[5]); "
                        + "end; "
                        + "if tonumber(ARGV[3]) > 0 then "
                            + "redis.call('zadd', KEYS[3], ARGV[3], ARGV[5]); "
                        + "else "
                            + "redis.call('zrem', KEYS[3], ARGV[5]); "
                        + "end; " +

                        "local hasListeners = redis.call('hget', KEYS[8], 'has-listeners'); " +

                        // last access time
                        "local maxSize = tonumber(redis.call('hget', KEYS[8], 'max-size')); " +
                        "local mode = redis.call('hget', KEYS[8], 'mode'); " +
                        "if maxSize ~= nil and maxSize ~= 0 then " +
                        "    local currentTime = tonumber(ARGV[1]); " +
                        "    local lastAccessTimeSetName = KEYS[6]; " +

                           "if mode == false or mode == 'LRU' then " +
                               "redis.call('zadd', lastAccessTimeSetName, currentTime, ARGV[5]); " +
                           "end; " +

                        "    local cacheSize = tonumber(redis.call('hlen', KEYS[1])); " +
                        "    if cacheSize >= maxSize then " +
                        "        local lruItems = redis.call('zrange', lastAccessTimeSetName, 0, cacheSize - maxSize); " +
                        "        for index, lruItem in ipairs(lruItems) do " +
                        "            if lruItem and lruItem ~= ARGV[5] then " +
                        "                local lruItemValue = redis.call('hget', KEYS[1], lruItem); " +
                        "                redis.call('hdel', KEYS[1], lruItem); " +
                        "                redis.call('zrem', KEYS[2], lruItem); " +
                        "                redis.call('zrem', KEYS[3], lruItem); " +
                        "                redis.call('zrem', lastAccessTimeSetName, lruItem); " +
                        "                if lruItemValue ~= false and hasListeners ~= false then " +
                            "                local removedChannelName = KEYS[7]; " +
                                            "local ttl, obj = struct.unpack('dLc0', lruItemValue);" +
                            "                local msg = struct.pack('Lc0Lc0', string.len(lruItem), lruItem, string.len(obj), obj);" +
                            "                redis.call(ARGV[7], removedChannelName, msg); " +
                                        "end; " +
                        "            end; " +
                        "        end; " +
                        "    end; " +

                            "if mode == 'LFU' then " +
                                 "redis.call('zincrby', lastAccessTimeSetName, 1, ARGV[5]); " +
                            "end; " +
                        "end; "

                        + "local value = struct.pack('dLc0', ARGV[4], string.len(ARGV[6]), ARGV[6]); "
                        + "redis.call('hset', KEYS[1], ARGV[5], value); "
                        + "if insertable == true then "
                            + "if hasListeners ~= false then "
                                + "local msg = struct.pack('Lc0Lc0', string.len(ARGV[5]), ARGV[5], string.len(ARGV[6]), ARGV[6]); "
                                + "redis.call(ARGV[7], KEYS[4], msg); "
                            + "end; "
                            + "return 1;"
                        + "else "
                            + "if hasListeners ~= false then "
                                + "local msg = struct.pack('Lc0Lc0Lc0', string.len(ARGV[5]), ARGV[5], string.len(ARGV[6]), ARGV[6], string.len(val), val); "
                                + "redis.call(ARGV[7], KEYS[5], msg); "
                            + "end; "
                            + "return 0;"
                        + "end;",
                Arrays.<Object>asList(name, getTimeoutSetName(name), getIdleSetName(name), getCreatedChannelName(name),
                        getUpdatedChannelName(name), getLastAccessTimeSetName(name), getRemovedChannelName(name), getOptionsName(name)),
                System.currentTimeMillis(), ttlTimeout, maxIdleTimeout, maxIdleDelta, encodeMapKey(key), encodeMapValue(value), publishCommand);
        return future;
    }

    @Override
    public boolean updateEntryExpiration(K key, long ttl, TimeUnit ttlUnit, long maxIdleTime, TimeUnit maxIdleUnit) {
        return get(updateEntryExpirationAsync(key, ttl, ttlUnit, maxIdleTime, maxIdleUnit));
    }

    @Override
    public RFuture<Boolean> updateEntryExpirationAsync(K key, long ttl, TimeUnit ttlUnit, long maxIdleTime, TimeUnit maxIdleUnit) {
        Duration ttld = null;
        if (ttl > 0) {
            ttld = Duration.ofMillis(ttlUnit.toMillis(ttl));
        }
        Duration idled = null;
        if (maxIdleTime > 0) {
            idled = Duration.ofMillis(maxIdleUnit.toMillis(maxIdleTime));
        }
        return expireEntryAsync(key, ttld, idled);
    }

    @Override
    public boolean expireEntry(K key, Duration ttl, Duration maxIdleTime) {
        return get(expireEntryAsync(key, ttl, maxIdleTime));
    }

    @Override
    public RFuture<Boolean> expireEntryAsync(K key, Duration ttl, Duration maxIdleTime) {
        checkKey(key);

        long currentTime = System.currentTimeMillis();
        long ttlTimeout = 0;
        if (ttl != null && !ttl.isZero()) {
            ttlTimeout = currentTime + ttl.toMillis();
        }

        long maxIdleTimeout = 0;
        long maxIdleDelta = 0;
        if (maxIdleTime != null && !maxIdleTime.isZero()) {
            maxIdleDelta = maxIdleTime.toMillis();
            maxIdleTimeout = currentTime + maxIdleDelta;
        }

        String name = getRawName(key);
        RFuture<Boolean> future = commandExecutor.evalWriteAsync(name, codec, RedisCommands.EVAL_BOOLEAN,
                        "local value = redis.call('hget', KEYS[1], ARGV[5]); "
                        + "local t, val;"
                        + "if value == false then "
                            + "return 0; "
                        + "else "
                            + "t, val = struct.unpack('dLc0', value); "
                            + "local expireDate = 92233720368547758; "
                            + "local expireDateScore = redis.call('zscore', KEYS[2], ARGV[5]); "
                            + "if expireDateScore ~= false then "
                                + "expireDate = tonumber(expireDateScore) "
                            + "end; "
                            + "if t ~= 0 then "
                                + "local expireIdle = redis.call('zscore', KEYS[3], ARGV[5]); "
                                + "if expireIdle ~= false then "
                                    + "expireDate = math.min(expireDate, tonumber(expireIdle)) "
                                + "end; "
                            + "end; "
                            + "if expireDate <= tonumber(ARGV[1]) then "
                                + "return 0; "
                            + "end; "
                        + "end; " +

                        "if tonumber(ARGV[2]) > 0 then "
                            + "redis.call('zadd', KEYS[2], ARGV[2], ARGV[5]); "
                        + "else "
                            + "redis.call('zrem', KEYS[2], ARGV[5]); "
                        + "end; "
                        + "if tonumber(ARGV[3]) > 0 then "
                            + "redis.call('zadd', KEYS[3], ARGV[3], ARGV[5]); "
                        + "else "
                            + "redis.call('zrem', KEYS[3], ARGV[5]); "
                        + "end; " +

                        // last access time
                        "local maxSize = tonumber(redis.call('hget', KEYS[5], 'max-size')); " +
                        "local mode = redis.call('hget', KEYS[5], 'mode'); " +
                        "if maxSize ~= nil and maxSize ~= 0 then " +
                           "local currentTime = tonumber(ARGV[1]); " +

                           "if mode == false or mode == 'LRU' then " +
                               "redis.call('zadd', KEYS[4], currentTime, ARGV[5]); " +
                           "end; " +
                           "if mode == 'LFU' then " +
                                "redis.call('zincrby', KEYS[4], 1, ARGV[5]); " +
                           "end; " +
                        "end; " +

                        "local value = struct.pack('dLc0', ARGV[4], string.len(val), val); " +
                        "redis.call('hset', KEYS[1], ARGV[5], value); " +

                        "return 1;",
                Arrays.asList(name, getTimeoutSetName(name), getIdleSetName(name),
                              getLastAccessTimeSetName(name), getOptionsName(name)),
                System.currentTimeMillis(), ttlTimeout, maxIdleTimeout, maxIdleDelta, encodeMapKey(key));
        return future;
    }

    public boolean expireEntryIfNotSet(K key, Duration ttl, Duration maxIdleTime) {
        return get(expireEntryIfNotSetAsync(key, ttl, maxIdleTime));
    }

    public RFuture<Boolean> expireEntryIfNotSetAsync(K key, Duration ttl, Duration maxIdleTime) {
        checkKey(key);

        long currentTime = System.currentTimeMillis();
        long ttlTimeout = 0;
        if (ttl != null && !ttl.isZero()) {
            ttlTimeout = currentTime + ttl.toMillis();
        }

        long maxIdleTimeout = 0;
        long maxIdleDelta = 0;
        if (maxIdleTime != null && !maxIdleTime.isZero()) {
            maxIdleDelta = maxIdleTime.toMillis();
            maxIdleTimeout = currentTime + maxIdleDelta;
        }

        String name = getRawName(key);
        RFuture<Boolean> future = commandExecutor.evalWriteAsync(name, codec, RedisCommands.EVAL_BOOLEAN,
                        "local value = redis.call('hget', KEYS[1], ARGV[5]); "
                        + "local t, val;"
                        + "if value == false then "
                            + "return 0; "
                        + "else "
                            + "t, val = struct.unpack('dLc0', value); "
                            + "local expireDate = 92233720368547758; "
                            + "local expireDateScore = redis.call('zscore', KEYS[2], ARGV[5]); "
                            + "if expireDateScore ~= false then "
                                + "expireDate = tonumber(expireDateScore) "
                            + "end; "
                            + "if t ~= 0 then "
                                + "local expireIdle = redis.call('zscore', KEYS[3], ARGV[5]); "
                                + "if expireIdle ~= false then "
                                    + "expireDate = math.min(expireDate, tonumber(expireIdle)) "
                                + "end; "
                            + "end; "
                            + "if expireDate ~= 92233720368547758 then "
                                + "return 0; "
                            + "end; "
                        + "end; " +

                        "if tonumber(ARGV[2]) > 0 then "
                            + "redis.call('zadd', KEYS[2], ARGV[2], ARGV[5]); "
                        + "else "
                            + "redis.call('zrem', KEYS[2], ARGV[5]); "
                        + "end; "
                        + "if tonumber(ARGV[3]) > 0 then "
                            + "redis.call('zadd', KEYS[3], ARGV[3], ARGV[5]); "
                        + "else "
                            + "redis.call('zrem', KEYS[3], ARGV[5]); "
                        + "end; " +

                        // last access time
                        "local maxSize = tonumber(redis.call('hget', KEYS[5], 'max-size')); " +
                        "local mode = redis.call('hget', KEYS[5], 'mode'); " +
                        "if maxSize ~= nil and maxSize ~= 0 then " +
                           "local currentTime = tonumber(ARGV[1]); " +

                           "if mode == false or mode == 'LRU' then " +
                               "redis.call('zadd', KEYS[4], currentTime, ARGV[5]); " +
                           "end; " +
                           "if mode == 'LFU' then " +
                                "redis.call('zincrby', KEYS[4], 1, ARGV[5]); " +
                           "end; " +
                        "end; " +

                        "local value = struct.pack('dLc0', ARGV[4], string.len(val), val); " +
                        "redis.call('hset', KEYS[1], ARGV[5], value); " +

                        "return 1;",
                Arrays.asList(name, getTimeoutSetName(name), getIdleSetName(name),
                              getLastAccessTimeSetName(name), getOptionsName(name)),
                System.currentTimeMillis(), ttlTimeout, maxIdleTimeout, maxIdleDelta, encodeMapKey(key));
        return future;
    }

    public int expireEntriesIfNotSet(Set<K> keys, Duration ttl, Duration maxIdleTime) {
        return get(expireEntriesIfNotSetAsync(keys, ttl, maxIdleTime));
    }

    public RFuture<Integer> expireEntriesIfNotSetAsync(Set<K> keys, Duration ttl, Duration maxIdleTime) {
        for (K key : keys) {
            checkKey(key);
        }

        long currentTime = System.currentTimeMillis();
        long ttlTimeout = 0;
        if (ttl != null && !ttl.isZero()) {
            ttlTimeout = currentTime + ttl.toMillis();
        }

        long maxIdleTimeout = 0;
        long maxIdleDelta = 0;
        if (maxIdleTime != null && !maxIdleTime.isZero()) {
            maxIdleDelta = maxIdleTime.toMillis();
            maxIdleTimeout = currentTime + maxIdleDelta;
        }

        List<Object> args = new ArrayList<>(keys.size() + 3);
        args.add(System.currentTimeMillis());
        args.add(ttlTimeout);
        args.add(maxIdleTimeout);
        args.add(maxIdleDelta);
        encodeMapKeys(args, keys);

        RFuture<Integer> future = commandExecutor.evalWriteAsync(getRawName(), IntegerCodec.INSTANCE, RedisCommands.EVAL_INTEGER,
          "local counter = 0;" +
                "for i = 5, #ARGV, 1 do " +
                        "local value = redis.call('hget', KEYS[1], ARGV[i]); "
                        + "local t, val;"
                        + "if value == false then "
                        + "else "
                            + "t, val = struct.unpack('dLc0', value); "
                            + "local expireDate = 92233720368547758; "
                            + "local expireDateScore = redis.call('zscore', KEYS[2], ARGV[i]); "
                            + "if expireDateScore ~= false then "
                                + "expireDate = tonumber(expireDateScore) "
                            + "end; "
                            + "if t ~= 0 then "
                                + "local expireIdle = redis.call('zscore', KEYS[3], ARGV[i]); "
                                + "if expireIdle ~= false then "
                                    + "expireDate = math.min(expireDate, tonumber(expireIdle)) "
                                + "end; "
                            + "end; "
                            + "if expireDate ~= 92233720368547758 then "
                                + "value = false; "
                            + "end; "
                        + "end; " +

                          "if value ~= false then " +
                            "if tonumber(ARGV[2]) > 0 then "
                                + "redis.call('zadd', KEYS[2], ARGV[2], ARGV[i]); "
                            + "else "
                                + "redis.call('zrem', KEYS[2], ARGV[i]); "
                            + "end; "
                            + "if tonumber(ARGV[3]) > 0 then "
                                + "redis.call('zadd', KEYS[3], ARGV[3], ARGV[i]); "
                            + "else "
                                + "redis.call('zrem', KEYS[3], ARGV[i]); "
                            + "end; " +

                            // last access time
                            "local maxSize = tonumber(redis.call('hget', KEYS[5], 'max-size')); " +
                            "local mode = redis.call('hget', KEYS[5], 'mode'); " +
                            "if maxSize ~= nil and maxSize ~= 0 then " +
                               "local currentTime = tonumber(ARGV[1]); " +

                               "if mode == false or mode == 'LRU' then " +
                                   "redis.call('zadd', KEYS[4], currentTime, ARGV[i]); " +
                               "end; " +
                               "if mode == 'LFU' then " +
                                    "redis.call('zincrby', KEYS[4], 1, ARGV[i]); " +
                               "end; " +
                            "end; " +
                            "counter = counter + 1; " +

                            "local value = struct.pack('dLc0', ARGV[4], string.len(val), val); " +
                            "redis.call('hset', KEYS[1], ARGV[i], value); " +
                          "end;" +


                    "end;" +
                    "return counter;",
                Arrays.asList(getRawName(), timeoutSetName, idleSetName,
                              lastAccessTimeSetName, optionsName),
                            args.toArray());
        return future;
    }


    @Override
    public int expireEntries(Set<K> keys, Duration ttl, Duration maxIdleTime) {
        return get(expireEntriesAsync(keys, ttl, maxIdleTime));
    }

    @Override
    public RFuture<Integer> expireEntriesAsync(Set<K> keys, Duration ttl, Duration maxIdleTime) {
        for (K key : keys) {
            checkKey(key);
        }

        long currentTime = System.currentTimeMillis();
        long ttlTimeout = 0;
        if (ttl != null && !ttl.isZero()) {
            ttlTimeout = currentTime + ttl.toMillis();
        }

        long maxIdleTimeout = 0;
        long maxIdleDelta = 0;
        if (maxIdleTime != null && !maxIdleTime.isZero()) {
            maxIdleDelta = maxIdleTime.toMillis();
            maxIdleTimeout = currentTime + maxIdleDelta;
        }

        List<Object> args = new ArrayList<>(keys.size() + 3);
        args.add(System.currentTimeMillis());
        args.add(ttlTimeout);
        args.add(maxIdleTimeout);
        args.add(maxIdleDelta);
        encodeMapKeys(args, keys);

        RFuture<Integer> future = commandExecutor.evalWriteAsync(getRawName(), IntegerCodec.INSTANCE, RedisCommands.EVAL_INTEGER,
          "local counter = 0;" +
                "for i = 5, #ARGV, 1 do " +
                        "local value = redis.call('hget', KEYS[1], ARGV[i]); "
                        + "local t, val;"
                        + "if value == false then "
                        + "else "
                            + "t, val = struct.unpack('dLc0', value); "
                            + "local expireDate = 92233720368547758; "
                            + "local expireDateScore = redis.call('zscore', KEYS[2], ARGV[i]); "
                            + "if expireDateScore ~= false then "
                                + "expireDate = tonumber(expireDateScore) "
                            + "end; "
                            + "if t ~= 0 then "
                                + "local expireIdle = redis.call('zscore', KEYS[3], ARGV[i]); "
                                + "if expireIdle ~= false then "
                                    + "expireDate = math.min(expireDate, tonumber(expireIdle)) "
                                + "end; "
                            + "end; "
                            + "if expireDate <= tonumber(ARGV[1]) then "
                                + "value = false; "
                            + "end; "
                        + "end; " +

                          "if value ~= false then " +
                            "if tonumber(ARGV[2]) > 0 then "
                                + "redis.call('zadd', KEYS[2], ARGV[2], ARGV[i]); "
                            + "else "
                                + "redis.call('zrem', KEYS[2], ARGV[i]); "
                            + "end; "
                            + "if tonumber(ARGV[3]) > 0 then "
                                + "redis.call('zadd', KEYS[3], ARGV[3], ARGV[i]); "
                            + "else "
                                + "redis.call('zrem', KEYS[3], ARGV[i]); "
                            + "end; " +

                            // last access time
                            "local maxSize = tonumber(redis.call('hget', KEYS[5], 'max-size')); " +
                            "local mode = redis.call('hget', KEYS[5], 'mode'); " +
                            "if maxSize ~= nil and maxSize ~= 0 then " +
                               "local currentTime = tonumber(ARGV[1]); " +

                               "if mode == false or mode == 'LRU' then " +
                                   "redis.call('zadd', KEYS[4], currentTime, ARGV[i]); " +
                               "end; " +
                               "if mode == 'LFU' then " +
                                    "redis.call('zincrby', KEYS[4], 1, ARGV[i]); " +
                               "end; " +
                            "end; " +
                            "counter = counter + 1; " +

                            "local value = struct.pack('dLc0', ARGV[4], string.len(val), val); " +
                            "redis.call('hset', KEYS[1], ARGV[i], value); " +
                          "end;" +


                    "end;" +
                    "return counter;",
                Arrays.asList(getRawName(), timeoutSetName, idleSetName,
                              lastAccessTimeSetName, optionsName),
                            args.toArray());
        return future;
    }

    @Override
    public RFuture<V> putAsync(K key, V value, long ttl, TimeUnit ttlUnit) {
        return putAsync(key, value, ttl, ttlUnit, 0, null);
    }

    @Override
    public V put(K key, V value, long ttl, TimeUnit ttlUnit, long maxIdleTime, TimeUnit maxIdleUnit) {
        return get(putAsync(key, value, ttl, ttlUnit, maxIdleTime, maxIdleUnit));
    }

    @Override
    public RFuture<V> putAsync(K key, V value, long ttl, TimeUnit ttlUnit, long maxIdleTime, TimeUnit maxIdleUnit) {
        checkKey(key);
        checkValue(value);

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
        long ttlTimeoutDelta = 0;
        if (ttl > 0) {
            ttlTimeoutDelta = ttlUnit.toMillis(ttl);
            ttlTimeout = System.currentTimeMillis() + ttlTimeoutDelta;
        }

        long maxIdleTimeout = 0;
        long maxIdleDelta = 0;
        if (maxIdleTime > 0) {
            maxIdleDelta = maxIdleUnit.toMillis(maxIdleTime);
            maxIdleTimeout = System.currentTimeMillis() + maxIdleDelta;
        }

        RFuture<V> future = putOperationAsync(key, value, ttlTimeout, maxIdleTimeout, maxIdleDelta, ttlTimeoutDelta);
        if (hasNoWriter()) {
            return future;
        }

        MapWriterTask.Add listener = new MapWriterTask.Add(key, value);
        return mapWriterFuture(future, listener);
    }

    protected RFuture<V> putOperationAsync(K key, V value, long ttlTimeout, long maxIdleTimeout,
            long maxIdleDelta, long ttlTimeoutDelta) {
        String name = getRawName(key);
        RFuture<V> future = commandExecutor.evalWriteAsync(name, codec, RedisCommands.EVAL_MAP_VALUE,
                "local insertable = false; "
                        + "local v = redis.call('hget', KEYS[1], ARGV[5]); "
                        + "if v == false then "
                            + "insertable = true; "
                        + "else "
                            + "local t, val = struct.unpack('dLc0', v); "
                            + "local expireDate = 92233720368547758; "
                            + "local expireDateScore = redis.call('zscore', KEYS[2], ARGV[5]); "
                            + "if expireDateScore ~= false then "
                                + "expireDate = tonumber(expireDateScore) "
                            + "end; "
                            + "if t ~= 0 then "
                                + "local expireIdle = redis.call('zscore', KEYS[3], ARGV[5]); "
                                + "if expireIdle ~= false then "
                                    + "expireDate = math.min(expireDate, tonumber(expireIdle)) "
                                + "end; "
                            + "end; "
                            + "if expireDate <= tonumber(ARGV[1]) then "
                                + "insertable = true; "
                            + "end; "
                        + "end; "

                        + "if tonumber(ARGV[2]) > 0 then "
                            + "redis.call('zadd', KEYS[2], ARGV[2], ARGV[5]); "
                        + "else "
                            + "redis.call('zrem', KEYS[2], ARGV[5]); "
                        + "end; "
                        + "if tonumber(ARGV[3]) > 0 then "
                            + "redis.call('zadd', KEYS[3], ARGV[3], ARGV[5]); "
                        + "else "
                            + "redis.call('zrem', KEYS[3], ARGV[5]); "
                        + "end; "

                        + "local hasListeners = redis.call('hget', KEYS[8], 'has-listeners'); "
                        // last access time
                        + "local maxSize = tonumber(redis.call('hget', KEYS[8], 'max-size')); " +
                        "if maxSize ~= nil and maxSize ~= 0 then " +
                        "    local currentTime = tonumber(ARGV[1]); " +
                        "    local lastAccessTimeSetName = KEYS[6]; " +

                    "        local mode = redis.call('hget', KEYS[8], 'mode'); " +
                    "        if mode == false or mode == 'LRU' then " +
                    "            redis.call('zadd', lastAccessTimeSetName, currentTime, ARGV[5]); " +
                    "        end; " +

                        "    local cacheSize = tonumber(redis.call('hlen', KEYS[1])); " +
                        "    if cacheSize >= maxSize then " +
                        "        local lruItems = redis.call('zrange', lastAccessTimeSetName, 0, cacheSize - maxSize); " +
                        "        for index, lruItem in ipairs(lruItems) do " +
                        "            if lruItem and lruItem ~= ARGV[5] then " +
                        "                local lruItemValue = redis.call('hget', KEYS[1], lruItem); " +
                        "                redis.call('hdel', KEYS[1], lruItem); " +
                        "                redis.call('zrem', KEYS[2], lruItem); " +
                        "                redis.call('zrem', KEYS[3], lruItem); " +
                        "                redis.call('zrem', lastAccessTimeSetName, lruItem); " +
                                        "if lruItemValue ~= false and hasListeners ~= false then  " +
                            "                local removedChannelName = KEYS[7]; " +
                                            "local ttl, obj = struct.unpack('dLc0', lruItemValue);" +
                            "                local msg = struct.pack('Lc0Lc0', string.len(lruItem), lruItem, string.len(obj), obj);" +
                            "                redis.call(ARGV[7], removedChannelName, msg); " +
                                        "end; " +
                        "            end; " +
                        "        end; " +
                        "    end; " +

                            "if mode == 'LFU' then " +
                                "redis.call('zincrby', lastAccessTimeSetName, 1, ARGV[5]); " +
                            "end; " +

                        "end; "

                        + "local value = struct.pack('dLc0', ARGV[4], string.len(ARGV[6]), ARGV[6]); "
                        + "redis.call('hset', KEYS[1], ARGV[5], value); "

                        + "if insertable == true then "
                            + "if hasListeners ~= false then "
                                + "local msg = struct.pack('Lc0Lc0', string.len(ARGV[5]), ARGV[5], string.len(ARGV[6]), ARGV[6]); "
                                + "redis.call(ARGV[7], KEYS[4], msg); "
                            + "end;"
                            + "return nil;"
                        + "end; "

                        + "local t, val = struct.unpack('dLc0', v); "
                        + "if hasListeners ~= false then "
                            + "local msg = struct.pack('Lc0Lc0Lc0', string.len(ARGV[5]), ARGV[5], string.len(ARGV[6]), ARGV[6], string.len(val), val); "
                            + "redis.call(ARGV[7], KEYS[5], msg); "
                        + "end;"

                        + "return val",
                Arrays.asList(name, getTimeoutSetName(name), getIdleSetName(name), getCreatedChannelName(name),
                        getUpdatedChannelName(name), getLastAccessTimeSetName(name), getRemovedChannelName(name), getOptionsName(name)),
                System.currentTimeMillis(), ttlTimeout, maxIdleTimeout, maxIdleDelta, encodeMapKey(key), encodeMapValue(value), publishCommand);
        return future;
    }

    @Override
    public V getWithTTLOnly(K key) {
        return get(getWithTTLOnlyAsync(key));
    }

    private RFuture<V> getWithTTLOnlyOperationAsync(K key) {
        String name = getRawName(key);
        return commandExecutor.evalReadAsync(name, codec, RedisCommands.EVAL_MAP_VALUE,
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
                        + "if expireDate <= tonumber(ARGV[1]) then "
                            + "return nil; "
                        + "end; "
                        + "return val; ",
                Arrays.asList(name, getTimeoutSetName(name)),
                System.currentTimeMillis(), encodeMapKey(key));
    }

    @Override
    public RFuture<V> getWithTTLOnlyAsync(K key) {
        checkKey(key);

        RFuture<V> future = getWithTTLOnlyOperationAsync(key);
        if (hasNoLoader()) {
            return future;
        }

        CompletionStage<V> f = future.thenCompose(res -> {
            if (res == null) {
                return loadValue(key, false);
            }
            return CompletableFuture.completedFuture(res);
        });
        return new CompletableFutureWrapper<>(f);
    }

    @Override
    public Map<K, V> getAllWithTTLOnly(Set<K> keys) {
        return get(getAllWithTTLOnlyAsync(keys));
    }

    @Override
    public RFuture<Map<K, V>> getAllWithTTLOnlyAsync(Set<K> keys) {
        if (keys.isEmpty()) {
            return new CompletableFutureWrapper<>(Collections.emptyMap());
        }

        RFuture<Map<K, V>> future = getAllWithTTLOnlyOperationAsync(keys);
        if (hasNoLoader()) {
            return future;
        }

        long threadId = Thread.currentThread().getId();
        CompletionStage<Map<K, V>> f = future.thenCompose(res -> {
            if (!res.keySet().containsAll(keys)) {
                Set<K> newKeys = new HashSet<K>(keys);
                newKeys.removeAll(res.keySet());

                CompletionStage<Map<K, V>> ff = loadAllMapAsync(newKeys.spliterator(), false, 1, threadId);
                return ff.thenApply(map -> {
                    res.putAll(map);
                    return res;
                });
            }
            return CompletableFuture.completedFuture(res);
        });
        return new CompletableFutureWrapper<>(f);
    }

    protected RFuture<Map<K, V>> getAllWithTTLOnlyOperationAsync(Set<K> keys) {
        List<Object> args = new ArrayList<>(keys.size() + 1);
        List<Object> plainKeys = new ArrayList<>(keys);

        args.add(System.currentTimeMillis());
        encodeMapKeys(args, keys);

        return commandExecutor.evalReadAsync(getRawName(), codec, new RedisCommand<Map<Object, Object>>("EVAL",
                        new MapValueDecoder(new MapGetAllDecoder(plainKeys, 0))),
                "local expireHead = redis.call('zrange', KEYS[2], 0, 0, 'withscores'); " +
                        "local currentTime = tonumber(table.remove(ARGV, 1)); " + // index is the first parameter
                        "local hasExpire = #expireHead == 2 and tonumber(expireHead[2]) <= currentTime; " +
                        "local map = {}; " +
                        "local values = redis.call('hmget', KEYS[1], unpack(ARGV));" +
                        "for i = 1, #values, 1 do " +
                        "    local value = values[i]; " +
                        "    map[i] = false;" +
                        "    if value ~= false then " +
                        "        local key = ARGV[i]; " +
                        "        local t, val = struct.unpack('dLc0', value); " +
                        "        map[i] = val; " +
                        "        if hasExpire then " +
                        "            local expireDate = redis.call('zscore', KEYS[2], key); " +
                        "            if expireDate ~= false and tonumber(expireDate) <= currentTime then " +
                        "                map[i] = false; " +
                        "            end; " +
                        "        end; " +
                        "    end; " +
                        "end; " +
                        "return map;",
                Arrays.asList(getRawName(), timeoutSetName),
                args.toArray());
    }


    @Override
    public long remainTimeToLive(K key) {
        return get(remainTimeToLiveAsync(key));
    }

    @Override
    public RFuture<Long> remainTimeToLiveAsync(K key) {
        checkKey(key);

        String name = getRawName(key);
        return commandExecutor.evalWriteAsync(name, codec, RedisCommands.EVAL_LONG,
                        "local value = redis.call('hget', KEYS[1], ARGV[2]); "
                        + "if value == false then "
                            + "return -2; "
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
                                + "expireDate = math.min(expireDate, tonumber(expireIdle)) "
                            + "end; "
                        + "end; "

                        + "if expireDate == 92233720368547758 then "
                            + "return -1; "
                        + "end;"
                        + "if expireDate > tonumber(ARGV[1]) then "
                            + "return expireDate - ARGV[1]; "
                        + "else "
                            + "return -2; "
                        + "end; "
                        + "return val; ",
                Arrays.asList(name, getTimeoutSetName(name), getIdleSetName(name)),
                System.currentTimeMillis(), encodeMapKey(key));

    }

    String getTimeoutSetName(String name) {
        return prefixName("redisson__timeout__set", name);
    }

    String getLastAccessTimeSetName(String name) {
        return prefixName("redisson__map_cache__last_access__set", name);
    }

    String getIdleSetName(String name) {
        return prefixName("redisson__idle__set", name);
    }

    String getOptionsName(String name) {
        return suffixName(name, "redisson_options");
    }

    String getCreatedChannelName(String name) {
        return prefixName("redisson_map_cache_created", name);
    }

    String getCreatedChannelName() {
        return prefixName("redisson_map_cache_created", getRawName());
    }

    String getUpdatedChannelName() {
        return prefixName("redisson_map_cache_updated", getRawName());
    }

    String getUpdatedChannelName(String name) {
        return prefixName("redisson_map_cache_updated", name);
    }

    String getExpiredChannelName(String name) {
        return prefixName("redisson_map_cache_expired", name);
    }

    String getExpiredChannelName() {
        return prefixName("redisson_map_cache_expired", getRawName());
    }

    String getRemovedChannelName() {
        return prefixName("redisson_map_cache_removed", getRawName());
    }

    String getRemovedChannelName(String name) {
        return prefixName("redisson_map_cache_removed", name);
    }


    @Override
    protected RFuture<V> removeOperationAsync(K key) {
        String name = getRawName(key);
        return commandExecutor.evalWriteAsync(name, codec, RedisCommands.EVAL_MAP_VALUE,
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
                                + "expireDate = math.min(expireDate, tonumber(expireIdle)) "
                            + "end; "
                        + "end; "
                        + "if expireDate <= tonumber(ARGV[1]) then "
                            + "return nil; "
                        + "end; "

                        + "redis.call('zrem', KEYS[2], ARGV[2]); "
                        + "redis.call('zrem', KEYS[3], ARGV[2]); "
                        + "redis.call('zrem', KEYS[5], ARGV[2]); "
                        + "redis.call('hdel', KEYS[1], ARGV[2]); "

                        + "local hasListeners = redis.call('hget', KEYS[6], 'has-listeners'); "
                        + "if hasListeners ~= false then "
                            + "local msg = struct.pack('Lc0Lc0', string.len(ARGV[2]), ARGV[2], string.len(val), val); "
                            + "redis.call(ARGV[3], KEYS[4], msg); "
                        + "end; "
                        + "return val; ",
                Arrays.asList(name, getTimeoutSetName(name), getIdleSetName(name), getRemovedChannelName(name),
                        getLastAccessTimeSetName(name), getOptionsName(name)),
                System.currentTimeMillis(), encodeMapKey(key), publishCommand);
    }

    @Override
    protected RFuture<List<Long>> fastRemoveOperationBatchAsync(K... keys) {
        List<Object> args = new ArrayList<>(keys.length);
        args.add(publishCommand);
        encodeMapKeys(args, Arrays.asList(keys));

        RFuture<List<Long>> future = commandExecutor.evalWriteAsync(getRawName(), LongCodec.INSTANCE, RedisCommands.EVAL_LIST,
                  "local publishCommand = table.remove(ARGV, 1); " +
                        "local maxSize = tonumber(redis.call('hget', KEYS[6], 'max-size')); "
                        + "if maxSize ~= nil and maxSize ~= 0 then "
                        + "    redis.call('zrem', KEYS[5], unpack(ARGV)); "
                        + "end; " +
                        "redis.call('zrem', KEYS[3], unpack(ARGV)); " +
                        "redis.call('zrem', KEYS[2], unpack(ARGV)); " +

                        "local hasListeners = redis.call('hget', KEYS[6], 'has-listeners'); " +
                        "if hasListeners ~= false then " +
                            "for i, key in ipairs(ARGV) do "
                            + "local v = redis.call('hget', KEYS[1], key); "
                            + "if v ~= false then "
                                + "local t, val = struct.unpack('dLc0', v); "
                                + "local msg = struct.pack('Lc0Lc0', string.len(key), key, string.len(val), val); "
                                + "redis.call(publishCommand, KEYS[4], msg); "
                            + "end; " +
                            "end; " +
                        "end; " +

                        "local result = {}; " +
                        "for i = 1, #ARGV, 1 do "
                            + "local val = redis.call('hdel', KEYS[1], ARGV[i]); "
                            + "table.insert(result, val); "
                        + "end;"
                        + "return result;",
                Arrays.asList(getRawName(), timeoutSetName, idleSetName,
                        getRemovedChannelName(), lastAccessTimeSetName, optionsName),
                args.toArray());
        return future;
    }

    @Override
    protected RFuture<Long> fastRemoveOperationAsync(K... keys) {
        List<Object> params = new ArrayList<>(keys.length);
        params.add(publishCommand);
        encodeMapKeys(params, Arrays.asList(keys));

        return commandExecutor.evalWriteAsync(getRawName(), codec, RedisCommands.EVAL_LONG,
                  "local publishCommand = table.remove(ARGV, 1); " +
                        "local maxSize = tonumber(redis.call('hget', KEYS[6], 'max-size')); "

                        + "for i=1, #ARGV, 5000 do "
                            + "if maxSize ~= nil and maxSize ~= 0 then "
                                + "redis.call('zrem', KEYS[5], unpack(ARGV, i, math.min(i+4999, table.getn(ARGV)))) "
                            + "end; "

                            + "redis.call('zrem', KEYS[3], unpack(ARGV, i, math.min(i+4999, table.getn(ARGV)))) "
                            + "redis.call('zrem', KEYS[2], unpack(ARGV, i, math.min(i+4999, table.getn(ARGV)))) "
                        + "end; "

                      + "local hasListeners = redis.call('hget', KEYS[6], 'has-listeners'); "
                      + "if hasListeners ~= false then "
                          + "for i, key in ipairs(ARGV) do "
                            + "local v = redis.call('hget', KEYS[1], key); "
                            + "if v ~= false then "
                                + "local t, val = struct.unpack('dLc0', v); "
                                + "local msg = struct.pack('Lc0Lc0', string.len(key), key, string.len(val), val); "
                                + "redis.call(publishCommand, KEYS[4], msg); "
                            + "end; " +
                            "end; " +
                        "end; " +

                        "local n = 0;" +
                        "for i=1, #ARGV, 5000 do "
                          + "n = n + redis.call('hdel', KEYS[1], unpack(ARGV, i, math.min(i+4999, table.getn(ARGV)))) "
                      + "end; "

                      + "return n; ",
                Arrays.asList(getRawName(), timeoutSetName, idleSetName,
                        getRemovedChannelName(), lastAccessTimeSetName, optionsName),
                params.toArray());
    }

    @Override
    public RFuture<ScanResult<Object>> scanKeyIteratorAsync(String name, RedisClient client, String startPos, String pattern, int count) {
        List<Object> params = new ArrayList<>();
        params.add(System.currentTimeMillis());
        params.add(startPos);
        if (pattern != null) {
            params.add(pattern);
        }
        params.add(count);

        RedisCommand<MapCacheKeyScanResult<Object>> evalScan = new RedisCommand<MapCacheKeyScanResult<Object>>("EVAL",
                new ListMultiDecoder2(new MapCacheKeyScanResultDecoder(), new ObjectDecoder<>(codec.getMapKeyDecoder())));

        RFuture<MapCacheKeyScanResult<Object>> future = commandExecutor.evalReadAsync(client, name, codec, evalScan,
                "local result = {}; "
                + "local idleKeys = {}; "
                + "local res; "
                + "if (#ARGV == 4) then "
                    + " res = redis.call('hscan', KEYS[1], ARGV[2], 'match', ARGV[3], 'count', ARGV[4]); "
                + "else "
                    + " res = redis.call('hscan', KEYS[1], ARGV[2], 'count', ARGV[3]); "
                + "end;"
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
                        + "end; "
                    + "end; "
                + "end;"
                + "return {res[1], result, idleKeys};",
                Arrays.asList(name, getTimeoutSetName(name), getIdleSetName(name)),
                params.toArray());

        CompletionStage<MapCacheKeyScanResult<Object>> f = future.thenApply(res -> {
            if (res.getIdleKeys().isEmpty()) {
                return res;
            }

            List<Object> args = new ArrayList<Object>(res.getIdleKeys().size() + 1);
            args.add(System.currentTimeMillis());
            encodeMapKeys(args, res.getIdleKeys());

            commandExecutor.evalWriteAsync(name, codec, new RedisCommand<Map<Object, Object>>("EVAL",
                            new MapValueDecoder(new MapGetAllDecoder(args, 1))),
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
                                            + "redis.call('zadd', KEYS[2], t + currentTime, key); "
                                        + "end; "
                                    + "end; "
                                + "end; "
                            + "end; "
                        + "end; ",
                    Arrays.asList(name, getIdleSetName(name)), args.toArray());
            return res;
        });

        return new CompletableFutureWrapper<>((CompletionStage<ScanResult<Object>>) (Object) f);

    }

    private static final RedisCommand<MapCacheScanResult<Object, Object>> SCAN = new RedisCommand<MapCacheScanResult<Object, Object>>("EVAL",
                new ListMultiDecoder2(
                        new MapCacheScanResultReplayDecoder(),
                        new ObjectMapDecoder(true)));

    @Override
    public RFuture<ScanResult<Map.Entry<Object, Object>>> scanIteratorAsync(String name, RedisClient client, String startPos, String pattern, int count) {
        List<Object> params = new ArrayList<Object>();
        params.add(System.currentTimeMillis());
        params.add(startPos);
        if (pattern != null) {
            params.add(pattern);
        }
        params.add(count);

        RFuture<MapCacheScanResult<Object, Object>> future = commandExecutor.evalReadAsync(client, name, codec, SCAN,
                "local result = {}; "
                + "local idleKeys = {}; "
                + "local res; "
                + "if (#ARGV == 4) then "
                    + " res = redis.call('hscan', KEYS[1], ARGV[2], 'match', ARGV[3], 'count', ARGV[4]); "
                + "else "
                    + " res = redis.call('hscan', KEYS[1], ARGV[2], 'count', ARGV[3]); "
                + "end;"
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
                + "return {res[1], result, idleKeys};",
                Arrays.asList(name, getTimeoutSetName(name), getIdleSetName(name)),
                params.toArray());

        CompletionStage<MapCacheScanResult<Object, Object>> f = future.thenApply(res -> {
            if (res.getIdleKeys().isEmpty()) {
                return res;
            }

            List<Object> args = new ArrayList<Object>(res.getIdleKeys().size() + 1);
            args.add(System.currentTimeMillis());
            encodeMapKeys(args, res.getIdleKeys());

            commandExecutor.evalWriteAsync(name, codec, new RedisCommand<Map<Object, Object>>("EVAL",
                            new MapValueDecoder(new MapGetAllDecoder(args, 1))),
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
                                                + "redis.call('zadd', KEYS[2], t + currentTime, key); "
                                            + "end; "
                                        + "end; "
                                    + "end; "
                                + "end; "
                            + "end; ",
                    Arrays.asList(name, getIdleSetName(name)), args.toArray());
            return res;
        });

        return new CompletableFutureWrapper<>((CompletionStage<ScanResult<Map.Entry<Object, Object>>>) (Object) f);
    }

    @Override
    protected RFuture<Boolean> fastPutOperationAsync(K key, V value) {
        String name = getRawName(key);
        return commandExecutor.evalWriteAsync(name, codec, RedisCommands.EVAL_BOOLEAN,
                "local insertable = false; "
                        + "local v = redis.call('hget', KEYS[1], ARGV[2]); "
                        + "if v == false then "
                                + "insertable = true; "
                        + "else "
                            + "local t, val = struct.unpack('dLc0', v); "
                            + "local expireDate = 92233720368547758; "
                            + "local expireDateScore = redis.call('zscore', KEYS[2], ARGV[2]); "
                            + "if expireDateScore ~= false then "
                                + "expireDate = tonumber(expireDateScore) "
                            + "end; "
                            + "if t ~= 0 then "
                                + "local expireIdle = redis.call('zscore', KEYS[3], ARGV[2]); "
                                + "if expireIdle ~= false then "
                                    + "expireDate = math.min(expireDate, tonumber(expireIdle)) "
                                + "end; "
                            + "end; "
                            + "if expireDate <= tonumber(ARGV[1]) then "
                                + "redis.call('zrem', KEYS[2], ARGV[2]); "
                                + "redis.call('zrem', KEYS[3], ARGV[2]); "
                                + "insertable = true; "
                            + "end; "
                        + "end; " +

                        "local val = struct.pack('dLc0', 0, string.len(ARGV[3]), ARGV[3]); "
                        + "redis.call('hset', KEYS[1], ARGV[2], val); " +

                        "local hasListeners = redis.call('hget', KEYS[8], 'has-listeners'); " +

                        // last access time
                        "local maxSize = tonumber(redis.call('hget', KEYS[8], 'max-size'));" +
                        "if maxSize ~= nil and maxSize ~= 0 then " +
                        "    local currentTime = tonumber(ARGV[1]); " +
                        "    local lastAccessTimeSetName = KEYS[6]; " +
                            "local mode = redis.call('hget', KEYS[8], 'mode'); " +
                            "if mode == false or mode == 'LRU' then " +
                                "redis.call('zadd', lastAccessTimeSetName, currentTime, ARGV[2]); " +
                            "end; " +
                        "    local cacheSize = tonumber(redis.call('hlen', KEYS[1])); " +
                        "    if cacheSize > maxSize then " +
                        "        local lruItems = redis.call('zrange', lastAccessTimeSetName, 0, cacheSize - maxSize - 1); " +
                        "        for index, lruItem in ipairs(lruItems) do " +
                        "            if lruItem and lruItem ~= ARGV[2] then " +
                        "                local lruItemValue = redis.call('hget', KEYS[1], lruItem); " +
                        "                redis.call('hdel', KEYS[1], lruItem); " +
                        "                redis.call('zrem', KEYS[2], lruItem); " +
                        "                redis.call('zrem', KEYS[3], lruItem); " +
                        "                redis.call('zrem', lastAccessTimeSetName, lruItem); " +
                                       " if lruItemValue ~= false and hasListeners ~= false then " +
                            "                local removedChannelName = KEYS[7]; " +
                                            "local ttl, obj = struct.unpack('dLc0', lruItemValue);" +
                            "                local msg = struct.pack('Lc0Lc0', string.len(lruItem), lruItem, string.len(obj), obj);" +
                            "                redis.call(ARGV[4], removedChannelName, msg); " +
                                        "end; " +
                        "            end; " +
                        "        end; " +
                        "    end; " +

                            "if mode == 'LFU' then " +
                                "redis.call('zincrby', lastAccessTimeSetName, 1, ARGV[2]); " +
                            "end; " +

                        "end; "

                        + "if insertable == true then "
                            + "if hasListeners ~= false then "
                                + "local msg = struct.pack('Lc0Lc0', string.len(ARGV[2]), ARGV[2], string.len(ARGV[3]), ARGV[3]); "
                                + "redis.call(ARGV[4], KEYS[4], msg); "
                            + "end; "
                            + "return 1;"
                        + "else "
                            + "if hasListeners ~= false then "
                                + "local t, val = struct.unpack('dLc0', v); "
                                + "local msg = struct.pack('Lc0Lc0Lc0', string.len(ARGV[2]), ARGV[2], string.len(ARGV[3]), ARGV[3], string.len(val), val); "
                                + "redis.call(ARGV[4], KEYS[5], msg); "
                            + "end; "
                            + "return 0;"
                        + "end;",
                Arrays.asList(getRawName(key), getTimeoutSetName(name), getIdleSetName(name), getCreatedChannelName(name),
                        getUpdatedChannelName(name), getLastAccessTimeSetName(name), getRemovedChannelName(name), getOptionsName(name)),
                System.currentTimeMillis(), encodeMapKey(key), encodeMapValue(value), publishCommand);
    }

    @Override
    protected RFuture<Boolean> fastPutIfExistsOperationAsync(K key, V value) {
        String name = getRawName(key);
        return commandExecutor.evalWriteAsync(name, codec, RedisCommands.EVAL_BOOLEAN,
                "local value = redis.call('hget', KEYS[1], ARGV[2]); "
                        + "local lastAccessTimeSetName = KEYS[5]; "
                        + "local maxSize = tonumber(redis.call('hget', KEYS[7], 'max-size')); "
                        + "local hasListeners = redis.call('hget', KEYS[7], 'has-listeners'); "
                        + "local currentTime = tonumber(ARGV[1]); "
                        + "if value ~= false then "
                            + "local val = struct.pack('dLc0', 0, string.len(ARGV[3]), ARGV[3]); "
                            + "redis.call('hset', KEYS[1], ARGV[2], val); "

                            + "if hasListeners ~= false then "
                                + "local msg = struct.pack('Lc0Lc0', string.len(ARGV[2]), ARGV[2], string.len(ARGV[3]), ARGV[3]); "
                                + "redis.call(ARGV[4], KEYS[4], msg); "
                            + "end; " +

                            // last access time

                            "if maxSize ~= nil and maxSize ~= 0 then " +
                                "local mode = redis.call('hget', KEYS[7], 'mode'); " +
                                "if mode == false or mode == 'LRU' then " +
                                    "redis.call('zadd', lastAccessTimeSetName, currentTime, ARGV[2]); " +
                                "end; " +
                            "    local cacheSize = tonumber(redis.call('hlen', KEYS[1])); " +
                            "    if cacheSize > maxSize then " +
                            "        local lruItems = redis.call('zrange', lastAccessTimeSetName, 0, cacheSize - maxSize - 1); " +
                            "        for index, lruItem in ipairs(lruItems) do " +
                            "            if lruItem and lruItem ~= ARGV[2] then " +
                            "                local lruItemValue = redis.call('hget', KEYS[1], lruItem); " +
                            "                redis.call('hdel', KEYS[1], lruItem); " +
                            "                redis.call('zrem', KEYS[2], lruItem); " +
                            "                redis.call('zrem', KEYS[3], lruItem); " +
                            "                redis.call('zrem', lastAccessTimeSetName, lruItem); " +
                                           " if lruItemValue ~= false and hasListeners ~= false then " +
                                "                local removedChannelName = KEYS[6]; " +
                                                "local ttl, obj = struct.unpack('dLc0', lruItemValue);" +
                                "                local msg = struct.pack('Lc0Lc0', string.len(lruItem), lruItem, string.len(obj), obj);" +
                                "                redis.call(ARGV[4], removedChannelName, msg); " +
                                            "end; " +
                            "            end; " +
                            "        end; " +
                            "    end; " +

                                "if mode == 'LFU' then " +
                                    "redis.call('zincrby', lastAccessTimeSetName, 1, ARGV[2]); " +
                                "end; " +

                            "end; "

                            + "return 1; "
                        + "end; "

                        + "return 0; ",
                Arrays.asList(name, getTimeoutSetName(name), getIdleSetName(name), getUpdatedChannelName(name),
                        getLastAccessTimeSetName(name), getRemovedChannelName(name), getOptionsName(name)),
                System.currentTimeMillis(), encodeMapKey(key), encodeMapValue(value), publishCommand);
    }

    @Override
    protected RFuture<Boolean> fastPutIfAbsentOperationAsync(K key, V value) {
        String name = getRawName(key);
        return commandExecutor.evalWriteAsync(name, codec, RedisCommands.EVAL_BOOLEAN,
                "local value = redis.call('hget', KEYS[1], ARGV[2]); "
                        + "local lastAccessTimeSetName = KEYS[5]; "
                        + "local maxSize = tonumber(redis.call('hget', KEYS[7], 'max-size')); "
                        + "local hasListeners = redis.call('hget', KEYS[7], 'has-listeners'); "
                        + "local currentTime = tonumber(ARGV[1]); "
                        + "if value == false then "
                            + "local val = struct.pack('dLc0', 0, string.len(ARGV[3]), ARGV[3]); "
                            + "redis.call('hset', KEYS[1], ARGV[2], val); "
                            + "if hasListeners ~= false then "
                                + "local msg = struct.pack('Lc0Lc0', string.len(ARGV[2]), ARGV[2], string.len(ARGV[3]), ARGV[3]); "
                                + "redis.call(ARGV[4], KEYS[4], msg); "
                            + "end; " +
                            // last access time

                            "if maxSize ~= nil and maxSize ~= 0 then " +
                                "local mode = redis.call('hget', KEYS[7], 'mode'); " +
                                "if mode == false or mode == 'LRU' then " +
                                    "redis.call('zadd', lastAccessTimeSetName, currentTime, ARGV[2]); " +
                                "end; " +
                            "    local cacheSize = tonumber(redis.call('hlen', KEYS[1])); " +
                            "    if cacheSize > maxSize then " +
                            "        local lruItems = redis.call('zrange', lastAccessTimeSetName, 0, cacheSize - maxSize - 1); " +
                            "        for index, lruItem in ipairs(lruItems) do " +
                            "            if lruItem and lruItem ~= ARGV[2] then " +
                            "                local lruItemValue = redis.call('hget', KEYS[1], lruItem); " +
                            "                redis.call('hdel', KEYS[1], lruItem); " +
                            "                redis.call('zrem', KEYS[2], lruItem); " +
                            "                redis.call('zrem', KEYS[3], lruItem); " +
                            "                redis.call('zrem', lastAccessTimeSetName, lruItem); " +
                                           " if lruItemValue ~= false and hasListeners ~= false then " +
                                "                local removedChannelName = KEYS[6]; " +
                                                "local ttl, obj = struct.unpack('dLc0', lruItemValue);" +
                                "                local msg = struct.pack('Lc0Lc0', string.len(lruItem), lruItem, string.len(obj), obj);" +
                                "                redis.call(ARGV[4], removedChannelName, msg); " +
                                            "end; " +
                            "            end; " +
                            "        end; " +
                            "    end; " +

                                "if mode == 'LFU' then " +
                                    "redis.call('zincrby', lastAccessTimeSetName, 1, ARGV[2]); " +
                                "end; " +

                            "end; "

                            + "return 1; "
                        + "end; "

                        + "if maxSize ~= nil and maxSize ~= 0 then " +
                                "local mode = redis.call('hget', KEYS[7], 'mode'); " +
                                "if mode == false or mode == 'LRU' then " +
                                    "redis.call('zadd', lastAccessTimeSetName, currentTime, ARGV[2]); " +
                                "else " +
                                    "redis.call('zincrby', lastAccessTimeSetName, 1, ARGV[2]); " +
                                "end; "
                        + "end; "
                        + "local t, val = struct.unpack('dLc0', value); "
                        + "local expireDate = 92233720368547758; "
                        + "local expireDateScore = redis.call('zscore', KEYS[2], ARGV[2]); "
                        + "if expireDateScore ~= false then "
                            + "expireDate = tonumber(expireDateScore) "
                        + "end; "
                        + "if t ~= 0 then "
                            + "local expireIdle = redis.call('zscore', KEYS[3], ARGV[2]); "
                            + "if expireIdle ~= false then "
                                + "if tonumber(expireIdle) > tonumber(ARGV[1]) then "
                                    + "redis.call('zadd', KEYS[3], t + tonumber(ARGV[1]), ARGV[2]); "
                                + "end; "
                                + "expireDate = math.min(expireDate, tonumber(expireIdle)) "
                            + "end; "
                        + "end; "
                        + "if expireDate > tonumber(ARGV[1]) then "
                            + "return 0; "
                        + "end; "

                        + "redis.call('zrem', KEYS[2], ARGV[2]); "
                        + "redis.call('zrem', KEYS[3], ARGV[2]); "
                        + "local val = struct.pack('dLc0', 0, string.len(ARGV[3]), ARGV[3]); "
                        + "redis.call('hset', KEYS[1], ARGV[2], val); "

                        + "if hasListeners ~= false then "
                            + "local msg = struct.pack('Lc0Lc0', string.len(ARGV[2]), ARGV[2], string.len(ARGV[3]), ARGV[3]); "
                            + "redis.call(ARGV[4], KEYS[4], msg); "
                        + "end; "
                        + "return 1; ",
                Arrays.asList(name, getTimeoutSetName(name), getIdleSetName(name), getCreatedChannelName(name),
                        getLastAccessTimeSetName(name), getRemovedChannelName(name), getOptionsName(name)),
                System.currentTimeMillis(), encodeMapKey(key), encodeMapValue(value), publishCommand);
    }

    @Override
    public boolean fastPutIfAbsent(K key, V value, long ttl, TimeUnit ttlUnit) {
        return fastPutIfAbsent(key, value, ttl, ttlUnit, 0, null);
    }

    @Override
    public boolean fastPutIfAbsent(K key, V value, long ttl, TimeUnit ttlUnit, long maxIdleTime, TimeUnit maxIdleUnit) {
        return get(fastPutIfAbsentAsync(key, value, ttl, ttlUnit, maxIdleTime, maxIdleUnit));
    }

    @Override
    public RFuture<Boolean> fastPutIfAbsentAsync(K key, V value, long ttl, TimeUnit ttlUnit, long maxIdleTime, TimeUnit maxIdleUnit) {
        checkKey(key);
        checkValue(value);

        if (ttl < 0) {
            throw new IllegalArgumentException("ttl can't be negative");
        }
        if (maxIdleTime < 0) {
            throw new IllegalArgumentException("maxIdleTime can't be negative");
        }
        if (ttl == 0 && maxIdleTime == 0) {
            return fastPutIfAbsentAsync(key, value);
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

        String name = getRawName(key);
        RFuture<Boolean> future = commandExecutor.evalWriteAsync(name, codec, RedisCommands.EVAL_BOOLEAN,
                "local insertable = false; " +
                        "local value = redis.call('hget', KEYS[1], ARGV[5]); " +
                        "if value == false then " +
                        "    insertable = true; " +
                        "else " +
                        "    if insertable == false then " +
                        "        local t, val = struct.unpack('dLc0', value); " +
                        "        local expireDate = 92233720368547758; " +
                        "        local expireDateScore = redis.call('zscore', KEYS[2], ARGV[5]); " +
                        "        if expireDateScore ~= false then " +
                        "            expireDate = tonumber(expireDateScore) " +
                        "        end; " +
                        "        if t ~= 0 then " +
                        "            local expireIdle = redis.call('zscore', KEYS[3], ARGV[5]); " +
                        "            if expireIdle ~= false then " +
                        "                expireDate = math.min(expireDate, tonumber(expireIdle)) " +
                        "            end; " +
                        "        end; " +
                        "        if expireDate <= tonumber(ARGV[1]) then " +
                        "            insertable = true; " +
                        "        end; " +
                        "    end; " +
                        "end; " +
                        "if insertable == true then " +
                             // ttl
                        "    if tonumber(ARGV[2]) > 0 then " +
                        "        redis.call('zadd', KEYS[2], ARGV[2], ARGV[5]); " +
                        "    else " +
                        "        redis.call('zrem', KEYS[2], ARGV[5]); " +
                        "    end; " +
                             // idle
                        "    if tonumber(ARGV[3]) > 0 then " +
                        "        redis.call('zadd', KEYS[3], ARGV[3], ARGV[5]); " +
                        "    else " +
                        "        redis.call('zrem', KEYS[3], ARGV[5]); " +
                        "    end; " +

                            "local hasListeners = redis.call('hget', KEYS[7], 'has-listeners'); " +
                             // last access time
                        "    local maxSize = tonumber(redis.call('hget', KEYS[7], 'max-size')); " +
                        "    if maxSize ~= nil and maxSize ~= 0 then " +
                        "        local currentTime = tonumber(ARGV[1]); " +
                        "        local lastAccessTimeSetName = KEYS[5]; " +

                                "local mode = redis.call('hget', KEYS[7], 'mode'); " +
                                "if mode == false or mode == 'LRU' then " +
                                    "redis.call('zadd', lastAccessTimeSetName, currentTime, ARGV[5]); " +
                                "end; " +

                        "        local cacheSize = tonumber(redis.call('hlen', KEYS[1])); " +
                        "        if cacheSize >= maxSize then " +
                        "            local lruItems = redis.call('zrange', lastAccessTimeSetName, 0, cacheSize - maxSize); " +
                        "            for index, lruItem in ipairs(lruItems) do " +
                        "                if lruItem and lruItem ~= ARGV[5] then " +
                        "                    local lruItemValue = redis.call('hget', KEYS[1], lruItem); " +
                        "                    redis.call('hdel', KEYS[1], lruItem); " +
                        "                    redis.call('zrem', KEYS[2], lruItem); " +
                        "                    redis.call('zrem', KEYS[3], lruItem); " +
                        "                    redis.call('zrem', lastAccessTimeSetName, lruItem); " +
                                           " if lruItemValue ~= false and hasListeners ~= false then " +
                            "                    local removedChannelName = KEYS[6]; " +
                                                "local ttl, obj = struct.unpack('dLc0', lruItemValue);" +
                            "                    local msg = struct.pack('Lc0Lc0', string.len(lruItem), lruItem, string.len(obj), obj);" +
                            "                    redis.call(ARGV[7], removedChannelName, msg); " +
                                            "end; " +
                        "                end; " +
                        "            end; " +
                        "        end; " +

                                "if mode == 'LFU' then " +
                                    "redis.call('zincrby', lastAccessTimeSetName, 1, ARGV[5]); " +
                                "end; " +

                        "    end; " +
                             // value
                        "    local val = struct.pack('dLc0', ARGV[4], string.len(ARGV[6]), ARGV[6]); " +
                        "    redis.call('hset', KEYS[1], ARGV[5], val); " +

                            "if hasListeners ~= false then " +
                                "local msg = struct.pack('Lc0Lc0', string.len(ARGV[5]), ARGV[5], string.len(ARGV[6]), ARGV[6]); " +
                                "redis.call(ARGV[7], KEYS[4], msg); " +
                            "end; " +
                        "    return 1; " +
                        "else " +
                        "    return 0; " +
                        "end; ",
                Arrays.asList(name, getTimeoutSetName(name), getIdleSetName(name), getCreatedChannelName(name),
                        getLastAccessTimeSetName(name), getRemovedChannelName(name), getOptionsName(name)),
                System.currentTimeMillis(), ttlTimeout, maxIdleTimeout, maxIdleDelta, encodeMapKey(key), encodeMapValue(value), publishCommand);
        if (hasNoWriter()) {
            return future;
        }

        MapWriterTask.Add listener = new MapWriterTask.Add(key, value);
        return mapWriterFuture(future, listener, Function.identity());
    }

    @Override
    protected RFuture<Boolean> replaceOperationAsync(K key, V oldValue, V newValue) {
        String name = getRawName(key);
        return commandExecutor.evalWriteAsync(name, codec, RedisCommands.EVAL_BOOLEAN,
            "local v = redis.call('hget', KEYS[1], ARGV[2]); " +
            "if v == false then " +
            "    return 0; " +
            "end; " +
            "local expireDate = 92233720368547758; " +
            "local expireDateScore = redis.call('zscore', KEYS[2], ARGV[2]); " +
            "if expireDateScore ~= false then " +
            "    expireDate = tonumber(expireDateScore) " +
            "end; " +
            "" +
            "local t, val = struct.unpack('dLc0', v); " +
            "if t ~= 0 then " +
            "    local expireIdle = redis.call('zscore', KEYS[3], ARGV[2]); " +
            "    if tonumber(expireIdle) > tonumber(ARGV[1]) then " +
            "        redis.call('zadd', KEYS[3], t + tonumber(ARGV[1]), ARGV[2]); " +
            "    end ;" +
            "    if expireIdle ~= false then " +
            "        expireDate = math.min(expireDate, tonumber(expireIdle)) " +
            "    end; " +
            "end; " +
            "if expireDate > tonumber(ARGV[1]) and val == ARGV[3] then " +
                "local hasListeners = redis.call('hget', KEYS[5], 'has-listeners'); " +
                "if hasListeners ~= false then " +
                    "local msg = struct.pack('Lc0Lc0Lc0', string.len(ARGV[2]), ARGV[2], string.len(ARGV[4]), ARGV[4], string.len(ARGV[3]), ARGV[3]); " +
                    "redis.call(ARGV[5], KEYS[4], msg); " +
                "end; " +

            "    local value = struct.pack('dLc0', t, string.len(ARGV[4]), ARGV[4]); " +
            "    redis.call('hset', KEYS[1], ARGV[2], value); " +
            "    return 1; " +
            "end; " +
            "return 0; ",
            Arrays.asList(name, getTimeoutSetName(name), getIdleSetName(name), getUpdatedChannelName(name), getOptionsName(name)),
            System.currentTimeMillis(), encodeMapKey(key), encodeMapValue(oldValue), encodeMapValue(newValue), publishCommand);
    }

    @Override
    protected RFuture<Boolean> fastReplaceOperationAsync(K key, V value) {
        String name = getRawName(key);
        return commandExecutor.evalWriteAsync(name, codec, RedisCommands.EVAL_BOOLEAN,
                "local value = redis.call('hget', KEYS[1], ARGV[2]); " +
                "if value == false then " +
                "    return 0; " +
                "end; " +
                "local t, val = struct.unpack('dLc0', value); " +
                "local expireDate = 92233720368547758; " +
                "local expireDateScore = redis.call('zscore', KEYS[2], ARGV[2]); " +
                "if expireDateScore ~= false then " +
                "    expireDate = tonumber(expireDateScore) " +
                "end; " +
                "if t ~= 0 then " +
                "    local expireIdle = redis.call('zscore', KEYS[3], ARGV[2]); " +
                "    if tonumber(expireIdle) > tonumber(ARGV[1]) then " +
                "        redis.call('zadd', KEYS[3], t + tonumber(ARGV[1]), ARGV[2]); " +
                "    end ;" +
                "    if expireIdle ~= false then " +
                "        expireDate = math.min(expireDate, tonumber(expireIdle)) " +
                "    end; " +
                "end; " +
                "if expireDate <= tonumber(ARGV[1]) then " +
                "    return 0; " +
                "end; " +
                "local value = struct.pack('dLc0', t, string.len(ARGV[3]), ARGV[3]); " +
                "redis.call('hset', KEYS[1], ARGV[2], value); " +

                "local hasListeners = redis.call('hget', KEYS[5], 'has-listeners'); " +
                "if hasListeners ~= false then " +
                    "local msg = struct.pack('Lc0Lc0Lc0', string.len(ARGV[2]), ARGV[2], string.len(ARGV[3]), ARGV[3], string.len(val), val); " +
                    "redis.call(ARGV[4], KEYS[4], msg); " +
                "end; " +
                "return 1; ",
                Arrays.asList(name, getTimeoutSetName(name), getIdleSetName(name), getUpdatedChannelName(name), getOptionsName(name)),
                System.currentTimeMillis(), encodeMapKey(key), encodeMapValue(value), publishCommand);
    }

    @Override
    protected RFuture<V> replaceOperationAsync(K key, V value) {
        String name = getRawName(key);
        return commandExecutor.evalWriteAsync(name, codec, RedisCommands.EVAL_MAP_VALUE,
                "local value = redis.call('hget', KEYS[1], ARGV[2]); " +
                "if value == false then " +
                "    return nil; " +
                "end; " +
                "local t, val = struct.unpack('dLc0', value); " +
                "local expireDate = 92233720368547758; " +
                "local expireDateScore = redis.call('zscore', KEYS[2], ARGV[2]); " +
                "if expireDateScore ~= false then " +
                "    expireDate = tonumber(expireDateScore) " +
                "end; " +
                "if t ~= 0 then " +
                "    local expireIdle = redis.call('zscore', KEYS[3], ARGV[2]); " +
                "    if tonumber(expireIdle) > tonumber(ARGV[1]) then " +
                "        redis.call('zadd', KEYS[3], t + tonumber(ARGV[1]), ARGV[2]); " +
                "    end ;" +
                "    if expireIdle ~= false then " +
                "        expireDate = math.min(expireDate, tonumber(expireIdle)) " +
                "    end; " +
                "end; " +
                "if expireDate <= tonumber(ARGV[1]) then " +
                "    return nil; " +
                "end; " +

                "local hasListeners = redis.call('hget', KEYS[5], 'has-listeners'); " +
                "local value = struct.pack('dLc0', t, string.len(ARGV[3]), ARGV[3]); " +
                "redis.call('hset', KEYS[1], ARGV[2], value); " +
                "if hasListeners ~= false then " +
                    "local msg = struct.pack('Lc0Lc0Lc0', string.len(ARGV[2]), ARGV[2], string.len(ARGV[3]), ARGV[3], string.len(val), val); " +
                    "redis.call(ARGV[4], KEYS[4], msg); " +
                "end; " +
                "return val; ",
                Arrays.asList(name, getTimeoutSetName(name), getIdleSetName(name), getUpdatedChannelName(name), getOptionsName(name)),
                System.currentTimeMillis(), encodeMapKey(key), encodeMapValue(value), publishCommand);
    }

    @Override
    protected RFuture<Void> putAllOperationAsync(Map<? extends K, ? extends V> map) {
        List<Object> params = new ArrayList<Object>(map.size()*2 + 1);
        params.add(System.currentTimeMillis());
        params.add(publishCommand);
        encodeMapKeys(params, map);

        return commandExecutor.evalWriteAsync(getRawName(), codec, RedisCommands.EVAL_VOID,
                  "local currentTime = tonumber(table.remove(ARGV, 1)); " + // index is the first parameter
                        "local publishCommand = tonumber(table.remove(ARGV, 1)); " + // index is the first parameter
                  "local maxSize = tonumber(redis.call('hget', KEYS[8], 'max-size'));" +
                  "local mode = redis.call('hget', KEYS[8], 'mode'); " +
                  "for i, value in ipairs(ARGV) do "
                    + "if i % 2 == 0 then "
                      + "local key = ARGV[i-1];" +

                        "local v = redis.call('hget', KEYS[1], key);" +
                        "local exists = false;" +
                        "if v ~= false then" +
                        "    local t, val = struct.unpack('dLc0', v);" +
                        "    local expireDate = 92233720368547758;" +
                        "    local expireDateScore = redis.call('zscore', KEYS[2], key);" +
                        "    if expireDateScore ~= false then" +
                        "        expireDate = tonumber(expireDateScore)" +
                        "    end;" +
                        "    if t ~= 0 then" +
                        "        local expireIdle = redis.call('zscore', KEYS[3], key);" +
                        "        if expireIdle ~= false then" +
                        "            expireDate = math.min(expireDate, tonumber(expireIdle))" +
                        "        end;" +
                        "    end;" +
                        "    if expireDate > tonumber(currentTime) then" +
                        "        exists = true;" +
                        "    end;" +
                        "end;" +
                        "" +
                        "local newvalue = struct.pack('dLc0', 0, string.len(value), value);" +
                        "redis.call('hset', KEYS[1], key, newvalue);" +

                        "local hasListeners = redis.call('hget', KEYS[8], 'has-listeners'); " +
                        "local lastAccessTimeSetName = KEYS[6];" +
                        "if exists == false then" +
                        "    if maxSize ~= nil and maxSize ~= 0 then " +
                                "if mode == false or mode == 'LRU' then " +
                                    "redis.call('zadd', lastAccessTimeSetName, currentTime, key); " +
                                "end; " +

                        "        local cacheSize = tonumber(redis.call('hlen', KEYS[1]));" +
                        "        if cacheSize > maxSize then" +
                        "            local lruItems = redis.call('zrange', lastAccessTimeSetName, 0, cacheSize - maxSize - 1);" +
                        "            for index, lruItem in ipairs(lruItems) do" +
                        "                if lruItem and lruItem ~= key then" +
                        "                    local lruItemValue = redis.call('hget', KEYS[1], lruItem);" +
                        "                    redis.call('hdel', KEYS[1], lruItem);" +
                        "                    redis.call('zrem', KEYS[2], lruItem);" +
                        "                    redis.call('zrem', KEYS[3], lruItem);" +
                        "                    redis.call('zrem', lastAccessTimeSetName, lruItem);" +
                                           " if lruItemValue ~= false and hasListeners ~= false then " +
                            "                    local removedChannelName = KEYS[7];" +
                                                "local ttl, obj = struct.unpack('dLc0', lruItemValue);" +
                            "                    local msg = struct.pack('Lc0Lc0', string.len(lruItem), lruItem, string.len(obj), obj);" +
                            "                    redis.call(publishCommand, removedChannelName, msg);"
                                          + "end; " +
                        "                end;" +
                        "            end" +
                        "        end;" +

                                "if mode == 'LFU' then " +
                                    "redis.call('zincrby', lastAccessTimeSetName, 1, key); " +
                                "end; " +

                        "    end;" +

                            "if hasListeners ~= false then " +
                            "    local msg = struct.pack('Lc0Lc0', string.len(key), key, string.len(value), value);" +
                            "    redis.call(publishCommand, KEYS[4], msg);" +
                            "end;" +
                        "else " +
                            "if hasListeners ~= false then " +
                                "local t, val = struct.unpack('dLc0', v);" +
                                "local msg = struct.pack('Lc0Lc0Lc0', string.len(key), key, string.len(value), value, string.len(val), val);" +
                                "redis.call(publishCommand, KEYS[5], msg);" +
                            "end; " +

                        "    if maxSize ~= nil and maxSize ~= 0 then " +
                                "if mode == false or mode == 'LRU' then " +
                                    "redis.call('zadd', lastAccessTimeSetName, currentTime, key); " +
                                "else " +
                                    "redis.call('zincrby', lastAccessTimeSetName, 1, key); " +
                                "end; " +
                        "    end;" +
                        "end;"
                    + "end;"
                + "end;",
                Arrays.<Object>asList(getRawName(), timeoutSetName, idleSetName, getCreatedChannelName(),
                        getUpdatedChannelName(), lastAccessTimeSetName, getRemovedChannelName(), optionsName),
            params.toArray());
    }

    private RFuture<Void> putAllOperationAsync(Map<? extends K, ? extends V> map, long ttl, TimeUnit ttlUnit) {
        List<Object> params = new ArrayList<Object>(map.size()*2 + 2);
        params.add(System.currentTimeMillis());
        long ttlTimeout = 0;
        if (ttl > 0) {
            ttlTimeout = System.currentTimeMillis() + ttlUnit.toMillis(ttl);
        }
        params.add(ttlTimeout);
        params.add(publishCommand);
        encodeMapKeys(params, map);

        return commandExecutor.evalWriteAsync(getRawName(), codec, RedisCommands.EVAL_VOID,
                  "local currentTime = tonumber(table.remove(ARGV, 1)); " + // index is the first parameter
                  "local ttl = table.remove(ARGV, 1); " + // ttl is the second parameter
                  "local publishCommand = table.remove(ARGV, 1); " +
                  "local ttlNumber = tonumber(ttl); " +
                  "local maxSize = tonumber(redis.call('hget', KEYS[8], 'max-size'));" +
                  "local mode = redis.call('hget', KEYS[8], 'mode'); " +
                  "for i, value in ipairs(ARGV) do "
                    + "if i % 2 == 0 then "
                      + "local key = ARGV[i-1];" +

                        "local v = redis.call('hget', KEYS[1], key);" +
                        "local exists = false;" +
                        "if v ~= false then" +
                        "    local t, val = struct.unpack('dLc0', v);" +
                        "    local expireDate = 92233720368547758;" +
                        "    local expireDateScore = redis.call('zscore', KEYS[2], key);" +
                        "    if expireDateScore ~= false then" +
                        "        expireDate = tonumber(expireDateScore)" +
                        "    end;" +
                        "    if t ~= 0 then" +
                        "        local expireIdle = redis.call('zscore', KEYS[3], key);" +
                        "        if expireIdle ~= false then" +
                        "            expireDate = math.min(expireDate, tonumber(expireIdle))" +
                        "        end;" +
                        "    end;" +
                        "    if expireDate > tonumber(currentTime) then" +
                        "        exists = true;" +
                        "    end;" +
                        "end;" +
                        "" +
                        "if ttlNumber > 0 then " +
                        "    redis.call('zadd', KEYS[2], ttl, key); " +
                        "else " +
                        "    redis.call('zrem', KEYS[2], key); " +
                        "end; " +
                        "" +
                        "local newvalue = struct.pack('dLc0', 0, string.len(value), value);" +
                        "redis.call('hset', KEYS[1], key, newvalue);" +

                        "local hasListeners = redis.call('hget', KEYS[8], 'has-listeners'); " +

                        "local lastAccessTimeSetName = KEYS[6];" +
                        "if exists == false then" +
                        "    if maxSize ~= nil and maxSize ~= 0 then " +
                                "if mode == false or mode == 'LRU' then " +
                                    "redis.call('zadd', lastAccessTimeSetName, currentTime, key); " +
                                "end; " +

                        "        local cacheSize = tonumber(redis.call('hlen', KEYS[1]));" +
                        "        if cacheSize > maxSize then" +
                        "            local lruItems = redis.call('zrange', lastAccessTimeSetName, 0, cacheSize - maxSize - 1);" +
                        "            for index, lruItem in ipairs(lruItems) do" +
                        "                if lruItem and lruItem ~= key then" +
                        "                    local lruItemValue = redis.call('hget', KEYS[1], lruItem);" +
                        "                    redis.call('hdel', KEYS[1], lruItem);" +
                        "                    redis.call('zrem', KEYS[2], lruItem);" +
                        "                    redis.call('zrem', KEYS[3], lruItem);" +
                        "                    redis.call('zrem', lastAccessTimeSetName, lruItem);" +
                                           " if lruItemValue ~= false and hasListeners ~= false then " +
                            "                    local removedChannelName = KEYS[7];" +
                                                "local ttl, obj = struct.unpack('dLc0', lruItemValue);" +
                            "                    local msg = struct.pack('Lc0Lc0', string.len(lruItem), lruItem, string.len(obj), obj);" +
                            "                    redis.call(publishCommand, removedChannelName, msg);"
                                          + "end; " +
                        "                end;" +
                        "            end" +
                        "        end;" +

                                "if mode == 'LFU' then " +
                                    "redis.call('zincrby', lastAccessTimeSetName, 1, key); " +
                                "end; " +

                        "    end;" +

                            "if hasListeners ~= false then " +
                                "local msg = struct.pack('Lc0Lc0', string.len(key), key, string.len(value), value);" +
                                "redis.call(publishCommand, KEYS[4], msg);" +
                            "end; " +
                        "else " +
                            "if hasListeners ~= false then " +
                                "local t, val = struct.unpack('dLc0', v);" +
                                "local msg = struct.pack('Lc0Lc0Lc0', string.len(key), key, string.len(value), value, string.len(val), val);" +
                                "redis.call(publishCommand, KEYS[5], msg);" +
                            "end; " +

                        "    if maxSize ~= nil and maxSize ~= 0 then " +
                                "if mode == false or mode == 'LRU' then " +
                                    "redis.call('zadd', lastAccessTimeSetName, currentTime, key); " +
                                "else " +
                                    "redis.call('zincrby', lastAccessTimeSetName, 1, key); " +
                                "end; " +
                        "    end;" +
                        "end;"
                    + "end;"
                + "end;",
                Arrays.<Object>asList(getRawName(), timeoutSetName, idleSetName, getCreatedChannelName(),
                        getUpdatedChannelName(), lastAccessTimeSetName, getRemovedChannelName(), optionsName),
            params.toArray());
    }

    private volatile MapCacheEventCodec.OSType osType;
    private volatile Codec topicCodec;

    @Override
    public int addListener(MapEntryListener listener) {
        return get(addListenerAsync(listener));
    }

    protected RTopic getTopic(String name) {
        if (getSubscribeService().isShardingSupported()) {
            return RedissonShardedTopic.createRaw(topicCodec, commandExecutor, name);
        }
        return RedissonTopic.createRaw(topicCodec, commandExecutor, name);
    }

    @Override
    public RFuture<Integer> addListenerAsync(MapEntryListener listener) {
        Objects.requireNonNull(listener);

        CompletionStage<Void> osTypeFuture = CompletableFuture.completedFuture(null);
        if (osType == null) {
            RFuture<Map<String, String>> serverFuture = commandExecutor.readAsync((String) null, StringCodec.INSTANCE, RedisCommands.INFO_SERVER);
            osTypeFuture = serverFuture.thenAccept(res -> {
                String os = res.get("os");
                if (os == null || os.contains("Windows")) {
                    osType = BaseEventCodec.OSType.WINDOWS;
                } else if (os.contains("NONSTOP")) {
                    osType = BaseEventCodec.OSType.HPNONSTOP;
                }
                topicCodec = new MapCacheEventCodec(codec, osType);
            }).thenCompose(r -> {
                return commandExecutor.writeAsync(getRawName(), LongCodec.INSTANCE, RedisCommands.HSET_VOID, optionsName, "has-listeners", 1);
            });
        }

        CompletionStage<Integer> f = osTypeFuture.thenCompose(osType -> {
            if (listener instanceof EntryRemovedListener) {
                RTopic topic = getTopic(getRemovedChannelName());
                return topic.addListenerAsync(List.class, new MessageListener<List<Object>>() {
                    @Override
                    public void onMessage(CharSequence channel, List<Object> msg) {
                        EntryEvent<K, V> event = new EntryEvent<K, V>(RedissonMapCache.this, EntryEvent.Type.REMOVED, (K) msg.get(0), (V) msg.get(1), null);
                        ((EntryRemovedListener<K, V>) listener).onRemoved(event);
                    }
                });
            }

            if (listener instanceof EntryCreatedListener) {
                RTopic topic = getTopic(getCreatedChannelName());
                return topic.addListenerAsync(List.class, new MessageListener<List<Object>>() {
                    @Override
                    public void onMessage(CharSequence channel, List<Object> msg) {
                        EntryEvent<K, V> event = new EntryEvent<K, V>(RedissonMapCache.this, EntryEvent.Type.CREATED, (K) msg.get(0), (V) msg.get(1), null);
                        ((EntryCreatedListener<K, V>) listener).onCreated(event);
                    }
                });
            }

            if (listener instanceof EntryUpdatedListener) {
                RTopic topic = getTopic(getUpdatedChannelName());
                return topic.addListenerAsync(List.class, new MessageListener<List<Object>>() {
                    @Override
                    public void onMessage(CharSequence channel, List<Object> msg) {
                        EntryEvent<K, V> event = new EntryEvent<K, V>(RedissonMapCache.this, EntryEvent.Type.UPDATED, (K) msg.get(0), (V) msg.get(1), (V) msg.get(2));
                        ((EntryUpdatedListener<K, V>) listener).onUpdated(event);
                    }
                });
            }

            if (listener instanceof EntryExpiredListener) {
                RTopic topic = getTopic(getExpiredChannelName());
                return topic.addListenerAsync(List.class, new MessageListener<List<Object>>() {
                    @Override
                    public void onMessage(CharSequence channel, List<Object> msg) {
                        EntryEvent<K, V> event = new EntryEvent<K, V>(RedissonMapCache.this, EntryEvent.Type.EXPIRED, (K) msg.get(0), (V) msg.get(1), null);
                        ((EntryExpiredListener<K, V>) listener).onExpired(event);
                    }
                });
            }

            CompletableFuture<Integer> res = new CompletableFuture<>();
            res.completeExceptionally(new IllegalArgumentException("Wrong listener type " + listener.getClass()));
            return res;
        });
        f = f.thenApply(id -> {
            if (listener instanceof EntryRemovedListener) {
                addListenerId(getRemovedChannelName(), id);
            }
            if (listener instanceof EntryUpdatedListener) {
                addListenerId(getUpdatedChannelName(), id);
            }
            if (listener instanceof EntryCreatedListener) {
                addListenerId(getCreatedChannelName(), id);
            }
            if (listener instanceof EntryExpiredListener) {
                addListenerId(getExpiredChannelName(), id);
            }
            return id;
        });
        return new CompletableFutureWrapper<>(f);
    }

    @Override
    public void removeListener(int listenerId) {
        super.removeListener(listenerId);

        String topicName = getNameByListenerId(listenerId);
        if (topicName != null) {
            RTopic topic = getTopic(topicName);
            removeListenerId(topicName, listenerId);
            topic.removeListener(listenerId);
        }
    }

    @Override
    public RFuture<Void> removeListenerAsync(int listenerId) {
        CompletionStage<Void> r = super.removeListenerAsync(listenerId);
        r = r.thenCompose(v -> {
            String topicName = getNameByListenerId(listenerId);
            if (topicName != null) {
                RTopic topic = getTopic(topicName);
                removeListenerId(topicName, listenerId);
                return topic.removeListenerAsync(listenerId);
            }
            return CompletableFuture.completedFuture(null);
        });

        return new CompletableFutureWrapper<>(r);
    }

    @Override
    public RFuture<Long> sizeInMemoryAsync() {
        List<Object> keys = Arrays.asList(getRawName(), timeoutSetName, idleSetName, lastAccessTimeSetName, optionsName);
        return super.sizeInMemoryAsync(keys);
    }

    @Override
    public RFuture<Boolean> copyAsync(List<Object> keys, int database, boolean replace) {
        String newName = (String) keys.get(1);
        List<Object> kks = Arrays.asList(getRawName(), timeoutSetName, idleSetName, lastAccessTimeSetName, optionsName,
                                         newName, getTimeoutSetName(newName), getIdleSetName(newName), getLastAccessTimeSetName(newName), getOptionsName(newName));
        return super.copyAsync(kks, database, replace);
    }

    @Override
    public RFuture<Void> renameAsync(String nn) {
        String newName = mapName(nn);
        List<Object> kks = Arrays.asList(getRawName(), timeoutSetName, idleSetName, lastAccessTimeSetName, optionsName,
                newName, getTimeoutSetName(newName), getIdleSetName(newName), getLastAccessTimeSetName(newName), getOptionsName(newName));
        return renameAsync(commandExecutor, kks, () -> {
            setName(nn);
            this.timeoutSetName = getTimeoutSetName(getRawName());
            this.idleSetName = getIdleSetName(getRawName());
            this.lastAccessTimeSetName = getLastAccessTimeSetName(getRawName());
            this.optionsName = getOptionsName(getRawName());
        });
    }

    @Override
    public RFuture<Boolean> renamenxAsync(String nn) {
        String newName = mapName(nn);
        List<Object> kks = Arrays.asList(getRawName(), timeoutSetName, idleSetName, lastAccessTimeSetName, optionsName,
                newName, getTimeoutSetName(newName), getIdleSetName(newName), getLastAccessTimeSetName(newName), getOptionsName(newName));
        return renamenxAsync(commandExecutor, kks, value -> {
            if (value) {
                setName(nn);
                this.timeoutSetName = getTimeoutSetName(getRawName());
                this.idleSetName = getIdleSetName(getRawName());
                this.lastAccessTimeSetName = getLastAccessTimeSetName(getRawName());
                this.optionsName = getOptionsName(getRawName());
            }
        });
    }

    @Override
    public void clear() {
        get(clearAsync());
    }

    @Override
    public RFuture<Boolean> clearAsync() {
        return deleteAsync(getRawName(), timeoutSetName, idleSetName, lastAccessTimeSetName);
    }

    @Override
    public RFuture<Boolean> deleteAsync() {
        return deleteAsync(getRawName(), timeoutSetName, idleSetName, lastAccessTimeSetName, optionsName);
    }

    @Override
    public RFuture<Boolean> expireAsync(long timeToLive, TimeUnit timeUnit, String param, String... keys) {
        return commandExecutor.evalWriteAsync(getRawName(), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                "local maxSize = tonumber(redis.call('hget', KEYS[5], 'max-size')); " +
                        "if maxSize ~= nil and maxSize ~= 0 then "
                            + "redis.call('zadd', KEYS[4], 92233720368547758, 'redisson__expiretag'); "
                            + "if ARGV[2] ~= '' then "
                                + "redis.call('pexpire', KEYS[5], ARGV[1], ARGV[2]); "
                                + "redis.call('pexpire', KEYS[4], ARGV[1], ARGV[2]); "
                            + "else "
                                + "redis.call('pexpire', KEYS[5], ARGV[1]); "
                                + "redis.call('pexpire', KEYS[4], ARGV[1]); "
                            + "end; "
                      + "end; " +
                        "redis.call('zadd', KEYS[2], 92233720368547758, 'redisson__expiretag'); " +
                        "redis.call('zadd', KEYS[3], 92233720368547758, 'redisson__expiretag'); " +
                        "if ARGV[2] ~= '' then "
                            + "redis.call('pexpire', KEYS[2], ARGV[1], ARGV[2]); "
                            + "redis.call('pexpire', KEYS[3], ARGV[1], ARGV[2]); "
                            + "return redis.call('pexpireat', KEYS[1], ARGV[1], ARGV[2]); "
                       + "end; "
                       + "redis.call('pexpire', KEYS[2], ARGV[1]); "
                       + "redis.call('pexpire', KEYS[3], ARGV[1]); "
                       + "return redis.call('pexpire', KEYS[1], ARGV[1]); ",
                Arrays.<Object>asList(getRawName(), timeoutSetName, idleSetName, lastAccessTimeSetName, optionsName),
                timeUnit.toMillis(timeToLive), param);
    }

    @Override
    protected RFuture<Boolean> expireAtAsync(long timestamp, String param, String... keys) {
        return commandExecutor.evalWriteAsync(getRawName(), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                        "local maxSize = tonumber(redis.call('hget', KEYS[5], 'max-size')); " +
                        "if maxSize ~= nil and maxSize ~= 0 then "
                                + "redis.call('zadd', KEYS[4], 92233720368547758, 'redisson__expiretag'); "
                                + "if ARGV[2] ~= '' then "
                                    + "redis.call('pexpireat', KEYS[5], ARGV[1], ARGV[2]); "
                                    + "redis.call('pexpireat', KEYS[4], ARGV[1], ARGV[2]); "
                                + "else "
                                    + "redis.call('pexpireat', KEYS[5], ARGV[1]); "
                                    + "redis.call('pexpireat', KEYS[4], ARGV[1]); "
                                + "end; "
                       + "end; "
                       + "redis.call('zadd', KEYS[2], 92233720368547758, 'redisson__expiretag'); "
                       + "redis.call('zadd', KEYS[3], 92233720368547758, 'redisson__expiretag'); "
                       + "if ARGV[2] ~= '' then "
                            + "redis.call('pexpireat', KEYS[2], ARGV[1], ARGV[2]); "
                            + "redis.call('pexpireat', KEYS[3], ARGV[1], ARGV[2]); "
                            + "return redis.call('pexpireat', KEYS[1], ARGV[1], ARGV[2]); "
                       + "end; "
                       + "redis.call('pexpireat', KEYS[2], ARGV[1]); "
                       + "redis.call('pexpireat', KEYS[3], ARGV[1]); "
                       + "return redis.call('pexpireat', KEYS[1], ARGV[1]); ",
                Arrays.asList(getRawName(), timeoutSetName, idleSetName, lastAccessTimeSetName, optionsName),
                timestamp, param);
    }

    @Override
    public RFuture<Boolean> clearExpireAsync() {
        return commandExecutor.evalWriteAsync(getRawName(), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                        "local maxSize = tonumber(redis.call('hget', KEYS[5], 'max-size')); " +
                        "if maxSize ~= nil and maxSize ~= 0 then " +
                        "    redis.call('persist', KEYS[5]); " +
                        "    redis.call('zrem', KEYS[4], 92233720368547758, 'redisson__expiretag'); " +
                        "    redis.call('persist', KEYS[4]); " +
                        "end; " +

                        "redis.call('zrem', KEYS[2], 'redisson__expiretag'); " +
                        "redis.call('persist', KEYS[2]); " +
                        "redis.call('zrem', KEYS[3], 'redisson__expiretag'); " +
                        "redis.call('persist', KEYS[3]); " +
                        "return redis.call('persist', KEYS[1]); ",
                Arrays.<Object>asList(getRawName(), timeoutSetName, idleSetName, lastAccessTimeSetName, optionsName));
    }

    @Override
    public RFuture<Set<K>> readAllKeySetAsync() {
        return commandExecutor.evalWriteAsync(getRawName(), codec, RedisCommands.EVAL_MAP_KEY_SET,
                "local s = redis.call('hgetall', KEYS[1]); " +
                "local maxSize = tonumber(redis.call('hget', KEYS[5], 'max-size'));"
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
                                    + "redis.call('zadd', KEYS[3], t + tonumber(ARGV[1]), key); "
                                    + "if maxSize ~= nil and maxSize ~= 0 then " +
                                        "local mode = redis.call('hget', KEYS[5], 'mode'); " +
                                        "if mode == false or mode == 'LRU' then " +
                                            "redis.call('zadd', KEYS[4], tonumber(ARGV[1]), key); " +
                                        "else " +
                                            "redis.call('zincrby', KEYS[4], 1, key); " +
                                        "end; "
                                    + "end; "
                                + "end; "
                                + "expireDate = math.min(expireDate, tonumber(expireIdle)) "
                                + "end; "
                                + "end; "
                                + "if expireDate > tonumber(ARGV[1]) then "
                                    + "table.insert(result, key); "
                                + "end; "
                            + "end; "
                        + "end;" +
                        "return result;",
                Arrays.<Object>asList(getRawName(), timeoutSetName, idleSetName, lastAccessTimeSetName, optionsName),
                System.currentTimeMillis());
    }

    @Override
    public RFuture<Set<K>> randomKeysAsync(int count) {
        return commandExecutor.evalWriteAsync(getRawName(), codec, RedisCommands.EVAL_MAP_KEY_SET,
            "local s = redis.call('hrandfield', KEYS[1], ARGV[2], 'withvalues'); " +
                  "if s == false then " +
                       "return {};" +
                  "end; " +
                  "local maxSize = tonumber(redis.call('hget', KEYS[5], 'max-size'));"
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
                                    + "redis.call('zadd', KEYS[3], t + tonumber(ARGV[1]), key); "
                                    + "if maxSize ~= nil and maxSize ~= 0 then " +
                                        "local mode = redis.call('hget', KEYS[5], 'mode'); " +
                                        "if mode == false or mode == 'LRU' then " +
                                            "redis.call('zadd', KEYS[4], tonumber(ARGV[1]), key); " +
                                        "else " +
                                            "redis.call('zincrby', KEYS[4], 1, key); " +
                                        "end; "
                                    + "end; "
                                + "end; "
                                + "expireDate = math.min(expireDate, tonumber(expireIdle)) "
                                + "end; "
                                + "end; "
                                + "if expireDate > tonumber(ARGV[1]) then "
                                    + "table.insert(result, key); "
                                + "end; "
                            + "end; "
                        + "end;" +
                        "return result;",
                Arrays.asList(getRawName(), timeoutSetName, idleSetName, lastAccessTimeSetName, optionsName),
                System.currentTimeMillis(), count);
    }

    @Override
    public RFuture<Map<K, V>> randomEntriesAsync(int count) {
        return commandExecutor.evalWriteAsync(getRawName(), codec, RedisCommands.EVAL_MAP,
            "local s = redis.call('hrandfield', KEYS[1], ARGV[2], 'withvalues'); " +
                "if s == false then " +
                    "return {};" +
                "end; "
                + "local result = {}; "
                + "local maxSize = tonumber(redis.call('hget', KEYS[5], 'max-size'));"
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
                                    + "redis.call('zadd', KEYS[3], t + tonumber(ARGV[1]), key); "
                                            + "if maxSize ~= nil and maxSize ~= 0 then " +
                                                    "local mode = redis.call('hget', KEYS[5], 'mode'); " +
                                                    "if mode == false or mode == 'LRU' then " +
                                                        "redis.call('zadd', KEYS[4], tonumber(ARGV[1]), key); " +
                                                    "else " +
                                                        "redis.call('zincrby', KEYS[4], 1, key); " +
                                                    "end; "
                                            + "end; "
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
                Arrays.asList(getRawName(), timeoutSetName, idleSetName, lastAccessTimeSetName, optionsName),
                System.currentTimeMillis(), count);
    }

    @Override
    public RFuture<Set<java.util.Map.Entry<K, V>>> readAllEntrySetAsync() {
        return readAll(RedisCommands.EVAL_MAP_ENTRY);
    }

    private <R> RFuture<R> readAll(RedisCommand<?> evalCommandType) {
        return commandExecutor.evalWriteAsync(getRawName(), codec, evalCommandType,
                "local s = redis.call('hgetall', KEYS[1]); "
                        + "local result = {}; "
                        + "local maxSize = tonumber(redis.call('hget', KEYS[5], 'max-size'));"
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
                                    + "redis.call('zadd', KEYS[3], t + tonumber(ARGV[1]), key); "
                                            + "if maxSize ~= nil and maxSize ~= 0 then " +
                                                    "local mode = redis.call('hget', KEYS[5], 'mode'); " +
                                                    "if mode == false or mode == 'LRU' then " +
                                                        "redis.call('zadd', KEYS[4], tonumber(ARGV[1]), key); " +
                                                    "else " +
                                                        "redis.call('zincrby', KEYS[4], 1, key); " +
                                                    "end; "
                                            + "end; "
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
                Arrays.<Object>asList(getRawName(), timeoutSetName, idleSetName, lastAccessTimeSetName, optionsName),
                System.currentTimeMillis());
    }

    @Override
    public RFuture<Map<K, V>> readAllMapAsync() {
        return readAll(RedisCommands.EVAL_MAP);
    }


    @Override
    public RFuture<Collection<V>> readAllValuesAsync() {
        return commandExecutor.evalWriteAsync(getRawName(), codec, RedisCommands.EVAL_MAP_VALUE_LIST,
                "local s = redis.call('hgetall', KEYS[1]); "
                    + "local result = {}; "
                    + "local maxSize = tonumber(redis.call('hget', KEYS[5], 'max-size')); "
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
                                    + "redis.call('zadd', KEYS[3], t + tonumber(ARGV[1]), key); "
                                    + "if maxSize ~= nil and maxSize ~= 0 then " +
                                        "local mode = redis.call('hget', KEYS[5], 'mode'); " +
                                        "if mode == false or mode == 'LRU' then " +
                                            "redis.call('zadd', KEYS[4], tonumber(ARGV[1]), key); " +
                                        "else " +
                                            "redis.call('zincrby', KEYS[4], 1, key); " +
                                        "end; "
                                    + "end; "
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
                Arrays.<Object>asList(getRawName(), timeoutSetName, idleSetName, lastAccessTimeSetName, optionsName),
                System.currentTimeMillis());
    }

    @Override
    public void destroy() {
        if (evictionScheduler != null) {
            evictionScheduler.remove(getRawName());
        }
        super.destroy();

        List<String> channels = Arrays.asList(getCreatedChannelName(), getRemovedChannelName(), getUpdatedChannelName(), getExpiredChannelName());
        for (String channel : channels) {
            Collection<Integer> ids = getListenerIdsByName(channel);
            if (ids.isEmpty()) {
                continue;
            }

            RTopic topic = getTopic(channel);
            for (Integer listenerId : ids) {
                removeListenerId(channel, listenerId);
                topic.removeListener(listenerId);
            }
        }
    }
}
