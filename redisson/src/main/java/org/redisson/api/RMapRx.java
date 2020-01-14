/**
 * Copyright (c) 2013-2020 Nikita Koksharov
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

import org.redisson.api.map.MapLoader;
import org.redisson.api.map.MapWriter;

import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Single;

/**
 * RxJava2 interface for Redis based implementation
 * of {@link java.util.concurrent.ConcurrentMap} and {@link java.util.Map}
 * <p>
 * This map uses serialized state of key instead of hashCode or equals methods.
 * This map doesn't allow to store <code>null</code> as key or value.
 *
 * @author Nikita Koksharov
 *
 * @param <K> key
 * @param <V> value
 */
public interface RMapRx<K, V> extends RExpirableRx {

    /**
     * Loads all map entries to this Redis map using {@link org.redisson.api.map.MapLoader}.
     * 
     * @param replaceExistingValues - <code>true</code> if existed values should be replaced, <code>false</code> otherwise.  
     * @param parallelism - parallelism level, used to increase speed of process execution
     * @return void
     */
    Completable loadAll(boolean replaceExistingValues, int parallelism);
    
    /**
     * Loads map entries using {@link org.redisson.api.map.MapLoader} whose keys are listed in defined <code>keys</code> parameter.
     * 
     * @param keys - map keys
     * @param replaceExistingValues - <code>true</code> if existed values should be replaced, <code>false</code> otherwise.
     * @param parallelism - parallelism level, used to increase speed of process execution
     * @return void
     */
    Completable loadAll(Set<? extends K> keys, boolean replaceExistingValues, int parallelism);

    /**
     * Returns size of value mapped by key in bytes
     * 
     * @param key - map key
     * @return size of value
     */
    Single<Integer> valueSize(K key);

    /**
     * Returns map slice contained the mappings with defined <code>keys</code>.
     * <p>
     * If map doesn't contain value/values for specified key/keys and {@link MapLoader} is defined 
     * then value/values will be loaded in read-through mode. 
     * <p>
     * The returned map is <b>NOT</b> backed by the original map.
     *
     * @param keys - map keys
     * @return Map slice
     */
    Single<Map<K, V>> getAll(Set<K> keys);

    /**
     * Stores map entries specified in <code>map</code> object in batch mode.
     * <p>
     * If {@link MapWriter} is defined then map entries will be stored in write-through mode.
     *
     * @param map mappings to be stored in this map
     * @return void
     */
    Completable putAll(Map<? extends K, ? extends V> map);

    /**
     * Adds the given <code>delta</code> to the current value
     * by mapped <code>key</code>.
     *
     * Works only for <b>numeric</b> values!
     *
     * @param key - map key
     * @param delta the value to add
     * @return the updated value
     */
    Single<V> addAndGet(K key, Number delta);

    /**
     * Returns <code>true</code> if this map contains any map entry
     * with specified <code>value</code>, otherwise <code>false</code>
     *
     * @param value - map value
     * @return <code>true</code> if this map contains any map entry
     *          with specified <code>value</code>, otherwise <code>false</code>
     */
    Single<Boolean> containsValue(Object value);

    /**
     * Returns <code>true</code> if this map contains map entry
     * mapped by specified <code>key</code>, otherwise <code>false</code>
     *
     * @param key - map key
     * @return <code>true</code> if this map contains map entry
     *          mapped by specified <code>key</code>, otherwise <code>false</code>
     */
    Single<Boolean> containsKey(Object key);

    /**
     * Returns size of this map
     *
     * @return size
     */
    Single<Integer> size();

    /**
     * Removes map entries mapped by specified <code>keys</code>.
     * <p>
     * Works faster than <code>{@link #remove(Object)}</code> but not returning
     * the value.
     * <p>
     * If {@link MapWriter} is defined then <code>keys</code>are deleted in write-through mode.
     *
     * @param keys - map keys
     * @return the number of keys that were removed from the hash, not including specified but non existing keys
     */
    Single<Long> fastRemove(K... keys);

    /**
     * Stores the specified <code>value</code> mapped by specified <code>key</code>.
     * <p>
     * Works faster than <code>{@link #put(Object, Object)}</code> but not returning
     * previous value.
     * <p>
     * Returns <code>true</code> if key is a new key in the hash and value was set or
     * <code>false</code> if key already exists in the hash and the value was updated.
     * <p>
     * If {@link MapWriter} is defined then map entry is stored in write-through mode.
     *
     * @param key - map key
     * @param value - map value
     * @return <code>true</code> if key is a new key in the hash and value was set.
     *         <code>false</code> if key already exists in the hash and the value was updated.
     */
    Single<Boolean> fastPut(K key, V value);

    /**
     * Stores the specified <code>value</code> mapped by specified <code>key</code>
     * only if there is no value with specified<code>key</code> stored before.
     * <p>
     * Returns <code>true</code> if key is a new one in the hash and value was set or
     * <code>false</code> if key already exists in the hash and change hasn't been made.
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
    Single<Boolean> fastPutIfAbsent(K key, V value);
    
    /**
     * Read all keys at once
     *
     * @return keys
     */
    Single<Set<K>> readAllKeySet();

    /**
     * Read all values at once
     *
     * @return values
     */
    Single<Collection<V>> readAllValues();

    /**
     * Read all map entries at once
     *
     * @return entries
     */
    Single<Set<Entry<K, V>>> readAllEntrySet();

    /**
     * Read all map as local instance at once
     *
     * @return map
     */
    Single<Map<K, V>> readAllMap();

    /**
     * Returns the value mapped by defined <code>key</code> or {@code null} if value is absent.
     * <p>
     * If map doesn't contain value for specified key and {@link MapLoader} is defined
     * then value will be loaded in read-through mode.
     *
     * @param key the key
     * @return the value mapped by defined <code>key</code> or {@code null} if value is absent
     */
    Maybe<V> get(K key);

    /**
     * Stores the specified <code>value</code> mapped by specified <code>key</code>.
     * Returns previous value if map entry with specified <code>key</code> already existed.
     * <p>
     * If {@link MapWriter} is defined then map entry is stored in write-through mode.
     *
     * @param key - map key
     * @param value - map value
     * @return previous associated value
     */
    Maybe<V> put(K key, V value);

    /**
     * Removes map entry by specified <code>key</code> and returns value.
     * <p>
     * If {@link MapWriter} is defined then <code>key</code>is deleted in write-through mode.
     *
     * @param key - map key
     * @return deleted value, <code>null</code> if map entry doesn't exist
     */
    Maybe<V> remove(K key);

    /**
     * Replaces previous value with a new <code>value</code> mapped by specified <code>key</code>.
     * Returns <code>null</code> if there is no map entry stored before and doesn't store new map entry.
     * <p>
     * If {@link MapWriter} is defined then new <code>value</code>is written in write-through mode.
     *
     * @param key - map key
     * @param value - map value
     * @return previous associated value
     *         or <code>null</code> if there is no map entry stored before and doesn't store new map entry
     */
    Maybe<V> replace(K key, V value);

    /**
     * Replaces previous <code>oldValue</code> with a <code>newValue</code> mapped by specified <code>key</code>.
     * Returns <code>false</code> if previous value doesn't exist or equal to <code>oldValue</code>.
     * <p>
     * If {@link MapWriter} is defined then <code>newValue</code>is written in write-through mode.
     *
     * @param key - map key
     * @param oldValue - map old value
     * @param newValue - map new value
     * @return <code>true</code> if value has been replaced otherwise <code>false</code>.
     */
    Single<Boolean> replace(K key, V oldValue, V newValue);

    /**
     * Removes map entry only if it exists with specified <code>key</code> and <code>value</code>.
     * <p>
     * If {@link MapWriter} is defined then <code>key</code>is deleted in write-through mode.
     *
     * @param key - map key
     * @param value - map value
     * @return <code>true</code> if map entry has been removed otherwise <code>false</code>.
     */
    Single<Boolean> remove(Object key, Object value);

    /**
     * Stores the specified <code>value</code> mapped by specified <code>key</code>
     * only if there is no value with specified<code>key</code> stored before.
     * <p>
     * If {@link MapWriter} is defined then new map entry is stored in write-through mode.
     *
     * @param key - map key
     * @param value - map value
     * @return <code>null</code> if key is a new one in the hash and value was set.
     *         Previous value if key already exists in the hash and change hasn't been made.
     */
    Maybe<V> putIfAbsent(K key, V value);

    /**
     * Returns iterator over map entries collection. 
     * Map entries are loaded in batch. Batch size is <code>10</code>.
     * 
     * @see #readAllEntrySet()
     *  
     * @return iterator
     */
    Flowable<Map.Entry<K, V>> entryIterator();
    
    /**
     * Returns iterator over map entries collection.
     * Map entries are loaded in batch. Batch size is defined by <code>count</code> param. 
     * 
     * @see #readAllEntrySet()
     * 
     * @param count - size of entries batch
     * @return iterator
     */
    Flowable<Map.Entry<K, V>> entryIterator(int count);
    
    /**
     * Returns iterator over map entries collection.
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
     * @param pattern - key pattern
     * @return iterator
     */
    Flowable<Map.Entry<K, V>> entryIterator(String pattern);
    
    /**
     * Returns iterator over map entries collection.
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
     * @param pattern - key pattern
     * @param count - size of entries batch
     * @return iterator
     */
    Flowable<Map.Entry<K, V>> entryIterator(String pattern, int count);

    /**
     * Returns iterator over values collection of this map. 
     * Values are loaded in batch. Batch size is <code>10</code>.
     * 
     * @see #readAllValues()
     * 
     * @return iterator
     */
    Flowable<V> valueIterator();
    
    /**
     * Returns iterator over values collection of this map.
     * Values are loaded in batch. Batch size is defined by <code>count</code> param. 
     * 
     * @see #readAllValues()
     * 
     * @param count - size of values batch
     * @return iterator
     */
    Flowable<V> valueIterator(int count);
    
    /**
     * Returns iterator over values collection of this map.
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
     * @param pattern - key pattern
     * @return iterator
     */
    Flowable<V> valueIterator(String pattern);
    
    /**
     * Returns iterator over values collection of this map.
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
     * @param pattern - key pattern
     * @param count - size of values batch
     * @return iterator
     */
    Flowable<V> valueIterator(String pattern, int count);

    /**
     * Returns iterator over key set of this map. 
     * Keys are loaded in batch. Batch size is <code>10</code>.
     * 
     * @see #readAllKeySet()
     * 
     * @return iterator
     */
    Flowable<K> keyIterator();
    
    /**
     * Returns iterator over key set of this map.
     * Keys are loaded in batch. Batch size is defined by <code>count</code> param. 
     * 
     * @see #readAllKeySet()
     * 
     * @param count - size of keys batch
     * @return iterator
     */
    Flowable<K> keyIterator(int count);
    
    /**
     * Returns iterator over key set of this map. 
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
     * @return iterator
     */
    Flowable<K> keyIterator(String pattern);

    /**
     * Returns iterator over key set of this map.
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
     * @return iterator
     */
    Flowable<K> keyIterator(String pattern, int count);
    
    /**
     * Returns <code>RPermitExpirableSemaphore</code> instance associated with key
     * 
     * @param key - map key
     * @return permitExpirableSemaphore
     */
    RPermitExpirableSemaphoreRx getPermitExpirableSemaphore(K key);

    /**
     * Returns <code>RSemaphore</code> instance associated with key
     * 
     * @param key - map key
     * @return semaphore
     */
    RSemaphoreRx getSemaphore(K key);
    
    /**
     * Returns <code>RLock</code> instance associated with key
     * 
     * @param key - map key
     * @return fairLock
     */
    RLockRx getFairLock(K key);
    
    /**
     * Returns <code>RReadWriteLock</code> instance associated with key
     * 
     * @param key - map key
     * @return readWriteLock
     */
    RReadWriteLockRx getReadWriteLock(K key);
    
    /**
     * Returns <code>RLock</code> instance associated with key
     * 
     * @param key - map key
     * @return lock
     */
    RLockRx getLock(K key);

}
