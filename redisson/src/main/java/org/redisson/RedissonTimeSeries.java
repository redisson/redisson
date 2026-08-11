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
import org.redisson.api.RTimeSeries;
import org.redisson.api.ts.TimeSeriesAddArgs;
import org.redisson.api.ts.TimeSeriesAddParams;
import org.redisson.api.TimeSeriesEntry;
import org.redisson.api.listener.ScoredSortedSetAddListener;
import org.redisson.api.listener.ScoredSortedSetRemoveListener;
import org.redisson.api.listener.TrackingListener;
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
    private String timeoutSetName;
    private String sequenceName;

    public RedissonTimeSeries(EvictionScheduler evictionScheduler, CommandAsyncExecutor connectionManager, String name) {
        super(connectionManager, name);

        this.evictionScheduler = evictionScheduler;
        this.timeoutSetName = getTimeoutSetName(getRawName());
        this.sequenceName = getSequenceName(getRawName());
        if (evictionScheduler != null) {
            evictionScheduler.scheduleTimeSeries(getRawName(), timeoutSetName, sequenceName);
        }
    }

    public RedissonTimeSeries(Codec codec, EvictionScheduler evictionScheduler, CommandAsyncExecutor connectionManager, String name) {
        super(codec, connectionManager, name);

        this.evictionScheduler = evictionScheduler;
        this.timeoutSetName = getTimeoutSetName(getRawName());
        this.sequenceName = getSequenceName(getRawName());
        if (evictionScheduler != null) {
            evictionScheduler.scheduleTimeSeries(getRawName(), timeoutSetName, sequenceName);
        }
    }

    String getTimeoutSetName(String name) {
        return prefixName("redisson__ts_ttl", name);
    }

    String getSequenceName(String name) {
        return prefixName("redisson__ts_seq", name);
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
        addAll(objects, 0, TimeUnit.MILLISECONDS);
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
    public void add(long timestamp, V object, Duration timeToLive) {
        get(addAsync(timestamp, object, timeToLive));
    }

    @Override
    public RFuture<Void> addAsync(long timestamp, V object, Duration timeToLive) {
        return addAllAsync(Collections.singletonMap(timestamp, object), timeToLive);
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
        return addAllAsync(objects, 0, TimeUnit.MILLISECONDS);
    }

    @Override
    public RFuture<Void> addAllAsync(Map<Long, V> objects, long timeToLive, TimeUnit timeUnit) {
        return addAllAsync(objects, Duration.ofMillis(timeUnit.toMillis(timeToLive)));
    }

    @Override
    public void addAll(Map<Long, V> objects, Duration timeToLive) {
        get(addAllAsync(objects, timeToLive));
    }

    @Override
    public RFuture<Void> addAllAsync(Map<Long, V> objects, Duration timeToLive) {
        long expirationTime = System.currentTimeMillis();
        if (timeToLive != null && !timeToLive.isZero()) {
            expirationTime += timeToLive.toMillis();
        } else {
            expirationTime += TimeUnit.DAYS.toMillis(365 * 100);
        }

        List<Object> params = new ArrayList<>();
        params.add(SEQUENCE_WIDTH);
        params.add(expirationTime);
        for (Map.Entry<Long, V> entry : objects.entrySet()) {
            params.add(entry.getKey());
            encode(params, entry.getValue());
        }

        if (timeToLive != null && !timeToLive.isZero()) {
            return commandExecutor.evalWriteAsync(getRawName(), codec, RedisCommands.EVAL_VOID,
                NEXT_ID +
                "for i = 3, #ARGV, 2 do " +
                    "local id = nextId(); " +
                    "local lbl = string.char(2); " +
                    "local val = struct.pack('BBc0Lc0Lc0', 4, string.len(id), id, string.len(ARGV[i+1]), ARGV[i+1], string.len(lbl), lbl); " +
                    "redis.call('zadd', KEYS[1], ARGV[i], val); " +
                    "redis.call('zadd', KEYS[2], ARGV[2], val); " +
                 "end; ",
                Arrays.asList(getRawName(), timeoutSetName, sequenceName),
                params.toArray());
        }
        return commandExecutor.evalWriteAsync(getRawName(), codec, RedisCommands.EVAL_VOID,
            NEXT_ID +
            "local expirationTime = ARGV[2]; " +
                 "local lastValues = redis.call('zrange', KEYS[2], -1, -1, 'withscores'); " +
                 "if (#lastValues > 0 and tonumber(lastValues[2]) > tonumber(ARGV[2])) then " +
                      "expirationTime = tonumber(lastValues[2]); " +
                 "end; " +
                 "for i = 3, #ARGV, 2 do " +
                    "local id = nextId(); " +
                    "local lbl = string.char(2); " +
                    "local val = struct.pack('BBc0Lc0Lc0', 4, string.len(id), id, string.len(ARGV[i+1]), ARGV[i+1], string.len(lbl), lbl); " +
                    "redis.call('zadd', KEYS[1], ARGV[i], val); " +
                    "redis.call('zadd', KEYS[2], expirationTime + 1, val); " +
                 "end; ",
                Arrays.asList(getRawName(), timeoutSetName, sequenceName),
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
        params.add(SEQUENCE_WIDTH);
        params.add(expirationTime);
        for (TimeSeriesEntry<V, L> entry : entries) {
            params.add(entry.getTimestamp());
            if (entry.getLabel() == null) {
                params.add(2);
            } else {
                params.add(3);
            }
            encode(params, entry.getValue());
            if (entry.getLabel() == null) {
                params.add("");
            } else {
                encode(params, entry.getLabel());
            }
        }

        if (timeToLive != null) {
            return commandExecutor.evalWriteAsync(getRawName(), codec, RedisCommands.EVAL_VOID,
                NEXT_ID +
                "for i = 3, #ARGV, 4 do " +
                    "local id = nextId(); " +
                    "local lbl = string.char(ARGV[i+1]) .. ARGV[i+3]; " +
                    "local val = struct.pack('BBc0Lc0Lc0', 4, " +
                                                         "string.len(id), id, " +
                                                         "string.len(ARGV[i+2]), ARGV[i+2], " +
                                                         "string.len(lbl), lbl); " +
                    "redis.call('zadd', KEYS[1], ARGV[i], val); " +
                    "redis.call('zadd', KEYS[2], ARGV[2], val); " +
                 "end; ",
                Arrays.asList(getRawName(), timeoutSetName, sequenceName),
                params.toArray());
        }
        return commandExecutor.evalWriteAsync(getRawName(), codec, RedisCommands.EVAL_VOID,
            NEXT_ID +
            "local expirationTime = ARGV[2]; " +
                 "local lastValues = redis.call('zrange', KEYS[2], -1, -1, 'withscores'); " +
                 "if (#lastValues > 0 and tonumber(lastValues[2]) > tonumber(ARGV[2])) then " +
                      "expirationTime = tonumber(lastValues[2]); " +
                 "end; " +
                 "for i = 3, #ARGV, 4 do " +
                    "local id = nextId(); " +
                    "local lbl = string.char(ARGV[i+1]) .. ARGV[i+3]; " +
                    "local val = struct.pack('BBc0Lc0Lc0', 4, " +
                                                         "string.len(id), id, " +
                                                         "string.len(ARGV[i+2]), ARGV[i+2], " +
                                                         "string.len(lbl), lbl); " +
                    "redis.call('zadd', KEYS[1], ARGV[i], val); " +
                    "redis.call('zadd', KEYS[2], expirationTime + 1, val); " +
                 "end; ",
                Arrays.asList(getRawName(), timeoutSetName, sequenceName),
                params.toArray());
    }

    /*
     * Members written by this version start with the marker 4 and carry the label flag as
     * the first byte of the label field. Members written by earlier versions start with
     * the flag itself (2 or 3) and hold the label unprefixed. Both shapes unpack with the
     * same format string, so only the places that actually read a label need to branch.
     */
    private static final String DECODE_LABEL =
             "if n == 4 then " +
                 "local flag = string.byte(label, 1); " +
                 "if flag == 2 then " +
                     "label = 0; " +
                 "else " +
                     "label = string.sub(label, 2); " +
                 "end; " +
                 "n = flag; " +
             "elseif n == 2 then " +
                 "label = 0; " +
             "end; ";

    static final int SEQUENCE_WIDTH = 20;

    private static final String NEXT_ID =
             "local sequenceWidth = tonumber(ARGV[1]); " +
             "local previous; " +
             "local function nextId() " +
                 "if previous == nil then " +
                     "previous = redis.call('get', KEYS[3]); " +
                     "if previous == false then " +
                         "previous = '0'; " +
                     "end; " +
                 "end; " +
                 "local sequence = redis.call('incrbyfloat', KEYS[3], 1); " +
                 "if sequence == previous or #sequence > sequenceWidth then " +
                     "error('RTimeSeries sequence overflow'); " +
                 "end; " +
                 "previous = sequence; " +
                 "return string.rep('0', sequenceWidth - #sequence) .. sequence; " +
             "end; ";


    /*
     * The window is anchored on the highest timestamp the collection holds once this call is
     * applied: the highest of the entries being added and the highest one already present.
     * An entry that has expired but has not been evicted yet is not a valid anchor - it would
     * drag the window backwards and, sitting inside its own window, keep doing it - so it is
     * removed here, bounded, exactly as the eviction task would remove it. Giving up leaves
     * the anchor low, which under-trims rather than over-trims.
     */
    private static final String RETENTION_CUTOFF =
             "local cutoff; " +
             "local retention = tonumber(ARGV[2]); " +
             "if retention > 0 then " +
                 "local highest; " +
                 "for i = 4, #ARGV, 5 do " +
                     "local timestamp = tonumber(ARGV[i]); " +
                     "if highest == nil or timestamp > highest then " +
                         "highest = timestamp; " +
                     "end; " +
                 "end; " +
                 "local probes = 0; " +
                 "while probes < 100 do " +
                     "local top = redis.call('zrevrange', KEYS[1], 0, 0, 'withscores'); " +
                     "if #top == 0 then " +
                         "break; " +
                     "end; " +
                     "local expirationDate = redis.call('zscore', KEYS[2], top[1]); " +
                     "if expirationDate == false or tonumber(expirationDate) > tonumber(ARGV[3]) then " +
                         "local timestamp = tonumber(top[2]); " +
                         "if highest == nil or timestamp > highest then " +
                             "highest = timestamp; " +
                         "end; " +
                         "break; " +
                     "end; " +
                     "redis.call('zrem', KEYS[1], top[1]); " +
                     "redis.call('zrem', KEYS[2], top[1]); " +
                     "probes = probes + 1; " +
                 "end; " +
                 "if highest ~= nil then " +
                     "cutoff = highest - retention; " +
                 "end; " +
             "end; ";

    private static final String RETENTION_TRIM =
             "if cutoff ~= nil then " +
                 "local stale = redis.call('zrangebyscore', KEYS[1], '-inf', " +
                                          "'(' .. string.format('%.0f', cutoff), 'limit', 0, 500); " +
                 "if #stale > 0 then " +
                     "redis.call('zrem', KEYS[1], unpack(stale)); " +
                     "redis.call('zrem', KEYS[2], unpack(stale)); " +
                 "end; " +
             "end; ";

    private static final String ADD_IF_ABSENT = NEXT_ID + RETENTION_CUTOFF +
             "local added = 0; " +
             "local occupiedAt = {}; " +
             "for i = 4, #ARGV, 5 do " +
                 "if cutoff == nil or tonumber(ARGV[i]) >= cutoff then " +
                     "local occupied = occupiedAt[ARGV[i]]; " +
                     "local existing = {}; " +
                     "if occupied == nil then " +
                         "occupied = false; " +
                         "existing = redis.call('zrangebyscore', KEYS[1], ARGV[i], ARGV[i]); " +
                     "end; " +
                     "for j = 1, #existing do " +
                         "local expirationDate = redis.call('zscore', KEYS[2], existing[j]); " +
                         "if expirationDate == false or tonumber(expirationDate) > tonumber(ARGV[3]) then " +
                             "occupied = true; " +
                         "else " +
                             "redis.call('zrem', KEYS[1], existing[j]); " +
                             "redis.call('zrem', KEYS[2], existing[j]); " +
                         "end; " +
                     "end; " +
                     "if occupied == false then " +
                         "local id = nextId(); " +
                         "local lbl = string.char(ARGV[i+1]) .. ARGV[i+3]; " +
                         "local val = struct.pack('BBc0Lc0Lc0', 4, " +
                                                               "string.len(id), id, " +
                                                               "string.len(ARGV[i+2]), ARGV[i+2], " +
                                                               "string.len(lbl), lbl); " +
                         "redis.call('zadd', KEYS[1], ARGV[i], val); " +
                         "redis.call('zadd', KEYS[2], ARGV[i+4], val); " +
                         "added = added + 1; " +
                     "end; " +
                     "occupiedAt[ARGV[i]] = true; " +
                 "end; " +
             "end; " +
             RETENTION_TRIM +
             "return added;";

    private static final String ADD_OR_REPLACE = NEXT_ID + RETENTION_CUTOFF +
             "local created = 0; " +
             "for i = 4, #ARGV, 5 do " +
                 "if cutoff == nil or tonumber(ARGV[i]) >= cutoff then " +
                     "local replaced = false; " +
                     "local existing = redis.call('zrangebyscore', KEYS[1], ARGV[i], ARGV[i]); " +
                     "for j = 1, #existing do " +
                         "local expirationDate = redis.call('zscore', KEYS[2], existing[j]); " +
                         "if expirationDate == false or tonumber(expirationDate) > tonumber(ARGV[3]) then " +
                             "replaced = true; " +
                         "end; " +
                         "redis.call('zrem', KEYS[1], existing[j]); " +
                         "redis.call('zrem', KEYS[2], existing[j]); " +
                     "end; " +
                     "local id = nextId(); " +
                     "local lbl = string.char(ARGV[i+1]) .. ARGV[i+3]; " +
                     "local val = struct.pack('BBc0Lc0Lc0', 4, " +
                                                           "string.len(id), id, " +
                                                           "string.len(ARGV[i+2]), ARGV[i+2], " +
                                                           "string.len(lbl), lbl); " +
                     "redis.call('zadd', KEYS[1], ARGV[i], val); " +
                     "redis.call('zadd', KEYS[2], ARGV[i+4], val); " +
                     "if replaced == false then " +
                         "created = created + 1; " +
                     "end; " +
                 "end; " +
             "end; " +
             RETENTION_TRIM +
             "return created;";

    private Object[] encodeArgs(Collection<? extends TimeSeriesAddArgs<V, ? super L>> entries) {
        long now = System.currentTimeMillis();
        long retention = 0;
        for (TimeSeriesAddArgs<V, ? super L> entry : entries) {
            Duration value = ((TimeSeriesAddParams<V, ?>) entry).getRetention();
            if (value == null || value.isNegative()) {
                continue;
            }
            // entries in one call describe one collection, so the widest window wins,
            // which is both order independent and the reading that loses no data
            if (value.getSeconds() >= Long.MAX_VALUE / 1000) {
                retention = Long.MAX_VALUE;
            } else {
                retention = Math.max(retention, value.toMillis());
            }
        }

        List<Object> params = new ArrayList<>();
        params.add(SEQUENCE_WIDTH);
        params.add(retention);
        params.add(now);
        for (TimeSeriesAddArgs<V, ? super L> entry : entries) {
            TimeSeriesAddParams<V, ?> args = (TimeSeriesAddParams<V, ?>) entry;
            params.add(args.getTimestamp());
            if (args.getLabel() == null) {
                params.add(2);
            } else {
                params.add(3);
            }
            encode(params, args.getObject());
            if (args.getLabel() == null) {
                params.add("");
            } else {
                encode(params, args.getLabel());
            }
            Duration ttl = args.getTimeToLive();
            if (ttl == null || ttl.isZero() || ttl.isNegative()) {
                params.add(now + TimeUnit.DAYS.toMillis(365 * 100));
            } else {
                params.add(now + ttl.toMillis());
            }
        }
        return params.toArray();
    }

    @Override
    public boolean addIfAbsent(TimeSeriesAddArgs<V, ? super L> entry) {
        return get(addIfAbsentAsync(entry));
    }

    @Override
    public RFuture<Boolean> addIfAbsentAsync(TimeSeriesAddArgs<V, ? super L> entry) {
        return commandExecutor.evalWriteAsync(getRawName(), codec, RedisCommands.EVAL_BOOLEAN,
                ADD_IF_ABSENT,
                Arrays.asList(getRawName(), timeoutSetName, sequenceName),
                encodeArgs(Collections.singletonList(entry)));
    }

    @Override
    public int addAllIfAbsent(Collection<? extends TimeSeriesAddArgs<V, ? super L>> entries) {
        return get(addAllIfAbsentAsync(entries));
    }

    @Override
    public RFuture<Integer> addAllIfAbsentAsync(Collection<? extends TimeSeriesAddArgs<V, ? super L>> entries) {
        if (entries.isEmpty()) {
            return new CompletableFutureWrapper<>(0);
        }
        return commandExecutor.evalWriteAsync(getRawName(), codec, RedisCommands.EVAL_INTEGER,
                ADD_IF_ABSENT,
                Arrays.asList(getRawName(), timeoutSetName, sequenceName),
                encodeArgs(entries));
    }

    @Override
    public boolean addOrReplace(TimeSeriesAddArgs<V, ? super L> entry) {
        return get(addOrReplaceAsync(entry));
    }

    @Override
    public RFuture<Boolean> addOrReplaceAsync(TimeSeriesAddArgs<V, ? super L> entry) {
        return commandExecutor.evalWriteAsync(getRawName(), codec, RedisCommands.EVAL_BOOLEAN,
                ADD_OR_REPLACE,
                Arrays.asList(getRawName(), timeoutSetName, sequenceName),
                encodeArgs(Collections.singletonList(entry)));
    }

    @Override
    public int addAllOrReplace(Collection<? extends TimeSeriesAddArgs<V, ? super L>> entries) {
        return get(addAllOrReplaceAsync(entries));
    }

    @Override
    public RFuture<Integer> addAllOrReplaceAsync(Collection<? extends TimeSeriesAddArgs<V, ? super L>> entries) {
        if (entries.isEmpty()) {
            return new CompletableFutureWrapper<>(0);
        }
        return commandExecutor.evalWriteAsync(getRawName(), codec, RedisCommands.EVAL_INTEGER,
                ADD_OR_REPLACE,
                Arrays.asList(getRawName(), timeoutSetName, sequenceName),
                encodeArgs(entries));
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
            Arrays.asList(getRawName(), timeoutSetName),
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
             "for i = 1, #values do " +
                 "local expirationDate = redis.call('zscore', KEYS[2], values[i]); " +
                 "if expirationDate == false or tonumber(expirationDate) > tonumber(ARGV[1]) then " +
                     "local n, t, val, label = struct.unpack('BBc0Lc0Lc0', values[i]); " +
                     "return val;" +
                 "end;" +
             "end;" +
             "return nil;",
            Arrays.asList(getRawName(), timeoutSetName),
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
             "for i = 1, #values do " +
                 "local expirationDate = redis.call('zscore', KEYS[2], values[i]); " +
                 "if expirationDate == false or tonumber(expirationDate) > tonumber(ARGV[1]) then " +
                     "local n, t, val, label = struct.unpack('BBc0Lc0Lc0', values[i]); " +
                     DECODE_LABEL +
                     "if n == 2 then " +
                        "return {n, ARGV[2], val};" +
                     "end;" +
                     "return {n, ARGV[2], val, label};" +
                 "end;" +
             "end;" +
             "return nil;",
            Arrays.asList(getRawName(), timeoutSetName),
            System.currentTimeMillis(), timestamp);
    }

    /*
     * Plural accessors. A timestamp may hold several entries, and the singular get/remove
     * family only ever reaches the first live one; these reach all of them, in the order
     * they were added. Expired entries are skipped, and left for the eviction task.
     */
    private static final String LIVE_AT_TIMESTAMP =
             "local result = {}; " +
             "local values = redis.call('zrangebyscore', KEYS[1], ARGV[2], ARGV[2]); " +
             "for i = 1, #values do " +
                 "local expirationDate = redis.call('zscore', KEYS[2], values[i]); " +
                 "if expirationDate == false or tonumber(expirationDate) > tonumber(ARGV[1]) then ";

    @Override
    public Collection<V> getAll(long timestamp) {
        return get(getAllAsync(timestamp));
    }

    @Override
    public RFuture<Collection<V>> getAllAsync(long timestamp) {
        return commandExecutor.evalReadAsync(getRawName(), codec, RedisCommands.EVAL_LIST,
            LIVE_AT_TIMESTAMP +
                     "local n, t, val, label = struct.unpack('BBc0Lc0Lc0', values[i]); " +
                     "table.insert(result, val); " +
                 "end; " +
             "end; " +
             "return result;",
            Arrays.asList(getRawName(), timeoutSetName),
            System.currentTimeMillis(), timestamp);
    }

    @Override
    public Collection<TimeSeriesEntry<V, L>> getAllEntries(long timestamp) {
        return get(getAllEntriesAsync(timestamp));
    }

    @Override
    public RFuture<Collection<TimeSeriesEntry<V, L>>> getAllEntriesAsync(long timestamp) {
        return commandExecutor.evalReadAsync(getRawName(), codec, EVAL_ENTRIES,
            LIVE_AT_TIMESTAMP +
                     "local n, t, val, label = struct.unpack('BBc0Lc0Lc0', values[i]); " +
                     DECODE_LABEL +
                     "table.insert(result, val); " +
                     "table.insert(result, label); " +
                     "table.insert(result, n); " +
                     "table.insert(result, ARGV[2]); " +
                 "end; " +
             "end; " +
             "return result;",
            Arrays.asList(getRawName(), timeoutSetName),
            System.currentTimeMillis(), timestamp);
    }

    @Override
    public int removeAll(long timestamp) {
        return get(removeAllAsync(timestamp));
    }

    @Override
    public RFuture<Integer> removeAllAsync(long timestamp) {
        return commandExecutor.evalWriteAsync(getRawName(), LongCodec.INSTANCE, RedisCommands.EVAL_INTEGER,
       "local counter = 0; " +
             "local values = redis.call('zrangebyscore', KEYS[1], ARGV[2], ARGV[2]); " +
             "for i = 1, #values do " +
                 "local expirationDate = redis.call('zscore', KEYS[2], values[i]); " +
                 "if expirationDate == false or tonumber(expirationDate) > tonumber(ARGV[1]) then " +
                     "redis.call('zrem', KEYS[2], values[i]); " +
                     "redis.call('zrem', KEYS[1], values[i]); " +
                     "counter = counter + 1; " +
                 "end; " +
             "end; " +
             "return counter;",
            Arrays.asList(getRawName(), timeoutSetName),
            System.currentTimeMillis(), timestamp);
    }

    @Override
    public Collection<V> getAndRemoveAll(long timestamp) {
        return get(getAndRemoveAllAsync(timestamp));
    }

    @Override
    public RFuture<Collection<V>> getAndRemoveAllAsync(long timestamp) {
        return commandExecutor.evalWriteAsync(getRawName(), codec, RedisCommands.EVAL_LIST,
            LIVE_AT_TIMESTAMP +
                     "local n, t, val, label = struct.unpack('BBc0Lc0Lc0', values[i]); " +
                     "table.insert(result, val); " +
                     "redis.call('zrem', KEYS[2], values[i]); " +
                     "redis.call('zrem', KEYS[1], values[i]); " +
                 "end; " +
             "end; " +
             "return result;",
            Arrays.asList(getRawName(), timeoutSetName),
            System.currentTimeMillis(), timestamp);
    }

    @Override
    public Collection<TimeSeriesEntry<V, L>> getAndRemoveAllEntries(long timestamp) {
        return get(getAndRemoveAllEntriesAsync(timestamp));
    }

    @Override
    public RFuture<Collection<TimeSeriesEntry<V, L>>> getAndRemoveAllEntriesAsync(long timestamp) {
        return commandExecutor.evalWriteAsync(getRawName(), codec, EVAL_ENTRIES,
            LIVE_AT_TIMESTAMP +
                     "local n, t, val, label = struct.unpack('BBc0Lc0Lc0', values[i]); " +
                     DECODE_LABEL +
                     "table.insert(result, val); " +
                     "table.insert(result, label); " +
                     "table.insert(result, n); " +
                     "table.insert(result, ARGV[2]); " +
                     "redis.call('zrem', KEYS[2], values[i]); " +
                     "redis.call('zrem', KEYS[1], values[i]); " +
                 "end; " +
             "end; " +
             "return result;",
            Arrays.asList(getRawName(), timeoutSetName),
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
             "for i = 1, #values do " +
                 "local expirationDate = redis.call('zscore', KEYS[2], values[i]); " +
                 "if expirationDate == false or tonumber(expirationDate) > tonumber(ARGV[1]) then " +
                     "redis.call('zrem', KEYS[2], values[i]); " +
                     "redis.call('zrem', KEYS[1], values[i]); " +
                     "return 1;" +
                 "end;" +
             "end;" +
             "return 0;",
            Arrays.asList(getRawName(), timeoutSetName),
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
             "for i = 1, #values do " +
                 "local expirationDate = redis.call('zscore', KEYS[2], values[i]); " +
                 "if expirationDate == false or tonumber(expirationDate) > tonumber(ARGV[1]) then " +
                     "redis.call('zrem', KEYS[2], values[i]); " +
                     "redis.call('zrem', KEYS[1], values[i]); " +
                     "local n, t, val, label = struct.unpack('BBc0Lc0Lc0', values[i]); " +
                     "return val;" +
                 "end;" +
             "end;" +
             "return nil;",
            Arrays.asList(getRawName(), timeoutSetName),
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
             "for i = 1, #values do " +
                 "local expirationDate = redis.call('zscore', KEYS[2], values[i]); " +
                 "if expirationDate == false or tonumber(expirationDate) > tonumber(ARGV[1]) then " +
                     "redis.call('zrem', KEYS[2], values[i]); " +
                     "redis.call('zrem', KEYS[1], values[i]); " +
                     "local n, t, val, label = struct.unpack('BBc0Lc0Lc0', values[i]); " +
                     DECODE_LABEL +
                     "if n == 2 then " +
                        "return {n, ARGV[2], val};" +
                     "end;" +
                     "return {n, ARGV[2], val, label};" +
                 "end;" +
             "end;" +
             "return nil;",
            Arrays.asList(getRawName(), timeoutSetName),
            System.currentTimeMillis(), timestamp);
    }

    @Override
    public V last() {
        return get(lastAsync());
    }

    @Override
    public RFuture<V> lastAsync() {
        return listAsync(true, 1, RedisCommands.EVAL_FIRST_LIST);
    }

    @Override
    public TimeSeriesEntry<V, L> lastEntry() {
        return get(lastEntryAsync());
    }

    @Override
    public RFuture<TimeSeriesEntry<V, L>> lastEntryAsync() {
        return listEntriesAsync(true, 1, EVAL_FIRST_ENTRY);
    }

    @Override
    public RFuture<Collection<V>> lastAsync(int count) {
        return listAsync(true, count, RedisCommands.EVAL_LIST_REVERSE);
    }

    @Override
    public V first() {
        return get(firstAsync());
    }

    @Override
    public RFuture<V> firstAsync() {
        return listAsync(false, 1, RedisCommands.EVAL_FIRST_LIST);
    }

    @Override
    public TimeSeriesEntry<V, L> firstEntry() {
        return get(firstEntryAsync());
    }

    @Override
    public RFuture<TimeSeriesEntry<V, L>> firstEntryAsync() {
        return listEntriesAsync(false, 1, EVAL_FIRST_ENTRY);
    }

    @Override
    public RFuture<Collection<V>> firstAsync(int count) {
        return listAsync(false, count, RedisCommands.EVAL_LIST);
    }

    @Override
    public Collection<V> first(int count) {
        return get(listAsync(false, count, RedisCommands.EVAL_LIST));
    }

    @Override
    public Collection<TimeSeriesEntry<V, L>> firstEntries(int count) {
        return get(firstEntriesAsync(count));
    }

    @Override
    public RFuture<Collection<TimeSeriesEntry<V, L>>> firstEntriesAsync(int count) {
        return listEntriesAsync(false, count, EVAL_ENTRIES);
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
        return listEntriesAsync(true, count, EVAL_ENTRIES_REVERSE);
    }

    @Override
    public Long firstTimestamp() {
        return get(firstTimestampAsync());
    }

    @Override
    public RFuture<Long> firstTimestampAsync() {
        return listTimestampAsync(false, 1, RedisCommands.EVAL_FIRST_LIST);
    }

    @Override
    public Long lastTimestamp() {
        return get(lastTimestampAsync());
    }

    @Override
    public RFuture<Long> lastTimestampAsync() {
        return listTimestampAsync(true, 1, RedisCommands.EVAL_FIRST_LIST);
    }

    /*
     * Three strategies, picked by how many entries are expired but not yet evicted:
     *
     *  - nothing expired (always so for entries added without a time to live): a single
     *    ranged read, no per entry lookup;
     *  - fewer live entries than expired ones: read the live entries straight out of the
     *    timeout set and order them by timestamp, so a large backlog of expired entries
     *    costs nothing;
     *  - otherwise: page through the main set by index, skipping expired entries. Paging
     *    is by index rather than by score, so entries sharing a timestamp are never
     *    skipped when a batch boundary falls between them.
     */
    private static final String COLLECT_HEAD_TAIL =
             "local result = {}; " +
             "local limit = tonumber(ARGV[3]); " +
             "if limit == 0 then " +
                 "return result; " +
             "end; " +
             "local cmd = 'zrange'; " +
             "if ARGV[2] ~= '0' then " +
                 "cmd = 'zrevrange'; " +
             "end; " +
             "local members = {}; " +
             "local scores = {}; " +
             "local total = redis.call('zcard', KEYS[1]); " +
             "local timeoutTotal = redis.call('zcard', KEYS[2]); " +
             "local expiredCount = redis.call('zcount', KEYS[2], '-inf', ARGV[1]); " +
             "local liveCount = timeoutTotal - expiredCount; " +
             "if expiredCount == 0 then " +
                 "local stop = -1; " +
                 "if limit > 0 then " +
                     "stop = limit - 1; " +
                 "end; " +
                 "local values = redis.call(cmd, KEYS[1], 0, stop, 'withscores'); " +
                 "for i = 1, #values, 2 do " +
                     "table.insert(members, values[i]); " +
                     "table.insert(scores, values[i+1]); " +
                 "end; " +
             "elseif total == timeoutTotal and liveCount < expiredCount and liveCount <= 100000 then " +
                 "local live = redis.call('zrangebyscore', KEYS[2], '(' .. ARGV[1], '+inf'); " +
                 "local items = {}; " +
                 "for i = 1, #live do " +
                     "local score = redis.call('zscore', KEYS[1], live[i]); " +
                     "if score ~= false then " +
                         "table.insert(items, {live[i], tonumber(score), score}); " +
                     "end; " +
                 "end; " +
                 "table.sort(items, function(a, b) " +
                     "if a[2] == b[2] then " +
                         "return a[1] < b[1]; " +
                     "end; " +
                     "return a[2] < b[2]; " +
                 "end); " +
                 "local from, to, step = 1, #items, 1; " +
                 "if ARGV[2] ~= '0' then " +
                     "from, to, step = #items, 1, -1; " +
                 "end; " +
                 "for i = from, to, step do " +
                     "table.insert(members, items[i][1]); " +
                     "table.insert(scores, items[i][3]); " +
                     "if #members == limit then " +
                         "break; " +
                     "end; " +
                 "end; " +
             "else " +
                 "local batch = limit; " +
                 "if batch < 10 then " +
                     "batch = 10; " +
                 "end; " +
                 "local offset = 0; " +
                 "while true do " +
                     "local values = redis.call(cmd, KEYS[1], offset, offset + batch - 1, 'withscores'); " +
                     "if #values == 0 then " +
                         "break; " +
                     "end; " +
                     "for i = 1, #values, 2 do " +
                         "local expirationDate = redis.call('zscore', KEYS[2], values[i]); " +
                         "if expirationDate == false or tonumber(expirationDate) > tonumber(ARGV[1]) then " +
                             "table.insert(members, values[i]); " +
                             "table.insert(scores, values[i+1]); " +
                             "if #members == limit then " +
                                 "break; " +
                             "end; " +
                         "end; " +
                     "end; " +
                     "if #members == limit then " +
                         "break; " +
                     "end; " +
                     "offset = offset + #values/2; " +
                 "end; " +
             "end; ";

    private static final String UNPACK_ENTRIES =
             "for i = 1, #members do " +
                 "local n, t, val, label = struct.unpack('BBc0Lc0Lc0', members[i]); " +
                 DECODE_LABEL +
                 "table.insert(result, val); " +
                 "table.insert(result, label); " +
                 "table.insert(result, n); " +
                 "table.insert(result, string.format('%d', tonumber(scores[i]))); " +
             "end; " +
             "return result;";

    private static final String REMOVE_COLLECTED =
             "for i = 1, #members do " +
                 "redis.call('zrem', KEYS[1], members[i]); " +
                 "redis.call('zrem', KEYS[2], members[i]); " +
             "end; ";

    private RFuture<Long> listTimestampAsync(boolean reverse, int limit, RedisCommand<?> evalCommandType) {
        return commandExecutor.evalReadAsync(getRawName(), LongCodec.INSTANCE, evalCommandType,
            COLLECT_HEAD_TAIL +
             "for i = 1, #scores do " +
                 "table.insert(result, string.format('%d', tonumber(scores[i]))); " +
             "end; " +
             "return result;",
            Arrays.asList(getRawName(), timeoutSetName),
            System.currentTimeMillis(), Boolean.compare(reverse, false), limit);
    }

    private <T> RFuture<T> listAsync(boolean reverse, int limit, RedisCommand<?> evalCommandType) {
        return commandExecutor.evalReadAsync(getRawName(), codec, evalCommandType,
            COLLECT_HEAD_TAIL +
             "for i = 1, #members do " +
                 "local n, t, val, label = struct.unpack('BBc0Lc0Lc0', members[i]); " +
                 "table.insert(result, val); " +
             "end; " +
             "return result;",
            Arrays.asList(getRawName(), timeoutSetName),
            System.currentTimeMillis(), Boolean.compare(reverse, false), limit);
    }

    private <T> RFuture<T> listEntriesAsync(boolean reverse, int limit, RedisCommand<?> evalCommandType) {
        return commandExecutor.evalReadAsync(getRawName(), codec, evalCommandType,
            COLLECT_HEAD_TAIL + UNPACK_ENTRIES,
            Arrays.asList(getRawName(), timeoutSetName),
            System.currentTimeMillis(), Boolean.compare(reverse, false), limit);
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
                 "if expirationDate == false or tonumber(expirationDate) > tonumber(ARGV[1]) then " +
                     "counter = counter + 1; " +
                     "redis.call('zrem', KEYS[2], v); " +
                     "redis.call('zrem', KEYS[1], v); " +
                 "end;" +
             "end;" +
             "return counter;",
            Arrays.asList(getRawName(), timeoutSetName),
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
          "local offset = 0; " +

          "local cmd = 'zrangebyscore'; " +
          "if ARGV[5] ~= '0' then " +
              "from = ARGV[3]; " +
              "to = ARGV[2]; " +
              "cmd = 'zrevrangebyscore';" +
          "end; " +

          "while true do " +
             "local values;" +
             "if ARGV[4] ~= '0' then " +
                "values = redis.call(cmd, KEYS[1], from, to, 'withscores', 'limit', offset, limit);" +
             "else " +
                "values = redis.call(cmd, KEYS[1], from, to, 'withscores');" +
             "end; " +

             "for i=1, #values, 2 do " +
                 "local expirationDate = redis.call('zscore', KEYS[2], values[i]);" +
                 "if expirationDate == false or tonumber(expirationDate) > tonumber(ARGV[1]) then " +
                     "local n, t, val, label = struct.unpack('BBc0Lc0Lc0', values[i]); " +
                     DECODE_LABEL +
                     "table.insert(result, val);" +
                     "table.insert(result, label);" +
                     "table.insert(result, n);" +
                     "table.insert(result, string.format('%d', tonumber(values[i+1])));" +
                 "end;" +
             "end;" +

             "if limit == 0 or #result/4 == tonumber(ARGV[4]) or #values/2 < limit then " +
                 "return result;" +
             "end;" +
             "offset = offset + #values/2;" +
             "limit = tonumber(ARGV[4]) - #result/4;" +
          "end;",
            Arrays.asList(getRawName(), timeoutSetName),
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
          "local offset = 0; " +

          "local cmd = 'zrangebyscore'; " +
          "if ARGV[5] ~= '0' then " +
              "from = ARGV[3]; " +
              "to = ARGV[2]; " +
              "cmd = 'zrevrangebyscore';" +
          "end; " +

          "while true do " +
             "local values;" +
             "if ARGV[4] ~= '0' then " +
                "values = redis.call(cmd, KEYS[1], from, to, 'withscores', 'limit', offset, limit);" +
             "else " +
                "values = redis.call(cmd, KEYS[1], from, to, 'withscores');" +
             "end; " +

             "for i=1, #values, 2 do " +
                 "local expirationDate = redis.call('zscore', KEYS[2], values[i]);" +
                 "if expirationDate == false or tonumber(expirationDate) > tonumber(ARGV[1]) then " +
                     "local n, t, val, label = struct.unpack('BBc0Lc0Lc0', values[i]); " +
                     "table.insert(result, val);" +
                 "end;" +
             "end;" +

             "if limit == 0 or #result == tonumber(ARGV[4]) or #values/2 < tonumber(limit) then " +
                 "return result;" +
             "end;" +
             "offset = offset + #values/2;" +
             "limit = tonumber(ARGV[4]) - #result;" +
          "end;",
            Arrays.asList(getRawName(), timeoutSetName),
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
            return new CompletableFutureWrapper<>(Collections.<V>emptyList());
        }

        return pollAsync(false, count, RedisCommands.EVAL_LIST);
    }

    @Override
    public RFuture<Collection<V>> pollLastAsync(int count) {
        if (count <= 0) {
            return new CompletableFutureWrapper<>(Collections.<V>emptyList());
        }
        return pollAsync(true, count, RedisCommands.EVAL_LIST_REVERSE);
    }

    @Override
    public Collection<TimeSeriesEntry<V, L>> pollFirstEntries(int count) {
        return get(pollFirstEntriesAsync(count));
    }

    @Override
    public RFuture<Collection<TimeSeriesEntry<V, L>>> pollFirstEntriesAsync(int count) {
        if (count <= 0) {
            return new CompletableFutureWrapper<>(Collections.<TimeSeriesEntry<V, L>>emptyList());
        }

        return pollEntriesAsync(false, count, EVAL_ENTRIES);
    }

    @Override
    public Collection<TimeSeriesEntry<V, L>> pollLastEntries(int count) {
        return get(pollLastEntriesAsync(count));
    }

    @Override
    public RFuture<Collection<TimeSeriesEntry<V, L>>> pollLastEntriesAsync(int count) {
        if (count <= 0) {
            return new CompletableFutureWrapper<>(Collections.<TimeSeriesEntry<V, L>>emptyList());
        }
        return pollEntriesAsync(true, count, EVAL_ENTRIES_REVERSE);
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
        return pollAsync(false, 1, RedisCommands.EVAL_FIRST_LIST);
    }

    @Override
    public RFuture<V> pollLastAsync() {
        return pollAsync(true, 1, RedisCommands.EVAL_FIRST_LIST);
    }

    @Override
    public TimeSeriesEntry<V, L> pollFirstEntry() {
        return get(pollFirstEntryAsync());
    }

    @Override
    public RFuture<TimeSeriesEntry<V, L>> pollFirstEntryAsync() {
        return pollEntriesAsync(false, 1, EVAL_FIRST_ENTRY);
    }

    @Override
    public TimeSeriesEntry<V, L> pollLastEntry() {
        return get(pollLastEntryAsync());
    }

    @Override
    public RFuture<TimeSeriesEntry<V, L>> pollLastEntryAsync() {
        return pollEntriesAsync(true, 1, EVAL_FIRST_ENTRY);
    }

    /*
     * Only the entries that are actually returned are removed. Expired entries are skipped
     * but left in place for the eviction task, so a poll never turns into an unbounded
     * delete of the expired prefix.
     */
    private <T> RFuture<T> pollAsync(boolean reverse, int limit, RedisCommand<?> command) {
        return commandExecutor.evalWriteAsync(getRawName(), codec, command,
            COLLECT_HEAD_TAIL + REMOVE_COLLECTED +
             "for i = 1, #members do " +
                 "local n, t, val, label = struct.unpack('BBc0Lc0Lc0', members[i]); " +
                 "table.insert(result, val); " +
             "end; " +
             "return result;",
            Arrays.asList(getRawName(), timeoutSetName),
            System.currentTimeMillis(), Boolean.compare(reverse, false), limit);
    }

    private <T> RFuture<T> pollEntriesAsync(boolean reverse, int limit, RedisCommand<?> command) {
        return commandExecutor.evalWriteAsync(getRawName(), codec, command,
            COLLECT_HEAD_TAIL + REMOVE_COLLECTED + UNPACK_ENTRIES,
            Arrays.asList(getRawName(), timeoutSetName),
            System.currentTimeMillis(), Boolean.compare(reverse, false), limit);
    }


    public ListScanResult<Object> scanIterator(String name, RedisClient client, String startPos, int count) {
        RFuture<ListScanResult<Object>> f = scanIteratorAsync(name, client, startPos, count);
        return get(f);
    }

    public RFuture<ListScanResult<Object>> scanIteratorAsync(String name, RedisClient client, String startPos, int count) {
        List<Object> params = new ArrayList<>();
        params.add(startPos);
        params.add(System.currentTimeMillis());
        params.add(count);

        return commandExecutor.evalReadAsync(client, name, codec, RedisCommands.EVAL_SCAN,
                  "local result = {}; "
                + "local res = redis.call('zrange', KEYS[1], ARGV[1], tonumber(ARGV[1]) + tonumber(ARGV[3]) - 1); "
                + "for i, value in ipairs(res) do "
                   + "local expirationDate = redis.call('zscore', KEYS[2], value); " +
                     "if expirationDate == false or tonumber(expirationDate) > tonumber(ARGV[2]) then " +
                         "local n, t, val, label = struct.unpack('BBc0Lc0Lc0', value); " +
                         "table.insert(result, val);" +
                     "end;"
                + "end;" +

                  "local nextPos = tonumber(ARGV[1]) + tonumber(ARGV[3]); " +
                  "if #res < tonumber(ARGV[3]) then " +
                    "nextPos = 0;" +
                  "end;"

                + "return {tostring(nextPos), result};",
                Arrays.asList(name, timeoutSetName),
                params.toArray());
    }

    @Override
    public Iterator<V> iterator(int count) {
        return new RedissonBaseIterator<V>() {

            @Override
            protected ListScanResult<Object> iterator(RedisClient client, String nextIterPos) {
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
        removeListeners();
    }

    @Override
    public RFuture<Boolean> deleteAsync() {
        return deleteAsync(getRawName(), timeoutSetName, sequenceName);
    }

    @Override
    public RFuture<Boolean> expireAsync(long timeToLive, TimeUnit timeUnit, String param, String... keys) {
        return super.expireAsync(timeToLive, timeUnit, param, getRawName(), timeoutSetName, sequenceName);
    }

    @Override
    protected RFuture<Boolean> expireAtAsync(long timestamp, String param, String... keys) {
        return super.expireAtAsync(timestamp, getRawName(), timeoutSetName);
    }

    @Override
    public RFuture<Boolean> clearExpireAsync() {
        return clearExpireAsync(getRawName(), timeoutSetName, sequenceName);
    }

    @Override
    public RFuture<Long> sizeInMemoryAsync() {
        List<Object> keys = Arrays.asList(getRawName(), timeoutSetName, sequenceName);
        return super.sizeInMemoryAsync(keys);
    }

    @Override
    public RFuture<Boolean> copyAsync(List<Object> keys, int database, boolean replace) {
        String newName = (String) keys.get(1);
        List<Object> kks = Arrays.asList(getRawName(), timeoutSetName, sequenceName,
                newName, getTimeoutSetName(newName), getSequenceName(newName));
        return super.copyAsync(kks, database, replace);
    }

    @Override
    public RFuture<Void> renameAsync(String nn) {
        String newName = mapName(nn);
        List<Object> kks = Arrays.asList(getRawName(), timeoutSetName, sequenceName,
                newName, getTimeoutSetName(newName), getSequenceName(newName));
        return renameAsync(commandExecutor, kks, () -> {
            setName(nn);
            this.timeoutSetName = getTimeoutSetName(newName);
            this.sequenceName = getSequenceName(newName);
        });
    }

    @Override
    public RFuture<Boolean> renamenxAsync(String nn) {
        String newName = mapName(nn);
        List<Object> kks = Arrays.asList(getRawName(), timeoutSetName, sequenceName,
                newName, getTimeoutSetName(newName), getSequenceName(newName));
        return renamenxAsync(commandExecutor, kks, value -> {
            if (value) {
                setName(nn);
                this.timeoutSetName = getTimeoutSetName(newName);
            this.sequenceName = getSequenceName(newName);
            }
        });
    }

    @Override
    public int addListener(ObjectListener listener) {
        if (listener instanceof ScoredSortedSetAddListener) {
            return addListener("__keyevent@*:zadd", (ScoredSortedSetAddListener) listener, ScoredSortedSetAddListener::onAdd);
        }
        if (listener instanceof ScoredSortedSetRemoveListener) {
            return addListener("__keyevent@*:zrem", (ScoredSortedSetRemoveListener) listener, ScoredSortedSetRemoveListener::onRemove);
        }
        if (listener instanceof TrackingListener) {
            return addTrackingListener((TrackingListener) listener);
        }

        return super.addListener(listener);
    }

    @Override
    public RFuture<Integer> addListenerAsync(ObjectListener listener) {
        if (listener instanceof ScoredSortedSetAddListener) {
            return addListenerAsync("__keyevent@*:zadd", (ScoredSortedSetAddListener) listener, ScoredSortedSetAddListener::onAdd);
        }
        if (listener instanceof ScoredSortedSetRemoveListener) {
            return addListenerAsync("__keyevent@*:zrem", (ScoredSortedSetRemoveListener) listener, ScoredSortedSetRemoveListener::onRemove);
        }
        if (listener instanceof TrackingListener) {
            return addTrackingListenerAsync((TrackingListener) listener);
        }

        return super.addListenerAsync(listener);
    }

    @Override
    public void removeListener(int listenerId) {
        removeTrackingListener(listenerId);
        removeListener(listenerId, "__keyevent@*:zadd", "__keyevent@*:zrem");
        super.removeListener(listenerId);
    }

    @Override
    public RFuture<Void> removeListenerAsync(int listenerId) {
        return removeListenerAsync(removeTrackingListenerAsync(listenerId), listenerId,
                "__keyevent@*:zadd", "__keyevent@*:zrem");
    }

}
