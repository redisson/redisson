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
package org.redisson.api.keys;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Arguments object.
 *
 * @author seakider
 * @author Nikita Koksharov
 *
 */
public interface SetArgs {

    /**
     * Defines entries to set
     *
     * @param values entries map to set
     * @return arguments object
     */
    static SetArgs entries(Map<String, ?> values) {
        return new SetParams(values);
    }

    /**
     * Defines retain the time to live associated with the keys
     *
     * @return SetArgs object
     */
    SetArgs keepTTL();

    /**
     * Defines the specified expiration time.
     *
     * @param ttl
     * @return SetArgs object
     */
    SetArgs timeToLive(Duration ttl);

    /**
     * Defines the specified Unix time at which the key(s) will expire.
     *
     * @param time expire date
     * @return SetArgs object
     */
    SetArgs expireAt(Instant time);

}
