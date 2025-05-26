/**
 * Copyright (c) 2013-2024 Nikita Koksharov
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
package org.redisson.config;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Equal jitter strategy that introduces moderate randomness while maintaining some stability.
 * This strategy keeps half of the exponential backoff delay and adds a random component
 * for the other half. The resulting delay is between backoff/2 and backoff, providing
 * a balance between predictability and randomness.
 *
 * @author Nikita Koksharov
 *
 */
public class EqualJitterDelay implements DelayStrategy {

    final Duration baseDelay;
    final Duration maxDelay;

    /**
     * Creates an equal jitter delay strategy with exponential backoff.
     *
     * @param baseDelay the base delay duration for the first retry attempt
     * @param maxDelay the maximum delay duration to cap the exponential growth
     */
    public EqualJitterDelay(Duration baseDelay, Duration maxDelay) {
        Objects.requireNonNull(baseDelay);
        Objects.requireNonNull(maxDelay);

        this.baseDelay = baseDelay;
        this.maxDelay = maxDelay;
    }

    @Override
    public Duration calcDelay(int attempt) {
        long exponentialDelayMs = Math.min(
                baseDelay.toMillis() * (1L << attempt),
                maxDelay.toMillis()
        );

        long halfDelay = exponentialDelayMs / 2;
        long randomComponent = 0;
        if (halfDelay != 0) {
            randomComponent = ThreadLocalRandom.current().nextLong(0, halfDelay + 1);
        }

        return Duration.ofMillis(halfDelay + randomComponent);
    }



    public Duration getBaseDelay() {
        return baseDelay;
    }

    public Duration getMaxDelay() {
        return maxDelay;
    }
}
