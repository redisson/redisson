/**
 * Copyright 2014 Nikita Koksharov, Nickolay Borbit
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

import io.netty.util.concurrent.Future;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

/**
 * Distributed and concurrent implementation of {@link java.util.concurrent.ConcurrentMap}
 * and {@link java.util.Map}
 *
 * @author Nikita Koksharov
 *
 * @param <K> key
 * @param <V> value
 */
public interface RMap<K, V> extends ConcurrentMap<K, V>, RExpirable {

    /**
     * Atomically adds the given <code>delta</code> to the current value
     * by mapped <code>key</code>.
     *
     * Works only for <b>numeric</b> values!
     *
     * @param key
     * @param delta the value to add
     * @return the updated value
     */
    V addAndGet(K key, V delta);

    /**
     * Gets a map slice contains the mappings with defined <code>keys</code>
     * by one operation. This operation <b>NOT</b> traverses all map entries
     * like any other <code>filter*</code> method, so works faster.
     *
     * The returned map is <b>NOT</b> backed by the original map.
     *
     * @param keys map keys
     * @return
     */
    Map<K, V> getAll(Set<K> keys);

    /**
     * Returns a map slice containing the mappings in whose <code>Map.Entry&lt;K, V&gt; entries</code>
     * satisfy a predicate. This operation traverses all map entries.
     *
     * The returned map is <b>NOT</b> backed by the original map.
     *
     * @param predicate
     * @return
     */
    Map<K, V> filterEntries(Predicate<Map.Entry<K, V>> predicate);

    /**
     * Returns a map slice containing the mappings in whose <code>values</code>
     * satisfy a predicate. Traverses all map entries.
     *
     * The returned map is <b>NOT</b> backed by the original map.
     *
     * @param predicate
     * @return
     */
    Map<K, V> filterValues(Predicate<V> predicate);

    /**
     * Returns a map slice containing the mappings in whose <code>keys</code>
     * satisfy a predicate. Traverses all map entries.
     *
     * The returned map is <b>NOT</b> backed by the original map.
     *
     * @param predicate
     * @return
     */
    Map<K, V> filterKeys(Predicate<K> predicate);

    /**
     * Removes <code>keys</code> from map by one operation
     *
     * Works faster than <code>RMap.remove</code> but not returning
     * the value associated with <code>key</code>
     *
     * @param keys
     * @return the number of keys that were removed from the hash, not including specified but non existing keys
     */
    long fastRemove(K ... keys);

    /**
     * Removes <code>keys</code> from map by one operation in async manner
     *
     * Works faster than <code>RMap.removeAsync</code> but not returning
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

    /**
     * Associates the specified <code>value</code> with the specified <code>key</code>.
     *
     * Works faster than <code>RMap.put</code> but not returning
     * the previous value associated with <code>key</code>
     *
     * @param key
     * @param value
     * @return <code>true</code> if key is a new key in the hash and value was set.
     *         <code>false</code> if key already exists in the hash and the value was updated.
     */
    boolean fastPut(K key, V value);

    Future<V> getAsync(K key);

    Future<V> putAsync(K key, V value);

    Future<V> removeAsync(K key);

    Future<V> replaceAsync(K key, V value);

    Future<V> replaceAsync(K key, V oldValue, V newValue);

    Future<Long> removeAsync(Object key, Object value);

    Future<V> putIfAbsentAsync(K key, V value);

}
