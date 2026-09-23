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

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;

/**
 *
 * @author Nikita Koksharov
 *
 */
public final class TSMultiRangeParams extends TSBaseRangeParams implements TSMultiRangeArgs {

    private final String[] filters;
    private boolean withLabels;
    private String[] selectedLabels;
    private String groupByLabel;
    private TSReducer reducer;
    private boolean excludeEmpty;

    TSMultiRangeParams(String[] filters) {
        this.filters = filters;
    }

    public String[] getFilters() {
        return filters;
    }

    public boolean isWithLabels() {
        return withLabels;
    }

    public String[] getSelectedLabels() {
        return selectedLabels;
    }

    public String getGroupByLabel() {
        return groupByLabel;
    }

    public TSReducer getReducer() {
        return reducer;
    }

    public boolean isExcludeEmpty() {
        return excludeEmpty;
    }

    @Override
    public TSMultiRangeArgs range(long fromTimestamp, long toTimestamp) {
        setRange(fromTimestamp, toTimestamp);
        return this;
    }

    @Override
    public TSMultiRangeArgs from(long fromTimestamp) {
        setFrom(fromTimestamp);
        return this;
    }

    @Override
    public TSMultiRangeArgs to(long toTimestamp) {
        setTo(toTimestamp);
        return this;
    }

    @Override
    public TSMultiRangeArgs all() {
        return this;
    }

    @Override
    public TSMultiRangeArgs latest() {
        setLatest();
        return this;
    }

    @Override
    public TSMultiRangeArgs filterByTimestamp(long... timestamps) {
        setFilterByTimestamps(timestamps);
        return this;
    }

    @Override
    public TSMultiRangeArgs filterByValue(double min, double max) {
        setFilterByValue(min, max);
        return this;
    }

    @Override
    public TSMultiRangeArgs count(int count) {
        setCount(count);
        return this;
    }

    @Override
    public TSMultiRangeArgs withLabels() {
        this.withLabels = true;
        return this;
    }

    @Override
    public TSMultiRangeArgs selectedLabels(String... labels) {
        this.selectedLabels = labels;
        return this;
    }

    @Override
    public TSMultiRangeArgs alignStart() {
        setAlign(EARLIEST);
        return this;
    }

    @Override
    public TSMultiRangeArgs alignEnd() {
        setAlign(LATEST_TIMESTAMP);
        return this;
    }

    @Override
    public TSMultiRangeArgs align(long timestamp) {
        setAlign(Long.toString(timestamp));
        return this;
    }

    @Override
    public TSMultiRangeArgs aggregation(Duration bucketDuration, TSAggregation... aggregations) {
        setAggregations(bucketDuration, Collections.singletonList(Arrays.asList(aggregations)));
        return this;
    }

    @Override
    public TSMultiRangeArgs bucketTimestamp(TSBucketTimestamp bucketTimestamp) {
        setBucketTimestamp(bucketTimestamp);
        return this;
    }

    @Override
    public TSMultiRangeArgs empty() {
        setEmpty();
        return this;
    }

    @Override
    public TSMultiRangeArgs groupBy(String label, TSReducer reducer) {
        this.groupByLabel = label;
        this.reducer = reducer;
        return this;
    }

    @Override
    public TSMultiRangeArgs excludeEmpty() {
        this.excludeEmpty = true;
        return this;
    }

}
