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

import java.util.Arrays;
import java.util.Objects;

/**
 * One point in time: a millisecond timestamp and the values reported for it.
 * <p>
 * How many values, and what they mean, is set by the command that returned the sample, and the
 * array is held as an array for that reason:
 * <ul>
 * <li>a plain sample, and a bucket aggregated by a single aggregator, carry exactly one value;</li>
 * <li>a bucket aggregated by several carries one per aggregator, in the order they were passed to
 * {@link TSRangeArgs#aggregation};</li>
 * <li>a row of {@link org.redisson.api.RTimeSeriesNatives#groupedRange} carries what every
 * queried series contributed at that timestamp, in the order the keys were passed to
 * {@link TSGroupedRangeArgs#keys}, and one value per aggregator per series under AGGREGATION.</li>
 * </ul>
 * A value that no series reported, and a bucket the EMPTY option left empty, are both
 * {@link Double#NaN}.
 *
 * @author Nikita Koksharov
 *
 */
public class TSSample {

    private final long timestamp;
    private final double[] values;

    public TSSample(long timestamp, double[] values) {
        this.timestamp = timestamp;
        this.values = values;
    }

    /**
     * Returns the timestamp of this sample in milliseconds.
     *
     * @return timestamp
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Returns the first value of this sample — the only one, unless several aggregators or
     * several series were asked for, in which case the rest are reachable through
     * {@link #getValues()}.
     *
     * @return first value
     */
    public double getValue() {
        return values[0];
    }

    /**
     * Returns every value of this sample, in the order the class documentation describes for the
     * command that produced it.
     *
     * @return values
     */
    public double[] getValues() {
        return values;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TSSample that = (TSSample) o;
        return timestamp == that.timestamp && Arrays.equals(values, that.values);
    }

    @Override
    public int hashCode() {
        return Objects.hash(timestamp) * 31 + Arrays.hashCode(values);
    }

    @Override
    public String toString() {
        return "TSSample [timestamp=" + timestamp + ", values=" + Arrays.toString(values) + "]";
    }

}
