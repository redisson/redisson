/**
 * Copyright (c) 2013-2020 Nikita Koksharov
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
package org.redisson.api;

import java.util.Objects;

/**
 * Time-series collection entry
 *
 * @author Nikita Koksharov
 *
 */
public class TimeSeriesEntry<V> {

    private long timestamp;
    private V value;

    public TimeSeriesEntry(long timestamp, V value) {
        this.timestamp = timestamp;
        this.value = value;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public V getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TimeSeriesEntry<?> entry = (TimeSeriesEntry<?>) o;
        return timestamp == entry.timestamp
                && Objects.equals(value, entry.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(timestamp, value);
    }

    @Override
    public String toString() {
        return "Entry{" +
                "timestamp=" + timestamp +
                ", value=" + value +
                '}';
    }

}
