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
package org.redisson.api;

import java.time.Duration;
import java.util.Objects;

/**
 * Rate configuration of {@link RGcra} object.
 *
 * @author Nikita Koksharov
 *
 */
public final class GcraConfig {

    private final long maxBurst;
    private final long tokensPerPeriod;
    private final Duration period;

    public GcraConfig(long maxBurst, long tokensPerPeriod, Duration period) {
        this.maxBurst = maxBurst;
        this.tokensPerPeriod = tokensPerPeriod;
        this.period = period;
    }

    /**
     * Returns maximum burst size set through
     * {@link RGcra#trySetRate(long, long, Duration)} or {@link RGcra#setRate(long, long, Duration)} method.
     *
     * @return maximum burst size
     */
    public long getMaxBurst() {
        return maxBurst;
    }

    /**
     * Returns token replenishment rate per period set through
     * {@link RGcra#trySetRate(long, long, Duration)} or {@link RGcra#setRate(long, long, Duration)} method.
     *
     * @return token amount replenished per period
     */
    public long getTokensPerPeriod() {
        return tokensPerPeriod;
    }

    /**
     * Returns replenishment period set through
     * {@link RGcra#trySetRate(long, long, Duration)} or {@link RGcra#setRate(long, long, Duration)} method.
     *
     * @return replenishment period
     */
    public Duration getPeriod() {
        return period;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        GcraConfig that = (GcraConfig) o;
        return maxBurst == that.maxBurst
                && tokensPerPeriod == that.tokensPerPeriod
                && Objects.equals(period, that.period);
    }

    @Override
    public int hashCode() {
        return Objects.hash(maxBurst, tokensPerPeriod, period);
    }

    @Override
    public String toString() {
        return "GcraConfig{"
                + "maxBurst=" + maxBurst
                + ", tokensPerPeriod=" + tokensPerPeriod
                + ", period=" + period
                + '}';
    }
}
