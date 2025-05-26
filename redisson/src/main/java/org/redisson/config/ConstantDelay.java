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

/**
 * A constant delay strategy that returns the same delay duration for every retry attempt.
 *
 * @author Nikita Koksharov
 *
 */
public class ConstantDelay implements DelayStrategy {

    private final Duration delay;

    /**
     * Creates a constant delay strategy with the specified delay duration.
     *
     * @param delay the fixed delay duration to use between retry attempts
     */
    public ConstantDelay(Duration delay) {
        this.delay = delay;
    }

    @Override
    public Duration calcDelay(int attempt) {
        return delay;
    }
}
