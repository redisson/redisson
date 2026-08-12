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

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * One time bucket of an aggregation, holding the aggregations that were asked for.
 * <p>
 * An aggregation that was not requested, and one that is not defined for the bucket, both
 * read as <code>null</code>.
 *
 * @author Nikita Koksharov
 *
 */
public class TimeSeriesBucket {

    private final long timestamp;
    private final Map<TimeSeriesAggregation, Double> values;

    public TimeSeriesBucket(long timestamp, Map<TimeSeriesAggregation, Double> values) {
        this.timestamp = timestamp;
        if (values.isEmpty()) {
            this.values = Collections.emptyMap();
        } else {
            this.values = Collections.unmodifiableMap(new EnumMap<>(values));
        }
    }

    /**
     * Returns the timestamp this bucket starts at.
     *
     * @return bucket timestamp
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Returns the value of <code>aggregation</code>, or <code>null</code> if it was not
     * requested or is not defined for this bucket.
     *
     * @param aggregation aggregation
     * @return aggregated value or <code>null</code>
     */
    public Double get(TimeSeriesAggregation aggregation) {
        return values.get(aggregation);
    }

    /**
     * Returns every aggregation held by this bucket, iterating in the order the aggregations
     * are declared rather than the order they were asked for.
     *
     * @return aggregations map
     */
    public Map<TimeSeriesAggregation, Double> getValues() {
        return values;
    }

    public Double getCount() {
        return get(TimeSeriesAggregation.COUNT);
    }

    public Double getSum() {
        return get(TimeSeriesAggregation.SUM);
    }

    public Double getAvg() {
        return get(TimeSeriesAggregation.AVG);
    }

    public Double getMin() {
        return get(TimeSeriesAggregation.MIN);
    }

    public Double getMax() {
        return get(TimeSeriesAggregation.MAX);
    }

    public Double getValueRange() {
        return get(TimeSeriesAggregation.VALUE_RANGE);
    }

    public Double getFirst() {
        return get(TimeSeriesAggregation.FIRST);
    }

    public Double getLast() {
        return get(TimeSeriesAggregation.LAST);
    }

    public Double getStdDevPopulation() {
        return get(TimeSeriesAggregation.STD_DEV_POPULATION);
    }

    public Double getStdDevSample() {
        return get(TimeSeriesAggregation.STD_DEV_SAMPLE);
    }

    public Double getVariancePopulation() {
        return get(TimeSeriesAggregation.VARIANCE_POPULATION);
    }

    public Double getVarianceSample() {
        return get(TimeSeriesAggregation.VARIANCE_SAMPLE);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TimeSeriesBucket that = (TimeSeriesBucket) o;
        return timestamp == that.timestamp && values.equals(that.values);
    }

    @Override
    public int hashCode() {
        return Objects.hash(timestamp, values);
    }

    @Override
    public String toString() {
        return "TimeSeriesBucket{timestamp=" + timestamp + ", values=" + values + "}";
    }

}
