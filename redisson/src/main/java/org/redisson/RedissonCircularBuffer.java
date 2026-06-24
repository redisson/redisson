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

import org.redisson.api.RCircularBuffer;
import org.redisson.api.RFuture;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.RedisStrictCommand;
import org.redisson.client.protocol.convertor.DoubleReplayConvertor;
import org.redisson.client.protocol.convertor.IntegerReplayConvertor;
import org.redisson.client.protocol.decoder.ListFirstObjectDecoder;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.misc.CompletableFutureWrapper;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 *
 * @param <V> value type
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonCircularBuffer<V> extends RedissonExpirable implements RCircularBuffer<V> {

    private static final RedisStrictCommand<Integer> GET_INTEGER =
            new RedisStrictCommand<>("GET", new IntegerReplayConvertor(0));

    private static final RedisStrictCommand<Integer> ARLEN_INTEGER =
            new RedisStrictCommand<>("ARLEN", new IntegerReplayConvertor(0));

    private static final RedisStrictCommand<Double> EVAL_DOUBLE =
            new RedisStrictCommand<>("EVAL", new DoubleReplayConvertor());

    private static final RedisCommand<Object> PEEK_LAST =
            new RedisCommand<>("ARLASTITEMS", new ListFirstObjectDecoder());

    private static final RedisCommand<Boolean> ARLEN_BOOL = new RedisCommand<Boolean>("ARLEN", obj -> (Long) obj == 0);

    private final String settingsName;

    public RedissonCircularBuffer(CommandAsyncExecutor commandExecutor, String name) {
        super(commandExecutor, name);
        settingsName = prefixName("redisson_acb", getRawName());
    }

    public RedissonCircularBuffer(Codec codec, CommandAsyncExecutor commandExecutor, String name) {
        super(codec, commandExecutor, name);
        settingsName = prefixName("redisson_acb", getRawName());
    }

    @Override
    public boolean trySetCapacity(int capacity) {
        return get(trySetCapacityAsync(capacity));
    }

    @Override
    public RFuture<Boolean> trySetCapacityAsync(int capacity) {
        checkCapacity(capacity);
        return commandExecutor.writeAsync(getRawName(), LongCodec.INSTANCE, RedisCommands.SETNX, settingsName, capacity);
    }

    @Override
    public void setCapacity(int capacity) {
        get(setCapacityAsync(capacity));
    }

    @Override
    public RFuture<Void> setCapacityAsync(int capacity) {
        checkCapacity(capacity);
        return commandExecutor.writeAsync(getRawName(), LongCodec.INSTANCE, RedisCommands.SET, settingsName, capacity);
    }

    @Override
    public int capacity() {
        return get(capacityAsync());
    }

    @Override
    public RFuture<Integer> capacityAsync() {
        return commandExecutor.writeAsync(getRawName(), LongCodec.INSTANCE, GET_INTEGER, settingsName);
    }

    @Override
    public int remainingCapacity() {
        return get(remainingCapacityAsync());
    }

    @Override
    public RFuture<Integer> remainingCapacityAsync() {
        return commandExecutor.evalWriteAsync(getRawName(), LongCodec.INSTANCE, RedisCommands.EVAL_INTEGER,
                "local limit = tonumber(redis.call('get', KEYS[2]) or '0'); "
              + "local size = redis.call('ARLEN', KEYS[1]); "
              + "local rem = limit - size; "
              + "if rem < 0 then rem = 0; end; "
              + "return rem; ",
             Arrays.asList(getRawName(), settingsName));
    }

    @Override
    public boolean add(V value) {
        return get(addAsync(value));
    }

    @Override
    public RFuture<Boolean> addAsync(V value) {
        Objects.requireNonNull(value, "Value can't be null");
        return commandExecutor.evalWriteNoRetryAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                "local limit = redis.call('get', KEYS[2]); "
              + "assert(limit ~= false, 'CircularBuffer capacity is not defined'); "
              + "redis.call('ARRING', KEYS[1], limit, ARGV[1]); "
              + "return 1; ",
             Arrays.asList(getRawName(), settingsName), encode(value));
    }

    @Override
    public boolean addAll(Collection<? extends V> values) {
        return get(addAllAsync(values));
    }

    @Override
    public RFuture<Boolean> addAllAsync(Collection<? extends V> values) {
        Objects.requireNonNull(values, "Values can't be null");
        if (values.isEmpty()) {
            return new CompletableFutureWrapper<>(false);
        }

        List<Object> args = new ArrayList<>(values.size());
        encode(args, values);
        return commandExecutor.evalWriteNoRetryAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                "local limit = redis.call('get', KEYS[2]); "
              + "assert(limit ~= false, 'CircularBuffer capacity is not defined'); "
              + "for i = 1, #ARGV, 4000 do "
                  + "redis.call('ARRING', KEYS[1], limit, unpack(ARGV, i, math.min(i + 3999, #ARGV))); "
              + "end; "
              + "return 1; ",
             Arrays.asList(getRawName(), settingsName), args.toArray());
    }

    @Override
    public long set(int size, V... values) {
        return get(setAsync(size, values));
    }

    @Override
    public RFuture<Long> setAsync(int size, V... values) {
        checkCapacity(size);
        Objects.requireNonNull(values, "Values can't be null");
        for (V value : values) {
            Objects.requireNonNull(value, "Value can't be null");
        }
        if (values.length == 0) {
            throw new IllegalArgumentException("Values can't be empty");
        }

        List<Object> args = new ArrayList<>(values.length + 1);
        args.add(size);
        encode(args, Arrays.asList(values));
        return commandExecutor.evalWriteNoRetryAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.EVAL_LONG,
                "redis.call('set', KEYS[2], ARGV[1]); "
              + "local last; "
              + "for i = 2, #ARGV, 4000 do "
                  + "last = redis.call('ARRING', KEYS[1], ARGV[1], unpack(ARGV, i, math.min(i + 3999, #ARGV))); "
              + "end; "
              + "return last; ",
             Arrays.asList(getRawName(), settingsName), args.toArray());
    }

    @Override
    public V get(long index) {
        return get(getAsync(index));
    }

    @Override
    public RFuture<V> getAsync(long index) {
        checkIndex(index);
        return commandExecutor.readAsync(getRawName(), codec, RedisCommands.ARGET, getRawName(), index);
    }

    @Override
    public List<V> lastItems(int count, boolean reverse) {
        return get(lastItemsAsync(count, reverse));
    }

    @Override
    public RFuture<List<V>> lastItemsAsync(int count, boolean reverse) {
        if (count <= 0) {
            return new CompletableFutureWrapper<>(Collections.emptyList());
        }
        if (reverse) {
            return commandExecutor.readAsync(getRawName(), codec,
                    RedisCommands.ARLASTITEMS, getRawName(), count, "REV");
        }
        return commandExecutor.readAsync(getRawName(), codec, RedisCommands.ARLASTITEMS, getRawName(), count);
    }

    @Override
    public List<V> range(long startIndex, long endIndex) {
        return get(rangeAsync(startIndex, endIndex));
    }

    @Override
    public RFuture<List<V>> rangeAsync(long startIndex, long endIndex) {
        checkIndex(startIndex);
        checkIndex(endIndex);
        return commandExecutor.readAsync(getRawName(), codec,
                RedisCommands.ARGETRANGE, getRawName(), startIndex, endIndex);
    }

    @Override
    public List<V> readAll() {
        return get(readAllAsync());
    }

    @Override
    public RFuture<List<V>> readAllAsync() {
        return commandExecutor.readAsync(getRawName(), codec, RedisCommands.ARLASTITEMS, getRawName(), Integer.MAX_VALUE);
    }

    @Override
    public int size() {
        return get(sizeAsync());
    }

    @Override
    public RFuture<Integer> sizeAsync() {
        return commandExecutor.readAsync(getRawName(), StringCodec.INSTANCE, ARLEN_INTEGER, getRawName());
    }

    @Override
    public Double sum() {
        return get(sumAsync());
    }

    @Override
    public RFuture<Double> sumAsync() {
        return aggregateAllAsync("SUM");
    }

    @Override
    public Double sum(long startIndex, long endIndex) {
        return get(sumAsync(startIndex, endIndex));
    }

    @Override
    public RFuture<Double> sumAsync(long startIndex, long endIndex) {
        return doubleOperationAsync(startIndex, endIndex, "SUM");
    }

    @Override
    public Double min() {
        return get(minAsync());
    }

    @Override
    public RFuture<Double> minAsync() {
        return aggregateAllAsync("MIN");
    }

    @Override
    public Double min(long startIndex, long endIndex) {
        return get(minAsync(startIndex, endIndex));
    }

    @Override
    public RFuture<Double> minAsync(long startIndex, long endIndex) {
        return doubleOperationAsync(startIndex, endIndex, "MIN");
    }

    @Override
    public Double max() {
        return get(maxAsync());
    }

    @Override
    public RFuture<Double> maxAsync() {
        return aggregateAllAsync("MAX");
    }

    @Override
    public Double max(long startIndex, long endIndex) {
        return get(maxAsync(startIndex, endIndex));
    }

    @Override
    public RFuture<Double> maxAsync(long startIndex, long endIndex) {
        return doubleOperationAsync(startIndex, endIndex, "MAX");
    }

    @Override
    public void clear() {
        get(clearAsync());
    }

    @Override
    public RFuture<Void> clearAsync() {
        return commandExecutor.writeAsync(getRawName(), RedisCommands.DEL_VOID, getRawName());
    }

    @Override
    public boolean isEmpty() {
        return get(isEmptyAsync());
    }

    @Override
    public RFuture<Boolean> isEmptyAsync() {
        return commandExecutor.readAsync(getRawName(), StringCodec.INSTANCE, ARLEN_BOOL, getRawName());
    }

    @Override
    public boolean isFull() {
        return get(isFullAsync());
    }

    @Override
    public RFuture<Boolean> isFullAsync() {
        return commandExecutor.evalReadAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                "local cap = tonumber(redis.call('get', KEYS[2]) or '0'); "
              + "if cap == 0 then "
                  + "return 0; "
              + "end; "
              + "local len = redis.call('exists', KEYS[1]) == 1 and redis.call('ARLEN', KEYS[1]) or 0; "
              + "return len >= cap and 1 or 0; ",
             Arrays.asList(getRawName(), settingsName));
    }

    @Override
    public V peekLast() {
        return get(peekLastAsync());
    }

    @Override
    public RFuture<V> peekLastAsync() {
        return commandExecutor.readAsync(getRawName(), codec, PEEK_LAST, getRawName(), 1, "REV");
    }

    @Override
    public V peekFirst() {
        return get(peekFirstAsync());
    }

    @Override
    public RFuture<V> peekFirstAsync() {
        return commandExecutor.evalReadAsync(getRawName(), codec, RedisCommands.EVAL_OBJECT,
         "local idx = redis.call('ARNEXT', KEYS[1]); "
              + "if not idx then idx = 0; end; "
              + "return redis.call('ARGET', KEYS[1], idx); ",
             Arrays.asList(getRawName(), settingsName));
    }

    @Override
    public List<V> get(long... indexes) {
        return get(getAsync(indexes));
    }

    @Override
    public RFuture<List<V>> getAsync(long... indexes) {
        if (indexes.length == 0) {
            return new CompletableFutureWrapper<>(Collections.emptyList());
        }

        List<Object> args = new ArrayList<>(indexes.length + 1);
        args.add(getRawName());
        for (long index : indexes) {
            checkIndex(index);
            args.add(index);
        }
        return commandExecutor.readAsync(getRawName(), codec, RedisCommands.ARMGET, args.toArray());
    }

    @Override
    public long count(V value) {
        return get(countAsync(value));
    }

    @Override
    public RFuture<Long> countAsync(V value) {
        Objects.requireNonNull(value, "Value can't be null");
        return commandExecutor.evalReadAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.EVAL_LONG,
                "local len = redis.call('exists', KEYS[1]) == 1 and redis.call('ARLEN', KEYS[1]) or 0; "
              + "if len == 0 then "
                  + "return 0; "
              + "end; "
              + "return redis.call('AROP', KEYS[1], 0, len - 1, 'MATCH', ARGV[1]); ",
             Arrays.asList(getRawName()), encode(value));
    }

    @Override
    public boolean contains(V value) {
        return get(containsAsync(value));
    }

    @Override
    public RFuture<Boolean> containsAsync(V value) {
        Objects.requireNonNull(value, "Value can't be null");
        return commandExecutor.evalReadAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
          "local len = redis.call('exists', KEYS[1]) == 1 and redis.call('ARLEN', KEYS[1]) or 0; "
              + "if len == 0 then "
                  + "return 0; "
              + "end; "
              + "return redis.call('AROP', KEYS[1], 0, len - 1, 'MATCH', ARGV[1]) > 0 and 1 or 0; ",
             Arrays.asList(getRawName()), encode(value));
    }

    @Override
    public Double average() {
        return get(averageAsync());
    }

    @Override
    public RFuture<Double> averageAsync() {
        return commandExecutor.evalReadAsync(getRawName(), StringCodec.INSTANCE, EVAL_DOUBLE,
          "local len = redis.call('exists', KEYS[1]) == 1 and redis.call('ARLEN', KEYS[1]) or 0; "
              + "if len == 0 then "
                  + "return false; "
              + "end; "
              + "local s = redis.call('AROP', KEYS[1], 0, len - 1, 'SUM'); "
              + "if not s then "
                  + "return false; "
              + "end; "
              + "return tostring(tonumber(s) / len); ",
             Arrays.asList(getRawName()));
    }

    @Override
    public Long bitAnd() {
        return get(bitAndAsync());
    }

    @Override
    public RFuture<Long> bitAndAsync() {
        return longAggregateAllAsync("AND");
    }

    @Override
    public Long bitAnd(long startIndex, long endIndex) {
        return get(bitAndAsync(startIndex, endIndex));
    }

    @Override
    public RFuture<Long> bitAndAsync(long startIndex, long endIndex) {
        return longOperationAsync(startIndex, endIndex, "AND");
    }

    @Override
    public Long bitOr() {
        return get(bitOrAsync());
    }

    @Override
    public RFuture<Long> bitOrAsync() {
        return longAggregateAllAsync("OR");
    }

    @Override
    public Long bitOr(long startIndex, long endIndex) {
        return get(bitOrAsync(startIndex, endIndex));
    }

    @Override
    public RFuture<Long> bitOrAsync(long startIndex, long endIndex) {
        return longOperationAsync(startIndex, endIndex, "OR");
    }

    @Override
    public Long bitXor() {
        return get(bitXorAsync());
    }

    @Override
    public RFuture<Long> bitXorAsync() {
        return longAggregateAllAsync("XOR");
    }

    @Override
    public Long bitXor(long startIndex, long endIndex) {
        return get(bitXorAsync(startIndex, endIndex));
    }

    @Override
    public RFuture<Long> bitXorAsync(long startIndex, long endIndex) {
        return longOperationAsync(startIndex, endIndex, "XOR");
    }

    private RFuture<Long> longAggregateAllAsync(String operation) {
        return commandExecutor.evalReadAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.EVAL_LONG,
                "local len = redis.call('exists', KEYS[1]) == 1 and redis.call('ARLEN', KEYS[1]) or 0; "
              + "if len == 0 then "
                  + "return nil; "
              + "end; "
              + "return redis.call('AROP', KEYS[1], 0, len - 1, ARGV[1]); ",
             Arrays.asList(getRawName()), operation);
    }

    private RFuture<Long> longOperationAsync(long startIndex, long endIndex, String operation) {
        checkIndex(startIndex);
        checkIndex(endIndex);
        return commandExecutor.readAsync(getRawName(), StringCodec.INSTANCE,
                RedisCommands.AROP_LONG, getRawName(), startIndex, endIndex, operation);
    }

    private RFuture<Double> aggregateAllAsync(String operation) {
        return commandExecutor.evalReadAsync(getRawName(), StringCodec.INSTANCE, EVAL_DOUBLE,
                "local len = redis.call('exists', KEYS[1]) == 1 and redis.call('ARLEN', KEYS[1]) or 0; "
              + "if len == 0 then "
                  + "return false; "
              + "end; "
              + "local r = redis.call('AROP', KEYS[1], 0, len - 1, ARGV[1]); "
              + "if not r then "
                  + "return false; "
              + "end; "
              + "return tostring(r); ",
             Arrays.asList(getRawName()), operation);
    }

    private RFuture<Double> doubleOperationAsync(long startIndex, long endIndex, String operation) {
        checkIndex(startIndex);
        checkIndex(endIndex);
        return commandExecutor.readAsync(getRawName(), StringCodec.INSTANCE,
                RedisCommands.AROP_DOUBLE, getRawName(), startIndex, endIndex, operation);
    }

    private void checkIndex(long index) {
        if (index < 0) {
            throw new IllegalArgumentException("Index must be non-negative");
        }
    }

    private void checkCapacity(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive");
        }
    }

    @Override
    public RFuture<Boolean> expireAsync(long timeToLive, TimeUnit timeUnit, String param, String... keys) {
        return super.expireAsync(timeToLive, timeUnit, param, getRawName(), settingsName);
    }

    @Override
    protected RFuture<Boolean> expireAtAsync(long timestamp, String param, String... keys) {
        return super.expireAtAsync(timestamp, param, getRawName(), settingsName);
    }

    @Override
    public RFuture<Boolean> clearExpireAsync() {
        return clearExpireAsync(getRawName(), settingsName);
    }

    @Override
    public RFuture<Boolean> deleteAsync() {
        return super.deleteAsync(getRawName(), settingsName);
    }

}
