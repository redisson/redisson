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
package org.redisson.api.options;

import org.redisson.api.RKeys;
import org.redisson.api.RType;

/**
 * {@link RKeys#getKeys()} method options
 *
 * @author Nikita Koksharov
 *
 */
public interface KeysScanOptions {

    /**
     * Creates the default options
     *
     * @return options instance
     */
    static KeysScanOptions defaults() {
        return new KeysScanParams();
    }

    /**
     * Defines the total amount of returned keys.
     *
     * @param value total amount of returned keys
     * @return options instance
     */
    KeysScanOptions limit(int value);

    /**
     * Defines the pattern that all keys should match.
     * Supported glob-style patterns:
     *  <p>
     *    h?llo matches hello, hallo and hxllo
     *    <p>
     *    h*llo matches to hllo and heeeello
     *    <p>
     *    h[ae]llo matches to hello and hallo, but not hillo     *
     *
     * @param value key pattern
     * @return options instance
     */
    KeysScanOptions pattern(String value);

    /**
     * Defines the amount of loaded keys per request.
     *
     * @param value amount of loaded keys per request
     * @return options instance
     */
    KeysScanOptions chunkSize(int value);

    /**
     * Defines the type of objects that all keys should match.
     *
     * @param value type of objects
     * @return options instance
     */
    KeysScanOptions type(RType value);

}
