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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 *
 * @author Nikita Koksharov
 *
 */
public final class TSGroupedRangeParams extends TSBaseRangeParams implements TSGroupedRangeArgs {

    private final String[] keys;

    TSGroupedRangeParams(String[] keys) {
        this.keys = keys;
    }

    public String[] getKeys() {
        return keys;
    }

    @Override
    public TSGroupedRangeArgs range(long fromTimestamp, long toTimestamp) {
        setRange(fromTimestamp, toTimestamp);
        return this;
    }

    @Override
    public TSGroupedRangeArgs all() {
        return this;
    }

    @Override
    public TSGroupedRangeArgs from(long fromTimestamp) {
        setFrom(fromTimestamp);
        return this;
    }

    @Override
    public TSGroupedRangeArgs to(long toTimestamp) {
        setTo(toTimestamp);
        return this;
    }

    @Override
    public TSGroupedRangeArgs latest() {
        setLatest();
        return this;
    }

    @Override
    public TSGroupedRangeArgs filterByTimestamp(long... timestamps) {
        setFilterByTimestamps(timestamps);
        return this;
    }

    @Override
    public TSGroupedRangeArgs filterByValue(double min, double max) {
        setFilterByValue(min, max);
        return this;
    }

    @Override
    public TSGroupedRangeArgs count(int count) {
        setCount(count);
        return this;
    }

    @Override
    public TSGroupedRangeArgs alignStart() {
        setAlign(EARLIEST);
        return this;
    }

    @Override
    public TSGroupedRangeArgs alignEnd() {
        setAlign(LATEST_TIMESTAMP);
        return this;
    }

    @Override
    public TSGroupedRangeArgs align(long timestamp) {
        setAlign(Long.toString(timestamp));
        return this;
    }

    @Override
    public TSGroupedRangeArgs aggregation(Duration bucketDuration, TSAggregation... aggregations) {
        List<TSAggregation> perKey = Arrays.asList(aggregations);
        List<List<TSAggregation>> repeated = new ArrayList<>(keys.length);
        for (int i = 0; i < keys.length; i++) {
            repeated.add(perKey);
        }
        setAggregations(bucketDuration, repeated);
        return this;
    }

    @Override
    public TSGroupedRangeArgs aggregations(Duration bucketDuration, List<List<TSAggregation>> aggregations) {
        setAggregations(bucketDuration, new ArrayList<>(aggregations));
        return this;
    }

    @Override
    public TSGroupedRangeArgs bucketTimestamp(TSBucketTimestamp bucketTimestamp) {
        setBucketTimestamp(bucketTimestamp);
        return this;
    }

    @Override
    public TSGroupedRangeArgs empty() {
        setEmpty();
        return this;
    }

}
