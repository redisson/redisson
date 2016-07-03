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
import java.util.Set;

/**
 * Base Multimap interface. Allows to map multiple values per key.
 *
 * @author Nikita Koksharov
 *
 * @param <K> key
 * @param <V> value
 */
public interface RMultimap<K, V> extends RExpirable, RMultimapAsync<K, V> {

    /**
     * Returns the number of key-value pairs in this multimap.
     *
     * @return
     */
    int size();

    /**
     * Check is map empty
     *
     * @return
     */
    boolean isEmpty();

    /**
     * Returns {@code true} if this multimap contains at least one key-value pair
     * with the key {@code key}.
     */
    boolean containsKey(Object key);

    /**
     * Returns {@code true} if this multimap contains at least one key-value pair
     * with the value {@code value}.
     */
    boolean containsValue(Object value);

    /**
     * Returns {@code true} if this multimap contains at least one key-value pair
     * with the key {@code key} and the value {@code value}.
     */
    boolean containsEntry(Object key, Object value);

    /**
     * Stores a key-value pair in this multimap.
     *
     * <p>Some multimap implementations allow duplicate key-value pairs, in which
     * case {@code put} always adds a new key-value pair and increases the
     * multimap size by 1. Other implementations prohibit duplicates, and storing
     * a key-value pair that's already in the multimap has no effect.
     *
     * @return {@code true} if the method increased the size of the multimap, or
     *     {@code false} if the multimap already contained the key-value pair and
     *     doesn't allow duplicates
     */
    boolean put(K key, V value);

    /**
     * Removes a single key-value pair with the key {@code key} and the value
     * {@code value} from this multimap, if such exists. If multiple key-value
     * pairs in the multimap fit this description, which one is removed is
     * unspecified.
     *
     * @return {@code true} if the multimap changed
     */
    boolean remove(Object key, Object value);

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
     * @return {@code true} if the multimap changed
     */
    boolean putAll(K key, Iterable<? extends V> values);

    /**
     * Stores a collection of values with the same key, replacing any existing
     * values for that key.
     *
     * <p>If {@code values} is empty, this is equivalent to
     * {@link #removeAll(Object) removeAll(key)}.
     *
     * @return the collection of replaced values, or an empty collection if no
     *     values were previously associated with the key. The collection
     *     <i>may</i> be modifiable, but updating it will have no effect on the
     *     multimap.
     */
    Collection<V> replaceValues(K key, Iterable<? extends V> values);

    /**
     * Removes all values associated with the key {@code key}.
     *
     * <p>Once this method returns, {@code key} will not be mapped to any values,
     * so it will not appear in {@link #keySet()}, {@link #asMap()}, or any other
     * views.
     * <p>Use {@link #fastRemove()} if values are not needed.</p>
     *
     * @return the values that were removed (possibly empty). The returned
     *     collection <i>may</i> be modifiable, but updating it will have no
     *     effect on the multimap.
     */
    Collection<V> removeAll(Object key);

    /**
     * Removes all key-value pairs from the multimap, leaving it {@linkplain
     * #isEmpty empty}.
     */
    void clear();

    /**
     * Returns a view collection of the values associated with {@code key} in this
     * multimap, if any. Note that when {@code containsKey(key)} is false, this
     * returns an empty collection, not {@code null}.
     *
     * <p>Changes to the returned collection will update the underlying multimap,
     * and vice versa.
     */
    Collection<V> get(K key);

    /**
     * Returns all elements at once. Result collection is <b>NOT</b> backed by map,
     * so changes are not reflected in map.
     *
     * @param key
     * @return
     */
    Collection<V> getAll(K key);

    /**
     * Returns a view collection of all <i>distinct</i> keys contained in this
     * multimap. Note that the key set contains a key if and only if this multimap
     * maps that key to at least one value.
     *
     * <p>Changes to the returned set will update the underlying multimap, and
     * vice versa. However, <i>adding</i> to the returned set is not possible.
     */
    Set<K> keySet();
    
    /**
     *  Returns the count of distinct keys in this multimap.
     */
    int keySize();

    /**
     * Returns a view collection containing the <i>value</i> from each key-value
     * pair contained in this multimap, without collapsing duplicates (so {@code
     * values().size() == size()}).
     *
     * <p>Changes to the returned collection will update the underlying multimap,
     * and vice versa. However, <i>adding</i> to the returned collection is not
     * possible.
     */
    Collection<V> values();

    /**
     * Returns a view collection of all key-value pairs contained in this
     * multimap, as {@link Map.Entry} instances.
     *
     * <p>Changes to the returned collection or the entries it contains will
     * update the underlying multimap, and vice versa. However, <i>adding</i> to
     * the returned collection is not possible.
     */
    Collection<Map.Entry<K, V>> entries();

    /**
     * Removes <code>keys</code> from map by one operation
     *
     * Works faster than <code>RMultimap.remove</code> but not returning
     * the value associated with <code>key</code>
     *
     * @param keys
     * @return the number of keys that were removed from the hash, not including specified but non existing keys
     */
    long fastRemove(K ... keys);


}
