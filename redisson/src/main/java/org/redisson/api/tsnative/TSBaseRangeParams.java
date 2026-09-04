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
import java.util.List;

/**
 *
 * @author Nikita Koksharov
 *
 */
public abstract class TSBaseRangeParams {

    /**
     * The module's sentinel for "the earliest sample in the series".
     */
    public static final String EARLIEST = "-";

    /**
     * The module's sentinel for "the latest sample in the series".
     */
    public static final String LATEST_TIMESTAMP = "+";

    private String fromTimestamp = EARLIEST;
    private String toTimestamp = LATEST_TIMESTAMP;
    private boolean latest;
    private long[] filterByTimestamps;
    private Double filterByMinValue;
    private Double filterByMaxValue;
    private Integer count;
    private String align;
    private Duration bucketDuration;
    private List<List<TSAggregation>> aggregations;
    private TSBucketTimestamp bucketTimestamp;
    private boolean empty;

    public String getFromTimestamp() {
        return fromTimestamp;
    }

    public String getToTimestamp() {
        return toTimestamp;
    }

    void setRange(long fromTimestamp, long toTimestamp) {
        this.fromTimestamp = Long.toString(fromTimestamp);
        this.toTimestamp = Long.toString(toTimestamp);
    }

    void setFrom(long fromTimestamp) {
        this.fromTimestamp = Long.toString(fromTimestamp);
        this.toTimestamp = LATEST_TIMESTAMP;
    }

    void setTo(long toTimestamp) {
        this.fromTimestamp = EARLIEST;
        this.toTimestamp = Long.toString(toTimestamp);
    }

    public boolean isLatest() {
        return latest;
    }

    void setLatest() {
        this.latest = true;
    }

    public long[] getFilterByTimestamps() {
        return filterByTimestamps;
    }

    void setFilterByTimestamps(long[] timestamps) {
        this.filterByTimestamps = timestamps;
    }

    public Double getFilterByMinValue() {
        return filterByMinValue;
    }

    public Double getFilterByMaxValue() {
        return filterByMaxValue;
    }

    void setFilterByValue(double min, double max) {
        this.filterByMinValue = min;
        this.filterByMaxValue = max;
    }

    public Integer getCount() {
        return count;
    }

    void setCount(Integer count) {
        this.count = count;
    }

    public String getAlign() {
        return align;
    }

    void setAlign(String align) {
        this.align = align;
    }

    public Duration getBucketDuration() {
        return bucketDuration;
    }

    public List<List<TSAggregation>> getAggregations() {
        return aggregations;
    }

    void setAggregations(Duration bucketDuration, List<List<TSAggregation>> aggregations) {
        this.bucketDuration = bucketDuration;
        this.aggregations = aggregations;
    }

    public TSBucketTimestamp getBucketTimestamp() {
        return bucketTimestamp;
    }

    void setBucketTimestamp(TSBucketTimestamp bucketTimestamp) {
        this.bucketTimestamp = bucketTimestamp;
    }

    public boolean isEmpty() {
        return empty;
    }

    void setEmpty() {
        this.empty = true;
    }

}
