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
import io.netty.buffer.ByteBuf;
import org.redisson.api.RFuture;
import org.redisson.api.RTimeSeries;
import org.redisson.api.TimeSeriesEntry;
import org.redisson.api.listener.ScoredSortedSetAddListener;
import org.redisson.api.listener.ScoredSortedSetRemoveListener;
import org.redisson.api.listener.TrackingListener;
import org.redisson.api.ts.TimeSeriesAddArgs;
import org.redisson.api.ts.TimeSeriesAddParams;
import org.redisson.api.ts.TimeSeriesAggregation;
import org.redisson.api.ts.TimeSeriesAggregationArgs;
import org.redisson.api.ts.TimeSeriesAggregationParams;
import org.redisson.api.ts.TimeSeriesBucket;
import org.redisson.api.ts.TimeSeriesInfo;
import org.redisson.api.ts.TimeSeriesReadArgs;
import org.redisson.api.ts.TimeSeriesReadParams;
import org.redisson.client.RedisClient;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.codec.StringCodec;
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

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletionStage;
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

    private static final int LABEL_NONE = 0;
    private static final int LABEL_MATCH = 1;
    private static final int LABEL_ABSENT = 2;

    // the first byte of an entry's label field, saying whether a label follows it. They are
    // the values 4.7.0 used as its marker byte, which is why its entries still decode.
    private static final int LABEL_FIELD_EMPTY = 2;
    private static final int LABEL_FIELD_SET = 3;

    /*
     * Each script that filters reads the mode and the label into labelMode and labelValue
     * first; the argument positions differ per script, so they are spelled out there rather
     * than derived. Any other value of labelMode, including none at all, means no filter.
     *
     * Matching is on the encoded bytes, which asks nothing of the label type beyond a codec
     * that encodes equal labels identically - the same assumption looking a member up by
     * value already makes. Expects label to have been decoded by DECODE_LABEL already, where
     * an absent label is the number 0 and a present one is a string, so a label can never
     * compare equal to the absent marker.
     */
    private static final String MATCHES_LABEL =
             "local matches = true; " +
             "if labelMode == '1' then " +
                 "matches = label == labelValue; " +
             "elseif labelMode == '2' then " +
                 "matches = label == 0; " +
             "end; ";

    /*
     * Entry ids are a zero padded counter, shared by the whole collection and held in
     * KEYS[3]. Ids are unique because the counter never repeats, and members sharing a
     * score compare equal up to the id, so duplicates of one timestamp read back in the
     * order they were added.
     *
     * INCRBYFLOAT is used rather than INCR because it answers with a bulk string. An
     * integer reply would be handed to Lua as a double, exact only to 2^53, and past that
     * consecutive values collapse onto one another and ids repeat. Keeping the counter as
     * text means it is never converted at all, and Redis does the arithmetic in long
     * double, which stays exact well beyond a signed 64 bit range.
     *
     * It shares its key with the running total addAndGet keeps, and with the highest
     * timestamp an increment has recorded, so the three live in one hash rather than in
     * three keys.
     *
     * How far beyond is a property of the platform, so rather than predict the limit the
     * counter is checked for having actually moved: once precision runs out an increment
     * leaves the value unchanged, and that is the only outcome that would issue an id
     * twice. A value too long to pad is refused as well, since a wider id would sort
     * before a narrower one and invert the order.
     */
    static final int SEQUENCE_WIDTH = 20;

    static final String FIELD_ID = "id";
    static final String FIELD_TOTAL = "total";
    static final String FIELD_INCREMENTED = "incremented";

    private static final String NEXT_ID =
             "local sequenceWidth = tonumber(ARGV[1]); " +
             "local previous; " +
             "local function nextId() " +
                 "if previous == nil then " +
                     "previous = redis.call('hget', KEYS[3], 'id'); " +
                     "if previous == false then " +
                         "previous = '0'; " +
                     "end; " +
                 "end; " +
                 "local sequence = redis.call('hincrbyfloat', KEYS[3], 'id', 1); " +
                 "if sequence == previous or #sequence > sequenceWidth then " +
                     "error('RTimeSeries sequence overflow'); " +
                 "end; " +
                 "previous = sequence; " +
                 "return string.rep('0', sequenceWidth - #sequence) .. sequence; " +
             "end; ";


    /*
     * One entry per call, so the five values it carries are named once and read by name
     * everywhere below. ARGV[9] is the duplicate policy, and only the comparing script has it.
     */
    private static final String ENTRY_ARGS =
             "local timestamp = ARGV[4]; " +
             "local labelFlag = ARGV[5]; " +
             "local entryValue = ARGV[6]; " +
             "local label = ARGV[7]; " +
             "local expiration = ARGV[8]; ";

    /*
     * Writes the member both sorted sets hold: the main one scored by timestamp, the timeout
     * one by when the entry expires. The label and the expiration are the ones the call
     * carries; the timestamp and the value are parameters because two of the scripts compute
     * one of them. The id is a parameter as well, because addAndGet has to take it before it
     * moves anything else.
     */
    private static final String STORE_ENTRY =
             "local function storeEntry(id, timestamp, value) " +
                 "local field = string.char(labelFlag) .. label; " +
                 "local packed = struct.pack('BBc0Lc0Lc0', 4, " +
                                            "string.len(id), id, " +
                                            "string.len(value), value, " +
                                            "string.len(field), field); " +
                 "redis.call('zadd', KEYS[1], timestamp, packed); " +
                 "redis.call('zadd', KEYS[2], expiration, packed); " +
             "end; ";

    /*
     * The window is anchored on the highest timestamp the collection holds once this call is
     * applied: the timestamp being added, or the highest one already present if that is later.
     * An entry that has expired but has not been evicted yet is not a valid anchor - it would
     * drag the window backwards and, sitting inside its own window, keep doing it - so it is
     * removed here, bounded, exactly as the eviction task would remove it. Giving up leaves
     * the anchor low, which under-trims rather than over-trims.
     *
     * Reads the `timestamp` the script declared before including this.
     */
    private static final String RETENTION_CUTOFF =
             "local cutoff; " +
             "local retention = tonumber(ARGV[2]); " +
             "if retention > 0 then " +
                 "local highest = tonumber(timestamp); " +
                 "local probes = 0; " +
                 "while probes < 100 do " +
                     "local top = redis.call('zrevrange', KEYS[1], 0, 0, 'withscores'); " +
                     "if #top == 0 then " +
                         "break; " +
                     "end; " +
                     "local expirationDate = redis.call('zscore', KEYS[2], top[1]); " +
                     "if expirationDate == false or tonumber(expirationDate) > tonumber(ARGV[3]) then " +
                         "local top1 = tonumber(top[2]); " +
                         "if top1 > highest then " +
                             "highest = top1; " +
                         "end; " +
                         "break; " +
                     "end; " +
                     "redis.call('zrem', KEYS[1], top[1]); " +
                     "redis.call('zrem', KEYS[2], top[1]); " +
                     "probes = probes + 1; " +
                 "end; " +
                 "cutoff = highest - retention; " +
             "end; ";

    /*
     * Every add script does nothing at all when the entry it was given falls outside the
     * retention window, and every one of them trims afterwards.
     */
    private static final String WITHIN_RETENTION =
             "if cutoff == nil or tonumber(timestamp) >= cutoff then ";

    /*
     * Removing members one at a time costs two calls each, which is what a timestamp holding
     * thousands of entries was paying. unpack() is bounded by the Lua stack, so a long list
     * goes in runs rather than in one call.
     */
    private static final String REMOVE_MEMBERS =
             "local function removeMembers(members) " +
                 "local from = 1; " +
                 "while from <= #members do " +
                     "local to = math.min(from + 499, #members); " +
                     "redis.call('zrem', KEYS[1], unpack(members, from, to)); " +
                     "redis.call('zrem', KEYS[2], unpack(members, from, to)); " +
                     "from = to + 1; " +
                 "end; " +
             "end; ";

    /*
     * Only entries that were already present can fall outside the window, because an entry
     * below the cutoff is never written, so what a call reports as added is what it left
     * behind. Bounded, so that applying a retention to a collection that has long outgrown
     * it converges over the following additions instead of turning one addition into an
     * unbounded delete. %.0f rather than %d because a cutoff beyond the range of a signed
     * 64 bit integer would wrap.
     */
    private static final String RETENTION_TRIM =
             "if cutoff ~= nil then " +
                 "removeMembers(redis.call('zrangebyscore', KEYS[1], '-inf', " +
                               "'(' .. string.format('%.0f', cutoff), 'limit', 0, 500)); " +
             "end; ";

    /*
     * Both scripts treat an expired entry at the target timestamp as absent, matching
     * getEntry(), and delete the ones they find so a stale entry cannot mask a live one.
     * An entry whose timestamp falls outside the retention window is not written at all,
     * so a call never reports an entry it went on to delete.
     * ARGV[1] is the id width, ARGV[2] the retention and ARGV[3] is now,
     * followed by 5 values per entry:
     * timestamp, label flag, value, label, expiration time. Ids are assigned by nextId.
     */
    /*
     * The plain add: whatever is at that timestamp already stays, so a collection can hold
     * several entries there, which is what add(long, Object) has always done. All it has to
     * decide is whether the entry falls inside its own retention window.
     */
    private static final String ADD_ENTRY = NEXT_ID + REMOVE_MEMBERS + ENTRY_ARGS +
             STORE_ENTRY + RETENTION_CUTOFF +
             "local added = 0; " +
             WITHIN_RETENTION +
                 "storeEntry(nextId(), timestamp, entryValue); " +
                 "added = 1; " +
             "end; " +
             RETENTION_TRIM +
             "return added;";

    private static final String ADD_IF_ABSENT = NEXT_ID + REMOVE_MEMBERS + ENTRY_ARGS +
             STORE_ENTRY + RETENTION_CUTOFF +
             "local now = tonumber(ARGV[3]); " +
             "local added = 0; " +
             WITHIN_RETENTION +
                 "local occupied = false; " +
                 "local stale; " +
                 "local existing = redis.call('zrangebyscore', KEYS[1], timestamp, timestamp); " +
                 "for j = 1, #existing do " +
                     "local expirationDate = redis.call('zscore', KEYS[2], existing[j]); " +
                     "if expirationDate == false or tonumber(expirationDate) > now then " +
                         "occupied = true; " +
                     "else " +
                         // usually there are none, so the table is not built until there is
                         "if stale == nil then " +
                             "stale = {}; " +
                         "end; " +
                         "table.insert(stale, existing[j]); " +
                     "end; " +
                 "end; " +
                 "if stale ~= nil then " +
                     "removeMembers(stale); " +
                 "end; " +
                 "if occupied == false then " +
                     "storeEntry(nextId(), timestamp, entryValue); " +
                     "added = 1; " +
                 "end; " +
             "end; " +
             RETENTION_TRIM +
             "return added;";

    private static final String ADD_OR_REPLACE = NEXT_ID + REMOVE_MEMBERS + ENTRY_ARGS +
             STORE_ENTRY + RETENTION_CUTOFF +
             "local now = tonumber(ARGV[3]); " +
             "local created = 0; " +
             WITHIN_RETENTION +
                 "local replaced = false; " +
                 "local existing = redis.call('zrangebyscore', KEYS[1], timestamp, timestamp); " +
                 "for j = 1, #existing do " +
                     "local expirationDate = redis.call('zscore', KEYS[2], existing[j]); " +
                     "if expirationDate == false or tonumber(expirationDate) > now then " +
                         "replaced = true; " +
                     "end; " +
                 "end; " +
                 "if #existing > 0 then " +
                     // whether it was live or merely not evicted yet, it is replaced
                     "removeMembers(existing); " +
                 "end; " +
                 "storeEntry(nextId(), timestamp, entryValue); " +
                 "if replaced == false then " +
                     "created = 1; " +
                 "end; " +
             "end; " +
             RETENTION_TRIM +
             "return created;";

    /*
     * %g renders a non-finite number the way C does, which Java cannot read back, so those
     * three are named instead.
     *
     * Seventeen significant digits always read back as the same double, but they are not
     * always needed: %.17g renders 0.1 as 0.10000000000000001, so summing zero into an entry
     * would rewrite the text of its value. The shortest of the three that reads back exactly
     * is used, which is what Double.toString picks as well.
     */
    private static final String FORMAT_NUMBER =
             "local function formatNumber(value) " +
                 "if value ~= value then " +
                     "return 'NaN'; " +
                 "end; " +
                 "if value == math.huge then " +
                     "return 'Infinity'; " +
                 "end; " +
                 "if value == -math.huge then " +
                     "return '-Infinity'; " +
                 "end; " +
                 "local text = string.format('%.15g', value); " +
                 "if tonumber(text) ~= value then " +
                     "text = string.format('%.16g', value); " +
                     "if tonumber(text) ~= value then " +
                         "text = string.format('%.17g', value); " +
                     "end; " +
                 "end; " +
                 "return text; " +
             "end; ";

    /*
     * Reads a stored or incoming value as a number, refusing anything that is not finite -
     * tonumber() also reads 'inf' and 'nan', and either of those would spread through every
     * comparison and sum the entry takes part in from then on.
     */
    private static final String READ_NUMBER =
             "local function readNumber(text, timestamp) " +
                 "local number = tonumber(text); " +
                 "if number == nil or number ~= number " +
                         "or number == math.huge or number == -math.huge then " +
                     "error('RTimeSeries value at timestamp ' .. timestamp " +
                            ".. ' is not a finite number'); " +
                 "end; " +
                 "return number; " +
             "end; ";

    private static final int POLICY_LESS = 1;
    private static final int POLICY_GREATER = 2;
    private static final int POLICY_SUM = 3;

    /*
     * The duplicate policies that have to look at the value: keep the smaller, keep the
     * larger, or add them together. A timestamp already holding several live entries is
     * folded into the one value the policy asks for, and a call that stores leaves exactly
     * one entry there - the shape addOrReplace produces. Entries that have expired are
     * dropped whether the call stores or not, since they take no part either way.
     *
     * ARGV[4] is the policy and the entries start at ARGV[5], five values each.
     */
    private static final String ADD_COMPARING = NEXT_ID + REMOVE_MEMBERS + ENTRY_ARGS +
             STORE_ENTRY + RETENTION_CUTOFF + FORMAT_NUMBER + READ_NUMBER +
             "local now = tonumber(ARGV[3]); " +
             "local policy = tonumber(ARGV[9]); " +
             "local result = 0; " +
             WITHIN_RETENTION +
                 // what the timestamp counts as holding: the smallest of its values, the
                 // largest, or their total, depending on which policy is asking
                 "local folded; " +
                 "local stale; " +
                 "local existing = redis.call('zrangebyscore', KEYS[1], timestamp, timestamp); " +
                 "for j = 1, #existing do " +
                     "local expirationDate = redis.call('zscore', KEYS[2], existing[j]); " +
                     "if expirationDate == false or tonumber(expirationDate) > now then " +
                         "local marker, id, val = struct.unpack('BBc0Lc0Lc0', existing[j]); " +
                         "local number = readNumber(val, timestamp); " +
                         "if folded == nil then " +
                             "folded = number; " +
                         "elseif policy == 3 then " +
                             "folded = folded + number; " +
                         "elseif policy == 1 and number < folded then " +
                             "folded = number; " +
                         "elseif policy == 2 and number > folded then " +
                             "folded = number; " +
                         "end; " +
                     "else " +
                         // nothing is expired in the ordinary case, so the table that holds
                         // them is not built until there is one to put in it
                         "if stale == nil then " +
                             "stale = {}; " +
                         "end; " +
                         "table.insert(stale, existing[j]); " +
                     "end; " +
                 "end; " +

                 "local incoming = readNumber(entryValue, timestamp); " +
                 "local store = true; " +
                 "local stored = entryValue; " +
                 "if folded ~= nil then " +
                     "if policy == 1 then " +
                         "store = incoming < folded; " +
                     "elseif policy == 2 then " +
                         "store = incoming > folded; " +
                     "else " +
                         "local sum = folded + incoming; " +
                         "if sum ~= sum or sum == math.huge or sum == -math.huge then " +
                             "error('RTimeSeries sum at timestamp ' .. timestamp " +
                                    ".. ' is not a finite number'); " +
                         "end; " +
                         "stored = formatNumber(sum); " +
                     "end; " +
                 "end; " +

                 "if store then " +
                     "if #existing > 0 then " +
                         // the whole timestamp goes, whether it expired or was replaced
                         "removeMembers(existing); " +
                     "end; " +
                     "storeEntry(nextId(), timestamp, stored); " +
                     // summing always stores, so what it reports is what it created;
                     // the other two report whether the value they were given won
                     "if policy ~= 3 then " +
                         "result = 1; " +
                     "elseif folded == nil then " +
                         "result = 1; " +
                     "end; " +
                 "elseif stale ~= nil then " +
                     "removeMembers(stale); " +
                 "end; " +
             "end; " +
             RETENTION_TRIM +
             "return result;";

    /*
     * A running total that keeps its history: the total is advanced in the counter hash and
     * the new total is recorded as the entry, so what a caller reads back at a timestamp is
     * the total as of then.
     *
     * The total is deliberately not read out of the last entry, which is what TS.INCRBY does.
     * Entries here expire one at a time and eviction is asynchronous, so a total kept in the
     * last entry is forgotten the moment that entry expires - and since the expired entry is
     * still there until eviction reaches it, the collection would read as a counter that had
     * reset when it had not. Holding it beside the entry counter also makes the timestamp
     * check a field read rather than a walk back through whatever has expired.
     *
     * ARGV[1] is the id width, ARGV[2] the retention and ARGV[3] is now, followed by 5 values
     * per increment: timestamp, label flag, delta, label, expiration time.
     */
    private static final String INCREMENT = NEXT_ID + REMOVE_MEMBERS + ENTRY_ARGS +
             STORE_ENTRY +
             "local state = redis.call('hmget', KEYS[3], 'incremented', 'total'); " +
             // a running total only means anything while it is non decreasing in timestamp
             // order, so an increment that arrives behind one already recorded is recorded at
             // that one's timestamp rather than behind it. Callers racing for a timestamp is
             // the ordinary case, not an error. Entries added by other means are untouched by
             // this and may still arrive in any order.
             "if state[1] ~= false and tonumber(timestamp) < tonumber(state[1]) then " +
                 "timestamp = state[1]; " +
             "end; " +
             RETENTION_CUTOFF + FORMAT_NUMBER + READ_NUMBER +
             WITHIN_RETENTION +
                 "local amount = readNumber(entryValue, timestamp); " +

                 // Redis adds in long double, which reaches far past what a double holds, so
                 // it would carry on well after the total stopped being readable as one. The
                 // check has to happen here, before the total is moved.
                 "local previous = '0'; " +
                 "if state[2] ~= false then " +
                     "previous = state[2]; " +
                 "end; " +
                 "local moved = tonumber(previous) + amount; " +
                 "if moved ~= moved or moved == math.huge or moved == -math.huge then " +
                     "error('RTimeSeries total at timestamp ' .. timestamp " +
                            ".. ' is not a finite number'); " +
                 "end; " +
                 // the id is taken before the total moves, so that running out of ids cannot
                 // leave a total ahead of the history that records it
                 "local id = nextId(); " +
                 "local total = redis.call('hincrbyfloat', KEYS[3], 'total', " +
                                          "formatNumber(amount)); " +
                 // %.17Lf is how Redis writes a long double back, so an amount smaller than
                 // the total can carry is rounded away by the store rather than by the
                 // arithmetic. Counting nothing has to be an error, not a silence.
                 "if amount ~= 0 and total == previous then " +
                     "error('RTimeSeries amount at timestamp ' .. timestamp " +
                            ".. ' is too small to change a total of ' .. previous); " +
                 "end; " +
                 "redis.call('hset', KEYS[3], 'incremented', timestamp); " +

                 "local existing = redis.call('zrangebyscore', KEYS[1], timestamp, timestamp); " +
                 "if #existing > 0 then " +
                     "removeMembers(existing); " +
                 "end; " +
                 "storeEntry(id, timestamp, total); " +
             "end; " +
             RETENTION_TRIM +
             "local reply = redis.call('hget', KEYS[3], 'total'); " +
             "if reply == false then " +
                 "reply = '0'; " +
             "end; " +
             "return reply;";

    /**
     * The arguments every add script reads: the id width, the retention window, the clock, and
     * then the entry itself as timestamp, label flag, value, label and expiration time.
     */
    private List<Object> encodeArgs(TimeSeriesAddArgs<V, ? super L> entry) {
        TimeSeriesAddParams<V, ?> args = (TimeSeriesAddParams<V, ?>) entry;
        long now = System.currentTimeMillis();

        List<Object> params = new ArrayList<>();
        params.add(SEQUENCE_WIDTH);
        params.add(retentionWindow(args));
        params.add(now);
        params.add(args.getTimestamp());
        // the flag says whether a label follows, and the script writes it as the first byte of
        // the label field. Both are worked out before anything is encoded, so a duration that
        // cannot be converted cannot strand a buffer allocated for an earlier field.
        long expiration = expirationTime(args, now);
        if (args.getLabel() == null) {
            params.add(LABEL_FIELD_EMPTY);
            encode(params, args.getObject());
            params.add("");
        } else {
            params.add(LABEL_FIELD_SET);
            encode(params, args.getObject());
            encode(params, args.getLabel());
        }
        params.add(expiration);
        return params;
    }

    /**
     * Milliseconds of retention, or zero for none. A window too wide to convert is taken as
     * one nothing can fall outside of.
     */
    private static long retentionWindow(TimeSeriesAddParams<?, ?> args) {
        Duration retention = args.getRetention();
        if (retention == null || retention.isNegative()) {
            return 0;
        }
        if (retention.getSeconds() >= Long.MAX_VALUE / 1000) {
            return Long.MAX_VALUE;
        }
        return retention.toMillis();
    }

    /**
     * When the entry expires. A time to live that is absent, negative or too wide to convert
     * leaves it a century away, which is what an entry without one gets.
     */
    private static long expirationTime(TimeSeriesAddParams<?, ?> args, long now) {
        Duration timeToLive = args.getTimeToLive();
        if (timeToLive == null || timeToLive.isNegative()
                || timeToLive.getSeconds() >= Long.MAX_VALUE / 1000
                || timeToLive.toMillis() <= 0) {
            return now + TimeUnit.DAYS.toMillis(365 * 100);
        }
        return now + timeToLive.toMillis();
    }

    @Override
    public boolean add(TimeSeriesAddArgs<V, ? super L> entry) {
        return get(addAsync(entry));
    }

    @Override
    public RFuture<Boolean> addAsync(TimeSeriesAddArgs<V, ? super L> entry) {
        return commandExecutor.evalWriteAsync(getRawName(), codec, RedisCommands.EVAL_BOOLEAN,
                ADD_ENTRY,
                Arrays.asList(getRawName(), timeoutSetName, sequenceName),
                encodeArgs(entry).toArray());
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
                encodeArgs(entry).toArray());
    }



    @Override
    public boolean addIfLess(TimeSeriesAddArgs<V, ? super L> entry) {
        return get(addIfLessAsync(entry));
    }

    @Override
    public RFuture<Boolean> addIfLessAsync(TimeSeriesAddArgs<V, ? super L> entry) {
        return addComparingAsync(entry, POLICY_LESS, "addIfLess");
    }



    @Override
    public boolean addIfGreater(TimeSeriesAddArgs<V, ? super L> entry) {
        return get(addIfGreaterAsync(entry));
    }

    @Override
    public RFuture<Boolean> addIfGreaterAsync(TimeSeriesAddArgs<V, ? super L> entry) {
        return addComparingAsync(entry, POLICY_GREATER, "addIfGreater");
    }



    @Override
    public boolean addAndSum(TimeSeriesAddArgs<V, ? super L> entry) {
        return get(addAndSumAsync(entry));
    }

    @Override
    public RFuture<Boolean> addAndSumAsync(TimeSeriesAddArgs<V, ? super L> entry) {
        return addComparingAsync(entry, POLICY_SUM, "addAndSum");
    }



    @Override
    public double addAndGet(TimeSeriesAddArgs<V, ? super L> increment) {
        return get(addAndGetAsync(increment));
    }

    @Override
    public RFuture<Double> addAndGetAsync(TimeSeriesAddArgs<V, ? super L> increment) {
        return incrementAsync(increment, "addAndGet");
    }



    private RFuture<Double> incrementAsync(TimeSeriesAddArgs<V, ? super L> increment, String method) {
        checkNumericCodec(method + "() on '" + getName() + "'");
        // the reply is text this script wrote, so it is read back as text
        RFuture<String> reply = commandExecutor.evalWriteAsync(getRawName(), StringCodec.INSTANCE,
                RedisCommands.EVAL_STRING, INCREMENT,
                Arrays.asList(getRawName(), timeoutSetName, sequenceName),
                encodeArgs(increment).toArray());
        return new CompletableFutureWrapper<>(reply.thenApply(Double::parseDouble));
    }

    private RFuture<Boolean> addComparingAsync(TimeSeriesAddArgs<V, ? super L> entry,
                                               int policy, String method) {
        checkNumericCodec(method + "() on '" + getName() + "'");
        List<Object> params = encodeArgs(entry);
        params.add(policy);
        return commandExecutor.evalWriteAsync(getRawName(), codec, RedisCommands.EVAL_BOOLEAN,
                ADD_COMPARING,
                Arrays.asList(getRawName(), timeoutSetName, sequenceName),
                params.toArray());
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
                encodeArgs(entry).toArray());
    }



    private volatile Boolean numericCodec;

    /*
     * The script reads the stored value with tonumber(), so the codec has to encode a number
     * as text. Probing what it actually does beats listing class names: it admits any codec
     * that qualifies, including one written by the caller, and rejects a binary one such as
     * the default.
     *
     * It only refuses a codec that encodes a number into something that is demonstrably not
     * one. A codec that cannot encode a number at all says nothing about what is stored -
     * ByteArrayCodec is handed its bytes ready made - so those are left to the script, which
     * names the timestamp of the first value it cannot read.
     */
    private void checkNumericCodec(String subject) {
        Boolean numeric = numericCodec;
        if (numeric == null) {
            numeric = probeNumericCodec();
            numericCodec = numeric;
        }
        if (!numeric) {
            throw new IllegalStateException(
                    subject + " requires a codec that encodes numbers as text. "
                  + "The configured codec (" + codec.getClass().getName() + ") encodes them as "
                  + "something else. Use StringCodec, DoubleCodec, LongCodec or IntegerCodec.");
        }
    }

    private boolean probeNumericCodec() {
        for (Number probe : new Number[]{1.5d, -2.25d}) {
            String written;
            ByteBuf encoded = null;
            try {
                encoded = encode(probe);
                written = encoded.toString(StandardCharsets.US_ASCII).trim();
            } catch (Exception e) {
                // it cannot be handed a number at all, which says nothing about what the
                // collection holds, so leave the verdict to the script
                return true;
            } finally {
                if (encoded != null) {
                    encoded.release();
                }
            }
            try {
                if (Double.parseDouble(written) != probe.doubleValue()) {
                    return false;
                }
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return true;
    }

    @Override
    public Collection<TimeSeriesBucket> aggregate(TimeSeriesAggregationArgs<? super L> args) {
        return get(aggregateAsync(args));
    }

    @Override
    public RFuture<Collection<TimeSeriesBucket>> aggregateAsync(TimeSeriesAggregationArgs<? super L> args) {
        checkNumericCodec("Aggregation of '" + getName() + "'");

        TimeSeriesAggregationParams<?> params = (TimeSeriesAggregationParams<?>) args;
        // the script works in milliseconds, so the interval has to survive the conversion:
        // a sub millisecond one is neither zero nor negative but truncates to zero
        Duration bucket = params.getBucket();
        long bucketMillis = 0;
        if (bucket != null && !bucket.isNegative()) {
            if (bucket.getSeconds() >= Long.MAX_VALUE / 1000) {
                bucketMillis = Long.MAX_VALUE;
            } else {
                bucketMillis = bucket.toMillis();
            }
        }
        if (bucketMillis <= 0) {
            throw new IllegalArgumentException(
                    "bucket interval should be defined and at least one millisecond");
        }
        List<TimeSeriesAggregation> aggregations = params.getAggregations();
        if (aggregations.isEmpty()) {
            throw new IllegalArgumentException("at least one aggregation should be defined");
        }

        int labelMode = LABEL_NONE;
        if (params.isLabelFiltered()) {
            labelMode = labelMode(params.getLabel());
        }
        List<Object> codes = new ArrayList<>(aggregations.size());
        for (TimeSeriesAggregation aggregation : aggregations) {
            codes.add(aggregation.ordinal() + 1);
        }

        // the encoded label owns a buffer, so nothing that can throw comes after it
        List<Object> args0 = new ArrayList<>();
        args0.add(System.currentTimeMillis());
        args0.add(params.getStartTimestamp());
        args0.add(params.getEndTimestamp());
        args0.add(0);
        args0.add(0);
        args0.add(labelMode);
        args0.add(bucketMillis);
        args0.add(params.getAlignment());
        args0.add(Boolean.compare(params.isValueFiltered(), false));
        args0.add(params.getMinValue());
        args0.add(params.getMaxValue());
        args0.addAll(codes);
        args0.add(6, encodeLabelArg(params.getLabel(), labelMode));

        // the reply is text this script wrote, so it is read back as text. The label argument
        // is already encoded with the collection's codec and passes through untouched.
        RFuture<List<Object>> future = commandExecutor.evalReadAsync(getRawName(), StringCodec.INSTANCE,
                RedisCommands.EVAL_LIST, AGGREGATE_RANGE,
                Arrays.asList(getRawName(), timeoutSetName),
                args0.toArray());
        CompletionStage<Collection<TimeSeriesBucket>> f = future.thenApply(rows ->
                decodeBuckets(rows, aggregations));
        return new CompletableFutureWrapper<>(f);
    }

    private Collection<TimeSeriesBucket> decodeBuckets(List<Object> rows,
                                                       List<TimeSeriesAggregation> aggregations) {
        int stride = aggregations.size() + 1;
        List<TimeSeriesBucket> buckets = new ArrayList<>(rows.size() / stride);
        for (int i = 0; i + stride <= rows.size(); i += stride) {
            Map<TimeSeriesAggregation, Double> values = new EnumMap<>(TimeSeriesAggregation.class);
            for (int j = 0; j < aggregations.size(); j++) {
                String value = rows.get(i + 1 + j).toString();
                if (!value.isEmpty()) {
                    values.put(aggregations.get(j), Double.parseDouble(value));
                }
            }
            buckets.add(new TimeSeriesBucket(Long.parseLong(rows.get(i).toString()), values));
        }
        return buckets;
    }

    @Override
    public Collection<TimeSeriesEntry<V, L>> readTail(TimeSeriesReadArgs<? super L> args) {
        return get(readTailAsync(args));
    }

    /*
     * The bound is exclusive and a score is a double, so what is wanted is the smallest double
     * that no timestamp at or below the cursor can reach. Above 2^53 a long does not survive
     * the conversion: if it rounds up, the double already stands for something past the cursor
     * and is the bound itself; otherwise the next double up is.
     */
    @Override
    public RFuture<Collection<TimeSeriesEntry<V, L>>> readTailAsync(TimeSeriesReadArgs<? super L> args) {
        TimeSeriesReadParams<?> params = (TimeSeriesReadParams<?>) args;
        if (params.getCount() < 0) {
            return new CompletableFutureWrapper<>(Collections.<TimeSeriesEntry<V, L>>emptyList());
        }

        int labelMode = LABEL_NONE;
        if (params.isLabelFiltered()) {
            labelMode = labelMode(params.getLabel());
        }
        double bound = params.getTimestamp();
        if ((long) bound <= params.getTimestamp()) {
            bound = Math.nextUp(bound);
        }
        return collectAsync(codec, EVAL_ENTRIES, COLLECT_ENTRIES, false,
                bound, WHOLE_RANGE_END,
                params.getCount(), params.getLabel(), labelMode, true);
    }


    @Override
    public TimeSeriesInfo info() {
        return get(infoAsync());
    }

    @Override
    public RFuture<TimeSeriesInfo> infoAsync() {
        RFuture<List<Object>> future = commandExecutor.evalReadAsync(getRawName(), StringCodec.INSTANCE,
                RedisCommands.EVAL_LIST, INFO,
                Arrays.asList(getRawName(), timeoutSetName, sequenceName),
                System.currentTimeMillis());
        CompletionStage<TimeSeriesInfo> f = future.thenApply(rows -> new TimeSeriesInfo(
                Integer.parseInt(rows.get(0).toString()),
                Integer.parseInt(rows.get(1).toString()),
                parseTimestamp(rows.get(2)),
                parseTimestamp(rows.get(3)),
                Long.parseLong(rows.get(4).toString()),
                Long.parseLong(rows.get(5).toString()),
                parseEntriesIssued(rows.get(6))));
        return new CompletableFutureWrapper<>(f);
    }

    private static Long parseTimestamp(Object value) {
        String timestamp = value.toString();
        if (timestamp.isEmpty()) {
            return null;
        }
        return Long.parseLong(timestamp);
    }

    /*
     * The counter is kept as text and can outgrow a long long before it trips the overflow
     * the script guards against, so it saturates rather than failing the call.
     */
    private static long parseEntriesIssued(Object value) {
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return Long.MAX_VALUE;
        }
    }

    @Override
    public int size() {
        return get(sizeAsync());
    }

    @Override
    public RFuture<Integer> sizeAsync() {
        return commandExecutor.evalReadAsync(getRawName(), LongCodec.INSTANCE, RedisCommands.EVAL_INTEGER,
       "local expired = redis.call('zcount', KEYS[2], '-inf', ARGV[1]);" +
             "return redis.call('zcard', KEYS[1]) - expired;",
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
             // the ones that only read never fill it, and removeMembers of an empty list
             // is a comparison and no call at all
             "local doomed = {}; " +
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
       REMOVE_MEMBERS +
             "local doomed = {}; " +
             "local values = redis.call('zrangebyscore', KEYS[1], ARGV[2], ARGV[2]); " +
             "for i = 1, #values do " +
                 "local expirationDate = redis.call('zscore', KEYS[2], values[i]); " +
                 "if expirationDate == false or tonumber(expirationDate) > tonumber(ARGV[1]) then " +
                     "table.insert(doomed, values[i]); " +
                 "end; " +
             "end; " +
             "removeMembers(doomed); " +
             "return #doomed;",
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
            REMOVE_MEMBERS + LIVE_AT_TIMESTAMP +
                     "local n, t, val, label = struct.unpack('BBc0Lc0Lc0', values[i]); " +
                     "table.insert(result, val); " +
                     "table.insert(doomed, values[i]); " +
                 "end; " +
             "end; " +
             "removeMembers(doomed); " +
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
            REMOVE_MEMBERS + LIVE_AT_TIMESTAMP +
                     "local n, t, val, label = struct.unpack('BBc0Lc0Lc0', values[i]); " +
                     DECODE_LABEL +
                     "table.insert(result, val); " +
                     "table.insert(result, label); " +
                     "table.insert(result, n); " +
                     "table.insert(result, ARGV[2]); " +
                     "table.insert(doomed, values[i]); " +
                 "end; " +
             "end; " +
             "removeMembers(doomed); " +
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
     * Shared prologue for head/tail lookups.
     *
     * Traversal order comes from the main set (KEYS[1]), which is scored by timestamp.
     * The timeout set (KEYS[2]) is scored by expiration time and is consulted only to
     * skip already expired entries - it never defines the order.
     *
     * Collects into `members` and `scores`. ARGV[1] is now, ARGV[2] the direction,
     * ARGV[3] the limit. A limit of 0 collects nothing, a negative limit collects everything.
     *
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
    /*
     * A score is a double, so a timestamp at the very top of the long range comes back as
     * 2^63, which %d wraps to a negative and %.0f renders as a number no long can hold. What
     * was stored in that case was Long.MAX_VALUE, so that is what is reported.
     */
    private static final String FORMAT_TIMESTAMP =
             "local function formatTimestamp(score) " +
                 "if score >= 9223372036854775808 then " +
                     "return '9223372036854775807'; " +
                 "end; " +
                 "if score <= -9223372036854775808 then " +
                     "return '-9223372036854775808'; " +
                 "end; " +
                 "return string.format('%.0f', score); " +
             "end; ";

    /*
     * The walk every read shares: the live entries of a timestamp window, oldest or newest
     * first, optionally only those carrying a given label, at most a given number of them.
     * It leaves them in members with their scores in scores, in the order to report them.
     *
     * Paging is by rank. ZRANGEBYSCORE with an offset walks past that offset on every call,
     * so advancing an offset turns one pass over a window into a quadratic one, which a
     * label filter or a backlog of entries the eviction task has not reached yet makes easy
     * to hit. The first member of the window costs one lookup; every page after it is a rank
     * slice. A page is a slice of ranks, so its size is what the walk reads rather than what
     * it returns: bounded above, or a limit far larger than the window would pull the whole
     * collection into one reply, and below, or rows dropped by the filter or by an expiry
     * would shrink it towards one row per call.
     *
     * ARGV: 1 now, 2 and 3 the window, 4 the limit with 0 meaning no limit and a negative
     * meaning no results, 5 the direction, 6 the label mode and 7 the label.
     */
    /*
     * What the walk does with a row it has accepted, and whether the walk should carry on.
     * Keeping the row is the usual answer. An aggregation folds it into its bucket instead and
     * never asks the walk to stop, so a window is never held whole - though its buckets are,
     * and a bucket interval far smaller than the spacing of the entries gives one bucket per
     * entry.
     */
    private static final String KEEP_MEMBERS = FORMAT_TIMESTAMP +
             "local result = {}; " +
             "local members = {}; " +
             "local scores = {}; " +
             "local keepLimit = tonumber(ARGV[4]); " +
             // a tail read advances its cursor to the timestamp of the last entry it was
             // given, so a batch that stopped in the middle of a timestamp would put the rest
             // of that timestamp behind the cursor for good. It takes the whole of it instead,
             // which is why a count is a floor rather than a ceiling for that caller.
             "local wholeTimestamps = ARGV[8] == '1'; " +
             "local function keep(member, score) " +
                 "if keepLimit > 0 and #members >= keepLimit then " +
                     "if not wholeTimestamps then " +
                         "return false; " +
                     "end; " +
                     "if score ~= scores[#members] then " +
                         "return false; " +
                     "end; " +
                 "end; " +
                 "table.insert(members, member); " +
                 "table.insert(scores, score); " +
                 "return true; " +
             "end; ";

    private static final String COLLECT_RANGE =
             "local labelMode = ARGV[6]; " +
             "local labelValue = ARGV[7]; " +
             "local function collect() " +
                 "local limit = tonumber(ARGV[4]); " +
                 "if limit < 0 then " +
                     "return; " +
                 "end; " +
                 "local pageSize = limit; " +
                 "if pageSize <= 0 or pageSize > 500 then " +
                     "pageSize = 500; " +
                 "elseif pageSize < 100 then " +
                     "pageSize = 100; " +
                 "end; " +
                 // with nothing expired no row needs its expiration looked up at all
                 "local anyExpired = redis.call('zcount', KEYS[2], '-inf', ARGV[1]) > 0; " +
                 "local low = tonumber(ARGV[2]); " +
                 "local high = tonumber(ARGV[3]); " +
                 "local from, to = ARGV[2], ARGV[3]; " +
                 "local seek, page, rank = 'zrangebyscore', 'zrange', 'zrank'; " +
                 "if ARGV[5] ~= '0' then " +
                     "from, to = ARGV[3], ARGV[2]; " +
                     "seek, page, rank = 'zrevrangebyscore', 'zrevrange', 'zrevrank'; " +
                 "end; " +
                 "local head = redis.call(seek, KEYS[1], from, to, 'limit', 0, 1); " +
                 "if #head == 0 then " +
                     "return; " +
                 "end; " +
                 "local index = redis.call(rank, KEYS[1], head[1]); " +
                 "while true do " +
                     "local values = redis.call(page, KEYS[1], index, index + pageSize - 1, 'withscores'); " +
                     "if #values == 0 then " +
                         "return; " +
                     "end; " +
                     "for i = 1, #values, 2 do " +
                         "local score = tonumber(values[i+1]); " +
                         "if score < low or score > high then " +
                             "return; " +
                         "end; " +
                         "local live = true; " +
                         "if anyExpired then " +
                             "local expirationDate = redis.call('zscore', KEYS[2], values[i]); " +
                             "live = expirationDate == false " +
                                     "or tonumber(expirationDate) > tonumber(ARGV[1]); " +
                         "end; " +
                         "if live then " +
                             "local label = 0; " +
                             "if labelMode == '1' or labelMode == '2' then " +
                                 "local n, t, val; " +
                                 "n, t, val, label = struct.unpack('BBc0Lc0Lc0', values[i]); " +
                                 DECODE_LABEL +
                             "end; " +
                             MATCHES_LABEL +
                             "if matches then " +
                                 "if not keep(values[i], values[i+1]) then " +
                                     "return; " +
                                 "end; " +
                             "end; " +
                         "end; " +
                     "end; " +
                     "index = index + #values/2; " +
                 "end; " +
             "end; " +
             "collect(); ";

    /*
     * Everything the collection can say about itself in one round trip. Only the two
     * timestamps can cost more than a lookup, and only while entries that have expired are
     * still held: they are the same walk first() and last() make.
     */
    private static final String INFO = FORMAT_TIMESTAMP +
             // the same window size() counts, so the two cannot disagree
             "local expired = redis.call('zcount', KEYS[2], '-inf', ARGV[1]); " +
             "local total = redis.call('zcard', KEYS[1]); " +

             // the first entry that has not expired is within the first expired + 1 of them,
             // so with nothing expired one row is read rather than a page of five hundred.
             // A page carries whole members, so its cost is the size of the values in it.
             "local pageSize = expired + 1; " +
             "if pageSize > 500 then " +
                 "pageSize = 500; " +
             "end; " +

             "local function firstLive(reverse) " +
                 "local page = 'zrange'; " +
                 "if reverse ~= 0 then " +
                     "page = 'zrevrange'; " +
                 "end; " +
                 "local index = 0; " +
                 "while true do " +
                     "local values = redis.call(page, KEYS[1], index, index + pageSize - 1, 'withscores'); " +
                     "if #values == 0 then " +
                         "return ''; " +
                     "end; " +
                     "for i = 1, #values, 2 do " +
                         "local expirationDate = redis.call('zscore', KEYS[2], values[i]); " +
                         "if expirationDate == false " +
                                 "or tonumber(expirationDate) > tonumber(ARGV[1]) then " +
                             "return formatTimestamp(tonumber(values[i+1])); " +
                         "end; " +
                     "end; " +
                     "index = index + #values/2; " +
                 "end; " +
             "end; " +

             // with nothing live to find, the walk from the far end would only repeat what
             // the walk from the near end has already established
             "local first = ''; " +
             "local last = ''; " +
             "if total > 0 then " +
                 "first = firstLive(0); " +
                 "if first ~= '' then " +
                     "last = firstLive(1); " +
                 "end; " +
             "end; " +

             "local memory = 0; " +
             "for j = 1, #KEYS do " +
                 "local used = redis.call('memory', 'usage', KEYS[j]); " +
                 "if used ~= false then " +
                     "memory = memory + used; " +
                 "end; " +
             "end; " +

             "local issued = redis.call('hget', KEYS[3], 'id'); " +
             "if issued == false then " +
                 "issued = '0'; " +
             "end; " +

             // tostring() renders with %.14g, so anything past fourteen digits - a time to
             // live of a few thousand years, say - would come back in exponent form
             "return {string.format('%.0f', total - expired), " +
                     "string.format('%.0f', total), " +
                     "first, " +
                     "last, " +
                     "string.format('%.0f', memory), " +
                     "string.format('%.0f', redis.call('pttl', KEYS[1])), " +
                     "issued};";

    private static final String UNPACK_VALUES =
             "for i = 1, #members do " +
                 "local n, t, val = struct.unpack('BBc0Lc0Lc0', members[i]); " +
                 "table.insert(result, val); " +
             "end; " +
             "return result;";

    private static final String UNPACK_ENTRIES =
             "for i = 1, #members do " +
                 "local n, t, val, label = struct.unpack('BBc0Lc0Lc0', members[i]); " +
                 DECODE_LABEL +
                 "table.insert(result, val); " +
                 "table.insert(result, label); " +
                 "table.insert(result, n); " +
                 "table.insert(result, formatTimestamp(tonumber(scores[i]))); " +
             "end; " +
             "return result;";

    private static final String UNPACK_TIMESTAMPS =
             "for i = 1, #scores do " +
                 "table.insert(result, formatTimestamp(tonumber(scores[i]))); " +
             "end; " +
             "return result;";

    private static final String REMOVE_COLLECTED = "removeMembers(members); ";

    private static final String COLLECT_VALUES = KEEP_MEMBERS + COLLECT_RANGE + UNPACK_VALUES;
    private static final String COLLECT_ENTRIES = KEEP_MEMBERS + COLLECT_RANGE + UNPACK_ENTRIES;
    private static final String COLLECT_TIMESTAMPS = KEEP_MEMBERS + COLLECT_RANGE + UNPACK_TIMESTAMPS;
    private static final String POLL_VALUES =
            REMOVE_MEMBERS + KEEP_MEMBERS + COLLECT_RANGE + REMOVE_COLLECTED + UNPACK_VALUES;
    private static final String POLL_ENTRIES =
            REMOVE_MEMBERS + KEEP_MEMBERS + COLLECT_RANGE + REMOVE_COLLECTED + UNPACK_ENTRIES;

    /*
     * Folds the rows the walk accepts into buckets, in one pass, computing every aggregation
     * that was asked for. The accumulators are the same eight numbers whichever aggregations
     * those are, so asking for more of them costs nothing beyond the reply.
     *
     * Mean and sum of squared deviations are kept by Welford's method rather than as a sum of
     * squares, which loses most of its significant digits once the values are large and close
     * together.
     *
     * ARGV, after the seven the walk reads: 8 the bucket width, 9 the alignment, 10 whether a
     * value filter is applied with 11 and 12 its bounds, and 13 onwards the aggregation codes
     * in the order to report them.
     */

    private static final String AGGREGATE = FORMAT_TIMESTAMP + FORMAT_NUMBER +
             "local result = {}; " +
             "local bucketSize = tonumber(ARGV[8]); " +
             "local alignment = tonumber(ARGV[9]); " +
             "local valueFilter = ARGV[10] == '1'; " +
             "local minValue = tonumber(ARGV[11]); " +
             "local maxValue = tonumber(ARGV[12]); " +
             "local index, count, sum, minv, maxv, firstv, lastv, mean, m2; " +

             "local function close() " +
                 "if index == nil then " +
                     "return; " +
                 "end; " +
                 "table.insert(result, formatTimestamp(alignment + index * bucketSize)); " +
                 "for k = 13, #ARGV do " +
                     "local code = tonumber(ARGV[k]); " +
                     "local value; " +
                     "if code == 1 then " +
                         "value = count; " +
                     "elseif code == 2 then " +
                         "value = sum; " +
                     "elseif code == 3 then " +
                         "value = sum / count; " +
                         // the running mean is worth less than sum/count when both work, but
                         // it is still finite when the sum has overflowed
                         "if value ~= value or value == math.huge or value == -math.huge then " +
                             "value = mean; " +
                         "end; " +
                     "elseif code == 4 then " +
                         "value = minv; " +
                     "elseif code == 5 then " +
                         "value = maxv; " +
                     "elseif code == 6 then " +
                         "value = maxv - minv; " +
                     "elseif code == 7 then " +
                         "value = firstv; " +
                     "elseif code == 8 then " +
                         "value = lastv; " +
                     "elseif code == 9 then " +
                         "value = math.sqrt(m2 / count); " +
                     "elseif code == 11 then " +
                         "value = m2 / count; " +
                     "elseif code == 10 and count > 1 then " +
                         "value = math.sqrt(m2 / (count - 1)); " +
                     "elseif code == 12 and count > 1 then " +
                         "value = m2 / (count - 1); " +
                     "end; " +
                     // an aggregation a bucket cannot define is reported as absent
                     "if value == nil then " +
                         "table.insert(result, ''); " +
                     "else " +
                         "table.insert(result, formatNumber(value)); " +
                     "end; " +
                 "end; " +
             "end; " +

             "local function keep(member, score) " +
                 "local n, t, val = struct.unpack('BBc0Lc0Lc0', member); " +
                 "local value = tonumber(val); " +
                 // tonumber also reads 'inf' and 'nan', which would quietly poison every
                 // aggregation of the bucket the entry lands in
                 "if value == nil or value ~= value " +
                         "or value == math.huge or value == -math.huge then " +
                     "error('RTimeSeries value at timestamp ' " +
                            ".. formatTimestamp(tonumber(score)) .. ' is not a finite number'); " +
                 "end; " +
                 "if valueFilter and (value < minValue or value > maxValue) then " +
                     "return true; " +
                 "end; " +
                 "local bucket = math.floor((tonumber(score) - alignment) / bucketSize); " +
                 "if bucket ~= index then " +
                     "close(); " +
                     "index = bucket; " +
                     "count, sum, minv, maxv, firstv, mean, m2 = 0, 0, value, value, value, 0, 0; " +
                 "end; " +
                 "count = count + 1; " +
                 "sum = sum + value; " +
                 "if value < minv then " +
                     "minv = value; " +
                 "end; " +
                 "if value > maxv then " +
                     "maxv = value; " +
                 "end; " +
                 "lastv = value; " +
                 "local delta = value - mean; " +
                 "mean = mean + delta / count; " +
                 "m2 = m2 + delta * (value - mean); " +
                 "return true; " +
             "end; ";

    private static final String CLOSE_LAST_BUCKET =
             "close(); " +
             "return result;";

    private static final String AGGREGATE_RANGE = AGGREGATE + COLLECT_RANGE + CLOSE_LAST_BUCKET;

    private static final long WHOLE_RANGE_START = Long.MIN_VALUE;
    private static final long WHOLE_RANGE_END = Long.MAX_VALUE;

    /*
     * The walk reads 0 as no limit and a negative as no results, which is what the range
     * family has always meant. first(count) and its siblings have always meant the opposite
     * by both, so they are translated here rather than quietly changed.
     */
    private static int headTailLimit(int count) {
        if (count == 0) {
            return -1;
        }
        if (count < 0) {
            return 0;
        }
        return count;
    }

    private <T> RFuture<T> collectAsync(Codec c, RedisCommand<?> command, String script, boolean reverse,
                                        Number startTimestamp, Number endTimestamp, int limit,
                                        Object label, int labelMode) {
        return collectAsync(c, command, script, reverse, startTimestamp, endTimestamp, limit,
                label, labelMode, false);
    }

    private <T> RFuture<T> collectAsync(Codec c, RedisCommand<?> command, String script, boolean reverse,
                                        Number startTimestamp, Number endTimestamp, int limit,
                                        Object label, int labelMode, boolean wholeTimestamps) {
        return commandExecutor.evalReadAsync(getRawName(), c, command, script,
                Arrays.asList(getRawName(), timeoutSetName),
                System.currentTimeMillis(), startTimestamp, endTimestamp, limit,
                Boolean.compare(reverse, false), labelMode, encodeLabelArg(label, labelMode),
                Boolean.compare(wholeTimestamps, false));
    }

    /*
     * Only the entries that are actually returned are removed. Expired entries are skipped
     * but left in place for the eviction task, so a poll never turns into an unbounded
     * delete of the expired prefix.
     */
    private <T> RFuture<T> collectAndRemoveAsync(RedisCommand<?> command, String script, boolean reverse, int count) {
        return commandExecutor.evalWriteAsync(getRawName(), codec, command, script,
                Arrays.asList(getRawName(), timeoutSetName),
                System.currentTimeMillis(), WHOLE_RANGE_START, WHOLE_RANGE_END, headTailLimit(count),
                Boolean.compare(reverse, false), LABEL_NONE, encodeLabelArg(null, LABEL_NONE),
                Boolean.compare(false, false));
    }

    private RFuture<Long> listTimestampAsync(boolean reverse, int count, RedisCommand<?> evalCommandType) {
        return collectAsync(LongCodec.INSTANCE, evalCommandType, COLLECT_TIMESTAMPS, reverse,
                WHOLE_RANGE_START, WHOLE_RANGE_END, headTailLimit(count), null, LABEL_NONE);
    }

    private <T> RFuture<T> listAsync(boolean reverse, int count, RedisCommand<?> evalCommandType) {
        return collectAsync(codec, evalCommandType, COLLECT_VALUES, reverse,
                WHOLE_RANGE_START, WHOLE_RANGE_END, headTailLimit(count), null, LABEL_NONE);
    }

    private <T> RFuture<T> listEntriesAsync(boolean reverse, int count, RedisCommand<?> evalCommandType) {
        return collectAsync(codec, evalCommandType, COLLECT_ENTRIES, reverse,
                WHOLE_RANGE_START, WHOLE_RANGE_END, headTailLimit(count), null, LABEL_NONE);
    }
    @Override
    public int removeRange(long startTimestamp, long endTimestamp) {
        return get(removeRangeAsync(startTimestamp, endTimestamp));
    }

    @Override
    public RFuture<Integer> removeRangeAsync(long startTimestamp, long endTimestamp) {
        return removeRangeAsync(startTimestamp, endTimestamp, null, LABEL_NONE);
    }

    // ARGV[4] and ARGV[5] carry the label filter; anything appended must go after them
    private RFuture<Integer> removeRangeAsync(long startTimestamp, long endTimestamp, Object label, int labelMode) {
        // the label arrives already encoded with the collection's codec, so the script itself
        // can keep reading its timestamps as plain numbers
        return commandExecutor.evalWriteAsync(getRawName(), LongCodec.INSTANCE, RedisCommands.EVAL_INTEGER,
             "local labelMode = ARGV[4]; " +
             "local labelValue = ARGV[5]; " +
       REMOVE_MEMBERS +
             "local values = redis.call('zrangebyscore', KEYS[1], ARGV[2], ARGV[3]);" +
             "local doomed = {}; " +
             "for i, v in ipairs(values) do " +
                 "local expirationDate = redis.call('zscore', KEYS[2], v); " +
                 "if expirationDate == false or tonumber(expirationDate) > tonumber(ARGV[1]) then " +
                     "local label = 0; " +
                     "if labelMode == '1' or labelMode == '2' then " +
                         "local n, t, val; " +
                         "n, t, val, label = struct.unpack('BBc0Lc0Lc0', v); " +
                         DECODE_LABEL +
                     "end; " +
                     MATCHES_LABEL +
                     "if matches then " +
                         "table.insert(doomed, v); " +
                     "end; " +
                 "end;" +
             "end;" +
             "removeMembers(doomed); " +
             "return #doomed;",
            Arrays.asList(getRawName(), timeoutSetName),
            System.currentTimeMillis(), startTimestamp, endTimestamp,
            labelMode, encodeLabelArg(label, labelMode));
    }

    private int labelMode(Object label) {
        if (label == null) {
            return LABEL_ABSENT;
        }
        return LABEL_MATCH;
    }

    private Object encodeLabelArg(Object label, int labelMode) {
        if (labelMode != LABEL_MATCH) {
            return 0;
        }
        return encode(label);
    }

    @Override
    public Collection<V> rangeByLabel(long startTimestamp, long endTimestamp, L label) {
        return get(rangeByLabelAsync(startTimestamp, endTimestamp, label));
    }

    @Override
    public RFuture<Collection<V>> rangeByLabelAsync(long startTimestamp, long endTimestamp, L label) {
        return rangeByLabelAsync(startTimestamp, endTimestamp, label, 0);
    }

    @Override
    public Collection<V> rangeByLabel(long startTimestamp, long endTimestamp, L label, int limit) {
        return get(rangeByLabelAsync(startTimestamp, endTimestamp, label, limit));
    }

    @Override
    public RFuture<Collection<V>> rangeByLabelAsync(long startTimestamp, long endTimestamp, L label, int limit) {
        return rangeAsync(false, startTimestamp, endTimestamp, limit, label, labelMode(label));
    }

    @Override
    public Collection<V> rangeReversedByLabel(long startTimestamp, long endTimestamp, L label) {
        return get(rangeReversedByLabelAsync(startTimestamp, endTimestamp, label));
    }

    @Override
    public RFuture<Collection<V>> rangeReversedByLabelAsync(long startTimestamp, long endTimestamp, L label) {
        return rangeReversedByLabelAsync(startTimestamp, endTimestamp, label, 0);
    }

    @Override
    public Collection<V> rangeReversedByLabel(long startTimestamp, long endTimestamp, L label, int limit) {
        return get(rangeReversedByLabelAsync(startTimestamp, endTimestamp, label, limit));
    }

    @Override
    public RFuture<Collection<V>> rangeReversedByLabelAsync(long startTimestamp, long endTimestamp, L label, int limit) {
        return rangeAsync(true, startTimestamp, endTimestamp, limit, label, labelMode(label));
    }

    @Override
    public Collection<TimeSeriesEntry<V, L>> entryRangeByLabel(long startTimestamp, long endTimestamp, L label) {
        return get(entryRangeByLabelAsync(startTimestamp, endTimestamp, label));
    }

    @Override
    public RFuture<Collection<TimeSeriesEntry<V, L>>> entryRangeByLabelAsync(long startTimestamp, long endTimestamp, L label) {
        return entryRangeByLabelAsync(startTimestamp, endTimestamp, label, 0);
    }

    @Override
    public Collection<TimeSeriesEntry<V, L>> entryRangeByLabel(long startTimestamp, long endTimestamp, L label, int limit) {
        return get(entryRangeByLabelAsync(startTimestamp, endTimestamp, label, limit));
    }

    @Override
    public RFuture<Collection<TimeSeriesEntry<V, L>>> entryRangeByLabelAsync(long startTimestamp, long endTimestamp, L label, int limit) {
        return entryRangeAsync(false, startTimestamp, endTimestamp, limit, label, labelMode(label));
    }

    @Override
    public Collection<TimeSeriesEntry<V, L>> entryRangeReversedByLabel(long startTimestamp, long endTimestamp, L label) {
        return get(entryRangeReversedByLabelAsync(startTimestamp, endTimestamp, label));
    }

    @Override
    public RFuture<Collection<TimeSeriesEntry<V, L>>> entryRangeReversedByLabelAsync(long startTimestamp, long endTimestamp, L label) {
        return entryRangeReversedByLabelAsync(startTimestamp, endTimestamp, label, 0);
    }

    @Override
    public Collection<TimeSeriesEntry<V, L>> entryRangeReversedByLabel(long startTimestamp, long endTimestamp, L label, int limit) {
        return get(entryRangeReversedByLabelAsync(startTimestamp, endTimestamp, label, limit));
    }

    @Override
    public RFuture<Collection<TimeSeriesEntry<V, L>>> entryRangeReversedByLabelAsync(long startTimestamp, long endTimestamp, L label, int limit) {
        return entryRangeAsync(true, startTimestamp, endTimestamp, limit, label, labelMode(label));
    }

    @Override
    public int removeRangeByLabel(long startTimestamp, long endTimestamp, L label) {
        return get(removeRangeByLabelAsync(startTimestamp, endTimestamp, label));
    }

    @Override
    public RFuture<Integer> removeRangeByLabelAsync(long startTimestamp, long endTimestamp, L label) {
        return removeRangeAsync(startTimestamp, endTimestamp, label, labelMode(label));
    }

    /*
     * There is no label index, so this walks the collection. It is read in pages rather than
     * in one ZRANGEBYSCORE so that the reply a single Lua call has to hold stays bounded no
     * matter how large the collection is; only the distinct labels accumulate.
     */
    private static final String COLLECT_LABELS =
             "local result = {}; " +
             "local seen = {}; " +
             "local low = tonumber(ARGV[2]); " +
             "local high = tonumber(ARGV[3]); " +
             "local head = redis.call('zrangebyscore', KEYS[1], ARGV[2], ARGV[3], 'limit', 0, 1); " +
             "if #head == 0 then " +
                 "return result; " +
             "end; " +
             "local index = redis.call('zrank', KEYS[1], head[1]); " +
             "while true do " +
                 "local values = redis.call('zrange', KEYS[1], index, index + 499, 'withscores'); " +
                 "if #values == 0 then " +
                     "return result; " +
                 "end; " +
                 "for i = 1, #values, 2 do " +
                     "local score = tonumber(values[i+1]); " +
                     "if score < low or score > high then " +
                         "return result; " +
                     "end; " +
                     "local expirationDate = redis.call('zscore', KEYS[2], values[i]); " +
                     "if expirationDate == false or tonumber(expirationDate) > tonumber(ARGV[1]) then " +
                         "local n, t, val, label = struct.unpack('BBc0Lc0Lc0', values[i]); " +
                         DECODE_LABEL +
                         "if label ~= 0 and seen[label] == nil then " +
                             "seen[label] = true; " +
                             "table.insert(result, label); " +
                         "end; " +
                     "end; " +
                 "end; " +
                 "index = index + #values/2; " +
             "end;";

    @Override
    public Set<L> labels() {
        return get(labelsAsync());
    }

    @Override
    public RFuture<Set<L>> labelsAsync() {
        return labelsAsync(Long.MIN_VALUE, Long.MAX_VALUE);
    }

    @Override
    public Set<L> labels(long startTimestamp, long endTimestamp) {
        return get(labelsAsync(startTimestamp, endTimestamp));
    }

    @SuppressWarnings("unchecked")
    @Override
    public RFuture<Set<L>> labelsAsync(long startTimestamp, long endTimestamp) {
        RFuture<List<Object>> future = commandExecutor.evalReadAsync(getRawName(), codec, RedisCommands.EVAL_LIST,
                COLLECT_LABELS,
                Arrays.asList(getRawName(), timeoutSetName),
                System.currentTimeMillis(), startTimestamp, endTimestamp);
        CompletionStage<Set<L>> f = future.thenApply(labels -> {
            Set<L> result = new LinkedHashSet<>((int) (labels.size() / 0.75f) + 1);
            for (Object label : labels) {
                result.add((L) label);
            }
            return result;
        });
        return new CompletableFutureWrapper<>(f);
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
        return get(entryRangeAsync(false, startTimestamp, endTimestamp, 0, null, LABEL_NONE));
    }

    @Override
    public Collection<TimeSeriesEntry<V, L>> entryRangeReversed(long startTimestamp, long endTimestamp) {
        return get(entryRangeAsync(true, startTimestamp, endTimestamp, 0, null, LABEL_NONE));
    }

    @Override
    public RFuture<Collection<TimeSeriesEntry<V, L>>> entryRangeReversedAsync(long startTimestamp, long endTimestamp) {
        return entryRangeAsync(true, startTimestamp, endTimestamp, 0, null, LABEL_NONE);
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
        return entryRangeAsync(false, startTimestamp, endTimestamp, 0, null, LABEL_NONE);
    }

    private RFuture<Collection<TimeSeriesEntry<V, L>>> entryRangeAsync(boolean reverse, long startTimestamp, long endTimestamp,
                                                                      int limit, Object label, int labelMode) {
        if (limit < 0) {
            return new CompletableFutureWrapper<>(Collections.<TimeSeriesEntry<V, L>>emptyList());
        }
        return collectAsync(codec, EVAL_ENTRIES, COLLECT_ENTRIES, reverse,
                startTimestamp, endTimestamp, limit, label, labelMode);
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
        return rangeAsync(false, startTimestamp, endTimestamp, limit, null, LABEL_NONE);
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
        return rangeAsync(true, startTimestamp, endTimestamp, limit, null, LABEL_NONE);
    }

    private RFuture<Collection<V>> rangeAsync(boolean reverse, long startTimestamp, long endTimestamp, int limit,
                                             Object label, int labelMode) {
        if (limit < 0) {
            return new CompletableFutureWrapper<>(Collections.<V>emptyList());
        }
        return collectAsync(codec, RedisCommands.EVAL_LIST, COLLECT_VALUES, reverse,
                startTimestamp, endTimestamp, limit, label, labelMode);
    }

    @Override
    public Collection<TimeSeriesEntry<V, L>> entryRange(long startTimestamp, long endTimestamp, int limit) {
        return get(entryRangeAsync(startTimestamp, endTimestamp, limit));
    }

    @Override
    public RFuture<Collection<TimeSeriesEntry<V, L>>> entryRangeAsync(long startTimestamp, long endTimestamp, int limit) {
        return entryRangeAsync(false, startTimestamp, endTimestamp, limit, null, LABEL_NONE);
    }

    @Override
    public Collection<TimeSeriesEntry<V, L>> entryRangeReversed(long startTimestamp, long endTimestamp, int limit) {
        return get(entryRangeReversedAsync(startTimestamp, endTimestamp, limit));
    }

    @Override
    public RFuture<Collection<TimeSeriesEntry<V, L>>> entryRangeReversedAsync(long startTimestamp, long endTimestamp, int limit) {
        return entryRangeAsync(true, startTimestamp, endTimestamp, limit, null, LABEL_NONE);
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

    private <T> RFuture<T> pollAsync(boolean reverse, int count, RedisCommand<?> command) {
        return collectAndRemoveAsync(command, POLL_VALUES, reverse, count);
    }

    private <T> RFuture<T> pollEntriesAsync(boolean reverse, int count, RedisCommand<?> command) {
        return collectAndRemoveAsync(command, POLL_ENTRIES, reverse, count);
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
        return super.expireAtAsync(timestamp, param, getRawName(), timeoutSetName, sequenceName);
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

    /*
     * A collection that has been emptied keeps its counter key until the eviction task
     * reclaims it, so a destination can hold a counter and no data. Copying key by key then
     * leaves that counter in place while the data lands, and the ids that came with the data
     * are handed out a second time: a reissued id carrying the same value and label is a byte
     * identical member, so the next write moves an existing entry instead of adding one.
     * Copying is all or nothing here for that reason, and reports honestly when it is not.
     */
    @Override
    public RFuture<Boolean> copyAsync(List<Object> keys, int database, boolean replace) {
        String newName = (String) keys.get(1);
        List<Object> kks = Arrays.asList(getRawName(), timeoutSetName, sequenceName,
                newName, getTimeoutSetName(newName), getSequenceName(newName));

        if (getServiceManager().getCfg().isClusterConfig()
                && commandExecutor.getConnectionManager().calcSlot(newName)
                    != commandExecutor.getConnectionManager().calcSlot(getRawName())) {
            return super.copyAsync(kks, database, replace);
        }

        return commandExecutor.evalWriteAsync(getRawName(), StringCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
               "local half = #KEYS/2; " +
               "local landed = {}; " +
               "for j = 1, half do " +
                   "if redis.call('exists', KEYS[j]) == 1 then " +
                       "local copied; " +
                       "if tonumber(ARGV[1]) >= 0 then " +
                           "if ARGV[2] == '1' then " +
                               "copied = redis.call('copy', KEYS[j], KEYS[half + j], 'db', ARGV[1], 'replace'); " +
                           "else " +
                               "copied = redis.call('copy', KEYS[j], KEYS[half + j], 'db', ARGV[1]); " +
                           "end; " +
                       "else " +
                           "if ARGV[2] == '1' then " +
                               "copied = redis.call('copy', KEYS[j], KEYS[half + j], 'replace'); " +
                           "else " +
                               "copied = redis.call('copy', KEYS[j], KEYS[half + j]); " +
                           "end; " +
                       "end; " +
                       "if copied == 0 then " +
                           // nothing was replaced, so every key already written is one this
                           // call created and can be taken back
                           "if tonumber(ARGV[1]) < 0 then " +
                               "for k = 1, #landed do " +
                                   "redis.call('del', landed[k]); " +
                               "end; " +
                           "end; " +
                           "return 0; " +
                       "end; " +
                       "table.insert(landed, KEYS[half + j]); " +
                   "end; " +
               "end; " +
               "if #landed == 0 then " +
                   "return 0; " +
               "end; " +
               "return 1;",
            kks,
            database, Boolean.compare(replace, false));
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
