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

import java.util.Map;
import java.util.Set;

import javax.cache.CacheException;
import javax.cache.event.CacheEntryRemovedListener;
import javax.cache.integration.CacheLoader;
import javax.cache.integration.CacheWriter;

import reactor.core.publisher.Mono;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <K> key type
 * @param <V> value type
 */
public interface CacheReactive<K, V> {

    /**
    * This method retrieves an entry from the cache.
    *
    * If the cache uses the read-through pattern, and the method would return null
    * because the entry is not present in the cache, then the cache's {@link CacheLoader}
    * will try to load the entry.
    *
    * @param key the key whose value should be returned
    * @return the element, or null if the entry does not exist.
    * @throws IllegalStateException if the cache is in a closed state
    * @throws NullPointerException if the key is null
    * @throws CacheException if there is a problem retrieving the entry from the cache
    */
    Mono<V> get(K key);
    
    /**
    * This method accepts a set of requested keys and retrieves a collection of entries from the
    * {@link CacheReactive}, returning them as a {@link Map} of the associated values.
    *
    * If the cache uses the read-through pattern, and the method would return null for a key
    * because an entry is not present in the cache, the Cache's {@link CacheLoader} will try to
    * load the entry. If a key's entry cannot be loaded, the key will not appear in the Map.
    *
    * @param keys The keys whose values should be returned.
    * @return A Map of entries associated with the given keys. If a key is not found
    * in the cache, it will not be in the Map.
    * @throws NullPointerException if keys is null or contains a null
    * @throws IllegalStateException if the cache is in a closed state
    * @throws CacheException if there is a problem retrieving the entries from the cache
    */
    Mono<Map<K, V>> getAll(Set<? extends K> keys);
    
    /**
    * This method returns a Boolean true/false value, depending on whether the
    * {@link CacheReactive} has a mapping for a key k such that key.equals(k).
    *
    *
    * @param key the key with a possible mapping in the cache.
    * @return true if such a mapping exists
    * @throws NullPointerException if key is null
    * @throws IllegalStateException if the cache is in a closed state
    * @throws CacheException if there is a problem with the cache
    */
    Mono<Boolean> containsKey(K key);
    
    /**
    * This method places the given value V in the cache and associates it with the given key K.
    *
    * If the {@link CacheReactive} already has a mapping for the key, the previous
    * value is replaced by the given value V.
    * This occurs if and only if {@link #containsKey(Object) c.containsKey(k)}
    * would return true.)
    *
    * @param key the key to place in the cache
    * @param value the value to associate with the given key
    * @return void
    * @throws NullPointerException if the key or value is null
    * @throws IllegalStateException if the cache is in a closed state
    * @throws CacheException if there is a problem with the cache
    */
    Mono<Void> put(K key, V value);
    
    /**
    * This method places the given key and value in the cache.
    * Any value already in the cache is returned and replaced by the new given value.
    * This occurs if and only if {@link #containsKey(Object) c.containsKey(k)}
    * would return true.)
    * If there was no value already in the cache, the method returns null.
    *
    * @param key the key to place in the cache
    * @param value the value to associate with the given key
    * @return the previous value in the cache, or null if none already existed
    * @throws NullPointerException if the key or value is null
    * @throws IllegalStateException if the cache is in a closed state
    * @throws CacheException if there is a problem with the cache
    */
    Mono<V> getAndPut(K key, V value);
    
    /**
    * This method copies all of the entries from the given Map to the {@link CacheReactive}.
    *
    * This method is equivalent to calling
    * {@link #put(Object, Object) put(k, v)} on this cache one time for each mapping
    * from key k to value v in the given Map.
    *
    * Individual puts may occur in any order.
    *
    * If entries in the cache corresponding to entries in the Map, or the Map itself, is
    * changed or removed during this operation, then the behavior of this method is
    * not defined.
    *
    * If default consistency mode is enabled, then each put is atomic but not
    * the entire putAll operation. Listeners can observe individual updates.
    *
    * @param map the Map that contains the entries to be copied to the cache
    * @return void
    * @throws NullPointerException if the map is null or contains null keys or values.
    * @throws IllegalStateException if the cache is in a closed state
    * @throws CacheException if there is a problem with the cache.
    */
    Mono<Void> putAll(java.util.Map<? extends K, ? extends V> map);
    
    /**
    * This method places the given key and value in the cache atomically, if the key is
    * not already associated with a value in the cache.
    *
    * @param key the key to place in the cache
    * @param value the value to associate with the given key
    * @return true if the value was successfully placed in the cache
    * @throws NullPointerException if the key or value is null
    * @throws IllegalStateException if the cache is in a closed state
    * @throws CacheException if there is a problem with the cache
    */
    Mono<Boolean> putIfAbsent(K key, V value);
    
    /**
    * This method deletes the mapping for a given key from the cache, if it is present.
    *
    * This occurs if and only if there is a mapping from key k to
    * value v such that
    * (key==null ? k==null : key.equals(k)).
    *
    *
    This method returns true if the removal was successful,
    * or false if there was no such mapping.
    *
    *
    * @param key the key whose mapping will be deleted
    * @return returns true if successful, or false if there was no mapping
    * @throws NullPointerException if the key is null
    * @throws IllegalStateException if the cache is in a closed state
    * @throws CacheException if there is a problem with the cache
    */
    Mono<Boolean> remove(K key);
    
    /**
    * This method atomically removes a key's mapping only if it is currently mapped to the
    * provided value.
    *
    * @param key the key whose mapping will be deleted
    * @param oldValue the value that should be mapped to the given key
    * @return returns true if successful, or false if there was no such mapping
    * @throws NullPointerException if the key is null
    * @throws IllegalStateException if the cache is in a closed state
    * @throws CacheException if there is a problem with the cache
    */
    Mono<Boolean> remove(K key, V oldValue);
    
    /**
    * This method atomically removes the entry for a key only if it is currently mapped to some
    * value.
    *
    * @param key the given key
    * @return the value if it existed, or null if it did not
    * @throws NullPointerException if the key is null.
    * @throws IllegalStateException if the cache is in a closed state
    * @throws CacheException if there is a problem with the cache
    */
    Mono<V> getAndRemove(K key);
    
    /**
    * This method atomically replaces an entry only if the key is currently mapped to a
    * given value.
    *
    * @param key the key associated with the given oldValue
    * @param oldValue the value that should be associated with the key
    * @param newValue the value that will be associated with the key
    * @return true if the value was replaced, or false if not
    * @throws NullPointerException if the key or values are null
    * @throws IllegalStateException if the cache is in a closed state
    * @throws CacheException if there is a problem with the cache
    */
    Mono<Boolean> replace(K key, V oldValue, V newValue);
    
    /**
    * This method atomically replaces an entry only if the key is currently mapped to some
    * value.
    *
    * @param key the key mapped to the given value
    * @param value the value mapped to the given key
    * @return true if the value was replaced, or false if not
    * @throws NullPointerException if the key or value is null
    * @throws IllegalStateException if the cache is in a closed state
    * @throws CacheException if there is a problem with the cache
    */
    Mono<Boolean> replace(K key, V value);
    
    /**
    * This method atomically replaces a given key's value if and only if the key is currently
    * mapped to a value.
    *
    * @param key the key associated with the given value
    * @param value the value associated with the given key
    * @return the previous value mapped to the given key, or
    * null if there was no such mapping.
    * @throws NullPointerException if the key or value is null
    * @throws IllegalStateException if the cache is in a closed state
    * @throws CacheException if there is a problem with the cache
    */
    Mono<V> getAndReplace(K key, V value);
    
    /**
    * This method deletes the entries for the given keys.
    *
    * The order in which the individual entries are removed is undefined.
    *
    * For every entry in the key set, the following are called:
    *
    •   any registered {@link CacheEntryRemovedListener}s
    •   if the cache is a write-through cache, the {@link CacheWriter}
    * If the key set is empty, the {@link CacheWriter} is not called.
    *
    * @param keys the keys to remove
    * @return void
    * @throws NullPointerException if keys is null or if it contains a null key
    * @throws IllegalStateException if the cache is in a closed state
    * @throws CacheException if there is a problem with the cache
    */
    Mono<Void> removeAll(Set<? extends K> keys);

    /**
    * This method empties the cache's contents, without notifying listeners or
    * {@link CacheWriter}s.
    *
    * @return void
    * @throws IllegalStateException if the cache is in a closed state
    * @throws CacheException if there is a problem with the cache
    */
    Mono<Void> clear();

    
}
