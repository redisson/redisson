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
import java.util.List;

/**
 * Arguments of {@link RTimeSeriesNatives#groupedRange(TSGroupedRangeArgs)} and
 * {@link RTimeSeriesNatives#groupedRangeReversed(TSGroupedRangeArgs)}.
 * <p>
 * Unlike TS.MRANGE these commands name their series outright rather than selecting them by
 * label, and return one row per timestamp instead of one per series.
 * <pre>
 *     multi.groupedRange(TSGroupedRangeArgs.keys("cpu:1", "cpu:2")
 *                                          .range(from, to)
 *                                          .aggregation(Duration.ofMinutes(1), TSAggregation.AVG));
 * </pre>
 *
 * @author Nikita Koksharov
 */
public interface TSGroupedRangeArgs {

    /**
     * Defines the series to query. Their order is the order of the values in every returned
     * {@link TSSample}.
     *
     * @param keys series keys
     * @return arguments object
     */
    static TSGroupedRangeArgs keys(String... keys) {
        return new TSGroupedRangeParams(keys);
    }

    /**
     * Defines a range bounded at both ends, both bounds inclusive.
     *
     * @param fromTimestamp start of the range in milliseconds
     * @param toTimestamp end of the range in milliseconds
     * @return arguments object
     */
    TSGroupedRangeArgs range(long fromTimestamp, long toTimestamp);

    /**
     * Defines a range covering the whole of every queried series. This is the default, and is
     * spelled out for the reader rather than left implicit.
     *
     * @return arguments object
     */
    TSGroupedRangeArgs all();

    /**
     * Defines a range running from the given timestamp to the latest sample.
     *
     * @param fromTimestamp start of the range in milliseconds
     * @return arguments object
     */
    TSGroupedRangeArgs from(long fromTimestamp);

    /**
     * Defines a range running from the earliest sample to the given timestamp.
     *
     * @param toTimestamp end of the range in milliseconds
     * @return arguments object
     */
    TSGroupedRangeArgs to(long toTimestamp);

    /**
     * Reports the compacted value of the still-open latest bucket rather than skipping it.
     *
     * @return arguments object
     */
    TSGroupedRangeArgs latest();

    /**
     * Keeps only samples at the given timestamps.
     *
     * @param timestamps timestamps in milliseconds
     * @return arguments object
     */
    TSGroupedRangeArgs filterByTimestamp(long... timestamps);

    /**
     * Keeps only samples whose value falls in the inclusive range.
     *
     * @param min smallest value to keep
     * @param max largest value to keep
     * @return arguments object
     */
    TSGroupedRangeArgs filterByValue(double min, double max);

    /**
     * Limits how many rows are returned.
     *
     * @param count maximum number of rows
     * @return arguments object
     */
    TSGroupedRangeArgs count(int count);

    /**
     * Aligns aggregation buckets on the range's start timestamp. Requires an explicit start,
     * and an {@link #aggregation} to align — the module rejects ALIGN on its own.
     *
     * @return arguments object
     */
    TSGroupedRangeArgs alignStart();

    /**
     * Aligns aggregation buckets on the range's end timestamp. Requires an explicit end, and an
     * {@link #aggregation} to align — the module rejects ALIGN on its own.
     *
     * @return arguments object
     */
    TSGroupedRangeArgs alignEnd();

    /**
     * Aligns aggregation buckets on the given timestamp. Requires an {@link #aggregation} to
     * align — the module rejects ALIGN on its own, so setting this without one is refused rather
     * than quietly dropped.
     *
     * @param timestamp alignment timestamp in milliseconds
     * @return arguments object
     */
    TSGroupedRangeArgs align(long timestamp);

    /**
     * Aggregates every queried series the same way, into buckets of the given width.
     * <p>
     * The server wants one aggregator specification per key, so this repeats the given one
     * across the key list. Use {@link #aggregations} where the series need different treatment.
     *
     * @param bucketDuration width of each bucket
     * @param aggregations aggregators applied to every series
     * @return arguments object
     */
    TSGroupedRangeArgs aggregation(Duration bucketDuration, TSAggregation... aggregations);

    /**
     * Aggregates each queried series in its own way, into buckets of the given width.
     * <p>
     * The outer list must hold one entry per key, in the order the keys were given, and each
     * inner list the aggregators for that key. This and {@link #aggregation} set the same thing,
     * so the later call replaces the earlier one rather than adding to it. A series aggregated by several aggregators
     * contributes one value per aggregator to every returned {@link TSSample}.
     *
     * @param bucketDuration width of each bucket
     * @param aggregations aggregators, one list per key
     * @return arguments object
     */
    TSGroupedRangeArgs aggregations(Duration bucketDuration, List<List<TSAggregation>> aggregations);

    /**
     * Defines which point of a bucket its reported timestamp refers to. Meaningful only
     * alongside {@link #aggregation}, and dropped without it.
     *
     * @param bucketTimestamp point of the bucket to report
     * @return arguments object
     */
    TSGroupedRangeArgs bucketTimestamp(TSBucketTimestamp bucketTimestamp);

    /**
     * Reports buckets holding no samples, their value being {@link Double#NaN}. Meaningful only
     * alongside {@link #aggregation}, and dropped without it.
     *
     * @return arguments object
     */
    TSGroupedRangeArgs empty();

}
