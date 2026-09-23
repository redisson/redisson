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

import org.redisson.api.RTimeSeriesNative;

import java.time.Duration;

/**
 * Arguments of {@link RTimeSeriesNative#createRule(TSRuleArgs)}.
 * <p>
 * Destination, aggregator and bucket width are all required by the server, so they are
 * constructor arguments rather than fluent ones — a rule that omits any of them cannot be
 * written.
 *
 * @author Nikita Koksharov
 *
 */
public interface TSRuleArgs {

    /**
     * Defines a rule compacting the source series into <code>destinationKey</code>.
     *
     * @param destinationKey key of the series receiving the compacted samples
     * @param aggregation aggregator applied to each bucket
     * @param bucketDuration width of each bucket
     * @return arguments object
     */
    static TSRuleArgs destination(String destinationKey, TSAggregation aggregation, Duration bucketDuration) {
        return new TSRuleParams(destinationKey, aggregation, bucketDuration);
    }

    /**
     * Defines the timestamp the rule's buckets are aligned on. Without it the buckets are
     * aligned on 0, which is midnight UTC.
     *
     * @param timestamp alignment timestamp in milliseconds
     * @return arguments object
     */
    TSRuleArgs alignTimestamp(long timestamp);

}
