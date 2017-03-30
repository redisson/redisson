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
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

import org.redisson.api.mapreduce.RMapReduce;

/**
 * Distributed and concurrent implementation of {@link java.util.concurrent.ConcurrentMap}
 * and {@link java.util.Map}
 *
 * This map doesn't allow to store <code>null</code> as key or value.
 *
 * @author Nikita Koksharov
 *
 * @param <K> map key
 * @param <V> value
 */
public interface RMap<K, V> extends ConcurrentMap<K, V>, RExpirable, RMapAsync<K, V> {

    /**
     * Returns <code>RMapReduce</code> object associated with this map
     * 
     * @param <KOut> output key
     * @param <VOut> output value
     * @return MapReduce instance
     */
    <KOut, VOut> RMapReduce<K, V, KOut, VOut> mapReduce();
    
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
     * Returns size of value mapped by key in bytes
     * 
     * @param key - map key
     * @return size of value
     */
    int valueSize(K key);
    
    /**
     * Atomically adds the given <code>delta</code> to the current value
     * by mapped <code>key</code>.
     *
     * Works only for <b>numeric</b> values!
     *
     * @param key - map key
     * @param delta the value to add
     * @return the updated value
     */
    V addAndGet(K key, Number delta);

    /**
     * Gets a map slice contains the mappings with defined <code>keys</code>
     * by one operation. This operation <b>NOT</b> traverses all map entries
     * like any other <code>filter*</code> method, so works faster.
     *
     * The returned map is <b>NOT</b> backed by the original map.
     *
     * @param keys - map keys
     * @return Map object
     */
    Map<K, V> getAll(Set<K> keys);

    /**
     * Removes <code>keys</code> from map by one operation
     *
     * Works faster than <code>RMap.remove</code> but not returning
     * the value associated with <code>key</code>
     *
     * @param keys - map keys
     * @return the number of keys that were removed from the hash, not including specified but non existing keys
     */
    long fastRemove(K ... keys);

    /**
     * Associates the specified <code>value</code> with the specified <code>key</code>.
     *
     * Works faster than <code>RMap.put</code> but not returning
     * the previous value associated with <code>key</code>
     *
     * @param key - map key
     * @param value - map value
     * @return <code>true</code> if key is a new key in the hash and value was set.
     *         <code>false</code> if key already exists in the hash and the value was updated.
     */
    boolean fastPut(K key, V value);

    boolean fastPutIfAbsent(K key, V value);

    /**
     * Read all keys at once
     *
     * @return keys
     */
    Set<K> readAllKeySet();

    /**
     * Read all values at once
     *
     * @return values
     */
    Collection<V> readAllValues();

    /**
     * Read all map entries at once
     *
     * @return entries
     */
    Set<Entry<K, V>> readAllEntrySet();

    /**
     * Read all map as local instance at once
     *
     * @return map
     */
    Map<K, V> readAllMap();
    
    /**
     * Returns key set. 
     * This method <b>DOESN'T</b> fetch all of them as {@link #readAllKeySet()} does.
     */
    @Override
    Set<K> keySet();

    /**
     * Returns values collections. 
     * This method <b>DOESN'T</b> fetch all of them as {@link #readAllValues()} does.
     */
    @Override
    Collection<V> values();

    /**
     * Returns values collections. 
     * This method <b>DOESN'T</b> fetch all of them as {@link #readAllEntrySet()} does.
     */
    @Override
    Set<java.util.Map.Entry<K, V>> entrySet();
    
}
