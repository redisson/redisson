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
package org.redisson.api.ts;

import org.redisson.api.RTimeSeries;

import java.time.Duration;

/**
 * Arguments of an entry added to {@link RTimeSeries} collection.
 * <p>
 * Timestamp and object are required, label and time to live are optional.
 * <pre>
 *     timeSeries.addOrReplace(TimeSeriesAddArgs.entry(timestamp, value));
 *
 *     timeSeries.addIfAbsent(TimeSeriesAddArgs.entry(timestamp, value, label)
 *                                             .timeToLive(Duration.ofHours(1)));
 * </pre>
 *
 * @author Nikita Koksharov
 *
 * @param <V> value type
 * @param <L> label type
 */
public interface TimeSeriesAddArgs<V, L> {

    /**
     * Defines an entry with the specified <code>timestamp</code> and <code>object</code>.
     *
     * @param timestamp object timestamp
     * @param object object itself
     * @return arguments object
     * @param <V> value type
     * @param <L> label type
     */
    static <V, L> TimeSeriesAddArgs<V, L> entry(long timestamp, V object) {
        return new TimeSeriesAddParams<>(timestamp, object);
    }

    /**
     * Defines an entry with the specified <code>timestamp</code>, <code>object</code>
     * and <code>label</code>.
     *
     * @param timestamp object timestamp
     * @param object object itself
     * @param label object label
     * @return arguments object
     * @param <V> value type
     * @param <L> label type
     */
    static <V, L> TimeSeriesAddArgs<V, L> entry(long timestamp, V object, L label) {
        return new TimeSeriesAddParams<V, L>(timestamp, object).label(label);
    }

    /**
     * Defines the label associated with the entry.
     *
     * @param label object label
     * @return arguments object
     */
    TimeSeriesAddArgs<V, L> label(L label);

    /**
     * Defines the time to live interval of the entry.
     * If not defined the entry is stored until it's removed explicitly.
     *
     * @param timeToLive time to live interval
     * @return arguments object
     */
    TimeSeriesAddArgs<V, L> timeToLive(Duration timeToLive);

}
