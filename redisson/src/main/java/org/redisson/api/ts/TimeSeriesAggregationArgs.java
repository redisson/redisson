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
package org.redisson.api.ts;

import org.redisson.api.RTimeSeries;

import java.time.Duration;

/**
 * Arguments of an aggregation over a {@link RTimeSeries} collection.
 * <p>
 * The timestamp range and the bucket interval are required, everything else is optional.
 * The values are read as numbers inside the script, so the collection's codec has to encode
 * numbers as text - see {@link TimeSeriesAggregation}.
 * <pre>
 *     timeSeries.aggregate(TimeSeriesAggregationArgs.between(from, to)
 *                                                   .bucket(Duration.ofMinutes(5))
 *                                                   .avg().max().count());
 * </pre>
 *
 * @author Nikita Koksharov
 *
 * @param &lt;L&gt; label type
 */
public interface TimeSeriesAggregationArgs<L> {

    /**
     * Aggregates the entries within the timestamp range. Including boundary values.
     *
     * @param startTimestamp start timestamp
     * @param endTimestamp end timestamp
     * @return arguments object
     * @param <L> label type
     */
    static <L> TimeSeriesAggregationArgs<L> between(long startTimestamp, long endTimestamp) {
        return new TimeSeriesAggregationParams<L>(startTimestamp, endTimestamp);
    }

    /**
     * Defines the width of a bucket. Required.
     * <p>
     * An entry belongs to the bucket starting at
     * <code>alignment + floor((timestamp - alignment) / bucket) * bucket</code>. Buckets
     * holding no entry are not reported, and one holding a single entry is still a bucket, so
     * an interval far smaller than the spacing of the entries returns as many buckets as there
     * are entries.
     * <p>
     * The arithmetic is done in double precision, so the grid is exact while the distance
     * between a timestamp and the alignment stays below 2^53 milliseconds, which is around
     * 285 000 years. Beyond that the boundaries drift.
     *
     * @param bucket bucket interval
     * @return arguments object
     */
    TimeSeriesAggregationArgs<L> bucket(Duration bucket);

    /**
     * Defines the timestamp bucket boundaries are aligned to. Defaults to <code>0</code>,
     * which is the epoch, matching <code>TS.RANGE</code>.
     *
     * @param timestamp alignment timestamp
     * @return arguments object
     */
    TimeSeriesAggregationArgs<L> alignTo(long timestamp);

    /**
     * Aggregates only the entries whose value falls within <code>min</code> and
     * <code>max</code>. Including boundary values.
     *
     * @param min smallest value to include
     * @param max largest value to include
     * @return arguments object
     */
    TimeSeriesAggregationArgs<L> filterByValue(double min, double max);

    /**
     * Aggregates only the entries carrying <code>label</code>. A <code>null</code>
     * <code>label</code> selects the entries that carry no label at all.
     *
     * <p>
     * <b>The label type is not checked here.</b> {@link #between(long, long)} has nothing to
     * infer <code>L</code> from, so in a chained call it is <code>Object</code> and any label
     * is accepted. A label of the wrong type simply matches nothing.
     *
     * @param label label to match, or <code>null</code> for entries without one
     * @return arguments object
     */
    TimeSeriesAggregationArgs<L> label(L label);

    /**
     * Adds <code>aggregations</code> to the set computed for every bucket. Every aggregation
     * asked for is computed in the same pass over the range.
     *
     * @param aggregations aggregations to compute
     * @return arguments object
     */
    TimeSeriesAggregationArgs<L> aggregations(TimeSeriesAggregation... aggregations);

    TimeSeriesAggregationArgs<L> count();

    TimeSeriesAggregationArgs<L> sum();

    TimeSeriesAggregationArgs<L> avg();

    TimeSeriesAggregationArgs<L> min();

    TimeSeriesAggregationArgs<L> max();

    TimeSeriesAggregationArgs<L> valueRange();

    TimeSeriesAggregationArgs<L> first();

    TimeSeriesAggregationArgs<L> last();

    TimeSeriesAggregationArgs<L> stdDevPopulation();

    TimeSeriesAggregationArgs<L> stdDevSample();

    TimeSeriesAggregationArgs<L> variancePopulation();

    TimeSeriesAggregationArgs<L> varianceSample();

}
