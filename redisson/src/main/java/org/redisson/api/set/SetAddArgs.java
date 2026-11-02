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
package org.redisson.api.set;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;

/**
 * Arguments for adding values to {@link org.redisson.api.RSetCacheNative}
 * with a shared expiration.
 *
 * @author Nikita Koksharov
 *
 * @param <V> value type
 */
public interface SetAddArgs<V> {

    /**
     * Defines values to add.
     *
     * @param values values to add
     * @param <V> value type
     * @return arguments object
     */
    static <V> SetAddArgs<V> values(Collection<V> values) {
        return new SetAddParams<>(values);
    }

    /**
     * Defines time to live of the values. Values which already exist get
     * the new time to live as well.
     * <p>
     * <code>0</code> stores the values without expiration.
     *
     * @param ttl time to live, not negative
     * @return arguments object
     */
    SetAddArgs<V> timeToLive(Duration ttl);

    /**
     * Defines the date at which the values expire. Values which already exist get
     * the new expiration date as well.
     * <p>
     * A date in the past adds nothing and removes the values which already exist.
     *
     * @param time expiration date
     * @return arguments object
     */
    SetAddArgs<V> expireAt(Instant time);

    /**
     * Keeps the expiration of values which already exist. New values are stored
     * without expiration.
     *
     * @return arguments object
     */
    SetAddArgs<V> keepTTL();

}
