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
import java.util.concurrent.ConcurrentMap;

import org.redisson.api.map.MapLoader;
import org.redisson.api.map.MapWriter;
import org.redisson.api.mapreduce.RMapReduce;

/**
 * Distributed implementation of {@link java.util.concurrent.ConcurrentMap}
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
     * Loads all map entries to this Redis map using {@link org.redisson.api.map.MapLoader}.
     * 
     * @param replaceExistingValues - <code>true</code> if existed values should be replaced, <code>false</code> otherwise.  
     * @param parallelism - parallelism level, used to increase speed of process execution
     */
    void loadAll(boolean replaceExistingValues, int parallelism);
    
    /**
     * Loads map entries using {@link org.redisson.api.map.MapLoader} whose keys are listed in defined <code>keys</code> parameter.
     * 
     * @param keys - map keys
     * @param replaceExistingValues - <code>true</code> if existed values should be replaced, <code>false</code> otherwise.
     * @param parallelism - parallelism level, used to increase speed of process execution
     */
    void loadAll(Set<? extends K> keys, boolean replaceExistingValues, int parallelism);
    
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
    @Override
    V get(Object key);
    
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
    @Override
    V put(K key, V value);
    
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
    @Override
    V putIfAbsent(K key, V value);
    
    /**
     * Returns <code>RMapReduce</code> object associated with this map
     * 
     * @param <KOut> output key
     * @param <VOut> output value
     * @return MapReduce instance
     */
    <KOut, VOut> RMapReduce<K, V, KOut, VOut> mapReduce();

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
     * Removes <code>key</code> from map and returns associated value in async manner.
     * <p>
     * If {@link MapWriter} is defined then <code>key</code>is deleted in write-through mode.
     *
     * @param key - map key
     * @return deleted value, <code>null</code> if there wasn't any association
     */
    @Override
    V remove(Object key);
    
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
    @Override
    V replace(K key, V value);

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
    @Override
    boolean replace(K key, V oldValue, V newValue);

    /**
     * Removes <code>key</code> from map only if it associated with <code>value</code>.
     * <p>
     * If {@link MapWriter} is defined then <code>key</code>is deleted in write-through mode.
     *
     * @param key - map key
     * @param value - map value
     * @return <code>true</code> if map entry has been replaced otherwise <code>false</code>.
     */
    @Override
    boolean remove(Object key, Object value);
    
    /**
     * Associates the specified <code>value</code> with the specified <code>key</code>
     * in batch.
     * <p>
     * If {@link MapWriter} is defined then new map entries will be stored in write-through mode. 
     *
     * @param map mappings to be stored in this map
     */
    @Override
    void putAll(java.util.Map<? extends K, ? extends V> map);
    
    /**
     * Associates the specified <code>value</code> with the specified <code>key</code>
     * in batch. Batch inserted by chunks limited by <code>batchSize</code> amount 
     * to avoid OOM and/or Redis response timeout error for map with big size. 
     * <p>
     * If {@link MapWriter} is defined then new map entries are stored in write-through mode. 
     *
     * @param map mappings to be stored in this map
     * @param batchSize - map chunk size
     */
    void putAll(Map<? extends K, ? extends V> map, int batchSize);
    
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
    Map<K, V> getAll(Set<K> keys);

    /**
     * Removes <code>keys</code> from map by one operation
     * <p>
     * Works faster than <code>{@link #remove(Object)}</code> but not returning
     * the value associated with <code>key</code>
     * <p>
     * If {@link MapWriter} is defined then <code>keys</code>are deleted in write-through mode.
     *
     * @param keys - map keys
     * @return the number of keys that were removed from the hash, not including specified but non existing keys
     */
    long fastRemove(K ... keys);

    /**
     * Associates the specified <code>value</code> with the specified <code>key</code>.
     * <p>
     * Works faster than <code>{@link #put(Object, Object)}</code> but not returning
     * the previous value associated with <code>key</code>
     * <p>
     * If {@link MapWriter} is defined then new map entry is stored in write-through mode.
     *
     * @param key - map key
     * @param value - map value
     * @return <code>true</code> if key is a new key in the hash and value was set.
     *         <code>false</code> if key already exists in the hash and the value was updated.
     */
    boolean fastPut(K key, V value);

    /**
     * Replaces previous value with a new <code>value</code> associated with the <code>key</code>.
     * <p>
     * Works faster than <code>{@link #replace(Object, Object)}</code> but not returning
     * the previous value associated with <code>key</code>
     * <p>
     * If {@link MapWriter} is defined then new map entry is stored in write-through mode.
     *
     * @param key - map key
     * @param value - map value
     * @return <code>true</code> if key exists and value was updated.
     *         <code>false</code> if key doesn't exists and value wasn't updated.
     */
    boolean fastReplace(K key, V value);
    
    /**
     * Associates the specified <code>value</code> with the specified <code>key</code>
     * only if there is no any association with specified<code>key</code>.
     * <p>
     * Works faster than <code>{@link #putIfAbsent(Object, Object)}</code> but not returning
     * the previous value associated with <code>key</code>
     * <p>
     * If {@link MapWriter} is defined then new map entry is stored in write-through mode.
     *
     * @param key - map key
     * @param value - map value
     * @return <code>true</code> if key is a new one in the hash and value was set.
     *         <code>false</code> if key already exists in the hash and change hasn't been made.
     */
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
     * Returns key set of this map. 
     * Keys are loaded in batch. Batch size is <code>10</code>.
     * 
     * @see #readAllKeySet()
     * 
     * @return key set
     */
    @Override
    Set<K> keySet();

    /**
     * Returns key set of this map.
     * Keys are loaded in batch. Batch size is defined by <code>count</code> param. 
     * 
     * @see #readAllKeySet()
     * 
     * @param count - size of keys batch
     * @return key set
     */
    Set<K> keySet(int count);
    
    /**
     * Returns key set of this map.
     * If <code>pattern</code> is not null then only keys match this pattern are loaded.
     * Keys are loaded in batch. Batch size is defined by <code>count</code> param.
     * 
     *  Supported glob-style patterns:
     *  <p>
     *    h?llo subscribes to hello, hallo and hxllo
     *    <p>
     *    h*llo subscribes to hllo and heeeello
     *    <p>
     *    h[ae]llo subscribes to hello and hallo, but not hillo
     * 
     * @see #readAllKeySet()
     * 
     * @param pattern - key pattern
     * @param count - size of keys batch
     * @return key set
     */
    Set<K> keySet(String pattern, int count);
    
    /**
     * Returns key set of this map. 
     * If <code>pattern</code> is not null then only keys match this pattern are loaded.
     * 
     *  Supported glob-style patterns:
     *  <p>
     *    h?llo subscribes to hello, hallo and hxllo
     *    <p>
     *    h*llo subscribes to hllo and heeeello
     *    <p>
     *    h[ae]llo subscribes to hello and hallo, but not hillo
     * 
     * @see #readAllKeySet()
     * 
     * @param pattern - key pattern
     * @return key set
     */
    Set<K> keySet(String pattern);
    
    /**
     * Returns values collection of this map. 
     * Values are loaded in batch. Batch size is <code>10</code>.
     * 
     * @see #readAllValues()
     * 
     * @return values collection
     */
    @Override
    Collection<V> values();

    /**
     * Returns values collection of this map.
     * Values are loaded in batch. Batch size is <code>10</code>. 
     * If <code>keyPattern</code> is not null then only values mapped by matched keys of this pattern are loaded.
     * 
     *  Supported glob-style patterns:
     *  <p>
     *    h?llo subscribes to hello, hallo and hxllo
     *    <p>
     *    h*llo subscribes to hllo and heeeello
     *    <p>
     *    h[ae]llo subscribes to hello and hallo, but not hillo
     * 
     * @see #readAllValues()
     * 
     * @param keyPattern - key pattern
     * @return values collection
     */
    Collection<V> values(String keyPattern);

    /**
     * Returns values collection of this map.
     * Values are loaded in batch. Batch size is defined by <code>count</code> param.
     * If <code>keyPattern</code> is not null then only values mapped by matched keys of this pattern are loaded.
     * 
     *  Supported glob-style patterns:
     *  <p>
     *    h?llo subscribes to hello, hallo and hxllo
     *    <p>
     *    h*llo subscribes to hllo and heeeello
     *    <p>
     *    h[ae]llo subscribes to hello and hallo, but not hillo
     * 
     * @see #readAllValues()
     * 
     * @param keyPattern - key pattern
     * @param count - size of values batch
     * @return values collection
     */
    Collection<V> values(String keyPattern, int count);
    
    /**
     * Returns values collection of this map.
     * Values are loaded in batch. Batch size is defined by <code>count</code> param. 
     * 
     * @see #readAllValues()
     * 
     * @param count - size of values batch
     * @return values collection
     */
    Collection<V> values(int count);
    
    /**
     * Returns map entries collection. 
     * Map entries are loaded in batch. Batch size is <code>10</code>.
     * 
     * @see #readAllEntrySet()
     *  
     * @return map entries collection
     */
    @Override
    Set<java.util.Map.Entry<K, V>> entrySet();

    /**
     * Returns map entries collection.
     * Map entries are loaded in batch. Batch size is <code>10</code>. 
     * If <code>keyPattern</code> is not null then only entries mapped by matched keys of this pattern are loaded.
     * 
     *  Supported glob-style patterns:
     *  <p>
     *    h?llo subscribes to hello, hallo and hxllo
     *    <p>
     *    h*llo subscribes to hllo and heeeello
     *    <p>
     *    h[ae]llo subscribes to hello and hallo, but not hillo
     * 
     * @see #readAllEntrySet()
     * 
     * @param keyPattern - key pattern
     * @return map entries collection
     */
    Set<java.util.Map.Entry<K, V>> entrySet(String keyPattern);
    
    /**
     * Returns map entries collection.
     * Map entries are loaded in batch. Batch size is defined by <code>count</code> param. 
     * If <code>keyPattern</code> is not null then only entries mapped by matched keys of this pattern are loaded.
     * 
     *  Supported glob-style patterns:
     *  <p>
     *    h?llo subscribes to hello, hallo and hxllo
     *    <p>
     *    h*llo subscribes to hllo and heeeello
     *    <p>
     *    h[ae]llo subscribes to hello and hallo, but not hillo
     * 
     * @see #readAllEntrySet()
     * 
     * @param keyPattern - key pattern
     * @param count - size of entries batch
     * @return map entries collection
     */
    Set<java.util.Map.Entry<K, V>> entrySet(String keyPattern, int count);

    /**
     * Returns map entries collection.
     * Map entries are loaded in batch. Batch size is defined by <code>count</code> param. 
     * 
     * @see #readAllEntrySet()
     * 
     * @param count - size of entries batch
     * @return map entries collection
     */
    Set<java.util.Map.Entry<K, V>> entrySet(int count);
    
}
