/**
 * Copyright (c) 2013-2022 Nikita Koksharov
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

import org.redisson.api.RFuture;
import org.redisson.api.RTimeSeries;
import org.redisson.api.TimeSeriesEntry;
import org.redisson.client.RedisClient;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.decoder.ListScanResult;
import org.redisson.client.protocol.decoder.TimeSeriesEntryReplayDecoder;
import org.redisson.client.protocol.decoder.TimeSeriesFirstEntryReplayDecoder;
import org.redisson.client.protocol.decoder.TimeSeriesSingleEntryReplayDecoder;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.eviction.EvictionScheduler;
import org.redisson.iterator.RedissonBaseIterator;
import org.redisson.misc.CompletableFutureWrapper;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonTimeSeries<V, L> extends RedissonExpirable implements RTimeSeries<V, L> {

    private final EvictionScheduler evictionScheduler;

    public RedissonTimeSeries(EvictionScheduler evictionScheduler, CommandAsyncExecutor connectionManager, String name) {
        super(connectionManager, name);

        this.evictionScheduler = evictionScheduler;
        if (evictionScheduler != null) {
            evictionScheduler.scheduleTimeSeries(getRawName(), getTimeoutSetName());
        }
    }

    public RedissonTimeSeries(Codec codec, EvictionScheduler evictionScheduler, CommandAsyncExecutor connectionManager, String name) {
        super(codec, connectionManager, name);

        this.evictionScheduler = evictionScheduler;
        if (evictionScheduler != null) {
            evictionScheduler.scheduleTimeSeries(getRawName(), getTimeoutSetName());
        }
    }

    String getTimeoutSetName() {
        return prefixName("redisson__ts_ttl", getRawName());
    }

    @Override
    public void add(long timestamp, V value) {
        addAll(Collections.singletonMap(timestamp, value));
    }

    @Override
    public RFuture<Void> addAsync(long timestamp, V object) {
        return addAllAsync(Collections.singletonMap(timestamp, object));
    }

    @Override
    public void add(long timestamp, V object, L label) {
        addAll(Collections.singletonList(new TimeSeriesEntry<>(timestamp, object, label)));
    }

    @Override
    public RFuture<Void> addAsync(long timestamp, V object, L label) {
        return addAllAsync(Collections.singletonList(new TimeSeriesEntry<>(timestamp, object, label)));
    }

    @Override
    public void addAll(Map<Long, V> objects) {
        addAll(objects, 0, null);
    }

    @Override
    public void add(long timestamp, V value, long timeToLive, TimeUnit timeUnit) {
        addAll(Collections.singletonMap(timestamp, value), timeToLive, timeUnit);
    }

    @Override
    public RFuture<Void> addAsync(long timestamp, V object, long timeToLive, TimeUnit timeUnit) {
        return addAllAsync(Collections.singletonMap(timestamp, object), timeToLive, timeUnit);
    }

    @Override
    public void add(long timestamp, V object, L label, Duration timeToLive) {
        addAll(Collections.singletonList(new TimeSeriesEntry<>(timestamp, object, label)), timeToLive);
    }

    @Override
    public RFuture<Void> addAsync(long timestamp, V object, L label, Duration timeToLive) {
        return addAllAsync(Collections.singletonList(new TimeSeriesEntry<>(timestamp, object, label)), timeToLive);
    }

    @Override
    public void addAll(Map<Long, V> objects, long timeToLive, TimeUnit timeUnit) {
        get(addAllAsync(objects, timeToLive, timeUnit));
    }

    @Override
    public RFuture<Void> addAllAsync(Map<Long, V> objects) {
        return addAllAsync(objects, 0, null);
    }

    @Override
    public RFuture<Void> addAllAsync(Map<Long, V> objects, long timeToLive, TimeUnit timeUnit) {
        long expirationTime = System.currentTimeMillis();
        if (timeToLive > 0) {
            expirationTime += timeUnit.toMillis(timeToLive);
        } else {
            expirationTime += TimeUnit.DAYS.toMillis(365 * 100);
        }

        List<Object> params = new ArrayList<>();
        params.add(expirationTime);
        for (Map.Entry<Long, V> entry : objects.entrySet()) {
            params.add(entry.getKey());
            byte[] random = getServiceManager().generateIdArray();
            params.add(random);
            encode(params, entry.getValue());
        }

        if (timeToLive > 0) {
            return commandExecutor.evalWriteAsync(getRawName(), codec, RedisCommands.EVAL_VOID,
           "for i = 2, #ARGV, 3 do " +
                    "local val = struct.pack('BBc0Lc0Lc0', 2, string.len(ARGV[i+1]), ARGV[i+1], string.len(ARGV[i+2]), ARGV[i+2], 0, ''); " +
                    "redis.call('zadd', KEYS[1], ARGV[i], val); " +
                    "redis.call('zadd', KEYS[2], ARGV[1], val); " +
                 "end; ",
                Arrays.asList(getRawName(), getTimeoutSetName()),
                params.toArray());
        }
        return commandExecutor.evalWriteAsync(getRawName(), codec, RedisCommands.EVAL_VOID,
            "local expirationTime = ARGV[1]; " +
                 "local lastValues = redis.call('zrange', KEYS[2], -1, -1, 'withscores'); " +
                 "if (#lastValues > 0 and tonumber(lastValues[2]) > tonumber(ARGV[1])) then " +
                      "expirationTime = tonumber(lastValues[2]); " +
                 "end; " +
                 "for i = 2, #ARGV, 3 do " +
                    "local val = struct.pack('BBc0Lc0Lc0', 2, string.len(ARGV[i+1]), ARGV[i+1], string.len(ARGV[i+2]), ARGV[i+2], 0, ''); " +
                    "redis.call('zadd', KEYS[1], ARGV[i], val); " +
                    "redis.call('zadd', KEYS[2], expirationTime + 1, val); " +
                 "end; ",
                Arrays.asList(getRawName(), getTimeoutSetName()),
                params.toArray());
    }

    @Override
    public void addAll(Collection<TimeSeriesEntry<V, L>> entries) {
        addAll(entries, null);
    }

    @Override
    public RFuture<Void> addAllAsync(Collection<TimeSeriesEntry<V, L>> entries) {
        return addAllAsync(entries, null);
    }

    @Override
    public void addAll(Collection<TimeSeriesEntry<V, L>> entries, Duration timeToLive) {
        get(addAllAsync(entries, timeToLive));
    }

    @Override
    public RFuture<Void> addAllAsync(Collection<TimeSeriesEntry<V, L>> entries, Duration timeToLive) {
        long expirationTime = System.currentTimeMillis();
        if (timeToLive != null) {
            expirationTime += timeToLive.toMillis();
        } else {
            expirationTime += TimeUnit.DAYS.toMillis(365 * 100);
        }

        List<Object> params = new ArrayList<>();
        params.add(expirationTime);
        for (TimeSeriesEntry<V, L> entry : entries) {
            params.add(entry.getTimestamp());
            byte[] random = getServiceManager().generateIdArray();
            if (entry.getLabel() == null) {
                params.add(2);
            } else {
                params.add(3);
            }
            params.add(random);
            encode(params, entry.getValue());
            if (entry.getLabel() == null) {
                params.add("");
            } else {
                encode(params, entry.getLabel());
            }
        }

        if (timeToLive != null) {
            return commandExecutor.evalWriteAsync(getRawName(), codec, RedisCommands.EVAL_VOID,
           "for i = 2, #ARGV, 5 do " +
                    "local val = struct.pack('BBc0Lc0Lc0', ARGV[i+1], " +
                                                         "string.len(ARGV[i+2]), ARGV[i+2], " +
                                                         "string.len(ARGV[i+3]), ARGV[i+3], " +
                                                         "string.len(ARGV[i+4]), ARGV[i+4]); " +
                    "redis.call('zadd', KEYS[1], ARGV[i], val); " +
                    "redis.call('zadd', KEYS[2], ARGV[1], val); " +
                 "end; ",
                Arrays.asList(getRawName(), getTimeoutSetName()),
                params.toArray());
        }
        return commandExecutor.evalWriteAsync(getRawName(), codec, RedisCommands.EVAL_VOID,
            "local expirationTime = ARGV[1]; " +
                 "local lastValues = redis.call('zrange', KEYS[2], -1, -1, 'withscores'); " +
                 "if (#lastValues > 0 and tonumber(lastValues[2]) > tonumber(ARGV[1])) then " +
                      "expirationTime = tonumber(lastValues[2]); " +
                 "end; " +
                 "for i = 2, #ARGV, 5 do " +
                    "local val = struct.pack('BBc0Lc0Lc0', ARGV[i+1]," +
                                                         "string.len(ARGV[i+2]), ARGV[i+2], " +
                                                         "string.len(ARGV[i+3]), ARGV[i+3], " +
                                                         "string.len(ARGV[i+4]), ARGV[i+4]); " +
                    "redis.call('zadd', KEYS[1], ARGV[i], val); " +
                    "redis.call('zadd', KEYS[2], expirationTime + 1, val); " +
                 "end; ",
                Arrays.asList(getRawName(), getTimeoutSetName()),
                params.toArray());
    }

    @Override
    public int size() {
        return get(sizeAsync());
    }

    @Override
    public RFuture<Integer> sizeAsync() {
        return commandExecutor.evalReadAsync(getRawName(), LongCodec.INSTANCE, RedisCommands.EVAL_INTEGER,
       "local values = redis.call('zrangebyscore', KEYS[2], 0, ARGV[1]);" +
             "return redis.call('zcard', KEYS[1]) - #values;",
            Arrays.asList(getRawName(), getTimeoutSetName()),
            System.currentTimeMillis());
    }

    @Override
    public V get(long timestamp) {
        return get(getAsync(timestamp));
    }

    @Override
    public RFuture<V> getAsync(long timestamp) {
        return commandExecutor.evalReadAsync(getRawName(), codec, RedisCommands.EVAL_OBJECT,
       "local values = redis.call('zrangebyscore', KEYS[1], ARGV[2], ARGV[2]);" +
             "if #values == 0 then " +
                 "return nil;" +
             "end;" +

             "local expirationDate = redis.call('zscore', KEYS[2], values[1]); " +
             "if expirationDate ~= false and tonumber(expirationDate) <= tonumber(ARGV[1]) then " +
                 "return nil;" +
             "end;" +
             "local n, t, val, label = struct.unpack('BBc0Lc0Lc0', values[1]); " +
             "return val;",
            Arrays.asList(getRawName(), getTimeoutSetName()),
            System.currentTimeMillis(), timestamp);
    }

    @Override
    public TimeSeriesEntry<V, L> getEntry(long timestamp) {
        return get(getEntryAsync(timestamp));
    }

    @Override
    public RFuture<TimeSeriesEntry<V, L>> getEntryAsync(long timestamp) {
        return commandExecutor.evalReadAsync(getRawName(), codec, EVAL_ENTRY,
       "local values = redis.call('zrangebyscore', KEYS[1], ARGV[2], ARGV[2]);" +
             "if #values == 0 then " +
                 "return nil;" +
             "end;" +

             "local expirationDate = redis.call('zscore', KEYS[2], values[1]); " +
             "if expirationDate ~= false and tonumber(expirationDate) <= tonumber(ARGV[1]) then " +
                 "return nil;" +
             "end;" +
             "local n, t, val, label = struct.unpack('BBc0Lc0Lc0', values[1]); " +
             "if n == 2 then " +
                "return {n, ARGV[2], val};" +
             "end;" +
             "return {n, ARGV[2], val, label};",
            Arrays.asList(getRawName(), getTimeoutSetName()),
            System.currentTimeMillis(), timestamp);
    }

    @Override
    public boolean remove(long timestamp) {
        return get(removeAsync(timestamp));
    }

    @Override
    public RFuture<Boolean> removeAsync(long timestamp) {
        return commandExecutor.evalWriteAsync(getRawName(), codec, RedisCommands.EVAL_BOOLEAN,
       "local values = redis.call('zrangebyscore', KEYS[1], ARGV[2], ARGV[2]);" +
             "if #values == 0 then " +
                 "return 0;" +
             "end;" +

             "local expirationDate = redis.call('zscore', KEYS[2], values[1]); " +
             "if expirationDate ~= false and tonumber(expirationDate) <= tonumber(ARGV[1]) then " +
                 "return 0;" +
             "end;" +
             "redis.call('zrem', KEYS[2], values[1]); " +
             "redis.call('zrem', KEYS[1], values[1]); " +
             "return 1;",
            Arrays.asList(getRawName(), getTimeoutSetName()),
            System.currentTimeMillis(), timestamp);
    }

    @Override
    public V getAndRemove(long timestamp) {
        return get(getAndRemoveAsync(timestamp));
    }

    @Override
    public RFuture<V> getAndRemoveAsync(long timestamp) {
        return commandExecutor.evalWriteAsync(getRawName(), codec, RedisCommands.EVAL_OBJECT,
       "local values = redis.call('zrangebyscore', KEYS[1], ARGV[2], ARGV[2]);" +
             "if #values == 0 then " +
                 "return nil;" +
             "end;" +

             "local expirationDate = redis.call('zscore', KEYS[2], values[1]); " +
             "if expirationDate ~= false and tonumber(expirationDate) <= tonumber(ARGV[1]) then " +
                 "return nil;" +
             "end;" +
             "redis.call('zrem', KEYS[2], values[1]); " +
             "redis.call('zrem', KEYS[1], values[1]); " +
             "local n, t, val, label = struct.unpack('BBc0Lc0Lc0', values[1]); " +
             "return val;",
            Arrays.asList(getRawName(), getTimeoutSetName()),
            System.currentTimeMillis(), timestamp);
    }

    @Override
    public TimeSeriesEntry<V, L> getAndRemoveEntry(long timestamp) {
        return get(getAndRemoveEntryAsync(timestamp));
    }

    @Override
    public RFuture<TimeSeriesEntry<V, L>> getAndRemoveEntryAsync(long timestamp) {
        return commandExecutor.evalWriteAsync(getRawName(), codec, EVAL_ENTRY,
       "local values = redis.call('zrangebyscore', KEYS[1], ARGV[2], ARGV[2]);" +
             "if #values == 0 then " +
                 "return nil;" +
             "end;" +

             "local expirationDate = redis.call('zscore', KEYS[2], values[1]); " +
             "if expirationDate ~= false and tonumber(expirationDate) <= tonumber(ARGV[1]) then " +
                 "return nil;" +
             "end;" +
             "redis.call('zrem', KEYS[2], values[1]); " +
             "redis.call('zrem', KEYS[1], values[1]); " +
             "local n, t, val, label = struct.unpack('BBc0Lc0Lc0', values[1]); " +
             "if n == 2 then " +
                "return {n, ARGV[2], val};" +
             "end;" +
             "return {n, ARGV[2], val, label};",
            Arrays.asList(getRawName(), getTimeoutSetName()),
            System.currentTimeMillis(), timestamp);
    }

    @Override
    public V last() {
        return get(lastAsync());
    }

    @Override
    public RFuture<V> lastAsync() {
        return listAsync(-1, 1, RedisCommands.EVAL_FIRST_LIST);
    }

    @Override
    public TimeSeriesEntry<V, L> lastEntry() {
        return get(lastEntryAsync());
    }

    @Override
    public RFuture<TimeSeriesEntry<V, L>> lastEntryAsync() {
        return listEntriesAsync(-1, 1, EVAL_FIRST_ENTRY);
    }

    @Override
    public RFuture<Collection<V>> lastAsync(int count) {
        return listAsync(-1, count, RedisCommands.EVAL_LIST_REVERSE);
    }

    @Override
    public V first() {
        return get(firstAsync());
    }

    @Override
    public RFuture<V> firstAsync() {
        return listAsync(0, 1, RedisCommands.EVAL_FIRST_LIST);
    }

    @Override
    public TimeSeriesEntry<V, L> firstEntry() {
        return get(firstEntryAsync());
    }

    @Override
    public RFuture<TimeSeriesEntry<V, L>> firstEntryAsync() {
        return listEntriesAsync(0, 1, EVAL_FIRST_ENTRY);
    }

    @Override
    public RFuture<Collection<V>> firstAsync(int count) {
        return listAsync(0, count, RedisCommands.EVAL_LIST);
    }

    @Override
    public Collection<V> first(int count) {
        return get(listAsync(0, count, RedisCommands.EVAL_LIST));
    }

    @Override
    public Collection<TimeSeriesEntry<V, L>> firstEntries(int count) {
        return get(firstEntriesAsync(count));
    }

    @Override
    public RFuture<Collection<TimeSeriesEntry<V, L>>> firstEntriesAsync(int count) {
        return listEntriesAsync(0, count, EVAL_ENTRIES);
    }

    @Override
    public Collection<V> last(int count) {
        return get(lastAsync(count));
    }

    @Override
    public Collection<TimeSeriesEntry<V, L>> lastEntries(int count) {
        return get(lastEntriesAsync(count));
    }

    @Override
    public RFuture<Collection<TimeSeriesEntry<V, L>>> lastEntriesAsync(int count) {
        return listEntriesAsync(-2, count, EVAL_ENTRIES_REVERSE);
    }

    @Override
    public Long firstTimestamp() {
        return get(firstTimestampAsync());
    }

    @Override
    public RFuture<Long> firstTimestampAsync() {
        return listTimestampAsync(0, 1, RedisCommands.EVAL_FIRST_LIST);
    }

    @Override
    public Long lastTimestamp() {
        return get(lastTimestampAsync());
    }

    @Override
    public RFuture<Long> lastTimestampAsync() {
        return listTimestampAsync(-1, 1, RedisCommands.EVAL_FIRST_LIST);
    }

    private RFuture<Long> listTimestampAsync(int startScore, int limit, RedisCommand<?> evalCommandType) {
        return commandExecutor.evalReadAsync(getRawName(), LongCodec.INSTANCE, evalCommandType,
               "local values;" +
               "if ARGV[2] == '0' then " +
                    "values = redis.call('zrangebyscore', KEYS[2], ARGV[1], '+inf', 'limit', 0, ARGV[3]);" +
               "else " +
                    "values = redis.call('zrevrangebyscore', KEYS[2], '+inf', ARGV[1], 'limit', 0, ARGV[3]);" +
               "end; " +

             "local result = {}; " +
             "for i, v in ipairs(values) do " +
                 "local t = redis.call('zscore', KEYS[1], v); " +
                 "table.insert(result, t);" +
             "end;" +
             "return result;",
            Arrays.asList(getRawName(), getTimeoutSetName()),
            System.currentTimeMillis(), startScore, limit);
    }

    private <T> RFuture<T> listAsync(int startScore, int limit, RedisCommand<?> evalCommandType) {
        return commandExecutor.evalReadAsync(getRawName(), codec, evalCommandType,
               "local values;" +
               "if ARGV[2] == '0' then " +
                    "values = redis.call('zrangebyscore', KEYS[2], ARGV[1], '+inf', 'limit', 0, ARGV[3]);" +
               "else " +
                    "values = redis.call('zrevrangebyscore', KEYS[2], '+inf', ARGV[1], 'limit', 0, ARGV[3]);" +
               "end; " +

             "local result = {}; " +
             "for i, v in ipairs(values) do " +
                 "local n, t, val, label = struct.unpack('BBc0Lc0Lc0', v); " +
                 "table.insert(result, val);" +
             "end;" +
             "return result;",
            Arrays.asList(getRawName(), getTimeoutSetName()),
            System.currentTimeMillis(), startScore, limit);
    }

    private <T> RFuture<T> listEntriesAsync(int startScore, int limit, RedisCommand<?> evalCommandType) {
        return commandExecutor.evalReadAsync(getRawName(), codec, evalCommandType,
             "local values;" +
             "if ARGV[2] == '0' then " +
                  "values = redis.call('zrangebyscore', KEYS[2], ARGV[1], '+inf', 'withscores', 'limit', 0, ARGV[3]);" +
             "else " +
                  "values = redis.call('zrevrangebyscore', KEYS[2], '+inf', ARGV[1], 'withscores', 'limit', 0, ARGV[3]);" +
             "end; " +

             "local result = {}; " +
             "for i=1, #values, 2 do " +
                 "local score = redis.call('zscore', KEYS[1], values[i]); " +
                 "local n, t, val, label = struct.unpack('BBc0Lc0Lc0', values[i]); " +
                 "table.insert(result, val);" +
                 "if n == 2 then " +
                     "label = 0; " +
                 "end; " +
                 "table.insert(result, label);" +
                 "table.insert(result, n);" +
                 "table.insert(result, score);" +
             "end;" +
             "return result;",
            Arrays.asList(getRawName(), getTimeoutSetName()),
            System.currentTimeMillis(), startScore, limit);
    }


    @Override
    public int removeRange(long startTimestamp, long endTimestamp) {
        return get(removeRangeAsync(startTimestamp, endTimestamp));
    }

    @Override
    public RFuture<Integer> removeRangeAsync(long startTimestamp, long endTimestamp) {
        return commandExecutor.evalWriteAsync(getRawName(), LongCodec.INSTANCE, RedisCommands.EVAL_INTEGER,
       "local values = redis.call('zrangebyscore', KEYS[1], ARGV[2], ARGV[3]);" +
             "local counter = 0; " +
             "for i, v in ipairs(values) do " +
                 "local expirationDate = redis.call('zscore', KEYS[2], v); " +
                 "if tonumber(expirationDate) > tonumber(ARGV[1]) then " +
                     "counter = counter + 1; " +
                     "redis.call('zrem', KEYS[2], v); " +
                     "redis.call('zrem', KEYS[1], v); " +
                 "end;" +
             "end;" +
             "return counter;",
            Arrays.asList(getRawName(), getTimeoutSetName()),
            System.currentTimeMillis(), startTimestamp, endTimestamp);
    }

    @Override
    public Collection<V> range(long startTimestamp, long endTimestamp, int limit) {
        return get(rangeAsync(startTimestamp, endTimestamp, limit));
    }

    @Override
    public Collection<V> range(long startTimestamp, long endTimestamp) {
        return get(rangeAsync(startTimestamp, endTimestamp));
    }

    @Override
    public Collection<TimeSeriesEntry<V, L>> entryRange(long startTimestamp, long endTimestamp) {
        return get(entryRangeAsync(false, startTimestamp, endTimestamp, 0));
    }

    @Override
    public Collection<TimeSeriesEntry<V, L>> entryRangeReversed(long startTimestamp, long endTimestamp) {
        return get(entryRangeAsync(true, startTimestamp, endTimestamp, 0));
    }

    @Override
    public RFuture<Collection<TimeSeriesEntry<V, L>>> entryRangeReversedAsync(long startTimestamp, long endTimestamp) {
        return entryRangeAsync(true, startTimestamp, endTimestamp, 0);
    }

    private static final RedisCommand<Object> EVAL_FIRST_ENTRY = new RedisCommand<>("EVAL", new TimeSeriesFirstEntryReplayDecoder() {});

    private static final RedisCommand<List<TimeSeriesEntry<Object, Object>>> EVAL_ENTRIES =
                            new RedisCommand<>("EVAL", new TimeSeriesEntryReplayDecoder());

    private static final RedisCommand<List<TimeSeriesEntry<Object, Object>>> EVAL_ENTRIES_REVERSE =
                            new RedisCommand<>("EVAL", new TimeSeriesEntryReplayDecoder(true));

    private static final RedisCommand<TimeSeriesEntry<Object, Object>> EVAL_ENTRY =
            new RedisCommand<>("EVAL", new TimeSeriesSingleEntryReplayDecoder());

    @Override
    public RFuture<Collection<TimeSeriesEntry<V, L>>> entryRangeAsync(long startTimestamp, long endTimestamp) {
        return entryRangeAsync(false, startTimestamp, endTimestamp, 0);
    }

    private RFuture<Collection<TimeSeriesEntry<V, L>>> entryRangeAsync(boolean reverse, long startTimestamp, long endTimestamp, int limit) {
        return commandExecutor.evalReadAsync(getRawName(), codec, EVAL_ENTRIES,
          "local result = {}; " +
          "local from = ARGV[2]; " +
          "local to = ARGV[3]; " +
          "local limit = tonumber(ARGV[4]); " +

          "local cmd = 'zrangebyscore'; " +
          "if ARGV[5] ~= '0' then " +
              "from = ARGV[3]; " +
              "to = ARGV[2]; " +
              "cmd = 'zrevrangebyscore';" +
          "end; " +

          "while true do " +
             "local values;" +
             "if ARGV[4] ~= '0' then " +
                "values = redis.call(cmd, KEYS[1], from, to, 'withscores', 'limit', 0, limit);" +
             "else " +
                "values = redis.call(cmd, KEYS[1], from, to, 'withscores');" +
             "end; " +

             "for i=1, #values, 2 do " +
                 "local expirationDate = redis.call('zscore', KEYS[2], values[i]);" +
                 "if tonumber(expirationDate) > tonumber(ARGV[1]) then " +
                     "local n, t, val, label = struct.unpack('BBc0Lc0Lc0', values[i]); " +
                     "table.insert(result, val);" +
                     "if n == 2 then " +
                         "label = 0; " +
                     "end; " +
                     "table.insert(result, label);" +
                     "table.insert(result, n);" +
                     "table.insert(result, values[i+1]);" +
                 "end;" +
             "end;" +

             "if limit == 0 or #result/4 == tonumber(ARGV[4]) or #values/2 < limit then " +
                 "return result;" +
             "end;" +
             "from = '(' .. values[#values];" +
             "limit = tonumber(ARGV[4]) - #result/4;" +
          "end;",
            Arrays.asList(getRawName(), getTimeoutSetName()),
            System.currentTimeMillis(), startTimestamp, endTimestamp, limit, Boolean.compare(reverse, false), encode((Object) null));
    }

    @Override
    public Collection<V> rangeReversed(long startTimestamp, long endTimestamp, int limit) {
        return get(rangeReversedAsync(startTimestamp, endTimestamp, limit));
    }

    @Override
    public RFuture<Collection<V>> rangeAsync(long startTimestamp, long endTimestamp) {
        return rangeAsync(startTimestamp, endTimestamp, 0);
    }

    @Override
    public RFuture<Collection<V>> rangeAsync(long startTimestamp, long endTimestamp, int limit) {
        return rangeAsync(false, startTimestamp, endTimestamp, limit);
    }

    @Override
    public Collection<V> rangeReversed(long startTimestamp, long endTimestamp) {
        return get(rangeReversedAsync(startTimestamp, endTimestamp));
    }

    @Override
    public RFuture<Collection<V>> rangeReversedAsync(long startTimestamp, long endTimestamp) {
        return rangeReversedAsync(startTimestamp, endTimestamp, 0);
    }

    @Override
    public RFuture<Collection<V>> rangeReversedAsync(long startTimestamp, long endTimestamp, int limit) {
        return rangeAsync(true, startTimestamp, endTimestamp, limit);
    }

    private RFuture<Collection<V>> rangeAsync(boolean reverse, long startTimestamp, long endTimestamp, int limit) {
        return commandExecutor.evalReadAsync(getRawName(), codec, RedisCommands.EVAL_LIST,
          "local result = {}; " +
          "local from = ARGV[2]; " +
          "local to = ARGV[3]; " +
          "local limit = tonumber(ARGV[4]); " +

          "local cmd = 'zrangebyscore'; " +
          "if ARGV[5] ~= '0' then " +
              "from = ARGV[3]; " +
              "to = ARGV[2]; " +
              "cmd = 'zrevrangebyscore';" +
          "end; " +

          "while true do " +
             "local values;" +
             "if ARGV[4] ~= '0' then " +
                "values = redis.call(cmd, KEYS[1], from, to, 'withscores', 'limit', 0, limit);" +
             "else " +
                "values = redis.call(cmd, KEYS[1], from, to, 'withscores');" +
             "end; " +

             "for i=1, #values, 2 do " +
                 "local expirationDate = redis.call('zscore', KEYS[2], values[i]);" +
                 "if tonumber(expirationDate) > tonumber(ARGV[1]) then " +
                     "local n, t, val, label = struct.unpack('BBc0Lc0Lc0', values[i]); " +
                     "table.insert(result, val);" +
                 "end;" +
             "end;" +

             "if limit == 0 or #result == tonumber(ARGV[4]) or #values/2 < tonumber(limit) then " +
                 "return result;" +
             "end;" +
             "from = '(' .. values[#values];" +
             "limit = tonumber(ARGV[4]) - #result;" +
          "end;",
            Arrays.asList(getRawName(), getTimeoutSetName()),
            System.currentTimeMillis(), startTimestamp, endTimestamp, limit, Boolean.compare(reverse, false));
    }

    @Override
    public Collection<TimeSeriesEntry<V, L>> entryRange(long startTimestamp, long endTimestamp, int limit) {
        return get(entryRangeAsync(startTimestamp, endTimestamp, limit));
    }

    @Override
    public RFuture<Collection<TimeSeriesEntry<V, L>>> entryRangeAsync(long startTimestamp, long endTimestamp, int limit) {
        return entryRangeAsync(false, startTimestamp, endTimestamp, limit);
    }

    @Override
    public Collection<TimeSeriesEntry<V, L>> entryRangeReversed(long startTimestamp, long endTimestamp, int limit) {
        return get(entryRangeReversedAsync(startTimestamp, endTimestamp, limit));
    }

    @Override
    public RFuture<Collection<TimeSeriesEntry<V, L>>> entryRangeReversedAsync(long startTimestamp, long endTimestamp, int limit) {
        return entryRangeAsync(true, startTimestamp, endTimestamp, limit);
    }

    @Override
    public Collection<V> pollFirst(int count) {
        return get(pollFirstAsync(count));
    }

    @Override
    public Collection<V> pollLast(int count) {
        return get(pollLastAsync(count));
    }

    @Override
    public RFuture<Collection<V>> pollFirstAsync(int count) {
        if (count <= 0) {
            return new CompletableFutureWrapper<>(Collections.emptyList());
        }

        return pollAsync(0, count, RedisCommands.EVAL_LIST);
    }

    @Override
    public RFuture<Collection<V>> pollLastAsync(int count) {
        if (count <= 0) {
            return new CompletableFutureWrapper<>(Collections.emptyList());
        }
        return pollAsync(-1, count, RedisCommands.EVAL_LIST_REVERSE);
    }

    @Override
    public Collection<TimeSeriesEntry<V, L>> pollFirstEntries(int count) {
        return get(pollFirstEntriesAsync(count));
    }

    @Override
    public RFuture<Collection<TimeSeriesEntry<V, L>>> pollFirstEntriesAsync(int count) {
        if (count <= 0) {
            return new CompletableFutureWrapper<>(Collections.emptyList());
        }

        return pollEntriesAsync(0, count, EVAL_ENTRIES);
    }

    @Override
    public Collection<TimeSeriesEntry<V, L>> pollLastEntries(int count) {
        return get(pollLastEntriesAsync(count));
    }

    @Override
    public RFuture<Collection<TimeSeriesEntry<V, L>>> pollLastEntriesAsync(int count) {
        if (count <= 0) {
            return new CompletableFutureWrapper<>(Collections.emptyList());
        }
        return pollEntriesAsync(-1, count, EVAL_ENTRIES_REVERSE);
    }

    @Override
    public V pollFirst() {
        return get(pollFirstAsync());
    }

    @Override
    public V pollLast() {
        return get(pollLastAsync());
    }

    @Override
    public RFuture<V> pollFirstAsync() {
        return pollAsync(0, 1, RedisCommands.EVAL_FIRST_LIST);
    }

    @Override
    public RFuture<V> pollLastAsync() {
        return pollAsync(-1, 1, RedisCommands.EVAL_FIRST_LIST);
    }

    @Override
    public TimeSeriesEntry<V, L> pollFirstEntry() {
        return get(pollFirstEntryAsync());
    }

    @Override
    public RFuture<TimeSeriesEntry<V, L>> pollFirstEntryAsync() {
        return pollEntriesAsync(0, 1, EVAL_FIRST_ENTRY);
    }

    @Override
    public TimeSeriesEntry<V, L> pollLastEntry() {
        return get(pollLastEntryAsync());
    }

    @Override
    public RFuture<TimeSeriesEntry<V, L>> pollLastEntryAsync() {
        return pollEntriesAsync(-1, 1, EVAL_FIRST_ENTRY);
    }

    private <T> RFuture<T> pollAsync(int startScore, int limit, RedisCommand<?> command) {
        return commandExecutor.evalWriteAsync(getRawName(), codec, command,
               "local values;" +
               "if ARGV[2] == '0' then " +
                    "values = redis.call('zrangebyscore', KEYS[2], ARGV[1], '+inf', 'limit', 0, ARGV[3]);" +
               "else " +
                    "values = redis.call('zrevrangebyscore', KEYS[2], '+inf', ARGV[1], 'limit', 0, ARGV[3]);" +
               "end; " +

             "local result = {}; " +
             "for i, v in ipairs(values) do " +
                 "redis.call('zrem', KEYS[2], v); " +
                 "redis.call('zrem', KEYS[1], v); " +
                 "local n, t, val, label = struct.unpack('BBc0Lc0Lc0', v); " +
                 "table.insert(result, val);" +
             "end;" +
             "return result;",
            Arrays.asList(getRawName(), getTimeoutSetName()),
            System.currentTimeMillis(), startScore, limit);
    }

    private <T> RFuture<T> pollEntriesAsync(int startScore, int limit, RedisCommand<?> command) {
        return commandExecutor.evalWriteAsync(getRawName(), codec, command,
               "local values;" +
               "if ARGV[2] == '0' then " +
                    "values = redis.call('zrangebyscore', KEYS[2], ARGV[1], '+inf', 'withscores', 'limit', 0, ARGV[3]);" +
               "else " +
                    "values = redis.call('zrevrangebyscore', KEYS[2], '+inf', ARGV[1], 'withscores', 'limit', 0, ARGV[3]);" +
               "end; " +

             "local result = {}; " +
             "for i=1, #values, 2 do " +
                 "local score = redis.call('zscore', KEYS[1], values[i]); " +
                 "redis.call('zrem', KEYS[2], values[i]); " +
                 "redis.call('zrem', KEYS[1], values[i]); " +
                 "local n, t, val, label = struct.unpack('BBc0Lc0Lc0', values[i]); " +
                 "table.insert(result, val);" +
                 "if n == 2 then " +
                     "label = 0; " +
                 "end; " +
                 "table.insert(result, label);" +
                 "table.insert(result, n);" +
                 "table.insert(result, score);" +
             "end;" +
             "return result;",
            Arrays.asList(getRawName(), getTimeoutSetName()),
            System.currentTimeMillis(), startScore, limit);
    }


    public ListScanResult<Object> scanIterator(String name, RedisClient client, long startPos, int count) {
        RFuture<ListScanResult<Object>> f = scanIteratorAsync(name, client, startPos, count);
        return get(f);
    }

    public RFuture<ListScanResult<Object>> scanIteratorAsync(String name, RedisClient client, long startPos, int count) {
        List<Object> params = new ArrayList<>();
        params.add(startPos);
        params.add(System.currentTimeMillis());
        params.add(count);

        return commandExecutor.evalReadAsync(client, name, codec, RedisCommands.EVAL_SCAN,
                  "local result = {}; "
                + "local res = redis.call('zrange', KEYS[1], ARGV[1], tonumber(ARGV[1]) + tonumber(ARGV[3]) - 1); "
                + "for i, value in ipairs(res) do "
                   + "local expirationDate = redis.call('zscore', KEYS[2], value); " +
                     "if tonumber(expirationDate) > tonumber(ARGV[2]) then " +
                         "local n, t, val, label = struct.unpack('BBc0Lc0Lc0', value); " +
                         "table.insert(result, val);" +
                     "end;"
                + "end;" +

                  "local nextPos = tonumber(ARGV[1]) + tonumber(ARGV[3]); " +
                  "if #res < tonumber(ARGV[3]) then " +
                    "nextPos = 0;" +
                  "end;"

                + "return {nextPos, result};",
                Arrays.asList(name, getTimeoutSetName()),
                params.toArray());
    }

    @Override
    public Iterator<V> iterator(int count) {
        return new RedissonBaseIterator<V>() {

            @Override
            protected ListScanResult<Object> iterator(RedisClient client, long nextIterPos) {
                return scanIterator(getRawName(), client, nextIterPos, count);
            }

            @Override
            protected void remove(Object value) {
                throw new UnsupportedOperationException();
            }

        };
    }

    @Override
    public Iterator<V> iterator() {
        return iterator(10);
    }

    @Override
    public Stream<V> stream() {
        return toStream(iterator());
    }

    @Override
    public Stream<V> stream(int count) {
        return toStream(iterator(count));
    }

    @Override
    public void destroy() {
        if (evictionScheduler != null) {
            evictionScheduler.remove(getRawName());
        }
    }

    @Override
    public RFuture<Boolean> deleteAsync() {
        return deleteAsync(getRawName(), getTimeoutSetName());
    }

    @Override
    public RFuture<Boolean> expireAsync(long timeToLive, TimeUnit timeUnit, String param, String... keys) {
        return super.expireAsync(timeToLive, timeUnit, param, getRawName(), getTimeoutSetName());
    }

    @Override
    protected RFuture<Boolean> expireAtAsync(long timestamp, String param, String... keys) {
        return super.expireAtAsync(timestamp, getRawName(), getTimeoutSetName());
    }

    @Override
    public RFuture<Boolean> clearExpireAsync() {
        return clearExpireAsync(getRawName(), getTimeoutSetName());
    }

    @Override
    public RFuture<Long> sizeInMemoryAsync() {
        List<Object> keys = Arrays.asList(getRawName(), getTimeoutSetName());
        return super.sizeInMemoryAsync(keys);
    }

}
