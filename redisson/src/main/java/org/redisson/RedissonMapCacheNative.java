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
    public RFuture<V> putAsync(K key, V value, Duration ttl) {
        checkKey(key);
        checkValue(value);

        if (ttl.toMillis() < 0) {
            throw new IllegalArgumentException("ttl can't be negative");
        }
        if (ttl.toMillis() == 0) {
            return putAsync(key, value);
        }

        RFuture<V> future = putOperationAsync(key, value, ttl);
        future = new CompletableFutureWrapper<>(future);
        if (hasNoWriter()) {
            return future;
        }

        MapWriterTask.Add listener = new MapWriterTask.Add(key, value);
        return mapWriterFuture(future, listener);
    }

    protected RFuture<V> putOperationAsync(K key, V value, Duration ttl) {
        String name = getRawName(key);

        return commandExecutor.evalWriteAsync(name, codec, RedisCommands.EVAL_OBJECT,
                    "local currValue = redis.call('hget', KEYS[1], ARGV[2]); "
                        + "redis.call('hset', KEYS[1], ARGV[2], ARGV[3]); "
                        + "redis.call('hpexpire', KEYS[1], ARGV[1], 'fields', 1, ARGV[2]); "
                        + "return currValue; ",
                Collections.singletonList(name),
                ttl.toMillis(), encodeMapKey(key), encodeMapValue(value));
    }

    @Override
    public boolean fastPut(K key, V value, Duration ttl) {
        return get(fastPutAsync(key, value, ttl));
    }

    @Override
    public RFuture<Boolean> fastPutAsync(K key, V value, Duration ttl) {
        checkKey(key);
        checkValue(value);

        if (ttl.toMillis() < 0) {
            throw new IllegalArgumentException("ttl can't be negative");
        }
        if (ttl.toMillis() == 0) {
            return fastPutAsync(key, value);
        }

        RFuture<Boolean> future = fastPutOperationAsync(key, value, ttl);
        future = new CompletableFutureWrapper<>(future);
        if (hasNoWriter()) {
            return future;
        }

        return mapWriterFuture(future, new MapWriterTask.Add(key, value));
    }

    protected RFuture<Boolean> fastPutOperationAsync(K key, V value, Duration ttl) {
        String name = getRawName(key);

        return commandExecutor.evalWriteAsync(name, StringCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
          "local added = redis.call('hset', KEYS[1], ARGV[2], ARGV[3]); " +
                "redis.call('hpexpire', KEYS[1], ARGV[1], 'fields', 1, ARGV[2]); " +
                "return added;",
             Collections.singletonList(name),
                ttl.toMillis(), encodeMapKey(key), encodeMapValue(value));
    }

    @Override
    public V putIfAbsent(K key, V value, Duration ttl) {
        return get(putIfAbsentAsync(key, value, ttl));
    }

    @Override
    public RFuture<V> putIfAbsentAsync(K key, V value, Duration ttl) {
        checkKey(key);
        checkValue(value);

        if (ttl.toMillis() < 0) {
            throw new IllegalArgumentException("ttl can't be negative");
        }
        if (ttl.toMillis() == 0) {
            return putIfAbsentAsync(key, value);
        }

        RFuture<V> future = putIfAbsentOperationAsync(key, value, ttl);
        future = new CompletableFutureWrapper<>(future);
        if (hasNoWriter()) {
            return future;
        }
        MapWriterTask.Add task = new MapWriterTask.Add(key, value);
        return mapWriterFuture(future, task, r -> r == null);
    }

    protected RFuture<V> putIfAbsentOperationAsync(K key, V value, Duration ttl) {
        String name = getRawName(key);

        if (value == null) {
            return commandExecutor.evalWriteAsync(name, codec, RedisCommands.EVAL_MAP_VALUE,
                      "local currValue = redis.call('hget', KEYS[1], ARGV[1]); " +
                            "if currValue ~= false then " +
                                "return currValue;" +
                            "end;" +
                            "redis.call('hdel', KEYS[1], ARGV[1]); " +
                            "return nil; ",
                    Collections.singletonList(name), encodeMapKey(key));
        }

        return commandExecutor.evalWriteAsync(name, codec, RedisCommands.EVAL_MAP_VALUE,
                  "local currValue = redis.call('hget', KEYS[1], ARGV[2]); " +
                        "if currValue ~= false then " +
                            "return currValue;" +
                        "end;" +
                        "redis.call('hset', KEYS[1], ARGV[2], ARGV[3]); " +
                        "redis.call('hpexpire', KEYS[1], ARGV[1], 'fields', 1, ARGV[2]); " +
                        "return nil; ",
                Collections.singletonList(name),
                ttl.toMillis(), encodeMapKey(key), encodeMapValue(value));
    }

    @Override
    public boolean fastPutIfAbsent(K key, V value, Duration ttl) {
        return get(fastPutIfAbsentAsync(key, value, ttl));
    }

    @Override
    public RFuture<Boolean> fastPutIfAbsentAsync(K key, V value, Duration ttl) {
        checkKey(key);
        checkValue(value);

        if (ttl.toMillis() < 0) {
            throw new IllegalArgumentException("ttl can't be negative");
        }
        if (ttl.toMillis() == 0) {
            return fastPutIfAbsentAsync(key, value);
        }

        RFuture<Boolean> future = fastPutIfAbsentOperationAsync(key, value, ttl);
        future = new CompletableFutureWrapper<>(future);
        if (hasNoWriter()) {
            return future;
        }
        MapWriterTask.Add task = new MapWriterTask.Add(key, value);
        return mapWriterFuture(future, task, Function.identity());
    }

    protected RFuture<Boolean> fastPutIfAbsentOperationAsync(K key, V value, Duration ttl) {
        String name = getRawName(key);

        if (value == null) {
            return commandExecutor.evalWriteAsync(name, StringCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                      "local currValue = redis.call('hget', KEYS[1], ARGV[1]); " +
                            "if currValue ~= false then " +
                                "return 0;" +
                            "end;" +
                            "redis.call('hdel', KEYS[1], ARGV[1]); " +
                            "return 1; ",
                    Collections.singletonList(name), encodeMapKey(key));
        }

        return commandExecutor.evalWriteAsync(name, StringCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                  "local currValue = redis.call('hget', KEYS[1], ARGV[2]); " +
                        "if currValue ~= false then " +
                            "return 0;" +
                        "end;" +
                        "redis.call('hset', KEYS[1], ARGV[2], ARGV[3]); " +
                        "redis.call('hpexpire', KEYS[1], ARGV[1], 'fields', 1, ARGV[2]); " +
                        "return 1; ",
                Collections.singletonList(name),
                ttl.toMillis(), encodeMapKey(key), encodeMapValue(value));
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
    public RFuture<Void> putAllAsync(Map<? extends K, ? extends V> map, Duration ttl) {
        if (map.isEmpty()) {
            return new CompletableFutureWrapper<>((Void) null);
        }

        RFuture<Void> future = putAllOperationAsync(map, ttl);
        if (hasNoWriter()) {
            return future;
        }

        MapWriterTask listener = new MapWriterTask.Add(map);
        return mapWriterFuture(future, listener);
    }

    protected RFuture<Void> putAllOperationAsync(Map<? extends K, ? extends V> map, Duration ttl) {
        List<Object> args = new ArrayList<>();
        args.add(ttl.toMillis());
        encodeMapKeys(args, map);

        return commandExecutor.evalWriteAsync(name, StringCodec.INSTANCE, RedisCommands.EVAL_VOID,
              "for i = 2, #ARGV, 2 do " +
                        "redis.call('hset', KEYS[1], ARGV[i], ARGV[i + 1]); " +
                        "redis.call('hpexpire', KEYS[1], ARGV[1], 'fields', 1, ARGV[i]); " +
                    "end; ",
                Collections.singletonList(name), args.toArray());
    }

    @Override
    public boolean expireEntry(K key, Duration ttl) {
        return get(expireEntryAsync(key, ttl));
    }

    @Override
    public RFuture<Boolean> expireEntryAsync(K key, Duration ttl) {
        String name = getRawName(key);
        return commandExecutor.evalWriteAsync(name, LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
            "local expireSet = redis.call('hpexpire', KEYS[1], ARGV[1], 'fields', 1, ARGV[2]); "
                + "if #expireSet > 0 and expireSet[1] >= 1 then "
                    + "return 1;"
                + "end; "
                + "return 0; ",
                Arrays.asList(name),
                ttl.toMillis(), encodeMapKey(key));
    }

    @Override
    public boolean expireEntryIfNotSet(K key, Duration ttl) {
        return get(expireEntryIfNotSetAsync(key, ttl));
    }

    @Override
    public RFuture<Boolean> expireEntryIfNotSetAsync(K key, Duration ttl) {
        return expireEntryAsync("NX", key, ttl);
    }

    private RFuture<Boolean> expireEntryAsync(String param, K key, Duration ttl) {
        String name = getRawName(key);
        return commandExecutor.evalWriteAsync(name, LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
            "local expireSet = redis.call('hpexpire', KEYS[1], ARGV[1], ARGV[3], 'fields', 1, ARGV[2]); "
                + "if #expireSet > 0 and expireSet[1] >= 1 then "
                    + "return 1;"
                + "end; "
                + "return 0; ",
                Arrays.asList(name),
                ttl.toMillis(), encodeMapKey(key), param);
    }

    @Override
    public int expireEntries(Set<K> keys, Duration ttl) {
        return get(expireEntriesAsync(keys, ttl));
    }

    @Override
    public RFuture<Integer> expireEntriesAsync(Set<K> keys, Duration ttl) {
        List<Object> args = new ArrayList<>();
        args.add(ttl.toMillis());
        encodeMapKeys(args, keys);

        return commandExecutor.evalWriteAsync(name, LongCodec.INSTANCE, RedisCommands.EVAL_INTEGER,
            "local result = 0;"
                + "for j = 2, #ARGV, 1 do "
                    + "local expireSet = redis.call('hpexpire', KEYS[1], ARGV[1], 'fields', 1, ARGV[j]); "
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
    public RFuture<Integer> expireEntriesIfNotSetAsync(Set<K> keys, Duration ttl) {
        return expireEntriesAsync("NX", keys, ttl);
    }

    private RFuture<Integer> expireEntriesAsync(String param, Set<K> keys, Duration ttl) {
        List<Object> args = new ArrayList<>();
        args.add(param);
        args.add(ttl.toMillis());
        encodeMapKeys(args, keys);

        return commandExecutor.evalWriteAsync(name, LongCodec.INSTANCE, RedisCommands.EVAL_INTEGER,
            "local result = 0;"
                + "for j = 3, #ARGV, 1 do "
                    + "local expireSet = redis.call('hpexpire', KEYS[1], ARGV[2], ARGV[1], 'fields', 1, ARGV[j]); "
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
    public boolean expireEntryIfLess(K key, Duration ttl) {
        return get(expireEntryIfLessAsync(key, ttl));
    }

    @Override
    public int expireEntriesIfGreater(Set<K> keys, Duration ttl) {
        return get(expireEntriesIfGreaterAsync(keys, ttl));
    }

    @Override
    public int expireEntriesIfLess(Set<K> keys, Duration ttl) {
        return get(expireEntriesIfLessAsync(keys, ttl));
    }

    @Override
    public RFuture<Boolean> expireEntryIfGreaterAsync(K key, Duration ttl) {
        return expireEntryAsync("GT", key, ttl);
    }

    @Override
    public RFuture<Boolean> expireEntryIfLessAsync(K key, Duration ttl) {
        return expireEntryAsync("LT", key, ttl);
    }

    @Override
    public RFuture<Integer> expireEntriesIfGreaterAsync(Set<K> keys, Duration ttl) {
        return expireEntriesAsync("GT", keys, ttl);
    }

    @Override
    public RFuture<Integer> expireEntriesIfLessAsync(Set<K> keys, Duration ttl) {
        return expireEntriesAsync("LT", keys, ttl);
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
            return addListener("__keyevent@*:hexpire", (MapExpiredListener) listener, MapExpiredListener::onExpired);
        }

        return super.addListener(listener);
    }

    @Override
    public RFuture<Integer> addListenerAsync(ObjectListener listener) {
        if (listener instanceof MapExpiredListener) {
            return addListenerAsync("__keyevent@*:hexpire", (MapExpiredListener) listener, MapExpiredListener::onExpired);
        }

        return super.addListenerAsync(listener);
    }

    @Override
    public void removeListener(int listenerId) {
        removeListener(listenerId, "__keyevent@*:hexpire");
        super.removeListener(listenerId);
    }

    @Override
    public RFuture<Void> removeListenerAsync(int listenerId) {
        return removeListenerAsync(super.removeListenerAsync(listenerId), listenerId, "__keyevent@*:hexpire");
    }


}
