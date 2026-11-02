/**
 * Copyright (c) 2013-2026 Nikita Koksharov
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

import org.redisson.api.ObjectListener;
import org.redisson.api.RFuture;
import org.redisson.api.RSetCacheNative;
import org.redisson.api.RedissonClient;
import org.redisson.api.listener.SetMemberClearExpireListener;
import org.redisson.api.listener.SetMemberExpiredListener;
import org.redisson.api.set.SetAddArgs;
import org.redisson.api.set.SetAddParams;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.decoder.ContainsSetDecoder;
import org.redisson.client.protocol.decoder.MultiDecoder;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.connection.decoder.MapNativeAllDecoder;
import org.redisson.misc.CompletableFutureWrapper;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 *
 * @author Nikita Koksharov
 *
 * @param <V> value type
 */
public class RedissonSetCacheNative<V> extends RedissonSet<V> implements RSetCacheNative<V> {

    private static final MultiDecoder<Integer> APPLIED_COUNT_DECODER = (parts, state) -> {
        int count = 0;
        for (Object part : parts) {
            if ((Long) part >= 1) {
                count++;
            }
        }
        return count;
    };

    private static final RedisCommand<Integer> SPEXPIRE_COUNT = new RedisCommand<>("SPEXPIRE", APPLIED_COUNT_DECODER);
    private static final RedisCommand<Integer> SPEXPIREAT_COUNT = new RedisCommand<>("SPEXPIREAT", APPLIED_COUNT_DECODER);

    public RedissonSetCacheNative(CommandAsyncExecutor commandExecutor, String name, RedissonClient redisson) {
        super(commandExecutor, name, redisson);
    }

    public RedissonSetCacheNative(Codec codec, CommandAsyncExecutor commandExecutor, String name, RedissonClient redisson) {
        super(codec, commandExecutor, name, redisson);
    }

    @Override
    public boolean add(V value, Duration ttl) {
        return get(addAsync(value, ttl));
    }

    @Override
    public boolean add(V value, Instant time) {
        return get(addAsync(value, time));
    }

    @Override
    public RFuture<Boolean> addAsync(V value, Duration ttl) {
        return addOrReplaceAsync(value, durationExpiration(ttl));
    }

    @Override
    public RFuture<Boolean> addAsync(V value, Instant time) {
        return addOrReplaceAsync(value, Arrays.asList("PXAT", time.toEpochMilli()));
    }

    private RFuture<Boolean> addOrReplaceAsync(V value, List<Object> expiration) {
        List<Object> args = new ArrayList<>(expiration.size() + 3);
        args.addAll(expiration);
        args.add("MEMBERS");
        args.add(1);
        args.add(encode(value));

        String name = getRawName(value);
        return commandExecutor.evalWriteAsync(name, StringCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                    "local added = redis.call('saddex', KEYS[1], 'MNX', unpack(ARGV)) "
                        + "if added == 0 then "
                        +     "redis.call('saddex', KEYS[1], unpack(ARGV)) "
                        + "end "
                        + "return added", Collections.singletonList(name), args.toArray());
    }

    @Override
    public boolean addIfAbsent(V value, Duration ttl) {
        return get(addIfAbsentAsync(value, ttl));
    }

    @Override
    public boolean addIfAbsent(V value, Instant time) {
        return get(addIfAbsentAsync(value, time));
    }

    @Override
    public RFuture<Boolean> addIfAbsentAsync(V value, Duration ttl) {
        return saddexAsync(getRawName(value), "MNX", durationExpiration(ttl), Collections.singletonList(value));
    }

    @Override
    public RFuture<Boolean> addIfAbsentAsync(V value, Instant time) {
        return saddexAsync(getRawName(value), "MNX", Arrays.asList("PXAT", time.toEpochMilli()),
                Collections.singletonList(value));
    }

    @Override
    public boolean addIfExists(V value, Duration ttl) {
        return get(addIfExistsAsync(value, ttl));
    }

    @Override
    public boolean addIfExists(V value, Instant time) {
        return get(addIfExistsAsync(value, time));
    }

    @Override
    public RFuture<Boolean> addIfExistsAsync(V value, Duration ttl) {
        return saddexAsync(getRawName(value), "MXX", durationExpiration(ttl), Collections.singletonList(value));
    }

    @Override
    public RFuture<Boolean> addIfExistsAsync(V value, Instant time) {
        return saddexAsync(getRawName(value), "MXX", Arrays.asList("PXAT", time.toEpochMilli()),
                Collections.singletonList(value));
    }

    @Override
    public boolean addAll(SetAddArgs<V> args) {
        return get(addAllAsync(args));
    }

    @Override
    public RFuture<Boolean> addAllAsync(SetAddArgs<V> args) {
        return addAllAsync(null, args);
    }

    @Override
    public boolean addIfAllAbsent(SetAddArgs<V> args) {
        return get(addIfAllAbsentAsync(args));
    }

    @Override
    public RFuture<Boolean> addIfAllAbsentAsync(SetAddArgs<V> args) {
        return addAllAsync("MNX", args);
    }

    @Override
    public boolean addIfAllExist(SetAddArgs<V> args) {
        return get(addIfAllExistAsync(args));
    }

    @Override
    public RFuture<Boolean> addIfAllExistAsync(SetAddArgs<V> args) {
        return addAllAsync("MXX", args);
    }

    private RFuture<Boolean> addAllAsync(String condition, SetAddArgs<V> args) {
        SetAddParams<V> params = (SetAddParams<V>) args;
        if (params.getValues().isEmpty()) {
            return new CompletableFutureWrapper<>(false);
        }

        List<Object> expiration = Collections.emptyList();
        if (params.isKeepTTL()) {
            expiration = Collections.singletonList("KEEPTTL");
        } else if (params.getTimeToLive() != null) {
            expiration = Arrays.asList("PX", params.getTimeToLive().toMillis());
        } else if (params.getExpireAt() != null) {
            expiration = Arrays.asList("PXAT", params.getExpireAt().toEpochMilli());
        }
        return saddexAsync(getRawName(), condition, expiration, params.getValues());
    }

    private RFuture<Boolean> saddexAsync(String name, String condition, List<Object> expiration,
                                         Collection<?> values) {
        List<Object> args = new ArrayList<>(values.size() + expiration.size() + 4);
        args.add(name);
        if (condition != null) {
            args.add(condition);
        }
        args.addAll(expiration);
        args.add("MEMBERS");
        args.add(values.size());
        encode(args, values);
        return commandExecutor.writeAsync(name, StringCodec.INSTANCE, RedisCommands.SADDEX, args.toArray());
    }

    private static List<Object> durationExpiration(Duration ttl) {
        if (ttl.isNegative()) {
            throw new IllegalArgumentException("ttl can't be negative");
        }
        if (ttl.isZero()) {
            return Collections.emptyList();
        }
        return Arrays.asList("PX", ttl.toMillis());
    }

    @Override
    public boolean expireValue(V value, Duration ttl) {
        return get(expireValueAsync(value, ttl));
    }

    @Override
    public boolean expireValue(V value, Instant time) {
        return get(expireValueAsync(value, time));
    }

    @Override
    public RFuture<Boolean> expireValueAsync(V value, Duration ttl) {
        return expireValueAsync(null, value, ttl);
    }

    @Override
    public RFuture<Boolean> expireValueAsync(V value, Instant time) {
        return expireValueAsync(null, value, time);
    }

    @Override
    public boolean expireValueIfSet(V value, Duration ttl) {
        return get(expireValueIfSetAsync(value, ttl));
    }

    @Override
    public boolean expireValueIfSet(V value, Instant time) {
        return get(expireValueIfSetAsync(value, time));
    }

    @Override
    public RFuture<Boolean> expireValueIfSetAsync(V value, Duration ttl) {
        return expireValueAsync("XX", value, ttl);
    }

    @Override
    public RFuture<Boolean> expireValueIfSetAsync(V value, Instant time) {
        return expireValueAsync("XX", value, time);
    }

    @Override
    public boolean expireValueIfNotSet(V value, Duration ttl) {
        return get(expireValueIfNotSetAsync(value, ttl));
    }

    @Override
    public boolean expireValueIfNotSet(V value, Instant time) {
        return get(expireValueIfNotSetAsync(value, time));
    }

    @Override
    public RFuture<Boolean> expireValueIfNotSetAsync(V value, Duration ttl) {
        return expireValueAsync("NX", value, ttl);
    }

    @Override
    public RFuture<Boolean> expireValueIfNotSetAsync(V value, Instant time) {
        return expireValueAsync("NX", value, time);
    }

    @Override
    public boolean expireValueIfGreater(V value, Duration ttl) {
        return get(expireValueIfGreaterAsync(value, ttl));
    }

    @Override
    public boolean expireValueIfGreater(V value, Instant time) {
        return get(expireValueIfGreaterAsync(value, time));
    }

    @Override
    public RFuture<Boolean> expireValueIfGreaterAsync(V value, Duration ttl) {
        return expireValueAsync("GT", value, ttl);
    }

    @Override
    public RFuture<Boolean> expireValueIfGreaterAsync(V value, Instant time) {
        return expireValueAsync("GT", value, time);
    }

    @Override
    public boolean expireValueIfLess(V value, Duration ttl) {
        return get(expireValueIfLessAsync(value, ttl));
    }

    @Override
    public boolean expireValueIfLess(V value, Instant time) {
        return get(expireValueIfLessAsync(value, time));
    }

    @Override
    public RFuture<Boolean> expireValueIfLessAsync(V value, Duration ttl) {
        return expireValueAsync("LT", value, ttl);
    }

    @Override
    public RFuture<Boolean> expireValueIfLessAsync(V value, Instant time) {
        return expireValueAsync("LT", value, time);
    }

    private RFuture<Boolean> expireValueAsync(String condition, V value, Duration ttl) {
        return expireAsync(getRawName(value), RedisCommands.SPEXPIRE, expireMillis(ttl), condition,
                Collections.singletonList(value));
    }

    private RFuture<Boolean> expireValueAsync(String condition, V value, Instant time) {
        return expireAsync(getRawName(value), RedisCommands.SPEXPIREAT, time.toEpochMilli(), condition,
                Collections.singletonList(value));
    }

    @Override
    public int expireValues(Set<V> values, Duration ttl) {
        return get(expireValuesAsync(values, ttl));
    }

    @Override
    public int expireValues(Set<V> values, Instant time) {
        return get(expireValuesAsync(values, time));
    }

    @Override
    public RFuture<Integer> expireValuesAsync(Set<V> values, Duration ttl) {
        return expireValuesAsync(null, values, ttl);
    }

    @Override
    public RFuture<Integer> expireValuesAsync(Set<V> values, Instant time) {
        return expireValuesAsync(null, values, time);
    }

    @Override
    public int expireValuesIfSet(Set<V> values, Duration ttl) {
        return get(expireValuesIfSetAsync(values, ttl));
    }

    @Override
    public int expireValuesIfSet(Set<V> values, Instant time) {
        return get(expireValuesIfSetAsync(values, time));
    }

    @Override
    public RFuture<Integer> expireValuesIfSetAsync(Set<V> values, Duration ttl) {
        return expireValuesAsync("XX", values, ttl);
    }

    @Override
    public RFuture<Integer> expireValuesIfSetAsync(Set<V> values, Instant time) {
        return expireValuesAsync("XX", values, time);
    }

    @Override
    public int expireValuesIfNotSet(Set<V> values, Duration ttl) {
        return get(expireValuesIfNotSetAsync(values, ttl));
    }

    @Override
    public int expireValuesIfNotSet(Set<V> values, Instant time) {
        return get(expireValuesIfNotSetAsync(values, time));
    }

    @Override
    public RFuture<Integer> expireValuesIfNotSetAsync(Set<V> values, Duration ttl) {
        return expireValuesAsync("NX", values, ttl);
    }

    @Override
    public RFuture<Integer> expireValuesIfNotSetAsync(Set<V> values, Instant time) {
        return expireValuesAsync("NX", values, time);
    }

    @Override
    public int expireValuesIfGreater(Set<V> values, Duration ttl) {
        return get(expireValuesIfGreaterAsync(values, ttl));
    }

    @Override
    public int expireValuesIfGreater(Set<V> values, Instant time) {
        return get(expireValuesIfGreaterAsync(values, time));
    }

    @Override
    public RFuture<Integer> expireValuesIfGreaterAsync(Set<V> values, Duration ttl) {
        return expireValuesAsync("GT", values, ttl);
    }

    @Override
    public RFuture<Integer> expireValuesIfGreaterAsync(Set<V> values, Instant time) {
        return expireValuesAsync("GT", values, time);
    }

    @Override
    public int expireValuesIfLess(Set<V> values, Duration ttl) {
        return get(expireValuesIfLessAsync(values, ttl));
    }

    @Override
    public int expireValuesIfLess(Set<V> values, Instant time) {
        return get(expireValuesIfLessAsync(values, time));
    }

    @Override
    public RFuture<Integer> expireValuesIfLessAsync(Set<V> values, Duration ttl) {
        return expireValuesAsync("LT", values, ttl);
    }

    @Override
    public RFuture<Integer> expireValuesIfLessAsync(Set<V> values, Instant time) {
        return expireValuesAsync("LT", values, time);
    }

    private RFuture<Integer> expireValuesAsync(String condition, Set<V> values, Duration ttl) {
        long ms = expireMillis(ttl);
        if (values.isEmpty()) {
            return new CompletableFutureWrapper<>(0);
        }
        return expireAsync(getRawName(), SPEXPIRE_COUNT, ms, condition, values);
    }

    private RFuture<Integer> expireValuesAsync(String condition, Set<V> values, Instant time) {
        if (values.isEmpty()) {
            return new CompletableFutureWrapper<>(0);
        }
        return expireAsync(getRawName(), SPEXPIREAT_COUNT, time.toEpochMilli(), condition, values);
    }

    private static long expireMillis(Duration ttl) {
        if (ttl.isNegative()) {
            throw new IllegalArgumentException("ttl can't be negative");
        }
        return ttl.toMillis();
    }

    private <R> RFuture<R> expireAsync(String name, RedisCommand<R> command, long time, String condition,
                                       Collection<V> values) {
        List<Object> args = new ArrayList<>(values.size() + 5);
        args.add(name);
        args.add(time);
        if (condition != null) {
            args.add(condition);
        }
        args.add("MEMBERS");
        args.add(values.size());
        encode(args, values);
        return commandExecutor.writeAsync(name, StringCodec.INSTANCE, command, args.toArray());
    }

    @Override
    public long remainTimeToLive(V value) {
        return get(remainTimeToLiveAsync(value));
    }

    @Override
    public RFuture<Long> remainTimeToLiveAsync(V value) {
        String name = getRawName(value);
        return commandExecutor.readAsync(name, StringCodec.INSTANCE, RedisCommands.SPTTL,
                name, "MEMBERS", 1, encode(value));
    }

    @Override
    public Map<V, Long> remainTimeToLive(Set<V> values) {
        return get(remainTimeToLiveAsync(values));
    }

    @Override
    public RFuture<Map<V, Long>> remainTimeToLiveAsync(Set<V> values) {
        return readTimesAsync("SPTTL", values);
    }

    @Override
    public long getExpireTime(V value) {
        return get(getExpireTimeAsync(value));
    }

    @Override
    public RFuture<Long> getExpireTimeAsync(V value) {
        String name = getRawName(value);
        return commandExecutor.readAsync(name, StringCodec.INSTANCE, RedisCommands.SPEXPIRETIME,
                name, "MEMBERS", 1, encode(value));
    }

    @Override
    public Map<V, Long> getExpireTime(Set<V> values) {
        return get(getExpireTimeAsync(values));
    }

    @Override
    public RFuture<Map<V, Long>> getExpireTimeAsync(Set<V> values) {
        return readTimesAsync("SPEXPIRETIME", values);
    }

    @Override
    public Boolean clearExpire(V value) {
        return get(clearExpireAsync(value));
    }

    @Override
    public RFuture<Boolean> clearExpireAsync(V value) {
        String name = getRawName(value);
        return commandExecutor.writeAsync(name, StringCodec.INSTANCE, RedisCommands.SPERSIST,
                name, "MEMBERS", 1, encode(value));
    }

    @Override
    public Set<V> clearExpire(Set<V> values) {
        return get(clearExpireAsync(values));
    }

    @Override
    public RFuture<Set<V>> clearExpireAsync(Set<V> values) {
        if (values.isEmpty()) {
            return new CompletableFutureWrapper<>(Collections.<V>emptySet());
        }

        List<V> plainValues = new ArrayList<>(values);
        List<Object> args = new ArrayList<>(plainValues.size() + 3);
        args.add(getRawName());
        args.add("MEMBERS");
        args.add(plainValues.size());
        encode(args, plainValues);

        return commandExecutor.writeAsync(getRawName(), StringCodec.INSTANCE,
                new RedisCommand<>("SPERSIST", new ContainsSetDecoder<>(plainValues)), args.toArray());
    }

    private RFuture<Map<V, Long>> readTimesAsync(String commandName, Set<V> values) {
        if (values.isEmpty()) {
            return new CompletableFutureWrapper<>(new LinkedHashMap<>());
        }

        List<Object> plainValues = new ArrayList<>(values);
        List<Object> args = new ArrayList<>(plainValues.size() + 3);
        args.add(getRawName());
        args.add("MEMBERS");
        args.add(plainValues.size());
        encode(args, plainValues);

        RedisCommand<Map<Object, Object>> command = new RedisCommand<>(commandName,
                new MapNativeAllDecoder(plainValues, Long.class));
        return commandExecutor.readAsync(getRawName(), StringCodec.INSTANCE, command, args.toArray());
    }

    @Override
    public int addListener(ObjectListener listener) {
        if (listener instanceof SetMemberExpiredListener) {
            return addMapFieldListener("__subkeyevent@*:sexpired", "__keyevent@*:sexpired",
                    (SetMemberExpiredListener) listener, SetMemberExpiredListener::onExpired);
        }
        if (listener instanceof SetMemberClearExpireListener) {
            return addMapFieldListener("__subkeyevent@*:spersist", "__keyevent@*:spersist",
                    (SetMemberClearExpireListener) listener, SetMemberClearExpireListener::onClearExpire);
        }

        return super.addListener(listener);
    }

    @Override
    public RFuture<Integer> addListenerAsync(ObjectListener listener) {
        if (listener instanceof SetMemberExpiredListener) {
            return addMapFieldListenerAsync("__subkeyevent@*:sexpired", "__keyevent@*:sexpired",
                    (SetMemberExpiredListener) listener, SetMemberExpiredListener::onExpired);
        }
        if (listener instanceof SetMemberClearExpireListener) {
            return addMapFieldListenerAsync("__subkeyevent@*:spersist", "__keyevent@*:spersist",
                    (SetMemberClearExpireListener) listener, SetMemberClearExpireListener::onClearExpire);
        }

        return super.addListenerAsync(listener);
    }

    @Override
    public void removeListener(int listenerId) {
        removeListener(listenerId, "__subkeyevent@*:sexpired", "__keyevent@*:sexpired",
                "__subkeyevent@*:spersist", "__keyevent@*:spersist");
        super.removeListener(listenerId);
    }

    @Override
    public RFuture<Void> removeListenerAsync(int listenerId) {
        return removeListenerAsync(super.removeListenerAsync(listenerId), listenerId,
                "__subkeyevent@*:sexpired", "__keyevent@*:sexpired",
                "__subkeyevent@*:spersist", "__keyevent@*:spersist");
    }

}
