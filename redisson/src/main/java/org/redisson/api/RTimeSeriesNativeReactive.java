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
package org.redisson.api;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import org.redisson.api.tsnative.TSAddArgs;
import org.redisson.api.tsnative.TSAlterArgs;
import org.redisson.api.tsnative.TSCreateArgs;
import org.redisson.api.tsnative.TSIncrArgs;
import org.redisson.api.tsnative.TSInfo;
import org.redisson.api.tsnative.TSRangeArgs;
import org.redisson.api.tsnative.TSReadArgs;
import org.redisson.api.tsnative.TSRuleArgs;
import org.redisson.api.tsnative.TSSample;

import java.util.List;
import java.util.Map;

/**
 * Time series based on TS.* commands of the RedisTimeSeries module.
 * <p>
 * Unlike {@link RTimeSeriesReactive}, which stores arbitrary objects in a sorted set, this object is a
 * thin binding over the module: a sample is a millisecond timestamp and a <code>double</code>,
 * and the labels indexing a series are strings. So there is no codec and no value type
 * parameter — the wire format is the module's, not Redisson's.
 * <p>
 * Commands spanning several series — TS.MADD, TS.MGET, TS.MRANGE, TS.MREVRANGE, TS.NRANGE,
 * TS.NREVRANGE, TS.QUERYINDEX and TS.QUERYLABELS — take no single key and live on
 * {@link RTimeSeriesNativesReactive} instead.
 * <p>
 * Requires <b>Redis 8.0.0 or higher, or the RedisTimeSeries module.</b>
 *
 * @author Nikita Koksharov
 */
public interface RTimeSeriesNativeReactive extends RExpirableReactive {

    /**
     * Creates this series, taking the server's defaults for retention, encoding, chunk size and
     * duplicate policy, and giving it no labels.
     * <p>
     * TS.CREATE
     */
    Mono<Void> create();

    /**
     * Creates this series.
     * <p>
     * TS.CREATE
     *
     * @param args retention, encoding, chunk size, duplicate policy, ignore thresholds and labels
     */
    Mono<Void> create(TSCreateArgs args);

    /**
     * Creates this series unless a key of that name already exists, taking the server's defaults.
     *
     * @return <code>true</code> if the series was created,
     *         <code>false</code> if a key of that name already existed, whatever it holds
     */
    Mono<Boolean> createIfAbsent();

    /**
     * Creates this series unless a key of that name already exists.
     * <p>
     * TS.CREATE reports an existing key as an error; this reports it as <code>false</code>, so
     * the common "make sure it is there" call needs no exception handling.
     *
     * @param args creation arguments
     * @return <code>true</code> if the series was created,
     *         <code>false</code> if the key already existed
     */
    Mono<Boolean> createIfAbsent(TSCreateArgs args);

    /**
     * Updates retention, chunk size, duplicate policy, ignore thresholds and labels of this
     * series.
     * <p>
     * A field left unset in <code>args</code> keeps its current value. Labels are the exception:
     * TS.ALTER replaces the whole set, so passing an empty one clears them while passing none
     * leaves them alone.
     * <p>
     * TS.ALTER
     *
     * @param args fields to change
     */
    Mono<Void> alter(TSAlterArgs args);

    /**
     * Adds a sample, creating the series if it does not exist.
     * <p>
     * TS.ADD
     *
     * @param timestamp sample timestamp in milliseconds
     * @param value sample value
     * @return timestamp of the added sample, or the series' largest timestamp if the sample was
     *         dropped by the IGNORE thresholds
     */
    Mono<Long> add(long timestamp, double value);

    /**
     * Adds a sample stamped with the server's clock, creating the series if it does not exist.
     * <p>
     * TS.ADD with <code>*</code> as the timestamp
     *
     * @param value sample value
     * @return timestamp the server assigned
     */
    Mono<Long> addCurrent(double value);

    /**
     * Adds a sample, creating the series if it does not exist.
     * <p>
     * The creation options carried by <code>args</code> apply only when this call is what
     * creates the series; against an existing one the server ignores them. The exception is the
     * duplicate policy, which overrides the series' own for this sample alone.
     * <p>
     * TS.ADD
     *
     * @param args timestamp, value and per-call options
     * @return timestamp of the added sample
     */
    Mono<Long> add(TSAddArgs args);

    /**
     * Adds many samples in one round trip, creating nothing: unlike a single add this reaches the
     * module through TS.MADD, which refuses a key that is not already a series.
     * <p>
     * TS.MADD, with this series' key repeated for every sample
     *
     * @param samples values by timestamp in milliseconds
     * @return timestamp of each added sample, in the map's iteration order
     */
    Mono<List<Long>> addAll(Map<Long, Double> samples);

    /**
     * Adds <code>addend</code> to the value of the sample at the highest timestamp, stamping the
     * result with the server's clock. Creates the series, holding a single sample equal to
     * <code>addend</code>, if the key does not exist.
     * <p>
     * TS.INCRBY
     *
     * @param addend value to add
     * @return timestamp of the upserted sample
     */
    Mono<Long> incrementBy(double addend);

    /**
     * Adds a value to the sample at the highest timestamp.
     * <p>
     * TS.INCRBY
     *
     * @param args addend, timestamp and creation options
     * @return timestamp of the upserted sample
     */
    Mono<Long> incrementBy(TSIncrArgs args);

    /**
     * Subtracts <code>subtrahend</code> from the value of the sample at the highest timestamp,
     * stamping the result with the server's clock.
     * <p>
     * TS.DECRBY
     *
     * @param subtrahend value to subtract
     * @return timestamp of the upserted sample
     */
    Mono<Long> decrementBy(double subtrahend);

    /**
     * Subtracts a value from the sample at the highest timestamp.
     * <p>
     * TS.DECRBY
     *
     * @param args subtrahend, timestamp and creation options
     * @return timestamp of the upserted sample
     */
    Mono<Long> decrementBy(TSIncrArgs args);

    /**
     * Removes every sample in the inclusive range.
     * <p>
     * TS.DEL
     *
     * @param fromTimestamp start of the range, inclusive
     * @param toTimestamp end of the range, inclusive
     * @return number of samples removed
     */
    Mono<Long> removeRange(long fromTimestamp, long toTimestamp);

    /**
     * Returns the sample at the lowest timestamp. The counterpart of the plain get, which the
     * module has a command for and this does not.
     * <p>
     * TS.RANGE bounded to a single sample
     *
     * @return first sample, empty if the series is empty
     */
    Mono<TSSample> first();

    /**
     * Returns the number of samples in this series.
     * <p>
     * The module has no counting command, so this reads TS.INFO and takes one field from it —
     * the reply carries the labels, the compaction rules and the rest with it. Where more than
     * one of those numbers is wanted, the info call fetches them together.
     *
     * @return sample count
     */
    Mono<Long> size();

    /**
     * Returns the lowest timestamp in this series.
     * <p>
     * An empty series reports 0, as the module does — which a series holding a sample at
     * timestamp 0 also reports, and the module offers no way to tell them apart. The sample
     * count is what answers whether there is a sample at all.
     * <p>
     * TS.INFO
     *
     * @return first timestamp in milliseconds, 0 if the series is empty
     */
    Mono<Long> firstTimestamp();

    /**
     * Returns the highest timestamp in this series, 0 if it is empty.
     * <p>
     * TS.INFO
     *
     * @return last timestamp in milliseconds
     */
    Mono<Long> lastTimestamp();

    /**
     * Returns the sample at the highest timestamp.
     * <p>
     * TS.GET
     *
     * @return last sample, or <code>null</code> if the series is empty
     */
    Mono<TSSample> get();

    /**
     * Returns the sample at the highest timestamp; on a compaction, the still-open latest bucket
     * is compacted and reported rather than skipped.
     * <p>
     * TS.GET LATEST
     *
     * @return last sample, or <code>null</code> if the series is empty
     */
    Mono<TSSample> getLatest();

    /**
     * Returns samples in the inclusive range, oldest first.
     * <p>
     * TS.RANGE
     *
     * @param fromTimestamp start of the range, inclusive
     * @param toTimestamp end of the range, inclusive
     * @return samples ordered by increasing timestamp
     */
    Mono<List<TSSample>> range(long fromTimestamp, long toTimestamp);

    /**
     * Returns samples in a range, oldest first, filtered and optionally aggregated.
     * <p>
     * With one aggregator each returned sample carries one value; with several it carries one
     * per aggregator, in the order they were given.
     * <p>
     * TS.RANGE
     *
     * @param args range bounds, filters, count and aggregation
     * @return samples ordered by increasing timestamp
     */
    Mono<List<TSSample>> range(TSRangeArgs args);

    /**
     * Returns samples in the inclusive range, newest first.
     * <p>
     * TS.REVRANGE
     *
     * @param fromTimestamp start of the range, inclusive
     * @param toTimestamp end of the range, inclusive
     * @return samples ordered by decreasing timestamp
     */
    Mono<List<TSSample>> rangeReversed(long fromTimestamp, long toTimestamp);

    /**
     * Returns samples in a range, newest first, filtered and optionally aggregated.
     * <p>
     * TS.REVRANGE
     *
     * @param args range bounds, filters, count and aggregation
     * @return samples ordered by decreasing timestamp
     */
    Mono<List<TSSample>> rangeReversed(TSRangeArgs args);

    /**
     * Returns samples from <code>timestamp</code> onwards, as a cursor over a growing series.
     * <p>
     * TS.READ
     *
     * @param timestamp inclusive lower bound in milliseconds
     * @return samples ordered by increasing timestamp
     */
    Mono<List<TSSample>> read(long timestamp);

    /**
     * Returns samples from a cursor position, optionally waiting until enough of them exist.
     * <p>
     * TS.READ
     *
     * @param args cursor, block timeout, minimum and maximum sample count
     * @return samples ordered by increasing timestamp
     */
    Mono<List<TSSample>> read(TSReadArgs args);

    /**
     * Creates a compaction rule feeding a destination series from this one.
     * <p>
     * The destination must already exist and must not already receive another rule. Samples
     * already held here are not back-filled — only samples added after the rule reach the
     * destination.
     * <p>
     * TS.CREATERULE
     *
     * @param args destination key, aggregator, bucket width and bucket alignment
     */
    Mono<Void> createRule(TSRuleArgs args);

    /**
     * Removes the compaction rule feeding the given destination series.
     * <p>
     * TS.DELETERULE
     *
     * @param destinationKey key of the destination series
     */
    Mono<Void> deleteRule(String destinationKey);

    /**
     * Returns every sample, oldest first, fetched ten at a time.
     *
     * @return sample flux
     */
    Flux<TSSample> iterator();

    /**
     * Returns every sample, oldest first, fetched <code>count</code> at a time.
     *
     * @param count samples to fetch per round trip
     * @return sample flux
     */
    Flux<TSSample> iterator(int count);

    /**
     * Returns the labels indexing this series.
     * <p>
     * Labels belong to the series, not to its samples: every sample shares them, which is why no
     * sample-returning method reports them. They are what {@link RTimeSeriesNativesReactive} filters
     * on, and its results carry them per series. Replace them with {@link #alter(TSAlterArgs)}.
     * <p>
     * TS.INFO
     *
     * @return label names and values, empty if the series has none
     */
    Mono<Map<String, String>> getLabels();

    /**
     * Returns configuration and statistics for this series.
     * <p>
     * TS.INFO
     *
     * @return series information, with an empty chunk list
     */
    Mono<TSInfo> getInfo();

    /**
     * Returns configuration and statistics for this series, per-chunk detail included.
     * <p>
     * TS.INFO DEBUG
     *
     * @return series information, with per-chunk detail
     */
    Mono<TSInfo> getDebugInfo();

}
