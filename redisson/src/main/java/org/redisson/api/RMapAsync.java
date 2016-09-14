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
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Async map functions
 *
 * @author Nikita Koksharov
 *
 * @param <K> key
 * @param <V> value
 */
public interface RMapAsync<K, V> extends RExpirableAsync {

    RFuture<Map<K, V>> getAllAsync(Set<K> keys);

    RFuture<Void> putAllAsync(Map<? extends K, ? extends V> map);

    RFuture<V> addAndGetAsync(K key, Number value);

    RFuture<Boolean> containsValueAsync(Object value);

    RFuture<Boolean> containsKeyAsync(Object key);

    RFuture<Integer> sizeAsync();

    /**
     * Removes <code>keys</code> from map by one operation in async manner
     *
     * Works faster than <code>RMap.removeAsync</code> but doesn't return
     * the value associated with <code>key</code>
     *
     * @param keys
     * @return the number of keys that were removed from the hash, not including specified but non existing keys
     */
    RFuture<Long> fastRemoveAsync(K ... keys);

    /**
     * Associates the specified <code>value</code> with the specified <code>key</code>
     * in async manner.
     *
     * Works faster than <code>RMap.putAsync</code> but not returning
     * the previous value associated with <code>key</code>
     *
     * @param key
     * @param value
     * @return <code>true</code> if key is a new key in the hash and value was set.
     *         <code>false</code> if key already exists in the hash and the value was updated.
     */
    RFuture<Boolean> fastPutAsync(K key, V value);

    RFuture<Boolean> fastPutIfAbsentAsync(K key, V value);

    /**
     * Read all keys at once
     *
     * @return
     */
    RFuture<Set<K>> readAllKeySetAsync();

    /**
     * Read all values at once
     *
     * @return
     */
    RFuture<Collection<V>> readAllValuesAsync();

    /**
     * Read all map entries at once
     *
     * @return
     */
    RFuture<Set<Entry<K, V>>> readAllEntrySetAsync();

    RFuture<V> getAsync(K key);

    RFuture<V> putAsync(K key, V value);

    RFuture<V> removeAsync(K key);

    RFuture<V> replaceAsync(K key, V value);

    RFuture<Boolean> replaceAsync(K key, V oldValue, V newValue);

    RFuture<Boolean> removeAsync(Object key, Object value);

    RFuture<V> putIfAbsentAsync(K key, V value);

}
