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

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <K>
 * @param <V>
 */
public interface RLocalCachedMapAsync<K, V> extends RExpirableAsync {

    /**
     * Returns map size
     * 
     * @return
     */
    RFuture<Integer> sizeAsync();

    /**
     * Checks if map contains the specified <code>key</code>
     * 
     * @return <code>true</code> if map contains <code>key</code>.
     *         <code>false</code> if map doesn't contain <code>key</code>.
     */
    RFuture<Boolean> containsKeyAsync(Object key);

    /**
     * Checks if map contains the specified <code>value</code>
     * 
     * @return <code>true</code> if map contains <code>value</code>.
     *         <code>false</code> if map doesn't contain <code>value</code>.
     */
    RFuture<Boolean> containsValueAsync(Object value);
    
    /**
     * Returns value associated with <code>key</code>
     * 
     * @param key
     * @return
     */
    RFuture<V> getAsync(Object key);

    /**
     * Associates the specified <code>value</code> with the specified <code>key</code>.
     *
     * @param key
     * @param value
     * @return previous value associated with <code>key</code>
     */
    RFuture<V> putAsync(K key, V value);
    
    /**
     * Removes <code>key</code> from map.
     * 
     * @param key
     * @return removed value associated with <code>key</code>
     */
    RFuture<V> removeAsync(K key);

    /**
     * Removes <code>key</code> from map
     * <p>
     * Works faster than <code>RLocalCachedMap.remove</code> but not returning
     * the value associated with <code>key</code>
     *
     * @param key
     * @return <code>true</code> if key has been deleted.
     *         <code>false</code> if key doesn't exist.
     */
    RFuture<Boolean> fastRemoveAsync(K key);
    
    /**
     * Associates the specified <code>value</code> with the specified <code>key</code>.
     * <p>
     * Works faster than <code>RLocalCachedMap.put</code> but not returning
     * the previous value associated with <code>key</code>
     *
     * @param key
     * @param value
     * @return <code>true</code> if key is a new key in the hash and value was set.
     *         <code>false</code> if key already exists in the hash and the value was updated.
     */
    RFuture<Boolean> fastPutAsync(K key, V value);
    
}
