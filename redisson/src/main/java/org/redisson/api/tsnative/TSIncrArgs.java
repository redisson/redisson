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
 * Arguments of {@link RTimeSeriesNative#incrementBy(TSIncrArgs)} and
 * {@link RTimeSeriesNative#decrementBy(TSIncrArgs)}.
 * <p>
 * One type serves both because TS.INCRBY and TS.DECRBY take identical options and differ only in
 * the sign they apply; the amount is always given as a positive quantity and the method decides
 * the direction.
 * <p>
 * There is no <code>onDuplicate</code> here: these commands upsert the sample at the highest
 * timestamp by definition, so the duplicate policy has nothing to decide.
 *
 * @author Nikita Koksharov
 *
 */
public interface TSIncrArgs {

    /**
     * Defines the amount to add or subtract, applied to the sample at the highest timestamp and
     * stamped with the server's clock.
     *
     * @param value amount
     * @return arguments object
     */
    static TSIncrArgs value(double value) {
        return new TSIncrParams(value);
    }

    /**
     * Defines the timestamp the result is written at. Must be at or after the series' highest
     * timestamp, or the server rejects the command.
     *
     * @param timestamp timestamp in milliseconds
     * @return arguments object
     */
    TSIncrArgs timestamp(long timestamp);

    /**
     * Defines the retention window applied if this call creates the series.
     *
     * @param retention retention window
     * @return arguments object
     */
    TSIncrArgs retention(Duration retention);

    /**
     * Defines the encoding applied if this call creates the series.
     *
     * @param encoding sample encoding
     * @return arguments object
     */
    TSIncrArgs encoding(TSEncoding encoding);

    /**
     * Defines the chunk size applied if this call creates the series.
     *
     * @param chunkSize chunk size in bytes
     * @return arguments object
     */
    TSIncrArgs chunkSize(int chunkSize);

    /**
     * Defines the duplicate policy applied if this call creates the series.
     *
     * @param duplicatePolicy duplicate policy
     * @return arguments object
     */
    TSIncrArgs duplicatePolicy(TSDuplicatePolicy duplicatePolicy);

    /**
     * Defines the ignore thresholds applied if this call creates the series.
     *
     * @param maxTimeDiff largest timestamp difference that may be ignored
     * @param maxValueDiff largest value difference that may be ignored
     * @return arguments object
     */
    TSIncrArgs ignore(Duration maxTimeDiff, double maxValueDiff);

    /**
     * Defines the labels applied if this call creates the series.
     *
     * @param labels label names and values
     * @return arguments object
     */
    TSIncrArgs labels(Map<String, String> labels);

    /**
     * Adds one label to the set applied if this call creates the series.
     *
     * @param name label name
     * @param value label value
     * @return arguments object
     */
    TSIncrArgs label(String name, String value);

}
