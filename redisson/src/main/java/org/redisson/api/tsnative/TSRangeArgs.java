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

import org.redisson.api.RTimeSeriesNative;

import java.time.Duration;

/**
 * Arguments of {@link RTimeSeriesNative#range(TSRangeArgs)} and
 * {@link RTimeSeriesNative#rangeReversed(TSRangeArgs)}.
 * <pre>
 *     timeSeries.range(TSRangeArgs.range(from, to)
 *                                 .filterByValue(0, 100)
 *                                 .aggregation(Duration.ofMinutes(5), TSAggregation.AVG));
 * </pre>
 *
 * @author Nikita Koksharov
 *
 */
public interface TSRangeArgs {

    /**
     * Defines a range bounded at both ends, both bounds inclusive.
     *
     * @param fromTimestamp start of the range in milliseconds
     * @param toTimestamp end of the range in milliseconds
     * @return arguments object
     */
    static TSRangeArgs range(long fromTimestamp, long toTimestamp) {
        TSRangeParams params = new TSRangeParams();
        params.setRange(fromTimestamp, toTimestamp);
        return params;
    }

    /**
     * Defines a range running from the given timestamp to the latest sample.
     *
     * @param fromTimestamp start of the range in milliseconds
     * @return arguments object
     */
    static TSRangeArgs from(long fromTimestamp) {
        TSRangeParams params = new TSRangeParams();
        params.setFrom(fromTimestamp);
        return params;
    }

    /**
     * Defines a range running from the earliest sample to the given timestamp.
     *
     * @param toTimestamp end of the range in milliseconds
     * @return arguments object
     */
    static TSRangeArgs to(long toTimestamp) {
        TSRangeParams params = new TSRangeParams();
        params.setTo(toTimestamp);
        return params;
    }

    /**
     * Defines a range covering the whole series.
     *
     * @return arguments object
     */
    static TSRangeArgs all() {
        return new TSRangeParams();
    }

    /**
     * Reports the compacted value of the still-open latest bucket rather than skipping it.
     * Meaningful on a compaction series only.
     *
     * @return arguments object
     */
    TSRangeArgs latest();

    /**
     * Keeps only samples at the given timestamps.
     *
     * @param timestamps timestamps in milliseconds
     * @return arguments object
     */
    TSRangeArgs filterByTimestamp(long... timestamps);

    /**
     * Keeps only samples whose value falls in the inclusive range.
     *
     * @param min smallest value to keep
     * @param max largest value to keep
     * @return arguments object
     */
    TSRangeArgs filterByValue(double min, double max);

    /**
     * Limits how many samples are returned — the ones with the lowest timestamps for a forward
     * range, the highest for a reversed one.
     *
     * @param count maximum number of samples
     * @return arguments object
     */
    TSRangeArgs count(int count);

    /**
     * Aligns aggregation buckets on the range's start timestamp. Requires an explicit start,
     * and an {@link #aggregation} to align — the module rejects ALIGN on its own.
     *
     * @return arguments object
     */
    TSRangeArgs alignStart();

    /**
     * Aligns aggregation buckets on the range's end timestamp. Requires an explicit end, and an
     * {@link #aggregation} to align — the module rejects ALIGN on its own.
     *
     * @return arguments object
     */
    TSRangeArgs alignEnd();

    /**
     * Aligns aggregation buckets on the given timestamp. Requires an {@link #aggregation} to
     * align — the module rejects ALIGN on its own, so setting this without one is refused rather
     * than quietly dropped.
     *
     * @param timestamp alignment timestamp in milliseconds
     * @return arguments object
     */
    TSRangeArgs align(long timestamp);

    /**
     * Aggregates samples into buckets of the given width.
     * <p>
     * Passing several aggregators makes every returned {@link TSSample} carry one value per
     * aggregator, in this order — reachable through {@link TSSample#getValues()}.
     *
     * @param bucketDuration width of each bucket
     * @param aggregations aggregators to apply
     * @return arguments object
     */
    TSRangeArgs aggregation(Duration bucketDuration, TSAggregation... aggregations);

    /**
     * Defines which point of a bucket its reported timestamp refers to. Meaningful only
     * alongside {@link #aggregation}.
     *
     * @param bucketTimestamp point of the bucket to report
     * @return arguments object
     */
    TSRangeArgs bucketTimestamp(TSBucketTimestamp bucketTimestamp);

    /**
     * Reports buckets holding no samples, their value being {@link Double#NaN}. Meaningful only
     * alongside {@link #aggregation}.
     *
     * @return arguments object
     */
    TSRangeArgs empty();

}
