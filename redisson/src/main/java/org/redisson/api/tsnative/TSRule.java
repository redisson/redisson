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
import java.util.Objects;

/**
 * A compaction rule as TS.INFO reports it on the source series.
 *
 * @author Nikita Koksharov
 *
 */
public class TSRule {

    private final String destinationKey;
    private final Duration bucketDuration;
    private final TSAggregation aggregation;
    private final long alignTimestamp;

    public TSRule(String destinationKey, Duration bucketDuration, TSAggregation aggregation, long alignTimestamp) {
        this.destinationKey = destinationKey;
        this.bucketDuration = bucketDuration;
        this.aggregation = aggregation;
        this.alignTimestamp = alignTimestamp;
    }

    /**
     * Returns the key of the series this rule feeds.
     *
     * @return destination key
     */
    public String getDestinationKey() {
        return destinationKey;
    }

    /**
     * Returns the width of the buckets this rule aggregates over.
     *
     * @return bucket duration
     */
    public Duration getBucketDuration() {
        return bucketDuration;
    }

    /**
     * Returns the aggregator this rule applies, or <code>null</code> if the server named one
     * this version of Redisson does not know.
     *
     * @return aggregator
     */
    public TSAggregation getAggregation() {
        return aggregation;
    }

    /**
     * Returns the timestamp the rule's buckets are aligned on.
     *
     * @return alignment timestamp
     */
    public long getAlignTimestamp() {
        return alignTimestamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TSRule that = (TSRule) o;
        return alignTimestamp == that.alignTimestamp
                && Objects.equals(destinationKey, that.destinationKey)
                && Objects.equals(bucketDuration, that.bucketDuration)
                && aggregation == that.aggregation;
    }

    @Override
    public int hashCode() {
        return Objects.hash(destinationKey, bucketDuration, aggregation, alignTimestamp);
    }

    @Override
    public String toString() {
        return "TSRule [destinationKey=" + destinationKey + ", bucketDuration=" + bucketDuration
                + ", aggregation=" + aggregation + ", alignTimestamp=" + alignTimestamp + "]";
    }

}
