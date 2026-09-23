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
 *
 * @author Nikita Koksharov
 *
 */
public final class TSRuleParams implements TSRuleArgs {

    private final String destinationKey;
    private final TSAggregation aggregation;
    private final Duration bucketDuration;
    private Long alignTimestamp;

    TSRuleParams(String destinationKey, TSAggregation aggregation, Duration bucketDuration) {
        this.destinationKey = Objects.requireNonNull(destinationKey, "destinationKey");
        this.aggregation = Objects.requireNonNull(aggregation, "aggregation");
        this.bucketDuration = Objects.requireNonNull(bucketDuration, "bucketDuration");
    }

    public String getDestinationKey() {
        return destinationKey;
    }

    public TSAggregation getAggregation() {
        return aggregation;
    }

    public Duration getBucketDuration() {
        return bucketDuration;
    }

    public Long getAlignTimestamp() {
        return alignTimestamp;
    }

    @Override
    public TSRuleArgs alignTimestamp(long timestamp) {
        this.alignTimestamp = timestamp;
        return this;
    }

}
