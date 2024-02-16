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
package org.redisson.api.stream;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Arguments object for RStream.add() method.
 *
 * @author Nikita Koksharov
 *
 */
public interface StreamAddArgs<K, V> {

    /**
     * Define to not create stream automatically if it doesn't exist.
     *
     * @return arguments object
     */
    StreamAddArgs<K, V> noMakeStream();

    /**
     * Defines strict trimming.
     *
     * @return arguments object
     */
    StreamTrimStrategyArgs<StreamAddArgs<K, V>> trim();

    /**
     * Defines non-strict trimming.
     *
     * @return arguments object
     */
    StreamTrimStrategyArgs<StreamAddArgs<K, V>> trimNonStrict();

    /**
     * Defines entry to add
     *
     * @param k1 - key to add
     * @param v1 - value to add
     * @param <K> - key type
     * @param <V> - value type
     * @return arguments object
     */
    static <K, V> StreamAddArgs<K, V> entry(K k1, V v1) {
        return entries(Collections.singletonMap(k1, v1));
    }

    /**
     * Defines entries to add
     *
     * @param k1 - 1st key to add
     * @param v1 - 1st value to add
     * @param k2 - 2nd key to add
     * @param v2 - 2nd value to add
     * @param <K> - key type
     * @param <V> - value type
     * @return arguments object
     */
    static <K, V> StreamAddArgs<K, V> entries(K k1, V v1, K k2, V v2) {
        Map<K, V> map = new HashMap<>();
        map.put(k1, v1);
        map.put(k2, v2);
        return entries(map);
    }

    /**
     * Defines entries to add
     *
     * @param k1 - 1st key to add
     * @param v1 - 1st value to add
     * @param k2 - 2nd key to add
     * @param v2 - 2nd value to add
     * @param k3 - 3rd key to add
     * @param v3 - 3rd value to add
     * @param <K> - key type
     * @param <V> - value type
     * @return arguments object
     */
    static <K, V> StreamAddArgs<K, V> entries(K k1, V v1, K k2, V v2, K k3, V v3) {
        Map<K, V> map = new HashMap<>();
        map.put(k1, v1);
        map.put(k2, v2);
        map.put(k3, v3);
        return entries(map);
    }

    /**
     * Defines entries to add
     *
     * @param k1 - 1st key to add
     * @param v1 - 1st value to add
     * @param k2 - 2nd key to add
     * @param v2 - 2nd value to add
     * @param k3 - 3rd key to add
     * @param v3 - 3rd value to add
     * @param k4 - 4th key to add
     * @param v4 - 4th key to add
     * @param <K> - key type
     * @param <V> - value type
     * @return arguments object
     */
    static <K, V> StreamAddArgs<K, V> entries(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4) {
        Map<K, V> map = new HashMap<>();
        map.put(k1, v1);
        map.put(k2, v2);
        map.put(k3, v3);
        map.put(k4, v4);
        return entries(map);
    }

    /**
     * Defines entries to add
     *
     * @param k1 - 1st key to add
     * @param v1 - 1st value to add
     * @param k2 - 2nd key to add
     * @param v2 - 2nd value to add
     * @param k3 - 3rd key to add
     * @param v3 - 3rd value to add
     * @param k4 - 4th key to add
     * @param v4 - 4th key to add
     * @param k5 - 5th key to add
     * @param v5 - 5th key to add
     * @param <K> - key type
     * @param <V> - value type
     * @return arguments object
     */
    static <K, V> StreamAddArgs<K, V> entries(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5) {
        Map<K, V> map = new HashMap<>();
        map.put(k1, v1);
        map.put(k2, v2);
        map.put(k3, v3);
        map.put(k4, v4);
        map.put(k5, v5);
        return entries(map);
    }

    /**
     * Defines entries to add
     *
     * @param entries - entries map to add
     * @param <K> - key type
     * @param <V> - value type
     * @return arguments object
     */
    static <K, V> StreamAddArgs<K, V> entries(Map<K, V> entries) {
        return new StreamAddParams<K, V>(entries);
    }

}
