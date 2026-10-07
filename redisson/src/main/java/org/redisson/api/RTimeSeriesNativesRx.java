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

import io.reactivex.rxjava3.core.Single;
import org.redisson.api.tsnative.TSGroupedRangeArgs;
import org.redisson.api.tsnative.TSMultiGetArgs;
import org.redisson.api.tsnative.TSMultiRangeArgs;
import org.redisson.api.tsnative.TSSample;
import org.redisson.api.tsnative.TSSeriesSample;
import org.redisson.api.tsnative.TSSeriesSamples;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Operations of the RedisTimeSeries module that span several series.
 * <p>
 * Requires <b>Redis 8.0.0 or higher, or the RedisTimeSeries module.</b>
 *
 * @author Nikita Koksharov
 */
public interface RTimeSeriesNativesRx {

    /**
     * Adds many samples to many series in one round trip, creating nothing: unlike TS.ADD, TS.MADD
     * refuses a key that is not already a series.
     * <p>
     * TS.MADD
     *
     * @param samples values by timestamp, by series key
     * @return the timestamp each sample was stored at, by series key. It differs from the
     *         timestamp given only where the series' IGNORE thresholds dropped the sample, in
     *         which case the server answers with the timestamp it kept.
     */
    Single<Map<String, List<Long>>> addAll(Map<String, Map<Long, Double>> samples);

    /**
     * Returns the last sample of every series matching the filters.
     * <p>
     * TS.MGET
     *
     * @param args filters, LATEST, and which labels to report
     * @return one entry per matching series
     */
    Single<Map<String, TSSeriesSample>> getAll(TSMultiGetArgs args);

    /**
     * Returns samples of every series matching the filters, oldest first, optionally aggregated
     * and grouped by a label.
     * <p>
     * TS.MRANGE
     *
     * @param args range bounds, filters, aggregation and grouping
     * @return one entry per matching series, or per group when GROUPBY is used
     */
    Single<Map<String, TSSeriesSamples>> range(TSMultiRangeArgs args);

    /**
     * Returns samples of every series matching the filters, newest first.
     * <p>
     * TS.MREVRANGE
     *
     * @param args range bounds, filters, aggregation and grouping
     * @return one entry per matching series, or per group when GROUPBY is used
     */
    Single<Map<String, TSSeriesSamples>> rangeReversed(TSMultiRangeArgs args);

    /**
     * Returns samples of the named series on a shared timeline, oldest first: one entry per
     * timestamp holding the value each key contributed, in key order, with {@link Double#NaN}
     * where a key had none.
     * <p>
     * TS.NRANGE
     *
     * @param args keys, range bounds, filters and aggregation
     * @return one entry per timestamp, ordered by increasing timestamp
     */
    Single<List<TSSample>> groupedRange(TSGroupedRangeArgs args);

    /**
     * Returns samples of the named series on a shared timeline, newest first.
     * <p>
     * TS.NREVRANGE
     *
     * @param args keys, range bounds, filters and aggregation
     * @return one entry per timestamp, ordered by decreasing timestamp
     */
    Single<List<TSSample>> groupedRangeReversed(TSGroupedRangeArgs args);

    /**
     * Returns the keys of every series matching the filters.
     * <p>
     * TS.QUERYINDEX
     *
     * @param filters filter expressions, all of which must match
     * @return matching keys
     */
    Single<Set<String>> queryIndex(String... filters);

    /**
     * Returns the distinct label names used by the series matching the filters, or by every
     * series when no filter is given.
     * <p>
     * TS.QUERYLABELS LABELS
     *
     * @param filters filter expressions, all of which must match
     * @return distinct label names
     */
    Single<Set<String>> labelNames(String... filters);

    /**
     * Returns the distinct values of one label across the series matching the filters, or across
     * every series when no filter is given.
     * <p>
     * TS.QUERYLABELS VALUES
     *
     * @param label label name
     * @param filters filter expressions, all of which must match
     * @return distinct values of that label
     */
    Single<Set<String>> labelValues(String label, String... filters);

}
