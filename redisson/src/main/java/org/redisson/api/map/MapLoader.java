/**
 * Copyright (c) 2013-2019 Nikita Koksharov
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
package org.redisson.api.map;

import org.redisson.api.RMap;

/**
 * Map loader used for read-through operations or during {@link RMap#loadAll} execution.
 * 
 * @author Nikita Koksharov
 *
 * @param <K> key type
 * @param <V> value type
 */
public interface MapLoader<K, V> {

    /**
     * Loads map value by key.
     * 
     * @param key - map key
     * @return value or <code>null</code> if value doesn't exists
     */
    V load(K key);
    
    /**
     * Loads all keys.
     * 
     * @return Iterable object. It's helpful if all keys don't fit in memory.
     */
    Iterable<K> loadAllKeys();
    
}
