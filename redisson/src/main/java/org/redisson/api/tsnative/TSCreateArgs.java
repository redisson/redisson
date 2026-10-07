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
import java.util.Map;

/**
 * Arguments of {@link RTimeSeriesNative#create(TSCreateArgs)}.
 * <p>
 * Every option is optional; whatever is left unset follows the server's own configuration.
 * <pre>
 *     timeSeries.create(TSCreateArgs.defaults()
 *                                   .retention(Duration.ofDays(7))
 *                                   .duplicatePolicy(TSDuplicatePolicy.LAST)
 *                                   .label("area", "32"));
 * </pre>
 *
 * @author Nikita Koksharov
 *
 */
public interface TSCreateArgs {

    /**
     * Defines creation arguments carrying no options, so that the series takes the server's
     * defaults for retention, encoding, chunk size and duplicate policy, and has no labels.
     *
     * @return arguments object
     */
    static TSCreateArgs defaults() {
        return new TSCreateParams();
    }

    /**
     * Defines how long a sample is kept, measured against the highest timestamp the series has
     * seen. {@link Duration#ZERO} keeps samples forever.
     *
     * @param retention retention window
     * @return arguments object
     */
    TSCreateArgs retention(Duration retention);

    /**
     * Defines how samples are encoded. Cannot be changed afterwards.
     *
     * @param encoding sample encoding
     * @return arguments object
     */
    TSCreateArgs encoding(TSEncoding encoding);

    /**
     * Defines the initial allocation size in bytes of each new chunk's data. Must be a multiple
     * of 8 in the range [48 .. 1048576].
     *
     * @param chunkSize chunk size in bytes
     * @return arguments object
     */
    TSCreateArgs chunkSize(int chunkSize);

    /**
     * Defines what happens when a sample is added at a timestamp that already holds one.
     *
     * @param duplicatePolicy duplicate policy
     * @return arguments object
     */
    TSCreateArgs duplicatePolicy(TSDuplicatePolicy duplicatePolicy);

    /**
     * Defines the thresholds under which an in-order duplicate is silently dropped rather than
     * stored. Applies only to a non-compaction series whose duplicate policy is
     * {@link TSDuplicatePolicy#LAST}.
     *
     * @param maxTimeDiff largest timestamp difference that may be ignored
     * @param maxValueDiff largest value difference that may be ignored
     * @return arguments object
     */
    TSCreateArgs ignore(Duration maxTimeDiff, double maxValueDiff);

    /**
     * Defines the labels indexing this series, replacing any set so far.
     *
     * @param labels label names and values
     * @return arguments object
     */
    TSCreateArgs labels(Map<String, String> labels);

    /**
     * Adds one label to the set indexing this series.
     *
     * @param name label name
     * @param value label value
     * @return arguments object
     */
    TSCreateArgs label(String name, String value);

}
