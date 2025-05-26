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
 * Decorrelated jitter strategy that increases delay exponentially while introducing
 * randomness influenced by the previous backoff duration. This approach helps avoid
 * exponential growth that could lead to excessively long delays while ensuring each
 * backoff duration is decorrelated from its predecessor.
 *
 * @author Nikita Koksharov
 *
 */
public class DecorrelatedJitterDelay implements DelayStrategy {

    private final Duration minDelay;
    private final Duration maxDelay;
    private Duration previousDelay;

    /**
     * Creates a decorrelated jitter delay strategy.
     *
     * @param minDelay the minimum delay duration (base delay)
     * @param maxDelay the maximum allowable delay duration
     */
    public DecorrelatedJitterDelay(Duration minDelay, Duration maxDelay) {
        Objects.requireNonNull(minDelay);
        Objects.requireNonNull(maxDelay);

        this.minDelay = minDelay;
        this.maxDelay = maxDelay;
        this.previousDelay = Duration.ZERO;
    }

    @Override
    public Duration calcDelay(int attempt) {
        long previousMs;
        if (previousDelay.isZero()) {
            previousMs = minDelay.toMillis();
        } else {
            previousMs = previousDelay.toMillis();
        }

        long randomRange = previousMs * 3;
        long randomComponent = 0;
        if (randomRange != 0) {
            randomComponent = ThreadLocalRandom.current().nextLong(0, randomRange);
        }

        long newDelayMs = Math.min(
                minDelay.toMillis() + randomComponent,
                maxDelay.toMillis()
        );

        previousDelay = Duration.ofMillis(newDelayMs);
        return previousDelay;
    }
}
