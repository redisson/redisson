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
package org.redisson.api.tsnative;

import org.redisson.api.RTimeSeriesNatives;

import java.time.Duration;

/**
 * Arguments of {@link RTimeSeriesNatives#range(TSMultiRangeArgs)} and
 * {@link RTimeSeriesNatives#rangeReversed(TSMultiRangeArgs)}.
 * <p>
 * {@link #filter} is required — TS.MRANGE selects its series by label, and the server rejects a
 * command without one.
 * <pre>
 *     multi.range(TSMultiRangeArgs.range(from, to)
 *                                 .filter("area=32")
 *                                 .withLabels()
 *                                 .aggregation(Duration.ofMinutes(1), TSAggregation.AVG)
 *                                 .groupBy("area", TSReducer.SUM));
 * </pre>
 *
 * @author Nikita Koksharov
 */
public interface TSMultiRangeArgs {

    /**
     * Selects the series to read by label filter.
     * <p>
     * The filter is where the arguments start, rather than one more fluent option, because the
     * module requires it — TS.MRANGE has no way to mean "every series". The range does have a
     * default, the whole of each matched series, so it stays optional below.
     *
     * @param filters filter expressions, all of which must match
     * @return arguments object
     */
    static TSMultiRangeArgs filter(String... filters) {
        return new TSMultiRangeParams(filters);
    }

    /**
     * Defines a range bounded at both ends, both bounds inclusive.
     *
     * @param fromTimestamp start of the range in milliseconds
     * @param toTimestamp end of the range in milliseconds
     * @return arguments object
     */
    TSMultiRangeArgs range(long fromTimestamp, long toTimestamp);

    /**
     * Defines a range running from the given timestamp to the latest sample.
     *
     * @param fromTimestamp start of the range in milliseconds
     * @return arguments object
     */
    TSMultiRangeArgs from(long fromTimestamp);

    /**
     * Defines a range running from the earliest sample to the given timestamp.
     *
     * @param toTimestamp end of the range in milliseconds
     * @return arguments object
     */
    TSMultiRangeArgs to(long toTimestamp);

    /**
     * Defines a range covering the whole of every matched series. This is the default, and is
     * spelled out for the reader rather than left implicit.
     *
     * @return arguments object
     */
    TSMultiRangeArgs all();

    /**
     * Reports the compacted value of the still-open latest bucket rather than skipping it.
     *
     * @return arguments object
     */
    TSMultiRangeArgs latest();

    /**
     * Keeps only samples at the given timestamps.
     *
     * @param timestamps timestamps in milliseconds
     * @return arguments object
     */
    TSMultiRangeArgs filterByTimestamp(long... timestamps);

    /**
     * Keeps only samples whose value falls in the inclusive range.
     *
     * @param min smallest value to keep
     * @param max largest value to keep
     * @return arguments object
     */
    TSMultiRangeArgs filterByValue(double min, double max);

    /**
     * Limits how many samples are returned per series.
     *
     * @param count maximum number of samples per series
     * @return arguments object
     */
    TSMultiRangeArgs count(int count);

    /**
     * Reports every label of each matched series. Mutually exclusive with
     * {@link #selectedLabels(String...)} — the server answers
     * "cannot accept WITHLABELS and SELECT_LABELS together".
     *
     * @return arguments object
     */
    TSMultiRangeArgs withLabels();

    /**
     * Reports only the named labels of each matched series. Mutually exclusive with
     * {@link #withLabels()}, and at least one label must be named.
     *
     * @param labels label names to report
     * @return arguments object
     */
    TSMultiRangeArgs selectedLabels(String... labels);

    /**
     * Aligns aggregation buckets on the range's start timestamp. Requires an explicit start,
     * and an {@link #aggregation} to align — the module rejects ALIGN on its own.
     *
     * @return arguments object
     */
    TSMultiRangeArgs alignStart();

    /**
     * Aligns aggregation buckets on the range's end timestamp. Requires an explicit end, and an
     * {@link #aggregation} to align — the module rejects ALIGN on its own.
     *
     * @return arguments object
     */
    TSMultiRangeArgs alignEnd();

    /**
     * Aligns aggregation buckets on the given timestamp. Requires an {@link #aggregation} to
     * align — the module rejects ALIGN on its own, so setting this without one is refused rather
     * than quietly dropped.
     *
     * @param timestamp alignment timestamp in milliseconds
     * @return arguments object
     */
    TSMultiRangeArgs align(long timestamp);

    /**
     * Aggregates each matched series into buckets of the given width.
     *
     * @param bucketDuration width of each bucket
     * @param aggregations aggregators to apply
     * @return arguments object
     */
    TSMultiRangeArgs aggregation(Duration bucketDuration, TSAggregation... aggregations);

    /**
     * Defines which point of a bucket its reported timestamp refers to. Meaningful only
     * alongside {@link #aggregation}, and dropped without it.
     *
     * @param bucketTimestamp point of the bucket to report
     * @return arguments object
     */
    TSMultiRangeArgs bucketTimestamp(TSBucketTimestamp bucketTimestamp);

    /**
     * Reports buckets holding no samples, their value being {@link Double#NaN}. Meaningful only
     * alongside {@link #aggregation}, and dropped without it.
     *
     * @return arguments object
     */
    TSMultiRangeArgs empty();

    /**
     * Collapses the matched series into one row per distinct value of <code>label</code>,
     * combining the series in each group with <code>reducer</code>.
     * <p>
     * Each returned row's key is the group's identity as the module writes it —
     * <code>label=value</code> — and, when {@link #withLabels()} is set, it carries a
     * <code>__reducer__</code> and <code>__source__</code> label naming the reducer and the
     * series that fed the group.
     * <p>
     * Rejected alongside an {@link #aggregation} carrying more than one aggregator, and
     * alongside {@link #excludeEmpty()} — both combinations are refused before the command is
     * sent, with the message naming the pair.
     *
     * @param label label to group by
     * @param reducer how to combine the series of a group
     * @return arguments object
     */
    TSMultiRangeArgs groupBy(String label, TSReducer reducer);

    /**
     * Leaves out matched series that hold no sample in the range, which are otherwise reported
     * with an empty sample list. Mutually exclusive with {@link #groupBy}.
     * <p>
     * This is about series, not buckets: a series whose samples in the range are all
     * {@link Double#NaN} still counts as non-empty and is reported, and {@link #empty()} still
     * fills in empty buckets of the series that survive. The server rejects this alongside
     * {@link #groupBy}.
     * <p>
     * Requires <b>Redis 8.10 or higher.</b>
     *
     * @return arguments object
     */
    TSMultiRangeArgs excludeEmpty();

}
