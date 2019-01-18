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
package org.redisson.api;

import java.util.Collection;
import java.util.Set;
/**
 * Base asynchronous MultiMap interface. A collection that maps multiple values per one key.
 *
 * @author Nikita Koksharov
 *
 * @param <K> key
 * @param <V> value
 */

public interface RMultimapAsync<K, V> extends RExpirableAsync {

    /**
     * Returns the number of key-value pairs in this multimap.
     *
     * @return size of multimap
     */
    RFuture<Integer> sizeAsync();

    /**
     * Returns {@code true} if this multimap contains at least one key-value pair
     * with the key {@code key}.
     * 
     * @param key - map key
     * @return <code>true</code> if contains a key
     */
    RFuture<Boolean> containsKeyAsync(Object key);

    /**
     * Returns {@code true} if this multimap contains at least one key-value pair
     * with the value {@code value}.
     * 
     * @param value - map value
     * @return <code>true</code> if contains a value
     */
    RFuture<Boolean> containsValueAsync(Object value);

    /**
     * Returns {@code true} if this multimap contains at least one key-value pair
     * with the key {@code key} and the value {@code value}.
     * 
     * @param key - map key
     * @param value - map value
     * @return <code>true</code> if contains an entry
     */
    RFuture<Boolean> containsEntryAsync(Object key, Object value);

    /**
     * Stores a key-value pair in this multimap.
     *
     * <p>Some multimap implementations allow duplicate key-value pairs, in which
     * case {@code put} always adds a new key-value pair and increases the
     * multimap size by 1. Other implementations prohibit duplicates, and storing
     * a key-value pair that's already in the multimap has no effect.
     *
     * @param key - map key
     * @param value - map value
     * @return {@code true} if the method increased the size of the multimap, or
     *     {@code false} if the multimap already contained the key-value pair and
     *     doesn't allow duplicates
     */
    RFuture<Boolean> putAsync(K key, V value);

    /**
     * Removes a single key-value pair with the key {@code key} and the value
     * {@code value} from this multimap, if such exists. If multiple key-value
     * pairs in the multimap fit this description, which one is removed is
     * unspecified.
     *
     * @param key - map key
     * @param value - map value
     * @return {@code true} if the multimap changed
     */
    RFuture<Boolean> removeAsync(Object key, Object value);

    // Bulk Operations

    /**
     * Stores a key-value pair in this multimap for each of {@code values}, all
     * using the same key, {@code key}. Equivalent to (but expected to be more
     * efficient than): <pre>   {@code
     *
     *   for (V value : values) {
     *     put(key, value);
     *   }}</pre>
     *
     * <p>In particular, this is a no-op if {@code values} is empty.
     *
     * @param key - map key
     * @param values - map values
     * @return {@code true} if the multimap changed
     */
    RFuture<Boolean> putAllAsync(K key, Iterable<? extends V> values);

    /**
     * Stores a collection of values with the same key, replacing any existing
     * values for that key.
     *
     * <p>If {@code values} is empty, this is equivalent to
     * {@link #removeAllAsync(Object)}.
     *
     * @param key - map key
     * @param values - map values
     * @return the collection of replaced values, or an empty collection if no
     *     values were previously associated with the key. The collection
     *     <i>may</i> be modifiable, but updating it will have no effect on the
     *     multimap.
     */
    RFuture<Collection<V>> replaceValuesAsync(K key, Iterable<? extends V> values);

    /**
     * Removes all values associated with the key {@code key}.
     *
     * <p>Once this method returns, {@code key} will not be mapped to any values.
     *
     * @param key - map key
     * @return the values that were removed (possibly empty). The returned
     *     collection <i>may</i> be modifiable, but updating it will have no
     *     effect on the multimap.
     */
    RFuture<Collection<V>> removeAllAsync(Object key);

    RFuture<Collection<V>> getAllAsync(K key);
    
    
    /**
     * Returns the number of key-value pairs in this multimap.
     *
     * @return keys amount
     */
    RFuture<Integer> keySizeAsync();

    /**
     * Removes <code>keys</code> from map by one operation
     *
     * Works faster than <code>RMultimap.remove</code> but not returning
     * the value associated with <code>key</code>
     *
     * @param keys - map keys
     * @return the number of keys that were removed from the hash, not including specified but non existing keys
     */
    RFuture<Long> fastRemoveAsync(K ... keys);

    /**
     * Read all keys at once
     *
     * @return keys
     */
    RFuture<Set<K>> readAllKeySetAsync();

}
