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

import org.redisson.api.map.MapWriter;

import java.time.Duration;
import java.util.Set;

/**
 * Map-based cache with ability to set TTL per entry.
 * Uses Redis native commands for entry expiration and not a scheduled eviction task.
 * <p>
 * Requires <b>Redis 7.4.0 and higher.</b>
 *
 * @author Nikita Koksharov
 *
 * @param <K> key
 * @param <V> value
 */
public interface RMapCacheNative<K, V> extends RMap<K, V>, RMapCacheNativeAsync<K, V> {

    /**
     * Stores value mapped by key with specified time to live.
     * Entry expires after specified time to live.
     * <p>
     * If the map previously contained a mapping for
     * the key, the old value is replaced by the specified value.
     *
     * @param key - map key
     * @param value - map value
     * @param ttl - time to live for key\value entry.
     *              If <code>0</code> then stores infinitely.
     * @return previous associated value
     */
    V put(K key, V value, Duration ttl);

    /**
     * Stores value mapped by key with specified time to live.
     * Entry expires after specified time to live.
     * <p>
     * If the map previously contained a mapping for
     * the key, the old value is replaced by the specified value.
     * <p>
     * Works faster than usual {@link #put(Object, Object, Duration)}
     * as it not returns previous value.
     *
     * @param key - map key
     * @param value - map value
     * @param ttl - time to live for key\value entry.
     *              If <code>0</code> then stores infinitely.
     *
     * @return <code>true</code> if key is a new key in the hash and value was set.
     *         <code>false</code> if key already exists in the hash and the value was updated.
     */
    boolean fastPut(K key, V value, Duration ttl);

    /**
     * If the specified key is not already associated
     * with a value, associate it with the given value.
     * <p>
     * Stores value mapped by key with specified time to live.
     * Entry expires after specified time to live.
     *
     * @param key - map key
     * @param value - map value
     * @param ttl - time to live for key\value entry.
     *              If <code>0</code> then stores infinitely.
     *
     * @return current associated value
     */
    V putIfAbsent(K key, V value, Duration ttl);

    /**
     * If the specified key is not already associated
     * with a value, associate it with the given value.
     * <p>
     * Stores value mapped by key with specified time to live.
     * Entry expires after specified time to live.
     * <p>
     * Works faster than usual {@link #putIfAbsent(Object, Object, Duration)}
     * as it not returns previous value.
     *
     * @param key - map key
     * @param value - map value
     * @param ttl - time to live for key\value entry.
     *              If <code>0</code> then stores infinitely.
     *
     * @return <code>true</code> if key is a new key in the hash and value was set.
     *         <code>false</code> if key already exists in the hash
     */
    boolean fastPutIfAbsent(K key, V value, Duration ttl);

    /**
     * Remaining time to live of map entry associated with a <code>key</code>.
     *
     * @param key - map key
     * @return time in milliseconds
     *          -2 if the key does not exist.
     *          -1 if the key exists but has no associated expire.
     */
    long remainTimeToLive(K key);

    /**
     * Associates the specified <code>value</code> with the specified <code>key</code>
     * in batch.
     * <p>
     * If {@link MapWriter} is defined then new map entries will be stored in write-through mode.
     *
     * @param map - mappings to be stored in this map
     * @param ttl - time to live for all key\value entries.
     *              If <code>0</code> then stores infinitely.
     */
    void putAll(java.util.Map<? extends K, ? extends V> map, Duration ttl);

    /**
     * Clear an expire timeout or expire date of specified entry by key.
     *
     * @param key map key
     * @return <code>true</code> if timeout was removed
     *         <code>false</code> if object does not exist or does not have an associated timeout
     */
    boolean clearExpire(K key);

    /**
     * Updates time to live of specified entry by key.
     * Entry expires when specified time to live was reached.
     * <p>
     * Returns <code>false</code> if entry already expired or doesn't exist,
     * otherwise returns <code>true</code>.
     *
     * @param key map key
     * @param ttl time to live for key\value entry.
     *              If <code>0</code> then time to live doesn't affect entry expiration.
     * <p>
     * if <code>ttl</code> params are equal to <code>0</code>
     * then entry stores infinitely.
     *
     * @return returns <code>false</code> if entry already expired or doesn't exist,
     *         otherwise returns <code>true</code>.
     */
    boolean expireEntry(K key, Duration ttl);

    /**
     * Sets time to live of specified entry by key.
     * If these parameters weren't set before.
     * Entry expires when specified time to live was reached.
     * <p>
     * Returns <code>false</code> if entry already has expiration time or doesn't exist,
     * otherwise returns <code>true</code>.
     *
     * @param key map key
     * @param ttl time to live for key\value entry.
     *              If <code>0</code> then time to live doesn't affect entry expiration.
     * <p>
     * if <code>ttl</code> params are equal to <code>0</code>
     * then entry stores infinitely.
     *
     * @return returns <code>false</code> if entry already has expiration time or doesn't exist,
     *         otherwise returns <code>true</code>.
     */
    boolean expireEntryIfNotSet(K key, Duration ttl);

    /**
     * Sets time to live of specified entry by key only if it's greater than timeout set before.
     * Entry expires when specified time to live was reached.
     * <p>
     * Returns <code>false</code> if entry already has expiration time or doesn't exist,
     * otherwise returns <code>true</code>.
     *
     * @param key map key
     * @param ttl time to live for key\value entry.
     *              If <code>0</code> then time to live doesn't affect entry expiration.
     * <p>
     * if <code>ttl</code> params are equal to <code>0</code>
     * then entry stores infinitely.
     *
     * @return returns <code>false</code> if entry already has expiration time or doesn't exist,
     *         otherwise returns <code>true</code>.
     */
    boolean expireEntryIfGreater(K key, Duration ttl);

    /**
     * Sets time to live of specified entry by key only if it's less than timeout set before.
     * Entry expires when specified time to live was reached.
     * <p>
     * Returns <code>false</code> if entry already has expiration time or doesn't exist,
     * otherwise returns <code>true</code>.
     *
     * @param key map key
     * @param ttl time to live for key\value entry.
     *              If <code>0</code> then time to live doesn't affect entry expiration.
     * <p>
     * if <code>ttl</code> params are equal to <code>0</code>
     * then entry stores infinitely.
     *
     * @return returns <code>false</code> if entry already has expiration time or doesn't exist,
     *         otherwise returns <code>true</code>.
     */
    boolean expireEntryIfLess(K key, Duration ttl);

    /**
     * Updates time to live of specified entries by keys.
     * Entries expires when specified time to live was reached.
     * <p>
     * Returns amount of updated entries.
     *
     * @param keys map keys
     * @param ttl time to live for key\value entries.
     *              If <code>0</code> then time to live doesn't affect entry expiration.
     * <p>
     * if <code>ttl</code> params are equal to <code>0</code>
     * then entries are stored infinitely.
     *
     * @return amount of updated entries.
     */
    int expireEntries(Set<K> keys, Duration ttl);

    /**
     * Sets time to live of specified entries by keys only if it's greater than timeout set before.
     * Entries expire when specified time to live was reached.
     * <p>
     * Returns amount of updated entries.
     *
     * @param keys map keys
     * @param ttl time to live for key\value entry.
     *              If <code>0</code> then time to live doesn't affect entry expiration.
     * <p>
     * if <code>ttl</code> params are equal to <code>0</code>
     * then entry stores infinitely.
     *
     * @return amount of updated entries.
     */
    int expireEntriesIfGreater(Set<K> keys, Duration ttl);

    /**
     * Sets time to live of specified entries by keys only if it's less than timeout set before.
     * Entries expire when specified time to live was reached.
     * <p>
     * Returns amount of updated entries.
     *
     * @param keys map keys
     * @param ttl time to live for key\value entry.
     *              If <code>0</code> then time to live doesn't affect entry expiration.
     * <p>
     * if <code>ttl</code> params are equal to <code>0</code>
     * then entry stores infinitely.
     *
     * @return amount of updated entries.
     */
    int expireEntriesIfLess(Set<K> keys, Duration ttl);

    /**
     * Sets time to live of specified entries by keys.
     * If these parameters weren't set before.
     * Entries expire when specified time to live was reached.
     * <p>
     * Returns amount of updated entries.
     *
     * @param keys map keys
     * @param ttl time to live for key\value entry.
     *              If <code>0</code> then time to live doesn't affect entry expiration.
     * <p>
     * if <code>ttl</code> params are equal to <code>0</code>
     * then entry stores infinitely.
     *
     * @return amount of updated entries.
     */
    int expireEntriesIfNotSet(Set<K> keys, Duration ttl);

}
