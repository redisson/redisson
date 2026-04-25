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

import java.util.Objects;

/**
 * Result returned by Redis {@code GCRA} command.
 *
 * @author Su Ko
 *
 */
public final class GcraResult {

    private final boolean limited;
    private final long maxTokens;
    private final long availableTokens;
    private final long retryAfterSeconds;
    private final long fullBurstAfterSeconds;

    public GcraResult(boolean limited, long maxTokens, long availableTokens,
                      long retryAfterSeconds, long fullBurstAfterSeconds) {
        this.limited = limited;
        this.maxTokens = maxTokens;
        this.availableTokens = availableTokens;
        this.retryAfterSeconds = retryAfterSeconds;
        this.fullBurstAfterSeconds = fullBurstAfterSeconds;
    }

    public boolean isLimited() {
        return limited;
    }

    public long getMaxTokens() {
        return maxTokens;
    }

    public long getAvailableTokens() {
        return availableTokens;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    public long getFullBurstAfterSeconds() {
        return fullBurstAfterSeconds;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        GcraResult that = (GcraResult) o;
        return limited == that.limited
                && maxTokens == that.maxTokens
                && availableTokens == that.availableTokens
                && retryAfterSeconds == that.retryAfterSeconds
                && fullBurstAfterSeconds == that.fullBurstAfterSeconds;
    }

    @Override
    public int hashCode() {
        return Objects.hash(limited, maxTokens, availableTokens, retryAfterSeconds, fullBurstAfterSeconds);
    }

    @Override
    public String toString() {
        return "GcraResult{"
                + "limited=" + limited
                + ", maxTokens=" + maxTokens
                + ", availableTokens=" + availableTokens
                + ", retryAfterSeconds=" + retryAfterSeconds
                + ", fullBurstAfterSeconds=" + fullBurstAfterSeconds
                + '}';
    }
}
