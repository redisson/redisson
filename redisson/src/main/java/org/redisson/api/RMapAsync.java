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
import java.util.Map.Entry;

import org.redisson.api.map.MapLoader;
import org.redisson.api.map.MapWriter;

import java.util.Set;

/**
 * Distributed async implementation of {@link Map}.
 * 
 * This map doesn't allow to store <code>null</code> as key or value.
 *
 * @author Nikita Koksharov
 *
 * @param <K> key
 * @param <V> value
 */
public interface RMapAsync<K, V> extends RExpirableAsync {

    /**
     * Loads all map entries to this Redis map using {@link org.redisson.api.map.MapLoader}.
     * 
     * @param replaceExistingValues - <code>true</code> if existed values should be replaced, <code>false</code> otherwise.  
     * @param parallelism - parallelism level, used to increase speed of process execution
     * @return void
     */
    RFuture<Void> loadAllAsync(boolean replaceExistingValues, int parallelism);
    
    /**
     * Loads map entries using {@link org.redisson.api.map.MapLoader} whose keys are listed in defined <code>keys</code> parameter.
     * 
     * @param keys - map keys
     * @param replaceExistingValues - <code>true</code> if existed values should be replaced, <code>false</code> otherwise.
     * @param parallelism - parallelism level, used to increase speed of process execution
     * @return void
     */
    RFuture<Void> loadAllAsync(Set<? extends K> keys, boolean replaceExistingValues, int parallelism);
    
    /**
     * Returns size of value mapped by key in bytes
     * 
     * @param key - map key
     * @return size of value
     */
    RFuture<Integer> valueSizeAsync(K key);
    
    /**
     * Gets a map slice contained the mappings with defined <code>keys</code>
     * by one operation.
     * <p>
     * If map doesn't contain value/values for specified key/keys and {@link MapLoader} is defined 
     * then value/values will be loaded in read-through mode. 
     * <p>
     * The returned map is <b>NOT</b> backed by the original map.
     *
     * @param keys - map keys
     * @return Map slice
     */
    RFuture<Map<K, V>> getAllAsync(Set<K> keys);

    /**
     * Associates the specified <code>value</code> with the specified <code>key</code>
     * in batch.
     * <p>
     * If {@link MapWriter} is defined then new map entries are stored in write-through mode. 
     *
     * @param map mappings to be stored in this map
     * @return void
     */
    RFuture<Void> putAllAsync(Map<? extends K, ? extends V> map);

    /**
     * Associates the specified <code>value</code> with the specified <code>key</code>
     * in batch. Batch inserted by chunks limited by <code>batchSize</code> amount 
     * to avoid OOM and/or Redis response timeout error for map with big size. 
     * <p>
     * If {@link MapWriter} is defined then new map entries are stored in write-through mode. 
     *
     * @param map mappings to be stored in this map
     * @param batchSize - map chunk size
     * @return void
     */
    RFuture<Void> putAllAsync(Map<? extends K, ? extends V> map, int batchSize);
    
    /**
     * Atomically adds the given <code>delta</code> to the current value
     * by mapped <code>key</code>.
     *
     * Works only for <b>numeric</b> values!
     *
     * @param key - map key
     * @param value - delta the value to add
     * @return the updated value
     */
    RFuture<V> addAndGetAsync(K key, Number value);

    RFuture<Boolean> containsValueAsync(Object value);

    RFuture<Boolean> containsKeyAsync(Object key);

    /**
     * Returns size of this map
     * 
     * @return size
     */
    RFuture<Integer> sizeAsync();

    /**
     * Removes <code>keys</code> from map by one operation in async manner.
     * <p>
     * Works faster than <code>{@link RMap#removeAsync(Object, Object)}</code> but doesn't return
     * the value associated with <code>key</code>.
     * <p>
     * If {@link MapWriter} is defined then <code>keys</code>are deleted in write-through mode.
     *
     * @param keys - map keys
     * @return the number of keys that were removed from the hash, not including specified but non existing keys
     */
    RFuture<Long> fastRemoveAsync(K... keys);

    /**
     * Associates the specified <code>value</code> with the specified <code>key</code>
     * in async manner.
     * <p>
     * Works faster than <code>{@link RMap#putAsync(Object, Object)}</code> but not returning
     * the previous value associated with <code>key</code>
     * <p>
     * If {@link MapWriter} is defined then new map entry is stored in write-through mode.
     *
     * @param key - map key
     * @param value - map value
     * @return <code>true</code> if key is a new one in the hash and value was set.
     *         <code>false</code> if key already exists in the hash and the value was updated.
     */
    RFuture<Boolean> fastPutAsync(K key, V value);

    /**
     * Replaces previous value with a new <code>value</code> associated with the <code>key</code>.
     * <p>
     * Works faster than <code>{@link RMap#replaceAsync(Object, Object)}</code> but not returning
     * the previous value associated with <code>key</code>
     * <p>
     * If {@link MapWriter} is defined then new map entry is stored in write-through mode.
     *
     * @param key - map key
     * @param value - map value
     * @return <code>true</code> if key exists and value was updated.
     *         <code>false</code> if key doesn't exists and value wasn't updated.
     */
    RFuture<Boolean> fastReplaceAsync(K key, V value);
    
    /**
     * Associates the specified <code>value</code> with the specified <code>key</code>
     * only if there is no any association with specified<code>key</code>.
     * <p>
     * Works faster than <code>{@link RMap#putIfAbsentAsync(Object, Object)}</code> but not returning
     * the previous value associated with <code>key</code>
     * <p>
     * If {@link MapWriter} is defined then new map entry is stored in write-through mode.
     *
     * @param key - map key
     * @param value - map value
     * @return <code>true</code> if key is a new one in the hash and value was set.
     *         <code>false</code> if key already exists in the hash and change hasn't been made.
     */
    RFuture<Boolean> fastPutIfAbsentAsync(K key, V value);

    /**
     * Read all keys at once
     *
     * @return keys
     */
    RFuture<Set<K>> readAllKeySetAsync();

    /**
     * Read all values at once
     *
     * @return values
     */
    RFuture<Collection<V>> readAllValuesAsync();

    /**
     * Read all map entries at once
     *
     * @return entries
     */
    RFuture<Set<Entry<K, V>>> readAllEntrySetAsync();

    /**
     * Read all map as local instance at once
     *
     * @return map
     */
    RFuture<Map<K, V>> readAllMapAsync();

    /**
     * Returns the value to which the specified key is mapped,
     * or {@code null} if this map contains no mapping for the key.
     * <p>
     * If map doesn't contain value for specified key and {@link MapLoader} is defined 
     * then value will be loaded in read-through mode. 
     *
     * @param key the key whose associated value is to be returned
     * @return the value to which the specified key is mapped, or
     *         {@code null} if this map contains no mapping for the key
     */
    RFuture<V> getAsync(K key);

    /**
     * Associates the specified <code>value</code> with the specified <code>key</code>
     * in async manner.
     * <p>
     * If {@link MapWriter} is defined then new map entry is stored in write-through mode.
     *
     * @param key - map key
     * @param value - map value
     * @return previous associated value
     */
    RFuture<V> putAsync(K key, V value);

    /**
     * Removes <code>key</code> from map and returns associated value in async manner.
     * <p>
     * If {@link MapWriter} is defined then <code>key</code>is deleted in write-through mode.
     *
     * @param key - map key
     * @return deleted value or <code>null</code> if there wasn't any association
     */
    RFuture<V> removeAsync(K key);

    /**
     * Replaces previous value with a new <code>value</code> associated with the <code>key</code>.
     * If there wasn't any association before then method returns <code>null</code>.
     * <p>
     * If {@link MapWriter} is defined then new <code>value</code>is written in write-through mode.
     *
     * @param key - map key
     * @param value - map value
     * @return previous associated value 
     *         or <code>null</code> if there wasn't any association and change hasn't been made
     */
    RFuture<V> replaceAsync(K key, V value);

    /**
     * Replaces previous <code>oldValue</code> with a <code>newValue</code> associated with the <code>key</code>.
     * If previous value doesn't exist or equal to <code>oldValue</code> then method returns <code>false</code>.
     * <p>
     * If {@link MapWriter} is defined then <code>newValue</code>is written in write-through mode.
     *
     * @param key - map key
     * @param oldValue - map old value
     * @param newValue - map new value
     * @return <code>true</code> if value has been replaced otherwise <code>false</code>.
     */
    RFuture<Boolean> replaceAsync(K key, V oldValue, V newValue);

    /**
     * Removes <code>key</code> from map only if it associated with <code>value</code>.
     * <p>
     * If {@link MapWriter} is defined then <code>key</code>is deleted in write-through mode.
     *
     * @param key - map key
     * @param value - map value
     * @return <code>true</code> if map entry has been replaced otherwise <code>false</code>.
     */
    RFuture<Boolean> removeAsync(Object key, Object value);

    /**
     * Associates the specified <code>value</code> with the specified <code>key</code>
     * only if there is no any association with specified<code>key</code>.
     * <p>
     * If {@link MapWriter} is defined then new map entry is stored in write-through mode.
     *
     * @param key - map key
     * @param value - map value
     * @return <code>null</code> if key is a new one in the hash and value was set.
     *         Previous value if key already exists in the hash and change hasn't been made.
     */
    RFuture<V> putIfAbsentAsync(K key, V value);

}
