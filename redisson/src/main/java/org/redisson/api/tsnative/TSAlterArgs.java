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
 * Arguments of {@link RTimeSeriesNative#alter(TSAlterArgs)}.
 * <p>
 * There is no <code>encoding</code> here because TS.ALTER cannot change it.
 * <p>
 * Labels are the one field that is not simply "unset means unchanged": TS.ALTER replaces the
 * whole label set, so calling {@link #labels(Map)} with an empty map clears them, while not
 * calling it at all leaves them alone.
 *
 * @author Nikita Koksharov
 *
 */
public interface TSAlterArgs {

    /**
     * Defines alter arguments carrying no changes.
     *
     * @return arguments object
     */
    static TSAlterArgs defaults() {
        return new TSAlterParams();
    }

    /**
     * Defines how long a sample is kept, measured against the highest timestamp the series has
     * seen. {@link Duration#ZERO} keeps samples forever.
     *
     * @param retention retention window
     * @return arguments object
     */
    TSAlterArgs retention(Duration retention);

    /**
     * Defines the initial allocation size in bytes of each new chunk's data. Chunks already
     * allocated keep their size.
     *
     * @param chunkSize chunk size in bytes
     * @return arguments object
     */
    TSAlterArgs chunkSize(int chunkSize);

    /**
     * Defines what happens when a sample is added at a timestamp that already holds one.
     *
     * @param duplicatePolicy duplicate policy
     * @return arguments object
     */
    TSAlterArgs duplicatePolicy(TSDuplicatePolicy duplicatePolicy);

    /**
     * Defines the thresholds under which an in-order duplicate is silently dropped rather than
     * stored.
     *
     * @param maxTimeDiff largest timestamp difference that may be ignored
     * @param maxValueDiff largest value difference that may be ignored
     * @return arguments object
     */
    TSAlterArgs ignore(Duration maxTimeDiff, double maxValueDiff);

    /**
     * Defines the labels indexing this series, replacing every label it currently has.
     *
     * @param labels label names and values
     * @return arguments object
     */
    TSAlterArgs labels(Map<String, String> labels);

    /**
     * Adds one label to the set that will replace the series' current labels.
     *
     * @param name label name
     * @param value label value
     * @return arguments object
     */
    TSAlterArgs label(String name, String value);

}
