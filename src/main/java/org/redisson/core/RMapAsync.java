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
package org.redisson.core;

import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import io.netty.util.concurrent.Future;

/**
 * Async map functions
 *
 * @author Nikita Koksharov
 *
 * @param <K> key
 * @param <V> value
 */
public interface RMapAsync<K, V> extends RExpirableAsync {

    Future<Map<K, V>> getAllAsync(Set<K> keys);

    Future<Void> putAllAsync(Map<? extends K, ? extends V> map);

    Future<V> addAndGetAsync(K key, Number value);

    Future<Boolean> containsValueAsync(Object value);

    Future<Boolean> containsKeyAsync(Object key);

    Future<Integer> sizeAsync();

    /**
     * Removes <code>keys</code> from map by one operation in async manner
     *
     * Works faster than <code>RMap.removeAsync</code> but doesn't return
     * the value associated with <code>key</code>
     *
     * @param keys
     * @return the number of keys that were removed from the hash, not including specified but non existing keys
     */
    Future<Long> fastRemoveAsync(K ... keys);

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
    Future<Boolean> fastPutAsync(K key, V value);

    Future<Boolean> fastPutIfAbsentAsync(K key, V value);

    /**
     * Read all keys at once
     *
     * @return
     */
    Future<Set<K>> readAllKeySetAsync();

    /**
     * Read all values at once
     *
     * @return
     */
    Future<Collection<V>> readAllValuesAsync();

    /**
     * Read all map entries at once
     *
     * @return
     */
    Future<Set<Entry<K, V>>> readAllEntrySetAsync();

    Future<V> getAsync(K key);

    Future<V> putAsync(K key, V value);

    Future<V> removeAsync(K key);

    Future<V> replaceAsync(K key, V value);

    Future<Boolean> replaceAsync(K key, V oldValue, V newValue);

    Future<Long> removeAsync(Object key, Object value);

    Future<V> putIfAbsentAsync(K key, V value);

}
