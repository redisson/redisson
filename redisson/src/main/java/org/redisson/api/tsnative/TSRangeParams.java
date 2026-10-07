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
public final class TSRangeParams extends TSBaseRangeParams implements TSRangeArgs {

    TSRangeParams() {
    }

    @Override
    public TSRangeArgs latest() {
        setLatest();
        return this;
    }

    @Override
    public TSRangeArgs filterByTimestamp(long... timestamps) {
        setFilterByTimestamps(timestamps);
        return this;
    }

    @Override
    public TSRangeArgs filterByValue(double min, double max) {
        setFilterByValue(min, max);
        return this;
    }

    @Override
    public TSRangeArgs count(int count) {
        setCount(count);
        return this;
    }

    @Override
    public TSRangeArgs alignStart() {
        setAlign(EARLIEST);
        return this;
    }

    @Override
    public TSRangeArgs alignEnd() {
        setAlign(LATEST_TIMESTAMP);
        return this;
    }

    @Override
    public TSRangeArgs align(long timestamp) {
        setAlign(Long.toString(timestamp));
        return this;
    }

    @Override
    public TSRangeArgs aggregation(Duration bucketDuration, TSAggregation... aggregations) {
        setAggregations(bucketDuration, Collections.singletonList(Arrays.asList(aggregations)));
        return this;
    }

    @Override
    public TSRangeArgs bucketTimestamp(TSBucketTimestamp bucketTimestamp) {
        setBucketTimestamp(bucketTimestamp);
        return this;
    }

    @Override
    public TSRangeArgs empty() {
        setEmpty();
        return this;
    }

}
