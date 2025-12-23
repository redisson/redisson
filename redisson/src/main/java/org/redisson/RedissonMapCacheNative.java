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

import java.time.Instant;
import org.redisson.api.*;
import org.redisson.api.listener.MapExpiredListener;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.connection.decoder.MapNativeAllDecoder;
import org.redisson.misc.CompletableFutureWrapper;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Map-based cache with ability to set TTL per entry.
 * Uses Redis native commands for entry expiration and not a scheduled eviction task.

 *
 * @author Nikita Koksharov
 *
 * @param <K> key
 * @param <V> value
 */
public class RedissonMapCacheNative<K, V> extends RedissonMap<K, V> implements RMapCacheNative<K, V> {

    public RedissonMapCacheNative(CommandAsyncExecutor commandExecutor, String name, RedissonClient redisson, MapOptions<K, V> options, WriteBehindService writeBehindService) {
        super(commandExecutor, name, redisson, options, writeBehindService);
    }

    public RedissonMapCacheNative(Codec codec, CommandAsyncExecutor commandExecutor, String name) {
        super(codec, commandExecutor, name);
    }

    public RedissonMapCacheNative(Codec codec, CommandAsyncExecutor commandExecutor, String name, RedissonClient redisson, MapOptions<K, V> options, WriteBehindService writeBehindService) {
        super(codec, commandExecutor, name, redisson, options, writeBehindService);
    }

    @Override
    public V put(K key, V value, Duration ttl) {
        return get(putAsync(key, value, ttl));
    }

    @Override
    public V put(K key, V value, Instant time) {
        return get(putAsync(key, value, time));
    }

    @Override
    public RFuture<V> putAsync(K key, V value, Duration ttl) {
        return putAsyncInternal(key, value, ttl.toMillis(), true);
    }

    @Override
    public RFuture<V> putAsync(K key, V value, Instant time) {
        return putAsyncInternal(key, value, time.toEpochMilli(), false);
    }

    private RFuture<V> putAsyncInternal(K key, V value, long ms, boolean isDuration) {
        checkKey(key);
        checkValue(value);

        if (ms < 0) {
            throw new IllegalArgumentException("ttl can't be negative");
        }
        if (ms == 0) {
            return putAsync(key, value);
        }

        RFuture<V> future = putOperationAsync(key, value, ms, isDuration);
        future = new CompletableFutureWrapper<>(future);
        if (hasNoWriter()) {
            return future;
        }

        MapWriterTask.Add listener = new MapWriterTask.Add(key, value);
        return mapWriterFuture(future, listener);
    }

    protected RFuture<V> putOperationAsync(K key, V value, long ms, boolean isDuration) {
        String name = getRawName(key);

        return commandExecutor.evalWriteAsync(name, codec, RedisCommands.EVAL_OBJECT,
                "local currValue = redis.call('hget', KEYS[1], ARGV[2]); "
                        + "redis.call('hset', KEYS[1], ARGV[2], ARGV[3]); "
                        + "redis.call(ARGV[4], KEYS[1], ARGV[1], 'fields', 1, ARGV[2]); "
                        + "return currValue; ",
                Collections.singletonList(name),
                ms, encodeMapKey(key), encodeMapValue(value), getExpireCommand(isDuration));
    }

    @Override
    public boolean fastPut(K key, V value, Duration ttl) {
        return get(fastPutAsync(key, value, ttl));
    }

    @Override
    public boolean fastPut(K key, V value, Instant time) {
        return get(fastPutAsync(key, value, time));
    }

    @Override
    public RFuture<Boolean> fastPutAsync(K key, V value, Duration ttl) {
        return fastPutAsyncInternal(key, value, ttl.toMillis(), true);
    }

    @Override
    public RFuture<Boolean> fastPutAsync(K key, V value, Instant time) {
        return fastPutAsyncInternal(key, value, time.toEpochMilli(), false);
    }

    private RFuture<Boolean> fastPutAsyncInternal(K key, V value, long ms, boolean isDuration) {
        checkKey(key);
        checkValue(value);

        if (ms < 0) {
            throw new IllegalArgumentException("ttl can't be negative");
        }
        if (ms == 0) {
            return fastPutAsync(key, value);
        }

        RFuture<Boolean> future = fastPutOperationAsync(key, value, ms, isDuration);
        future = new CompletableFutureWrapper<>(future);
        if (hasNoWriter()) {
            return future;
        }

        return mapWriterFuture(future, new MapWriterTask.Add(key, value));
    }

    protected RFuture<Boolean> fastPutOperationAsync(K key, V value, long ms, boolean isDuration) {
        String name = getRawName(key);

        return commandExecutor.evalWriteAsync(name, StringCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                "local added = redis.call('hset', KEYS[1], ARGV[2], ARGV[3]); " +
                        "redis.call(ARGV[4], KEYS[1], ARGV[1], 'fields', 1, ARGV[2]); " +
                        "return added;",
                Collections.singletonList(name),
                ms, encodeMapKey(key), encodeMapValue(value), getExpireCommand(isDuration));
    }

    @Override
    public V putIfAbsent(K key, V value, Duration ttl) {
        return get(putIfAbsentAsync(key, value, ttl));
    }

    @Override
    public V putIfAbsent(K key, V value, Instant time) {
        return get(putIfAbsentAsync(key, value, time));
    }

    @Override
    public RFuture<V> putIfAbsentAsync(K key, V value, Duration ttl) {
        return  putIfAbsentAsyncInternal(key, value, ttl.toMillis(), true);
    }

    @Override
    public RFuture<V> putIfAbsentAsync(K key, V value, Instant time) {
        return putIfAbsentAsyncInternal(key, value, time.toEpochMilli(), false);
    }

    private RFuture<V> putIfAbsentAsyncInternal(K key, V value, long ms, boolean isDuration) {
        checkKey(key);
        checkValue(value);

        if (ms < 0) {
            throw new IllegalArgumentException("ms can't be negative");
        }
        if (ms == 0) {
            return putIfAbsentAsync(key, value);
        }

        RFuture<V> future = putIfAbsentOperationAsync(key, value, ms, isDuration);
        future = new CompletableFutureWrapper<>(future);
        if (hasNoWriter()) {
            return future;
        }
        MapWriterTask.Add task = new MapWriterTask.Add(key, value);
        return mapWriterFuture(future, task, r -> r == null);
    }

    protected RFuture<V> putIfAbsentOperationAsync(K key, V value, long ms, boolean isDuration) {
        String name = getRawName(key);

        if (value == null) {
            return commandExecutor.readAsync(name, codec, RedisCommands.HGET, name, encodeMapKey(key));
        }

        return commandExecutor.evalWriteAsync(name, codec, RedisCommands.EVAL_MAP_VALUE,
                  "local currValue = redis.call('hget', KEYS[1], ARGV[2]); " +
                        "if currValue ~= false then " +
                            "return currValue;" +
                        "end;" +
                        "redis.call('hset', KEYS[1], ARGV[2], ARGV[3]); " +
                        "redis.call(ARGV[4], KEYS[1], ARGV[1], 'fields', 1, ARGV[2]); " +
                        "return nil; ",
                Collections.singletonList(name),
                ms, encodeMapKey(key), encodeMapValue(value), getExpireCommand(isDuration));
    }

    @Override
    public boolean fastPutIfAbsent(K key, V value, Duration ttl) {
        return get(fastPutIfAbsentAsync(key, value, ttl));
    }

    @Override
    public boolean fastPutIfAbsent(K key, V value, Instant time) {
        return get(fastPutIfAbsentAsync(key, value, time));
    }

    @Override
    public RFuture<Boolean> fastPutIfAbsentAsync(K key, V value, Duration ttl) {
        return fastPutIfAbsentAsyncInternal(key, value, ttl.toMillis(), true);
    }

    @Override
    public RFuture<Boolean> fastPutIfAbsentAsync(K key, V value, Instant time) {
        return fastPutIfAbsentAsyncInternal(key, value, time.toEpochMilli(), false);
    }

    private RFuture<Boolean> fastPutIfAbsentAsyncInternal(K key, V value, long ms, boolean isDuration) {
        checkKey(key);
        checkValue(value);

        if (ms < 0) {
            throw new IllegalArgumentException("ttl can't be negative");
        }
        if (ms == 0) {
            return fastPutIfAbsentAsync(key, value);
        }

        RFuture<Boolean> future = fastPutIfAbsentOperationAsync(key, value, ms, isDuration);
        future = new CompletableFutureWrapper<>(future);
        if (hasNoWriter()) {
            return future;
        }
        MapWriterTask.Add task = new MapWriterTask.Add(key, value);
        return mapWriterFuture(future, task, Function.identity());
    }

    protected RFuture<Boolean> fastPutIfAbsentOperationAsync(K key, V value, long ms, boolean isDuration) {
        String name = getRawName(key);


        if (value == null) {
            return commandExecutor.evalReadAsync(name, StringCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                      "local currValue = redis.call('hget', KEYS[1], ARGV[1]); " +
                            "if currValue ~= false then " +
                                "return 0;" +
                            "end;" +
                            "return 1; ",
                    Collections.singletonList(name), encodeMapKey(key));
        }

        return commandExecutor.evalWriteAsync(name, StringCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                  "local currValue = redis.call('hget', KEYS[1], ARGV[2]); " +
                        "if currValue ~= false then " +
                            "return 0;" +
                        "end;" +
                        "redis.call('hset', KEYS[1], ARGV[2], ARGV[3]); " +
                        "redis.call(ARGV[4], KEYS[1], ARGV[1], 'fields', 1, ARGV[2]); " +
                        "return 1; ",
                Collections.singletonList(name),
                ms, encodeMapKey(key), encodeMapValue(value), getExpireCommand(isDuration));
    }

    @Override
    public long remainTimeToLive(K key) {
        return get(remainTimeToLiveAsync(key));
    }

    @Override
    public RFuture<Long> remainTimeToLiveAsync(K key) {
        checkKey(key);

        String name = getRawName(key);
        return commandExecutor.readAsync(name, StringCodec.INSTANCE, RedisCommands.HPTTL, name, "FIELDS", 1, encodeMapKey(key));
    }

    @Override
    public Map<K, Long> remainTimeToLive(Set<K> keys) {
        return get(remainTimeToLiveAsync(keys));
    }

    @Override
    public RFuture<Map<K, Long>> remainTimeToLiveAsync(Set<K> keys) {
        List<Object> plainKeys = new ArrayList<>(keys);

        List<Object> params = new ArrayList<>(keys.size() + 1);
        params.add(getRawName());
        params.add("FIELDS");
        params.add(plainKeys.size());
        encodeMapKeys(params, plainKeys);

        RedisCommand<Map<Object, Object>> command = new RedisCommand<>("HPTTL",
                new MapNativeAllDecoder(plainKeys, Long.class));
        return commandExecutor.readAsync(getRawName(), StringCodec.INSTANCE, command, params.toArray());
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> map, Duration ttl) {
        get(putAllAsync(map, ttl));
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> map, Instant time) {
        get(putAllAsync(map, time));
    }

    @Override
    public RFuture<Void> putAllAsync(Map<? extends K, ? extends V> map, Duration ttl) {
        return putAllAsyncInternal(map, ttl.toMillis(), true);
    }

    @Override
    public RFuture<Void> putAllAsync(Map<? extends K, ? extends V> map, Instant time) {
        return putAllAsyncInternal(map, time.toEpochMilli(), false);
    }

    private RFuture<Void> putAllAsyncInternal(Map<? extends K, ? extends V> map, long ms, boolean isDuration) {
        if (map.isEmpty()) {
            return new CompletableFutureWrapper<>((Void) null);
        }

        RFuture<Void> future = putAllOperationAsync(map, ms, isDuration);
        if (hasNoWriter()) {
            return future;
        }

        MapWriterTask listener = new MapWriterTask.Add(map);
        return mapWriterFuture(future, listener);
    }

    protected RFuture<Void> putAllOperationAsync(Map<? extends K, ? extends V> map, long ms, boolean isDuration) {
        List<Object> args = new ArrayList<>();
        args.add(ms);
        args.add(getExpireCommand(isDuration));
        encodeMapKeys(args, map);

        return commandExecutor.evalWriteAsync(name, StringCodec.INSTANCE, RedisCommands.EVAL_VOID,
                "for i = 3, #ARGV, 2 do " +
                        "redis.call('hset', KEYS[1], ARGV[i], ARGV[i + 1]); " +
                        "redis.call(ARGV[2], KEYS[1], ARGV[1], 'fields', 1, ARGV[i]); " +
                        "end; ",
                Collections.singletonList(name), args.toArray());
    }

    @Override
    public boolean expireEntry(K key, Duration ttl) {
        return get(expireEntryAsync(key, ttl));
    }

    @Override
    public boolean expireEntry(K key, Instant time) {
        return get(expireEntryAsync(key, time));
    }

    @Override
    public RFuture<Boolean> expireEntryAsync(K key, Duration ttl) {
        return expireEntryAsyncInternal(key, ttl.toMillis(), true);
    }

    @Override
    public RFuture<Boolean> expireEntryAsync(K key, Instant time) {
        return expireEntryAsyncInternal(key, time.toEpochMilli(), false);
    }

    private RFuture<Boolean> expireEntryAsyncInternal(K key, long ms, boolean isDuration) {
        String name = getRawName(key);

        return commandExecutor.evalWriteAsync(name, LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                "local expireSet = redis.call(ARGV[3], KEYS[1], ARGV[1], 'fields', 1, ARGV[2]); "
                        + "if #expireSet > 0 and expireSet[1] >= 1 then "
                        + "return 1;"
                        + "end; "
                        + "return 0; ",
                Arrays.asList(name),
                ms, encodeMapKey(key), getExpireCommand(isDuration));
    }

    @Override
    public boolean expireEntryIfNotSet(K key, Duration ttl) {
        return get(expireEntryIfNotSetAsync(key, ttl));
    }

    @Override
    public boolean expireEntryIfNotSet(K key, Instant time) {
        return get(expireEntryIfNotSetAsync(key, time));
    }

    @Override
    public RFuture<Boolean> expireEntryIfNotSetAsync(K key, Duration ttl) {
        return expireEntryAsync("NX", key, ttl.toMillis(), true);
    }

    @Override
    public RFuture<Boolean> expireEntryIfNotSetAsync(K key, Instant time) {
        return expireEntryAsync("NX", key, time.toEpochMilli(), false);
    }

    private RFuture<Boolean> expireEntryAsync(String param, K key, long ms, boolean isDuration) {
        String name = getRawName(key);

        return commandExecutor.evalWriteAsync(name, LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
            "local expireSet = redis.call(ARGV[4], KEYS[1], ARGV[1], ARGV[3], 'fields', 1, ARGV[2]); "
                + "if #expireSet > 0 and expireSet[1] >= 1 then "
                    + "return 1;"
                + "end; "
                + "return 0; ",
                Arrays.asList(name),
                ms, encodeMapKey(key), param, getExpireCommand(isDuration));
    }

    @Override
    public int expireEntries(Set<K> keys, Duration ttl) {
        return get(expireEntriesAsync(keys, ttl));
    }

    @Override
    public int expireEntries(Set<K> keys, Instant time) {
        return get(expireEntriesAsync(keys, time));
    }

    @Override
    public RFuture<Integer> expireEntriesAsync(Set<K> keys, Duration ttl) {
        return expireEntriesAsyncInternal(keys, ttl.toMillis(), true);
    }

    @Override
    public RFuture<Integer> expireEntriesAsync(Set<K> keys, Instant time) {
        return expireEntriesAsyncInternal(keys, time.toEpochMilli(), false);
    }

    private RFuture<Integer> expireEntriesAsyncInternal(Set<K> keys, long ms, boolean isDuration) {
        List<Object> args = new ArrayList<>();
        args.add(ms);
        args.add(getExpireCommand(isDuration));
        encodeMapKeys(args, keys);

        return commandExecutor.evalWriteAsync(name, LongCodec.INSTANCE, RedisCommands.EVAL_INTEGER,
                "local result = 0;"
                        + "for j = 3, #ARGV, 1 do "
                        + "local expireSet = redis.call(ARGV[2], KEYS[1], ARGV[1], 'fields', 1, ARGV[j]); "
                        + "if #expireSet > 0 and expireSet[1] >= 1 then "
                        + "result = result + 1;"
                        + "end; "
                        + "end; "
                        + "return result; ",
                Arrays.asList(name),
                args.toArray());
    }

    @Override
    public int expireEntriesIfNotSet(Set<K> keys, Duration ttl) {
        return get(expireEntriesIfNotSetAsync(keys, ttl));
    }

    @Override
    public int expireEntriesIfNotSet(Set<K> keys, Instant time) {
        return get(expireEntriesIfNotSetAsync(keys, time));
    }

    @Override
    public RFuture<Integer> expireEntriesIfNotSetAsync(Set<K> keys, Duration ttl) {
        return expireEntriesAsyncInternal("NX", keys, ttl.toMillis(), true);
    }

    @Override
    public RFuture<Integer> expireEntriesIfNotSetAsync(Set<K> keys, Instant time) {
        return expireEntriesAsyncInternal("NX", keys, time.toEpochMilli(), false);
    }

    private RFuture<Integer> expireEntriesAsyncInternal(String param, Set<K> keys, long ms, boolean isDuration) {
        List<Object> args = new ArrayList<>();
        args.add(param);
        args.add(ms);
        args.add(getExpireCommand(isDuration));
        encodeMapKeys(args, keys);

        return commandExecutor.evalWriteAsync(name, LongCodec.INSTANCE, RedisCommands.EVAL_INTEGER,
            "local result = 0;"
                + "for j = 4, #ARGV, 1 do "
                    + "local expireSet = redis.call(ARGV[3], KEYS[1], ARGV[2], ARGV[1], 'fields', 1, ARGV[j]); "
                    + "if #expireSet > 0 and expireSet[1] >= 1 then "
                        + "result = result + 1;"
                    + "end; "
                + "end; "
                + "return result; ",
                Arrays.asList(name),
                args.toArray());
    }

    @Override
    public boolean expireEntryIfGreater(K key, Duration ttl) {
        return get(expireEntryIfGreaterAsync(key, ttl));
    }

    @Override
    public boolean expireEntryIfGreater(K key, Instant time) {
        return get(expireEntryIfGreaterAsync(key, time));
    }

    @Override
    public boolean expireEntryIfLess(K key, Duration ttl) {
        return get(expireEntryIfLessAsync(key, ttl));
    }

    @Override
    public boolean expireEntryIfLess(K key, Instant time) {
        return get(expireEntryIfLessAsync(key, time));
    }

    @Override
    public int expireEntriesIfGreater(Set<K> keys, Duration ttl) {
        return get(expireEntriesIfGreaterAsync(keys, ttl));
    }

    @Override
    public int expireEntriesIfGreater(Set<K> keys, Instant time) {
        return get(expireEntriesIfGreaterAsync(keys, time));
    }

    @Override
    public int expireEntriesIfLess(Set<K> keys, Duration ttl) {
        return get(expireEntriesIfLessAsync(keys, ttl));
    }

    @Override
    public int expireEntriesIfLess(Set<K> keys, Instant time) {
        return get(expireEntriesIfLessAsync(keys, time));
    }

    @Override
    public RFuture<Boolean> expireEntryIfGreaterAsync(K key, Duration ttl) {
        return expireEntryAsync("GT", key, ttl.toMillis(), true);
    }

    @Override
    public RFuture<Boolean> expireEntryIfGreaterAsync(K key, Instant time) {
        return expireEntryAsync("GT", key, time.toEpochMilli(), false);
    }

    @Override
    public RFuture<Boolean> expireEntryIfLessAsync(K key, Duration ttl) {
        return expireEntryAsync("LT", key, ttl.toMillis(), true);
    }

    @Override
    public RFuture<Boolean> expireEntryIfLessAsync(K key, Instant time) {
        return expireEntryAsync("LT", key, time.toEpochMilli(), false);
    }

    @Override
    public RFuture<Integer> expireEntriesIfGreaterAsync(Set<K> keys, Duration ttl) {
        return expireEntriesAsyncInternal("GT", keys, ttl.toMillis(), true);
    }

    @Override
    public RFuture<Integer> expireEntriesIfGreaterAsync(Set<K> keys, Instant time) {
        return expireEntriesAsyncInternal("GT", keys, time.toEpochMilli(), false);
    }

    @Override
    public RFuture<Integer> expireEntriesIfLessAsync(Set<K> keys, Duration ttl) {
        return expireEntriesAsyncInternal("LT", keys, ttl.toMillis(), true);
    }

    @Override
    public RFuture<Integer> expireEntriesIfLessAsync(Set<K> keys, Instant time) {
        return expireEntriesAsyncInternal("LT", keys, time.toEpochMilli(), false);
    }

    @Override
    public Boolean clearExpire(K key) {
        return get(clearExpireAsync(key));
    }

    @Override
    public RFuture<Boolean> clearExpireAsync(K key) {
        String name = getRawName(key);
        return commandExecutor.writeAsync(name, LongCodec.INSTANCE, RedisCommands.HPERSIST, name, "FIELDS", 1, encodeMapKey(key));
    }

    @Override
    public Map<K, Boolean> clearExpire(Set<K> keys) {
        return get(clearExpireAsync(keys));
    }

    @Override
    public RFuture<Map<K, Boolean>> clearExpireAsync(Set<K> keys) {
        List<Object> plainKeys = new ArrayList<>(keys);

        List<Object> params = new ArrayList<>(keys.size() + 1);
        params.add(getRawName());
        params.add("FIELDS");
        params.add(plainKeys.size());
        encodeMapKeys(params, plainKeys);

        RedisCommand<Map<Object, Object>> command = new RedisCommand<>("HPERSIST",
                new MapNativeAllDecoder(plainKeys, Boolean.class));
        return commandExecutor.readAsync(getRawName(), StringCodec.INSTANCE, command, params.toArray());
    }

    @Override
    public int addListener(ObjectListener listener) {
        if (listener instanceof MapExpiredListener) {
            return addListener("__keyevent@*:hexpired", (MapExpiredListener) listener, MapExpiredListener::onExpired);
        }

        return super.addListener(listener);
    }

    @Override
    public RFuture<Integer> addListenerAsync(ObjectListener listener) {
        if (listener instanceof MapExpiredListener) {
            return addListenerAsync("__keyevent@*:hexpired", (MapExpiredListener) listener, MapExpiredListener::onExpired);
        }

        return super.addListenerAsync(listener);
    }

    @Override
    public void removeListener(int listenerId) {
        removeListener(listenerId, "__keyevent@*:hexpired");
        super.removeListener(listenerId);
    }

    @Override
    public RFuture<Void> removeListenerAsync(int listenerId) {
        return removeListenerAsync(super.removeListenerAsync(listenerId), listenerId, "__keyevent@*:hexpired");
    }

    @Override
    public V compute(K key, Duration ttl, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        return computeInternal(key, ttl.toMillis(), true, remappingFunction);
    }

    @Override
    public V compute(K key, Instant time, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        return computeInternal(key, time.toEpochMilli(), false, remappingFunction);
    }


    private V computeInternal(K key, long ms, boolean isDuration, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        checkNotBatch();

        checkKey(key);
        Objects.requireNonNull(remappingFunction);

        RLock lock = getLock(key);
        lock.lock();
        try {
            V oldValue = get(key);

            V newValue = remappingFunction.apply(key, oldValue);
            if (newValue == null) {
                if (oldValue != null) {
                    fastRemove(key);
                }
            } else {
                get(fastPutAsyncInternal(key, newValue, ms, isDuration));
            }
            return newValue;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public RFuture<V> computeAsync(K key, Duration ttl, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        return computeAsyncInternal(key, ttl.toMillis(), true, remappingFunction);
    }

    @Override
    public RFuture<V> computeAsync(K key, Instant time,
                                   BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        return computeAsyncInternal(key, time.toEpochMilli(), false, remappingFunction);
    }

    private RFuture<V> computeAsyncInternal(K key, long ms, boolean isDuration,
                                   BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        checkNotBatch();
        checkKey(key);
        Objects.requireNonNull(remappingFunction);

        RLock lock = getLock(key);
        long threadId = Thread.currentThread().getId();
        CompletionStage<V> f = (CompletionStage<V>) lock.lockAsync(threadId)
                .thenCompose(r -> {
                    RFuture<V> oldValueFuture = getAsync(key, threadId);
                    return oldValueFuture.thenCompose(oldValue -> {
                        return CompletableFuture.supplyAsync(() -> remappingFunction.apply(key, oldValue), getServiceManager().getExecutor())
                                .thenCompose(newValue -> {
                                    if (newValue == null) {
                                        if (oldValue != null) {
                                            return fastRemoveAsync(key)
                                                    .thenApply(rr -> newValue);
                                        }
                                        return CompletableFuture.completedFuture(newValue);
                                    }
                                    return fastPutAsyncInternal(key, newValue, ms, isDuration)
                                            .thenApply(rr -> newValue);
                                });
                    }).whenComplete((c, e) -> {
                        lock.unlockAsync(threadId);
                    });
                });

        return new CompletableFutureWrapper<>(f);
    }

    @Override
    public V computeIfAbsent(K key, Duration ttl, Function<? super K, ? extends V> mappingFunction) {
        return computeIfAbsentInternal(key, ttl.toMillis(), true, mappingFunction);
    }

    @Override
    public V computeIfAbsent(K key, Instant time, Function<? super K, ? extends V> mappingFunction) {
        return computeIfAbsentInternal(key, time.toEpochMilli(), false, mappingFunction);
    }

    private V computeIfAbsentInternal(K key, long ms, boolean isDuration, Function<? super K, ? extends V> mappingFunction) {
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
                    V r = get(putIfAbsentAsyncInternal(key, newValue, ms, isDuration));
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
        return computeIfAbsentAsyncInternal(key, ttl.toMillis(), true, mappingFunction);
    }

    @Override
    public RFuture<V> computeIfAbsentAsync(K key, Instant time, Function<? super K, ? extends V> mappingFunction) {
        return computeIfAbsentAsyncInternal(key, time.toEpochMilli(), false, mappingFunction);
    }

    public RFuture<V> computeIfAbsentAsyncInternal(K key, long ms, boolean isDuration, Function<? super K, ? extends V> mappingFunction) {
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
                                        return putIfAbsentAsyncInternal(key, newValue, ms, isDuration).thenApply(rr -> {
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

    private String getExpireCommand(boolean isDuration) {
        if (isDuration) {
            return "hpexpire";
        }

        return "hpexpireat";
    }

    @Override
    public V putIfExist(K key, V value, Duration ttl) {
        return get(putIfExistAsync(key, value, ttl));
    }

    @Override
    public V putIfExist(K key, V value, Instant time) {
        return get(putIfExistAsync(key, value, time));
    }

    @Override
    public RFuture<V> putIfExistAsync(K key, V value, Duration ttl) {
        return putIfExistAsyncInternal(key, value, ttl.toMillis(), true);
    }

    @Override
    public RFuture<V> putIfExistAsync(K key, V value, Instant time) {
        return putIfExistAsyncInternal(key, value, time.toEpochMilli(), false);
    }

    private RFuture<V> putIfExistAsyncInternal(K key, V value, long ms, boolean isDuration) {
        checkKey(key);

        if (ms < 0) {
            throw new IllegalArgumentException("ms can't be negative");
        }
        if (ms == 0) {
            return putIfExistsAsync(key, value);
        }

        RFuture<V> future = putIfExistOperationAsync(key, value, ms, isDuration);
        future = new CompletableFutureWrapper<>(future);
        if (hasNoWriter()) {
            return future;
        }
        MapWriterTask.Add task = new MapWriterTask.Add(key, value);
        return mapWriterFuture(future, task, r -> r != null);
    }

    protected RFuture<V> putIfExistOperationAsync(K key, V value, long ms, boolean isDuration) {
        String name = getRawName(key);

        if (value == null) {
            return commandExecutor.evalWriteAsync(name, codec, RedisCommands.EVAL_MAP_VALUE,
                      "local currValue = redis.call('hget', KEYS[1], ARGV[1]); " +
                            "if currValue == false then " +
                                "return nil;" +
                            "end;" +
                            "redis.call('hdel', KEYS[1], ARGV[1]); " +
                            "return currValue; ",
                    Collections.singletonList(name), encodeMapKey(key));
        }

        return commandExecutor.evalWriteAsync(name, codec, RedisCommands.EVAL_MAP_VALUE,
                  "local currValue = redis.call('hget', KEYS[1], ARGV[2]); " +
                        "if currValue == false then " +
                            "return nil;" +
                        "end;" +
                        "redis.call('hset', KEYS[1], ARGV[2], ARGV[3]); " +
                        "redis.call(ARGV[4], KEYS[1], ARGV[1], 'fields', 1, ARGV[2]); " +
                        "return currValue; ",
                Collections.singletonList(name),
                ms, encodeMapKey(key), encodeMapValue(value), getExpireCommand(isDuration));
    }

}
