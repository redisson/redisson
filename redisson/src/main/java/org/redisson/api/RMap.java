/**
 * Copyright (c) 2013-2024 Nikita Koksharov
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
 * Redis based implementation of {@link java.util.concurrent.ConcurrentMap}
 * and {@link java.util.Map}
 * <p>
 * This map uses serialized state of key instead of hashCode or equals methods.
 * This map doesn't allow to store <code>null</code> as key or value.
 *
 * @author Nikita Koksharov
 *
 * @param <K> map key
 * @param <V> value
 */
public interface RMap<K, V> extends ConcurrentMap<K, V>, RExpirable, RMapAsync<K, V>, RDestroyable {

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
     * Returns the value mapped by defined <code>key</code> or {@code null} if value is absent.
     * <p>
     * If map doesn't contain value for specified key and {@link MapLoader} is defined 
     * then value will be loaded in read-through mode. 
     *
     * @param key the key
     * @return the value mapped by defined <code>key</code> or {@code null} if value is absent
     */
    @Override
    V get(Object key);
    
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
    @Override
    V put(K key, V value);
    
    /**
     * Stores the specified <code>value</code> mapped by <code>key</code>
     * only if there is no value with specified<code>key</code> stored before.
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
     * Stores the specified <code>value</code> mapped by <code>key</code>
     * only if mapping already exists.
     * <p>
     * If {@link MapWriter} is defined then new map entry is stored in write-through mode.
     *
     * @param key - map key
     * @param value - map value
     * @return <code>null</code> if key doesn't exist in the hash and value hasn't been set.
     *         Previous value if key already exists in the hash and new value has been stored.
     */
    V putIfExists(K key, V value);

    /**
     * Returns random keys from this map limited by <code>count</code>
     *
     * @param count - keys amount to return
     * @return random keys
     */
    Set<K> randomKeys(int count);

    /**
     * Returns random map entries from this map limited by <code>count</code>
     *
     * @param count - entries amount to return
     * @return random entries
     */
    Map<K, V> randomEntries(int count);

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
     * Returns size of value mapped by specified <code>key</code> in bytes
     * 
     * @param key - map key
     * @return size of value
     */
    int valueSize(K key);
    
    /**
     * Adds the given <code>delta</code> to the current value
     * by mapped <code>key</code>.
     * <p>
     * Works only with codecs below
     * <p>
     * {@link org.redisson.codec.JsonJacksonCodec},
     * <p>
     * {@link org.redisson.client.codec.StringCodec},
     * <p>
     * {@link org.redisson.client.codec.IntegerCodec},
     * <p>
     * {@link org.redisson.client.codec.DoubleCodec}
     * <p>
     * {@link org.redisson.client.codec.LongCodec}
     *
     * @param key - map key
     * @param delta the value to add
     * @return the updated value
     */
    V addAndGet(K key, Number delta);

    /**
     * Returns <code>true</code> if this map contains map entry
     * mapped by specified <code>key</code>, otherwise <code>false</code>
     *
     * @param key - map key
     * @return <code>true</code> if this map contains map entry
     *          mapped by specified <code>key</code>, otherwise <code>false</code>
     */
    @Override
    boolean containsKey(Object key);

    /**
     * Returns <code>true</code> if this map contains any map entry
     * with specified <code>value</code>, otherwise <code>false</code>
     *
     * @param value - map value
     * @return <code>true</code> if this map contains any map entry
     *          with specified <code>value</code>, otherwise <code>false</code>
     */
    @Override
    boolean containsValue(Object value);

    /**
     * Removes map entry by specified <code>key</code> and returns value.
     * <p>
     * If {@link MapWriter} is defined then <code>key</code>is deleted in write-through mode.
     *
     * @param key - map key
     * @return deleted value, <code>null</code> if map entry doesn't exist
     */
    @Override
    V remove(Object key);
    
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
    @Override
    V replace(K key, V value);

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
    @Override
    boolean replace(K key, V oldValue, V newValue);

    /**
     * Removes map entry only if it exists with specified <code>key</code> and <code>value</code>.
     * <p>
     * If {@link MapWriter} is defined then <code>key</code>is deleted in write-through mode.
     *
     * @param key - map key
     * @param value - map value
     * @return <code>true</code> if map entry has been removed otherwise <code>false</code>.
     */
    @Override
    boolean remove(Object key, Object value);
    
    /**
     * Stores map entries specified in <code>map</code> object in batch mode.
     * <p>
     * If {@link MapWriter} is defined then map entries will be stored in write-through mode.
     *
     * @param map mappings to be stored in this map
     */
    @Override
    void putAll(java.util.Map<? extends K, ? extends V> map);
    
    /**
     * Stores map entries specified in <code>map</code> object in batch mode.
     * Batch inserted by chunks limited by <code>batchSize</code> value
     * to avoid OOM and/or Redis response timeout error for map with big size. 
     * <p>
     * If {@link MapWriter} is defined then map entries are stored in write-through mode.
     *
     * @param map mappings to be stored in this map
     * @param batchSize - size of map entries batch
     */
    void putAll(Map<? extends K, ? extends V> map, int batchSize);
    
    /**
     * Returns map slice contained the mappings with defined <code>keys</code>.
     * <p>
     * If map doesn't contain value/values for specified key/keys and {@link MapLoader} is defined 
     * then value/values will be loaded in read-through mode. 
     * <p>
     * The returned map is <b>NOT</b> backed by the original map.
     *
     * @param keys map keys
     * @return Map slice
     */
    Map<K, V> getAll(Set<K> keys);

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
    long fastRemove(K... keys);

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
    boolean fastPut(K key, V value);

    /**
     * Replaces previous value with a new <code>value</code> mapped by specified <code>key</code>.
     * <p>
     * Works faster than <code>{@link #replace(Object, Object)}</code> but not returning
     * the previous value.
     * <p>
     * Returns <code>true</code> if key exists and value was updated or
     * <code>false</code> if key doesn't exists and value wasn't updated.
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
    boolean fastPutIfAbsent(K key, V value);

    /**
     * Stores the specified <code>value</code> mapped by <code>key</code>
     * only if mapping already exists.
     * <p>
     * Returns <code>true</code> if key is a new one in the hash and value was set or
     * <code>false</code> if key already exists in the hash and change hasn't been made.
     * <p>
     * Works faster than <code>{@link #putIfExists(Object, Object)}</code> but doesn't return
     * previous value associated with <code>key</code>
     * <p>
     * If {@link MapWriter} is defined then new map entry is stored in write-through mode.
     *
     * @param key - map key
     * @param value - map value
     * @return <code>true</code> if key already exists in the hash and new value has been stored.
     *         <code>false</code> if key doesn't exist in the hash and value hasn't been set.
     */
    boolean fastPutIfExists(K key, V value);

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
     * <p>
     * Use <code>org.redisson.client.codec.StringCodec</code> for Map keys.
     * <p>
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
     * <p>
     * Use <code>org.redisson.client.codec.StringCodec</code> for Map keys.
     * <p>
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
     * <p>
     * Use <code>org.redisson.client.codec.StringCodec</code> for Map keys.
     * <p>
     * Usage example:
     * <pre>
     *     Codec valueCodec = ...
     *     RMap<String, MyObject> map = redissonClient.getMap("simpleMap", new CompositeCodec(StringCodec.INSTANCE, valueCodec, valueCodec));
     *
     *     // or
     *
     *     RMap<String, String> map = redissonClient.getMap("simpleMap", StringCodec.INSTANCE);
     * </pre>
     * <pre>
     *  Supported glob-style patterns:
     *    h?llo subscribes to hello, hallo and hxllo
     *    h*llo subscribes to hllo and heeeello
     *    h[ae]llo subscribes to hello and hallo, but not hillo
     * </pre>
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
     * <p>
     * Use <code>org.redisson.client.codec.StringCodec</code> for Map keys.
     * <p>
     * Usage example:
     * <pre>
     *     Codec valueCodec = ...
     *     RMap<String, MyObject> map = redissonClient.getMap("simpleMap", new CompositeCodec(StringCodec.INSTANCE, valueCodec, valueCodec));
     *
     *     // or
     *
     *     RMap<String, String> map = redissonClient.getMap("simpleMap", StringCodec.INSTANCE);
     * </pre>
     * <pre>
     *  Supported glob-style patterns:
     *    h?llo subscribes to hello, hallo and hxllo
     *    h*llo subscribes to hllo and heeeello
     *    h[ae]llo subscribes to hello and hallo, but not hillo
     * </pre>
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
     * <p>
     * Use <code>org.redisson.client.codec.StringCodec</code> for Map keys.
     * <p>
     * Usage example:
     * <pre>
     *     Codec valueCodec = ...
     *     RMap<String, MyObject> map = redissonClient.getMap("simpleMap", new CompositeCodec(StringCodec.INSTANCE, valueCodec, valueCodec));
     *
     *     // or
     *
     *     RMap<String, String> map = redissonClient.getMap("simpleMap", StringCodec.INSTANCE);
     * </pre>
     * <pre>
     *  Supported glob-style patterns:
     *    h?llo subscribes to hello, hallo and hxllo
     *    h*llo subscribes to hllo and heeeello
     *    h[ae]llo subscribes to hello and hallo, but not hillo
     * </pre>
     * @see #readAllEntrySet()
     * 
     * @param keyPattern key pattern
     * @return map entries collection
     */
    Set<java.util.Map.Entry<K, V>> entrySet(String keyPattern);
    
    /**
     * Returns map entries collection.
     * Map entries are loaded in batch. Batch size is defined by <code>count</code> param. 
     * If <code>keyPattern</code> is not null then only entries mapped by matched keys of this pattern are loaded.
     * <p>
     * Use <code>org.redisson.client.codec.StringCodec</code> for Map keys.
     * <p>
     * Usage example:
     * <pre>
     *     Codec valueCodec = ...
     *     RMap<String, MyObject> map = redissonClient.getMap("simpleMap", new CompositeCodec(StringCodec.INSTANCE, valueCodec, valueCodec));
     *
     *     // or
     *
     *     RMap<String, String> map = redissonClient.getMap("simpleMap", StringCodec.INSTANCE);
     * </pre>
     * <pre>
     *  Supported glob-style patterns:
     *    h?llo subscribes to hello, hallo and hxllo
     *    h*llo subscribes to hllo and heeeello
     *    h[ae]llo subscribes to hello and hallo, but not hillo
     * </pre>
     * @see #readAllEntrySet()
     * 
     * @param keyPattern key pattern
     * @param count size of entries batch
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

    /**
     * Adds object event listener
     *
     * @see org.redisson.api.listener.TrackingListener
     * @see org.redisson.api.listener.MapPutListener
     * @see org.redisson.api.listener.MapRemoveListener
     * @see org.redisson.api.ExpiredObjectListener
     * @see org.redisson.api.DeletedObjectListener
     *
     * @param listener object event listener
     * @return listener id
     */
    int addListener(ObjectListener listener);

}
