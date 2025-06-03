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

import org.redisson.api.map.MapLoader;
import org.redisson.api.map.MapWriter;

import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Async interface for Redis based implementation
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
public interface RMapAsync<K, V> extends RExpirableAsync {

    /**
     * Associates specified key with the given value if key isn't already associated with a value.
     * Otherwise, replaces the associated value with the results of the given
     * remapping function, or removes if the result is {@code null}.
     *
     * @param key - map key
     * @param value - value to be merged with the existing value
     *        associated with the key or to be associated with the key,
     *        if no existing value
     * @param remappingFunction - the function is invoked with the existing value to compute new value
     * @return new value associated with the specified key or
     *         {@code null} if no value associated with the key
     */
    RFuture<V> mergeAsync(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction);

    /**
     * Computes a new mapping for the specified key and its current mapped value.
     *
     * @param key - map key
     * @param remappingFunction - function to compute a value
     * @return the new value associated with the specified key, or {@code null} if none
     */
    RFuture<V> computeAsync(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction);

    /**
     * Computes a mapping for the specified key if it's not mapped before.
     *
     * @param key - map key
     * @param mappingFunction - function to compute a value
     * @return current or new computed value associated with
     *         the specified key, or {@code null} if the computed value is null
     */
    RFuture<V> computeIfAbsentAsync(K key, Function<? super K, ? extends V> mappingFunction);

    /**
     * Computes a mapping for the specified key only if it's already mapped.
     *
     * @param key - map key
     * @param remappingFunction - function to compute a value
     * @return the new value associated with the specified key, or null if none
     */
    RFuture<V> computeIfPresentAsync(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction);

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
    RFuture<Map<K, V>> getAllAsync(Set<K> keys);

    /**
     * Stores map entries specified in <code>map</code> object in batch mode.
     * <p>
     * If {@link MapWriter} is defined then map entries will be stored in write-through mode.
     *
     * @param map mappings to be stored in this map
     * @return void
     */
    RFuture<Void> putAllAsync(Map<? extends K, ? extends V> map);

    /**
     * Stores map entries specified in <code>map</code> object in batch mode.
     * Batch inserted by chunks limited by <code>batchSize</code> value
     * to avoid OOM and/or Redis response timeout error for map with big size.
     * <p>
     * If {@link MapWriter} is defined then map entries are stored in write-through mode.
     *
     * @param map mappings to be stored in this map
     * @param batchSize - size of map entries batch
     * @return void
     */
    RFuture<Void> putAllAsync(Map<? extends K, ? extends V> map, int batchSize);

    /**
     * Returns random keys from this map limited by <code>count</code>
     *
     * @param count - keys amount to return
     * @return random keys
     */
    RFuture<Set<K>> randomKeysAsync(int count);

    /**
     * Returns random map entries from this map limited by <code>count</code>
     *
     * @param count - entries amount to return
     * @return random entries
     */
    RFuture<Map<K, V>> randomEntriesAsync(int count);

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
    RFuture<V> addAndGetAsync(K key, Number delta);

    /**
     * Returns <code>true</code> if this map contains any map entry
     * with specified <code>value</code>, otherwise <code>false</code>
     *
     * @param value - map value
     * @return <code>true</code> if this map contains any map entry
     *          with specified <code>value</code>, otherwise <code>false</code>
     */
    RFuture<Boolean> containsValueAsync(Object value);

    /**
     * Returns <code>true</code> if this map contains map entry
     * mapped by specified <code>key</code>, otherwise <code>false</code>
     *
     * @param key - map key
     * @return <code>true</code> if this map contains map entry
     *          mapped by specified <code>key</code>, otherwise <code>false</code>
     */
    RFuture<Boolean> containsKeyAsync(Object key);

    /**
     * Returns size of this map
     * 
     * @return size
     */
    RFuture<Integer> sizeAsync();

    /**
     * Returns values of this map using iterable.
     * Values are loaded in batch. Batch size is <code>10</code>.
     *
     * @return Asynchronous Iterable object
     */
    AsyncIterator<V> valuesAsync();

    /**
     * Returns values of this map using iterable.
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
     *
     * @param keyPattern - key pattern
     * @return Asynchronous Iterable object
     */
    AsyncIterator<V> valuesAsync(String keyPattern);

    /**
     * Returns values of this map using iterable.
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
     *
     * @param keyPattern - key pattern
     * @param count - size of values batch
     * @return Asynchronous Iterable object
     */
    AsyncIterator<V> valuesAsync(String keyPattern, int count);

    /**
     * Returns values of this map using iterable.
     * Values are loaded in batch. Batch size is defined by <code>count</code> param.
     *
     * @param count - size of values batch
     * @return Asynchronous Iterable object
     */
    AsyncIterator<V> valuesAsync(int count);

    /**
     * Returns map entries using iterable.
     * Map entries are loaded in batch. Batch size is <code>10</code>.
     *
     * @return Asynchronous Iterable object
     */
    AsyncIterator<java.util.Map.Entry<K, V>> entrySetAsync();

    /**
     * Returns map entries using iterable.
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
     *
     * @param keyPattern key pattern
     * @return Asynchronous Iterable object
     */
    AsyncIterator<java.util.Map.Entry<K, V>> entrySetAsync(String keyPattern);

    /**
     * Returns map entries using iterable.
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
     *
     * @param keyPattern key pattern
     * @param count size of entries batch
     * @return Asynchronous Iterable object
     */
    AsyncIterator<java.util.Map.Entry<K, V>> entrySetAsync(String keyPattern, int count);

    /**
     * Returns map entries using iterable.
     * Map entries are loaded in batch. Batch size is defined by <code>count</code> param.
     *
     * @param count - size of entries batch
     * @return Asynchronous Iterable object
     */
    AsyncIterator<java.util.Map.Entry<K, V>> entrySetAsync(int count);

    /**
     * Removes map entries mapped by specified <code>keys</code>.
     * <p>
     * Works faster than <code>{@link #removeAsync(Object)}</code> but not returning
     * the value.
     * <p>
     * If {@link MapWriter} is defined then <code>keys</code>are deleted in write-through mode.
     *
     * @param keys - map keys
     * @return the number of keys that were removed from the hash, not including specified but non existing keys
     */
    RFuture<Long> fastRemoveAsync(K... keys);

    /**
     * Stores the specified <code>value</code> mapped by specified <code>key</code>.
     * <p>
     * Works faster than <code>{@link #putAsync(Object, Object)}</code> but not returning
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
    RFuture<Boolean> fastPutAsync(K key, V value);

    /**
     * Replaces previous value with a new <code>value</code> mapped by specified <code>key</code>.
     * <p>
     * Works faster than <code>{@link #replaceAsync(Object, Object)}</code> but not returning
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
    RFuture<Boolean> fastReplaceAsync(K key, V value);
    
    /**
     * Stores the specified <code>value</code> mapped by specified <code>key</code>
     * only if there is no value with specified<code>key</code> stored before.
     * <p>
     * Returns <code>true</code> if key is a new one in the hash and value was set or
     * <code>false</code> if key already exists in the hash and change hasn't been made.
     * <p>
     * Works faster than <code>{@link #putIfAbsentAsync(Object, Object)}</code> but not returning
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
     * Stores the specified <code>value</code> mapped by <code>key</code>
     * only if mapping already exists.
     * <p>
     * Returns <code>true</code> if key is a new one in the hash and value was set or
     * <code>false</code> if key already exists in the hash and change hasn't been made.
     * <p>
     * Works faster than <code>{@link #putIfExistsAsync(Object, Object)}</code> but doesn't return
     * previous value associated with <code>key</code>
     * <p>
     * If {@link MapWriter} is defined then new map entry is stored in write-through mode.
     *
     * @param key - map key
     * @param value - map value
     * @return <code>true</code> if key already exists in the hash and new value has been stored.
     *         <code>false</code> if key doesn't exist in the hash and value hasn't been set.
     */
    RFuture<Boolean> fastPutIfExistsAsync(K key, V value);

    /**
     * Read all keys at once
     *
     * @return keys
     */
    RFuture<Set<K>> readAllKeySetAsync();

    /**
     * Read all keys mapped by matched keys of this pattern at once
     *
     * @param keyPattern - key Pattern
     * @return keys
     */
    RFuture<Set<K>> readAllKeySetAsync(String keyPattern);

    /**
     * Read all values at once
     *
     * @return values
     */
    RFuture<Collection<V>> readAllValuesAsync();

    /**
     * Read all values mapped by matched keys of this pattern at once
     *
     * @param keyPattern - key Pattern
     * @return values
     */
    RFuture<Collection<V>> readAllValuesAsync(String keyPattern);

    /**
     * Read all map entries at once
     *
     * @return entries
     */
    RFuture<Set<Entry<K, V>>> readAllEntrySetAsync();


    /**
     * Read all entries mapped by matched keys of this pattern at once
     *
     * @param keyPattern - key Pattern
     * @return entries
     */
    RFuture<Set<Entry<K, V>>> readAllEntrySetAsync(String keyPattern);

    /**
     * Read all map as local instance at once
     *
     * @return map
     */
    RFuture<Map<K, V>> readAllMapAsync();

    /**
     * Returns the value mapped by defined <code>key</code> or {@code null} if value is absent.
     * <p>
     * If map doesn't contain value for specified key and {@link MapLoader} is defined
     * then value will be loaded in read-through mode.
     *
     * @param key the key
     * @return the value mapped by defined <code>key</code> or {@code null} if value is absent
     */
    RFuture<V> getAsync(K key);

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
    RFuture<V> putAsync(K key, V value);

    /**
     * Removes map entry by specified <code>key</code> and returns value.
     * <p>
     * If {@link MapWriter} is defined then <code>key</code>is deleted in write-through mode.
     *
     * @param key - map key
     * @return deleted value, <code>null</code> if map entry doesn't exist
     */
    RFuture<V> removeAsync(K key);

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
    RFuture<V> replaceAsync(K key, V value);

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
    RFuture<Boolean> replaceAsync(K key, V oldValue, V newValue);

    /**
     * Removes map entry only if it exists with specified <code>key</code> and <code>value</code>.
     * <p>
     * If {@link MapWriter} is defined then <code>key</code>is deleted in write-through mode.
     *
     * @param key - map key
     * @param value - map value
     * @return <code>true</code> if map entry has been removed otherwise <code>false</code>.
     */
    RFuture<Boolean> removeAsync(Object key, Object value);

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
    RFuture<V> putIfAbsentAsync(K key, V value);

    /**
     * Stores the specified <code>value</code> mapped by <code>key</code>
     * only if mapping already exists.
     * <p>
     * If {@link MapWriter} is defined then new map entry is stored in write-through mode.
     *
     * @param key - map key
     * @param value - map value
     * @return <code>null</code> if key is doesn't exists in the hash and value hasn't been set.
     *         Previous value if key already exists in the hash and new value has been stored.
     */
    RFuture<V> putIfExistsAsync(K key, V value);

    /**
     * Clears map without removing options data used during map creation.
     *
     * @return <code>true</code> if map was cleared <code>false</code> if not
     */
    RFuture<Boolean> clearAsync();

    /**
     * Adds object event listener
     *
     * @see org.redisson.api.listener.TrackingListener
     * @see org.redisson.api.listener.MapPutListener
     * @see org.redisson.api.listener.MapRemoveListener
     * @see org.redisson.api.ExpiredObjectListener
     * @see org.redisson.api.DeletedObjectListener
     *
     * @param listener - object event listener
     * @return listener id
     */
    RFuture<Integer> addListenerAsync(ObjectListener listener);

}
