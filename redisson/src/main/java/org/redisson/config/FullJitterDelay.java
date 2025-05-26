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
 * Full jitter strategy that applies complete randomization to the exponential backoff delay.
 *
 * @author Nikita Koksharov
 *
 */
public class FullJitterDelay implements DelayStrategy {

    private final Duration baseDelay;
    private final Duration maxDelay;

    /**
     * Creates a full jitter delay strategy with exponential backoff.
     *
     * @param baseDelay the base delay duration for the first retry attempt
     * @param maxDelay the maximum delay duration to cap the exponential growth
     */
    public FullJitterDelay(Duration baseDelay, Duration maxDelay) {
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

        long jitteredDelayMs = 0;
        if (exponentialDelayMs != 0) {
            jitteredDelayMs = ThreadLocalRandom.current().nextLong(0, exponentialDelayMs + 1);
        }

        return Duration.ofMillis(jitteredDelayMs);
    }
}
