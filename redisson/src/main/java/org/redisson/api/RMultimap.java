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
     * Returns <code>RCountDownLatch</code> instance associated with key
     * 
     * @param key - map key
     * @return countdownlatch
     */
    RCountDownLatch getCountDownLatch(K key);
    
    /**
     * Returns <code>RPermitExpirableSemaphore</code> instance associated with key
     * 
     * @param key - map key
     * @return permitExpirableSemaphore
     */
    RPermitExpirableSemaphore getPermitExpirableSemaphore(K key);

    /**
     * Returns <code>RSemaphore</code> instance associated with key
     * 
     * @param key - map key
     * @return semaphore
     */
    RSemaphore getSemaphore(K key);
    
    /**
     * Returns <code>RLock</code> instance associated with key
     * 
     * @param key - map key
     * @return fairlock
     */
    RLock getFairLock(K key);

    /**
     * Returns <code>RReadWriteLock</code> instance associated with key
     * 
     * @param key - map key
     * @return readWriteLock
     */
    RReadWriteLock getReadWriteLock(K key);
    
    /**
     * Returns <code>RLock</code> instance associated with key
     * 
     * @param key - map key
     * @return lock
     */
    RLock getLock(K key);
    
    /**
     * Returns the number of key-value pairs in this multimap.
     *
     * @return size of multimap
     */
    int size();

    /**
     * Check is map empty
     *
     * @return <code>true</code> if empty
     */
    boolean isEmpty();

    /**
     * Returns {@code true} if this multimap contains at least one key-value pair
     * with the key {@code key}.
     * 
     * @param key - map key
     * @return <code>true</code> if contains a key
     */
    boolean containsKey(Object key);

    /**
     * Returns {@code true} if this multimap contains at least one key-value pair
     * with the value {@code value}.
     * 
     * @param value - map value
     * @return <code>true</code> if contains a value
     */
    boolean containsValue(Object value);

    /**
     * Returns {@code true} if this multimap contains at least one key-value pair
     * with the key {@code key} and the value {@code value}.
     * 
     * @param key - map key
     * @param value - map value
     * @return <code>true</code> if contains an entry
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
     * @param key - map key
     * @param value - map value
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
     * @param key - map key
     * @param value - map value
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
     * @param key - map key
     * @param values - map values
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
     * @param key - map key
     * @param values - map values
     * @return the collection of replaced values, or an empty collection if no
     *     values were previously associated with the key. The collection
     *     <i>may</i> be modifiable, but updating it will have no effect on the
     *     multimap.
     */
    Collection<V> replaceValues(K key, Iterable<? extends V> values);

    /**
     * Removes all values associated with the key {@code key}.
     *
     * <p>Once this method returns, {@code key} will not be mapped to any values
     * <p>Use {@link RMultimap#fastRemove} if values are not needed.</p>
     * 
     * @param key - map key
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
     * 
     * @param key - map key
     * @return collection of values
     */
    Collection<V> get(K key);

    /**
     * Returns all elements at once. Result collection is <b>NOT</b> backed by map,
     * so changes are not reflected in map.
     *
     * @param key - map key
     * @return collection of values 
     */
    Collection<V> getAll(K key);

    /**
     * Returns a view collection of all <i>distinct</i> keys contained in this
     * multimap. Note that the key set contains a key if and only if this multimap
     * maps that key to at least one value.
     *
     * <p>Changes to the returned set will update the underlying multimap, and
     * vice versa. However, <i>adding</i> to the returned set is not possible.
     * 
     * @return set of keys
     */
    Set<K> keySet();
    
    /**
     *  Returns the count of distinct keys in this multimap.
     *  
     *  @return keys amount
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
     * 
     * @return collection of values
     */
    Collection<V> values();

    /**
     * Returns a view collection of all key-value pairs contained in this
     * multimap, as {@link Map.Entry} instances.
     *
     * <p>Changes to the returned collection or the entries it contains will
     * update the underlying multimap, and vice versa. However, <i>adding</i> to
     * the returned collection is not possible.
     * 
     * @return collection of entries
     */
    Collection<Map.Entry<K, V>> entries();

    /**
     * Removes <code>keys</code> from map by one operation
     *
     * Works faster than <code>RMultimap.remove</code> but not returning
     * the value associated with <code>key</code>
     *
     * @param keys - map keys
     * @return the number of keys that were removed from the hash, not including specified but non existing keys
     */
    long fastRemove(K... keys);

    /**
     * Read all keys at once
     *
     * @return keys
     */
    Set<K> readAllKeySet();

}
