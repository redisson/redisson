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

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * @author Nikita Koksharov
 *
 * @param &lt;L&gt; label type
 */
public final class TimeSeriesAggregationParams<L> implements TimeSeriesAggregationArgs<L> {

    private final long startTimestamp;
    private final long endTimestamp;
    private Duration bucket;
    private long alignment;
    private boolean valueFiltered;
    private double minValue;
    private double maxValue;
    private boolean labelFiltered;
    private L label;
    private final Set<TimeSeriesAggregation> aggregations = new LinkedHashSet<>();

    TimeSeriesAggregationParams(long startTimestamp, long endTimestamp) {
        this.startTimestamp = startTimestamp;
        this.endTimestamp = endTimestamp;
    }

    @Override
    public TimeSeriesAggregationArgs<L> bucket(Duration bucket) {
        this.bucket = bucket;
        return this;
    }

    @Override
    public TimeSeriesAggregationArgs<L> alignTo(long timestamp) {
        this.alignment = timestamp;
        return this;
    }

    @Override
    public TimeSeriesAggregationArgs<L> filterByValue(double min, double max) {
        this.valueFiltered = true;
        this.minValue = min;
        this.maxValue = max;
        return this;
    }

    @Override
    public TimeSeriesAggregationArgs<L> label(L label) {
        this.labelFiltered = true;
        this.label = label;
        return this;
    }

    @Override
    public TimeSeriesAggregationArgs<L> aggregations(TimeSeriesAggregation... values) {
        for (TimeSeriesAggregation value : values) {
            aggregations.add(Objects.requireNonNull(value, "aggregation should not be null"));
        }
        return this;
    }

    @Override
    public TimeSeriesAggregationArgs<L> count() {
        return aggregations(TimeSeriesAggregation.COUNT);
    }

    @Override
    public TimeSeriesAggregationArgs<L> sum() {
        return aggregations(TimeSeriesAggregation.SUM);
    }

    @Override
    public TimeSeriesAggregationArgs<L> avg() {
        return aggregations(TimeSeriesAggregation.AVG);
    }

    @Override
    public TimeSeriesAggregationArgs<L> min() {
        return aggregations(TimeSeriesAggregation.MIN);
    }

    @Override
    public TimeSeriesAggregationArgs<L> max() {
        return aggregations(TimeSeriesAggregation.MAX);
    }

    @Override
    public TimeSeriesAggregationArgs<L> valueRange() {
        return aggregations(TimeSeriesAggregation.VALUE_RANGE);
    }

    @Override
    public TimeSeriesAggregationArgs<L> first() {
        return aggregations(TimeSeriesAggregation.FIRST);
    }

    @Override
    public TimeSeriesAggregationArgs<L> last() {
        return aggregations(TimeSeriesAggregation.LAST);
    }

    @Override
    public TimeSeriesAggregationArgs<L> stdDevPopulation() {
        return aggregations(TimeSeriesAggregation.STD_DEV_POPULATION);
    }

    @Override
    public TimeSeriesAggregationArgs<L> stdDevSample() {
        return aggregations(TimeSeriesAggregation.STD_DEV_SAMPLE);
    }

    @Override
    public TimeSeriesAggregationArgs<L> variancePopulation() {
        return aggregations(TimeSeriesAggregation.VARIANCE_POPULATION);
    }

    @Override
    public TimeSeriesAggregationArgs<L> varianceSample() {
        return aggregations(TimeSeriesAggregation.VARIANCE_SAMPLE);
    }

    public long getStartTimestamp() {
        return startTimestamp;
    }

    public long getEndTimestamp() {
        return endTimestamp;
    }

    public Duration getBucket() {
        return bucket;
    }

    public long getAlignment() {
        return alignment;
    }

    public boolean isValueFiltered() {
        return valueFiltered;
    }

    public double getMinValue() {
        return minValue;
    }

    public double getMaxValue() {
        return maxValue;
    }

    public boolean isLabelFiltered() {
        return labelFiltered;
    }

    public L getLabel() {
        return label;
    }

    public List<TimeSeriesAggregation> getAggregations() {
        return new ArrayList<>(aggregations);
    }

}
