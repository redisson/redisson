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
 * Arguments of {@link RTimeSeriesNative#add(TSAddArgs)}.
 * <p>
 * The creation options apply only when the call is what brings the series into existence;
 * against a series that already exists the server ignores them. {@link #onDuplicate} is the
 * exception — it overrides the series' duplicate policy for this one sample.
 * <pre>
 *     timeSeries.add(TSAddArgs.sample(timestamp, 21.5)
 *                             .onDuplicate(TSDuplicatePolicy.LAST));
 * </pre>
 *
 * @author Nikita Koksharov
 */
public interface TSAddArgs {

    /**
     * Defines a sample with the given timestamp and value.
     *
     * @param timestamp sample timestamp in milliseconds
     * @param value sample value
     * @return arguments object
     */
    static TSAddArgs sample(long timestamp, double value) {
        return new TSAddParams(timestamp, value);
    }

    /**
     * Defines a sample stamped by the server with its own clock.
     *
     * @param value sample value
     * @return arguments object
     */
    static TSAddArgs currentSample(double value) {
        return new TSAddParams(value);
    }

    /**
     * Defines the policy for this sample alone, overriding the series' own without changing it.
     *
     * @param duplicatePolicy duplicate policy
     * @return arguments object
     */
    TSAddArgs onDuplicate(TSDuplicatePolicy duplicatePolicy);

    /**
     * Defines the duplicate policy the series is created with, if this call is what creates it.
     * <p>
     * Distinct from {@link #onDuplicate}, which decides this one sample's fate and leaves the
     * series' own policy alone.
     *
     * @param duplicatePolicy duplicate policy
     * @return arguments object
     */
    TSAddArgs duplicatePolicy(TSDuplicatePolicy duplicatePolicy);

    /**
     * Defines the retention window applied if this call creates the series.
     *
     * @param retention retention window
     * @return arguments object
     */
    TSAddArgs retention(Duration retention);

    /**
     * Defines the encoding applied if this call creates the series.
     *
     * @param encoding sample encoding
     * @return arguments object
     */
    TSAddArgs encoding(TSEncoding encoding);

    /**
     * Defines the chunk size applied if this call creates the series.
     *
     * @param chunkSize chunk size in bytes
     * @return arguments object
     */
    TSAddArgs chunkSize(int chunkSize);

    /**
     * Defines the ignore thresholds applied if this call creates the series.
     *
     * @param maxTimeDiff largest timestamp difference that may be ignored
     * @param maxValueDiff largest value difference that may be ignored
     * @return arguments object
     */
    TSAddArgs ignore(Duration maxTimeDiff, double maxValueDiff);

    /**
     * Defines the labels applied if this call creates the series.
     *
     * @param labels label names and values
     * @return arguments object
     */
    TSAddArgs labels(Map<String, String> labels);

    /**
     * Adds one label to the set applied if this call creates the series.
     *
     * @param name label name
     * @param value label value
     * @return arguments object
     */
    TSAddArgs label(String name, String value);

}
