/**
 * Copyright 2016 Nikita Koksharov
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

import java.util.Collection;

public interface RKeysAsync {

    /**
     * Get Redis object type by key
     * 
     * @param key - name of key
     * @return type of key
     */
    RFuture<RType> getTypeAsync(String key);
    
    /**
     * Get hash slot identifier for key in async mode.
     * Available for cluster nodes only
     *
     * @param key - name of key
     * @return slot
     */
    RFuture<Integer> getSlotAsync(String key);

    /**
     * Get random key in async mode
     *
     * @return random key
     */
    RFuture<String> randomKeyAsync();

    /**
     * Find keys by key search pattern in async mode
     *
     *  Supported glob-style patterns:
     *    h?llo subscribes to hello, hallo and hxllo
     *    h*llo subscribes to hllo and heeeello
     *    h[ae]llo subscribes to hello and hallo, but not hillo
     *
     * @param pattern - match pattern
     * @return collections of keys
     */
    RFuture<Collection<String>> findKeysByPatternAsync(String pattern);

    /**
     * Delete multiple objects by a key pattern.
     * <p>
     * Method executes in <b>NON atomic way</b> in cluster mode due to lua script limitations.
     * <p>
     *  Supported glob-style patterns:
     *    h?llo subscribes to hello, hallo and hxllo
     *    h*llo subscribes to hllo and heeeello
     *    h[ae]llo subscribes to hello and hallo, but not hillo
     *
     * @param pattern - match pattern
     * @return number of removed keys
     */
    RFuture<Long> deleteByPatternAsync(String pattern);

    /**
     * Delete multiple objects by name
     *
     * @param keys - object names
     * @return number of removed keys
     */
    RFuture<Long> deleteAsync(String ... keys);

    /**
     * Returns the number of keys in the currently-selected database in async mode
     *
     * @return number of keys
     */
    RFuture<Long> countAsync();

    /**
     * Delete all keys of currently selected database
     * @return void
     */
    RFuture<Void> flushdbAsync();

    /**
     * Delete all keys of all existing databases
     * @return void
     */
    RFuture<Void> flushallAsync();

}
