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

import org.reactivestreams.Publisher;

public interface RKeysReactive {

    /**
     * Load keys in incrementally iterate mode.
     *
     * Uses <code>SCAN</code> Redis command.
     *
     * @return all keys
     */
    Publisher<String> getKeys();

    /**
     * Find keys by pattern and load it in incrementally iterate mode.
     *
     * Uses <code>SCAN</code> Redis command.
     *
     *  Supported glob-style patterns:
     *    h?llo subscribes to hello, hallo and hxllo
     *    h*llo subscribes to hllo and heeeello
     *    h[ae]llo subscribes to hello and hallo, but not hillo
     *
     * @param pattern - match pattern
     * @return keys
     */
    Publisher<String> getKeysByPattern(String pattern);

    /**
     * Get hash slot identifier for key.
     * Available for cluster nodes only.
     *
     * Uses <code>KEYSLOT</code> Redis command.
     *
     * @param key - name of key
     * @return slot number
     */
    Publisher<Integer> getSlot(String key);

    /**
     * Find keys by key search pattern by one Redis call.
     *
     * Uses <code>KEYS</code> Redis command.
     *
     *  Supported glob-style patterns:
     *    h?llo subscribes to hello, hallo and hxllo
     *    h*llo subscribes to hllo and heeeello
     *    h[ae]llo subscribes to hello and hallo, but not hillo
     *
     * @param pattern - match pattern
     * @return collection of keys
     */
    Publisher<Collection<String>> findKeysByPattern(String pattern);

    /**
     * Get random key
     *
     * Uses <code>RANDOM_KEY</code> Redis command.
     *
     * @return random key
     */
    Publisher<String> randomKey();

    /**
     * Delete multiple objects by a key pattern.
     *
     * Uses Lua script.
     *
     *  Supported glob-style patterns:
     *    h?llo subscribes to hello, hallo and hxllo
     *    h*llo subscribes to hllo and heeeello
     *    h[ae]llo subscribes to hello and hallo, but not hillo
     *
     * @param pattern - match pattern
     * @return deleted objects amount
     */
    Publisher<Long> deleteByPattern(String pattern);

    /**
     * Delete multiple objects by name.
     *
     * Uses <code>DEL</code> Redis command.
     *
     * @param keys - object names
     * @return deleted objects amount
     */
    Publisher<Long> delete(String ... keys);

    /**
     * Delete all the keys of the currently selected database
     *
     * Uses <code>FLUSHDB</code> Redis command.
     * 
     * @return void
     */
    Publisher<Void> flushdb();

    /**
     * Delete all the keys of all the existing databases
     *
     * Uses <code>FLUSHALL</code> Redis command.
     *
     * @return void
     */
    Publisher<Void> flushall();

}
