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

import org.redisson.api.ObjectListener;
import org.redisson.api.RBucket;
import org.redisson.api.RFuture;
import org.redisson.api.bucket.*;
import org.redisson.api.listener.SetObjectListener;
import org.redisson.api.listener.TrackingListener;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.misc.CompletableFutureWrapper;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <V> value type
 */
public class RedissonBucket<V> extends RedissonExpirable implements RBucket<V> {

    public RedissonBucket(CommandAsyncExecutor connectionManager, String name) {
        super(connectionManager, name);
    }

    public RedissonBucket(Codec codec, CommandAsyncExecutor connectionManager, String name) {
        super(codec, connectionManager, name);
    }

    public RedissonBucket(String name, Codec codec, CommandAsyncExecutor connectionManager) {
        super(codec, connectionManager, name);
        this.name = name;
    }

    @Override
    public boolean compareAndSet(V expect, V update) {
        return get(compareAndSetAsync(expect, update));
    }

    @Override
    public RFuture<Boolean> compareAndSetAsync(V expect, V update) {
        if (expect == null && update == null) {
            return trySetAsync(null);
        }

        if (expect == null) {
            return trySetAsync(update);
        }

        if (update == null) {
            return commandExecutor.evalWriteAsync(getRawName(), codec, RedisCommands.EVAL_BOOLEAN,
                    "if redis.call('get', KEYS[1]) == ARGV[1] then "
                            + "redis.call('del', KEYS[1]); "
                            + "return 1 "
                          + "else "
                            + "return 0 end",
                    Collections.singletonList(getRawName()), encode(expect));
        }

        return commandExecutor.evalWriteAsync(getRawName(), codec, RedisCommands.EVAL_BOOLEAN,
                "if redis.call('get', KEYS[1]) == ARGV[1] then "
                     + "redis.call('set', KEYS[1], ARGV[2]); "
                     + "return 1 "
                   + "else "
                     + "return 0 end",
                Collections.singletonList(getRawName()), encode(expect), encode(update));
    }

    @Override
    public V getAndSet(V newValue) {
        return get(getAndSetAsync(newValue));
    }

    @Override
    public RFuture<V> getAndSetAsync(V newValue) {
        if (newValue == null) {
            return commandExecutor.evalWriteAsync(getRawName(), codec, RedisCommands.EVAL_OBJECT,
                    "local v = redis.call('get', KEYS[1]); "
                    + "redis.call('del', KEYS[1]); "
                    + "return v",
                    Collections.singletonList(getRawName()));
        }

        return commandExecutor.writeAsync(getRawName(), codec, RedisCommands.GETSET, getRawName(), encode(newValue));
    }

    @Override
    public V getAndExpire(Instant time) {
        return get(getAndExpireAsync(time));
    }

    @Override
    public RFuture<V> getAndExpireAsync(Instant time) {
        return commandExecutor.writeAsync(getRawName(), codec, RedisCommands.GETEX, getRawName(), "PXAT", time.toEpochMilli());
    }

    @Override
    public V getAndExpire(Duration duration) {
        return get(getAndExpireAsync(duration));
    }

    @Override
    public RFuture<V> getAndExpireAsync(Duration duration) {
        return commandExecutor.writeAsync(getRawName(), codec, RedisCommands.GETEX, getRawName(), "PX", duration.toMillis());
    }

    @Override
    public V getAndClearExpire() {
        return get(getAndClearExpireAsync());
    }

    @Override
    public RFuture<V> getAndClearExpireAsync() {
        return commandExecutor.writeAsync(getRawName(), codec, RedisCommands.GETEX, getRawName(), "PERSIST");
    }

    @Override
    public V get() {
        return get(getAsync());
    }

    @Override
    public RFuture<V> getAsync() {
        return commandExecutor.readAsync(getRawName(), codec, RedisCommands.GET, getRawName());
    }
    
    @Override
    public V getAndDelete() {
        return get(getAndDeleteAsync());
    }
    
    @Override
    public RFuture<V> getAndDeleteAsync() {
        return commandExecutor.evalWriteAsync(getRawName(), codec, RedisCommands.EVAL_OBJECT,
                   "local currValue = redis.call('get', KEYS[1]); "
                 + "redis.call('del', KEYS[1]); "
                 + "return currValue; ",
                Collections.singletonList(getRawName()));
    }
    
    @Override
    public long size() {
        return get(sizeAsync());
    }
    
    @Override
    public RFuture<Long> sizeAsync() {
        return commandExecutor.readAsync(getRawName(), codec, RedisCommands.STRLEN, getRawName());
    }

    @Override
    public void set(V value) {
        get(setAsync(value));
    }

    @Override
    public RFuture<Void> setAsync(V value) {
        if (value == null) {
            return commandExecutor.writeAsync(getRawName(), RedisCommands.DEL_VOID, getRawName());
        }

        return commandExecutor.writeAsync(getRawName(), codec, RedisCommands.SET, getRawName(), encode(value));
    }

    @Override
    public void set(V value, long timeToLive, TimeUnit timeUnit) {
        get(setAsync(value, timeToLive, timeUnit));
    }

    @Override
    public RFuture<Void> setAsync(V value, long timeToLive, TimeUnit timeUnit) {
        if (value == null) {
            return commandExecutor.writeAsync(getRawName(), RedisCommands.DEL_VOID, getRawName());
        }

        return commandExecutor.writeAsync(getRawName(), codec, RedisCommands.PSETEX, getRawName(), timeUnit.toMillis(timeToLive), encode(value));
    }

    @Override
    public void set(V value, Duration duration) {
        get(setAsync(value, duration));
    }

    @Override
    public RFuture<Void> setAsync(V value, Duration duration) {
        if (value == null) {
            return commandExecutor.writeAsync(getRawName(), RedisCommands.DEL_VOID, getRawName());
        }

        return commandExecutor.writeAsync(getRawName(), codec, RedisCommands.PSETEX, getRawName(), duration.toMillis(), encode(value));
    }

    @Override
    public RFuture<Boolean> trySetAsync(V value) {
        if (value == null) {
            return commandExecutor.readAsync(getRawName(), codec, RedisCommands.NOT_EXISTS, getRawName());
        }

        return commandExecutor.writeAsync(getRawName(), codec, RedisCommands.SETNX, getRawName(), encode(value));
    }

    @Override
    public RFuture<Boolean> trySetAsync(V value, long timeToLive, TimeUnit timeUnit) {
        if (value == null) {
            throw new IllegalArgumentException("Value can't be null");
        }
        return commandExecutor.writeAsync(getRawName(), codec, RedisCommands.SET_BOOLEAN, getRawName(), encode(value), "PX", timeUnit.toMillis(timeToLive), "NX");
    }

    @Override
    public boolean trySet(V value, long timeToLive, TimeUnit timeUnit) {
        return get(trySetAsync(value, timeToLive, timeUnit));
    }

    @Override
    public boolean trySet(V value) {
        return get(trySetAsync(value));
    }

    @Override
    public boolean setIfAbsent(V value) {
        return get(setIfAbsentAsync(value));
    }

    @Override
    public boolean setIfAbsent(V value, Duration duration) {
        return get(setIfAbsentAsync(value, duration));
    }

    @Override
    public RFuture<Boolean> setIfAbsentAsync(V value) {
        if (value == null) {
            return commandExecutor.readAsync(getRawName(), codec, RedisCommands.NOT_EXISTS, getRawName());
        }

        return commandExecutor.writeAsync(getRawName(), codec, RedisCommands.SETNX, getRawName(), encode(value));
    }

    @Override
    public RFuture<Boolean> setIfAbsentAsync(V value, Duration duration) {
        if (value == null) {
            throw new IllegalArgumentException("Value can't be null");
        }
        return commandExecutor.writeAsync(getRawName(), codec, RedisCommands.SET_BOOLEAN, getRawName(), encode(value), "PX", duration.toMillis(), "NX");
    }

    @Override
    public boolean setIfExists(V value) {
        return get(setIfExistsAsync(value));
    }

    @Override
    public RFuture<Boolean> setIfExistsAsync(V value) {
        if (value == null) {
            return commandExecutor.evalWriteAsync(getRawName(), codec, RedisCommands.EVAL_BOOLEAN,
                  "local currValue = redis.call('get', KEYS[1]); " +
                        "if currValue ~= false then " +
                            "redis.call('del', KEYS[1]); " +
                            "return 1;" +
                        "end;" +
                        "return 0; ",
                    Collections.singletonList(getRawName()));
        }

        return commandExecutor.writeAsync(getRawName(), codec, RedisCommands.SET_BOOLEAN, getRawName(), encode(value), "XX");
    }

    @Override
    public void setAndKeepTTL(V value) {
        get(setAndKeepTTLAsync(value));
    }

    @Override
    public RFuture<Void> setAndKeepTTLAsync(V value) {
        if (value == null) {
            return commandExecutor.writeAsync(getRawName(), RedisCommands.DEL_VOID, getRawName());
        }

        return commandExecutor.writeAsync(getRawName(), codec, RedisCommands.SET, getRawName(), encode(value), "KEEPTTL");
    }

    @Override
    public boolean setIfExists(V value, long timeToLive, TimeUnit timeUnit) {
        return get(setIfExistsAsync(value, timeToLive, timeUnit));
    }

    @Override
    public RFuture<Boolean> setIfExistsAsync(V value, long timeToLive, TimeUnit timeUnit) {
        if (value == null) {
            throw new IllegalArgumentException("Value can't be null");
        }

        return commandExecutor.writeAsync(getRawName(), codec, RedisCommands.SET_BOOLEAN, getRawName(), encode(value), "PX", timeUnit.toMillis(timeToLive), "XX");
    }

    @Override
    public boolean setIfExists(V value, Duration duration) {
        return get(setIfExistsAsync(value, duration));
    }

    @Override
    public RFuture<Boolean> setIfExistsAsync(V value, Duration duration) {
        if (value == null) {
            throw new IllegalArgumentException("Value can't be null");
        }

        return commandExecutor.writeAsync(getRawName(), codec, RedisCommands.SET_BOOLEAN, getRawName(), encode(value), "PX", duration.toMillis(), "XX");
    }

    @Override
    public V getAndSet(V value, Duration duration) {
        return get(getAndSetAsync(value, duration));
    }

    @Override
    public RFuture<V> getAndSetAsync(V value, Duration duration) {
        return commandExecutor.evalWriteAsync(getRawName(), codec, RedisCommands.EVAL_OBJECT,
                "local currValue = redis.call('get', KEYS[1]); "
              + "redis.call('psetex', KEYS[1], ARGV[2], ARGV[1]); "
              + "return currValue; ",
             Collections.singletonList(getRawName()),
             encode(value), duration.toMillis());
    }

    @Override
    public RFuture<V> getAndSetAsync(V value, long timeToLive, TimeUnit timeUnit) {
        return commandExecutor.evalWriteAsync(getRawName(), codec, RedisCommands.EVAL_OBJECT,
                "local currValue = redis.call('get', KEYS[1]); "
              + "redis.call('psetex', KEYS[1], ARGV[2], ARGV[1]); "
              + "return currValue; ",
             Collections.singletonList(getRawName()),
             encode(value), timeUnit.toMillis(timeToLive));
    }

    @Override
    public V getAndSet(V value, long timeToLive, TimeUnit timeUnit) {
        return get(getAndSetAsync(value, timeToLive, timeUnit));
    }

    @Override
    public int addListener(ObjectListener listener) {
        if (listener instanceof SetObjectListener) {
            return addListener("__keyevent@*:set", (SetObjectListener) listener, SetObjectListener::onSet);
        }
        if (listener instanceof TrackingListener) {
            return addTrackingListener((TrackingListener) listener);
        }

        return super.addListener(listener);
    }

    @Override
    public RFuture<Integer> addListenerAsync(ObjectListener listener) {
        if (listener instanceof SetObjectListener) {
            return addListenerAsync("__keyevent@*:set", (SetObjectListener) listener, SetObjectListener::onSet);
        }
        if (listener instanceof TrackingListener) {
            return addTrackingListenerAsync((TrackingListener) listener);
        }

        return super.addListenerAsync(listener);
    }

    @Override
    public void removeListener(int listenerId) {
        removeTrackingListener(listenerId);
        removeListener(listenerId, "__keyevent@*:set");
        super.removeListener(listenerId);
    }

    @Override
    public RFuture<Void> removeListenerAsync(int listenerId) {
        RFuture<Void> f1 = removeTrackingListenerAsync(listenerId);
        RFuture<Void> f2 = removeListenerAsync(listenerId, "__keyevent@*:set");
        return new CompletableFutureWrapper<>(CompletableFuture.allOf(f1.toCompletableFuture(), f2.toCompletableFuture()));
    }

    @Override
    public V findCommon(String name) {
        return get(findCommonAsync(name));
    }

    @Override
    public RFuture<V> findCommonAsync(String name) {
        return commandExecutor.readAsync(getRawName(), codec, RedisCommands.LCS, getRawName(), mapName(name), "MINMATCHLEN", 30);
    }

    @Override
    public long findCommonLength(String name) {
        return get(findCommonLengthAsync(name));
    }

    @Override
    public RFuture<Long> findCommonLengthAsync(String name) {
        return commandExecutor.readAsync(getRawName(), codec, RedisCommands.LCS, getRawName(), mapName(name), "LEN");
    }

    @Override
    public String getDigest() {
        return get(getDigestAsync());
    }

    @Override
    public RFuture<String> getDigestAsync() {
        return commandExecutor.readAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.DIGEST, getRawName());
    }

    @Override
    public boolean compareAndSet(CompareAndSetArgs<V> args) {
        return get(compareAndSetAsync(args));
    }

    @Override
    public RFuture<Boolean> compareAndSetAsync(CompareAndSetArgs<V> args) {
        CompareAndSetParams<V> params = (CompareAndSetParams<V>) args;

        Objects.requireNonNull(params, "Args can't be null");
        Objects.requireNonNull(params.getNewValue(), "New value must be set");

        ConditionType conditionType = params.getConditionType();

        switch (conditionType) {
            case EXPECTED:
                return compareAndSetAsync(params, params.getExpectedValue(), "E");
            case UNEXPECTED:
                return compareAndSetAsync(params, params.getUnexpectedValue(), "U");
            case EXPECTED_DIGEST:
                return compareAndSetDigestAsync(params, params.getExpectedDigest(), "IFDEQ");
            case UNEXPECTED_DIGEST:
                return compareAndSetDigestAsync(params, params.getUnexpectedDigest(), "IFDNE");
            default:
                throw new IllegalStateException("Unknown condition type: " + conditionType);
        }
    }

    private RFuture<Boolean> compareAndSetAsync(CompareAndSetParams<V> args, V value, String cond) {
        List<Object> params = new ArrayList<>();
        if (value == null) {
            cond += "N";
        }
        params.add(cond);
        params.add(encode(value));
        params.add(encode(args.getNewValue()));
        if (args.getTimeToLive() != null) {
            params.add("pexpire");
            params.add(args.getTimeToLive().toMillis());
        } else if (args.getExpireAt() != null) {
            params.add("pexpireat");
            params.add(args.getExpireAt().toEpochMilli());
        } else {
            params.add("");
        }

        return commandExecutor.evalWriteAsync(getName(), codec, RedisCommands.EVAL_BOOLEAN,
                  "local cv = redis.call('get', KEYS[1]) " +
                        "if (ARGV[1] == 'E' and cv == ARGV[2]) " +
                            "or (ARGV[1] == 'EN' and cv == false) " +
                            "or (ARGV[1] == 'UN' and cv ~= false) " +
                            "or (ARGV[1] == 'U' and cv ~= ARGV[2]) then " +

                            "redis.call('set', KEYS[1], ARGV[3]) " +

                            "if #ARGV[4] > 0 then " +
                                "redis.call(ARGV[4], KEYS[1], ARGV[5]) " +
                            "end " +
                            "return 1 " +
                        "end " +
                        "return 0 ",
                Collections.singletonList(getName()),
                params.toArray());
    }

    private RFuture<Boolean> compareAndSetDigestAsync(CompareAndSetParams<V> args, String value, String command) {
        V newValue = args.getNewValue();

        List<Object> params = new ArrayList<>();
        params.add(getName());
        params.add(encode(newValue));
        params.add(command);
        params.add(value);

        if (args.getTimeToLive() != null) {
            params.add("PX");
            params.add(args.getTimeToLive().toMillis());
        } else if (args.getExpireAt() != null) {
            params.add("PXAT");
            params.add(args.getExpireAt().toEpochMilli());
        }

        return commandExecutor.writeAsync(getName(), StringCodec.INSTANCE, RedisCommands.SET_BOOLEAN, params.toArray());
    }

    @Override
    public boolean compareAndDelete(CompareAndDeleteArgs<V> args) {
        return get(compareAndDeleteAsync(args));
    }

    @Override
    public RFuture<Boolean> compareAndDeleteAsync(CompareAndDeleteArgs<V> args) {
        CompareAndDeleteParams<V> params = (CompareAndDeleteParams<V>) args;

        Objects.requireNonNull(params, "Args can't be null");

        ConditionType conditionType = params.getConditionType();

        switch (conditionType) {
            case EXPECTED:
                return compareAndDeleteExpectedAsync(params.getValue());
            case UNEXPECTED:
                return compareAndDeleteUnexpectedAsync(params.getValue());
            case EXPECTED_DIGEST:
                return commandExecutor.writeAsync(getName(), codec, RedisCommands.DELEX, getName(), "IFDEQ", params.getDigest());
            case UNEXPECTED_DIGEST:
                return commandExecutor.writeAsync(getName(), codec, RedisCommands.DELEX, getName(), "IFDNE", params.getDigest());
            default:
                throw new IllegalArgumentException("Unknown mode: " + params.getConditionType());
        }
    }

    private RFuture<Boolean> compareAndDeleteExpectedAsync(V expected) {
        String script =
                "local currValue = redis.call('get', KEYS[1]) " +
                "if currValue == ARGV[1] then " +
                    "redis.call('del', KEYS[1]) " +
                    "return 1 " +
                "end " +
                "return 0 ";
        return commandExecutor.evalWriteAsync(getName(), codec, RedisCommands.EVAL_BOOLEAN,
                script,
                Collections.singletonList(getName()), encode(expected));
    }

    private RFuture<Boolean> compareAndDeleteUnexpectedAsync(V unexpected) {
        String script =
                "local currValue = redis.call('get', KEYS[1]) " +
                "if currValue ~= false and currValue ~= ARGV[1] then " +
                    "redis.call('del', KEYS[1]) " +
                    "return 1 " +
                "end " +
                "return 0 ";
        return commandExecutor.evalWriteAsync(getName(), codec, RedisCommands.EVAL_BOOLEAN,
                script,
                Collections.singletonList(getName()), encode(unexpected));
    }

}
