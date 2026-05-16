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
package org.redisson.api.atomic;

import java.time.Duration;
import java.time.Instant;

/**
 * Base arguments for extended atomic increment operations.
 *
 * @author lamnt2008
 *
 * @param <T> arguments type
 */
public interface BaseIncrementArgs<T> {

    /**
     * Defines overflow policy used if the increment result is out of bounds.
     */
    enum OverflowPolicy {

        /**
         * Throws an error and leaves the value unchanged.
         */
        FAIL,

        /**
         * Caps the value at the lower or upper bound.
         */
        SAT,

        /**
         * Leaves the value and its expiration unchanged.
         */
        REJECT

    }

    /**
     * Defines overflow policy used if the increment result is out of bounds.
     *
     * @param overflowPolicy overflow policy
     * @return arguments object
     */
    T overflow(OverflowPolicy overflowPolicy);

    /**
     * Defines the specified expiration time.
     *
     * @param ttl time to live duration
     * @return arguments object
     */
    T timeToLive(Duration ttl);

    /**
     * Defines the specified Unix time at which the key will expire.
     *
     * @param time expire date
     * @return arguments object
     */
    T expireAt(Instant time);

    /**
     * Defines removal of the existing expiration.
     *
     * @return arguments object
     */
    T persist();

    /**
     * Defines expiration setting only if the key doesn't have an expiration.
     *
     * @return arguments object
     */
    T expireIfNotSet();

}
