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

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import java.time.Instant;
import org.redisson.api.map.MapWriter;
import org.redisson.api.map.PutArgs;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

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
public interface RMapCacheNativeRx<K, V> extends RMapRx<K, V>, RDestroyable {

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
    Maybe<V> put(K key, V value, Duration ttl);

    /**
     * Stores value mapped by key with specified time to live.
     * Entry expires after specified time to live.
     * <p>
     * If the map previously contained a mapping for
     * the key, the old value is replaced by the specified value.
     *
     * @param key - map key
     * @param value - map value
     * @param time - time expire date
     * @return previous associated value
     */
    Maybe<V> put(K key, V value, Instant time);

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
    Single<Boolean> fastPut(K key, V value, Duration ttl);

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
     * @param time - time expire date
     *
     * @return <code>true</code> if key is a new key in the hash and value was set.
     *         <code>false</code> if key already exists in the hash and the value was updated.
     */
    Single<Boolean> fastPut(K key, V value, Instant time);

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
    Maybe<V> putIfAbsent(K key, V value, Duration ttl);

    /**
     * If the specified key is not already associated
     * with a value, associate it with the given value.
     * <p>
     * Stores value mapped by key with specified time to live.
     * Entry expires after specified time to live.
     *
     * @param key - map key
     * @param value - map value
     * @param time - time expire date
     *
     * @return current associated value
     */
    Maybe<V> putIfAbsent(K key, V value, Instant time);

    /**
     * Stores the specified {@code value} mapped by {@code key}
     * only if mapping already exists.
     * <p>
     * Specified time to live starts from the moment this method call was completed.
     *
     * @param key - map key
     * @param value - map value
     * @param ttl - time to live
     * @return previous associated value
     *         or {@code null} if key doesn't exist
     */
    Maybe<V> putIfExist(K key, V value, Duration ttl);

    /**
     * Stores the specified {@code value} mapped by {@code key}
     * only if mapping already exists.
     * <p>
     * Entry expires at specified instant.
     *
     * @param key - map key
     * @param value - map value
     * @param time - expiration instant
     * @return previous associated value
     *         or {@code null} if key doesn't exist
     */
    Maybe<V> putIfExist(K key, V value, Instant time);

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
    Single<Boolean> fastPutIfAbsent(K key, V value, Duration ttl);

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
     * @param time - time expire date
     *
     * @return <code>true</code> if key is a new key in the hash and value was set.
     *         <code>false</code> if key already exists in the hash
     */
    Single<Boolean> fastPutIfAbsent(K key, V value, Instant time);

    /**
     * Remaining time to live of map entry associated with a <code>key</code>.
     *
     * @param key map key
     * @return time in milliseconds
     *          -2 if the key does not exist.
     *          -1 if the key exists but has no associated expire.
     */
    Single<Long> remainTimeToLive(K key);

    /**
     * Remaining time to live of map entries associated with <code>keys</code>.
     *
     * @param keys map keys
     * @return Time to live mapped by key.
     *          Time in milliseconds
     *          -2 if the key does not exist.
     *          -1 if the key exists but has no associated expire.
     */
    Single<Map<K, Long>> remainTimeToLive(Set<K> keys);

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
    Completable putAll(java.util.Map<? extends K, ? extends V> map, Duration ttl);

    /**
     * Associates the specified <code>value</code> with the specified <code>key</code>
     * in batch.
     * <p>
     * If {@link MapWriter} is defined then new map entries will be stored in write-through mode.
     *
     * @param map - mappings to be stored in this map
     * @param time - time expire date for all key\value entries
     */
    Completable putAll(java.util.Map<? extends K, ? extends V> map, Instant time);

    /**
     * Stores the specified entries only if all specified keys already exist.
     * <p>
     * Requires <b>Redis 8.0.0 and higher.</b> or <b>Valkey 9.0.0 and higher.</b>
     *
     * @param args put arguments
     * @return {@code true} if all entries were set, {@code false} otherwise
     */
    Single<Boolean> putIfAllKeysExist(PutArgs<K, V> args);

    /**
     * Stores the specified entries only if none of the specified keys exist.
     * <p>
     * Requires <b>Redis 8.0.0 and higher.</b> or <b>Valkey 9.0.0 and higher.</b>
     *
     * @param args put arguments
     * @return {@code true} if all entries were set, {@code false} otherwise
     */
    Single<Boolean> putIfAllKeysAbsent(PutArgs<K, V> args);

    /**
     * Clears an expiration timeout or date of specified entry by key.
     *
     * @param key map key
     * @return <code>true</code> if timeout was removed
     *         <code>false</code> if entry does not have an associated timeout
     *         <code>null</code> if entry does not exist
     */
    Maybe<Boolean> clearExpire(K key);

    /**
     * Clears an expiration timeout or date of specified entries by keys.
     *
     * @param keys map keys
     * @return Boolean mapped by key.
     *         <code>true</code> if timeout was removed
     *         <code>false</code> if entry does not have an associated timeout
     *         <code>null</code> if entry does not exist
     */
    Single<Map<K, Boolean>> clearExpire(Set<K> keys);

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
    Single<Boolean> expireEntry(K key, Duration ttl);

    /**
     * Updates time to live of specified entry by key.
     * Entry expires when specified time to live was reached.
     * <p>
     * Returns <code>false</code> if entry already expired or doesn't exist,
     * otherwise returns <code>true</code>.
     *
     * @param key map key
     * @param time time expire date
     * <p>
     *
     * @return returns <code>false</code> if entry already expired or doesn't exist,
     *         otherwise returns <code>true</code>.
     */
    Single<Boolean> expireEntry(K key, Instant time);

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
    Single<Boolean> expireEntryIfNotSet(K key, Duration ttl);

    /**
     * Sets time to live of specified entry by key.
     * If these parameters weren't set before.
     * Entry expires when specified time to live was reached.
     * <p>
     * Returns <code>false</code> if entry already has expiration time or doesn't exist,
     * otherwise returns <code>true</code>.
     *
     * @param key map key
     * @param time time expire date
     * <p>
     *
     * @return returns <code>false</code> if entry already has expiration time or doesn't exist,
     *         otherwise returns <code>true</code>.
     */
    Single<Boolean> expireEntryIfNotSet(K key, Instant time);

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
    Single<Boolean> expireEntryIfGreater(K key, Duration ttl);


    /**
     * Sets time to live of specified entry by key only if it's greater than timeout set before.
     * Entry expires when specified time to live was reached.
     * <p>
     * Returns <code>false</code> if entry already has expiration time or doesn't exist,
     * otherwise returns <code>true</code>.
     *
     * @param key map key
     * @param time time expire date
     * <p>
     *
     * @return returns <code>false</code> if entry already has expiration time or doesn't exist,
     *         otherwise returns <code>true</code>.
     */
    Single<Boolean> expireEntryIfGreater(K key, Instant time);

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
    Single<Boolean> expireEntryIfLess(K key, Duration ttl);

    /**
     * Sets time to live of specified entry by key only if it's less than timeout set before.
     * Entry expires when specified time to live was reached.
     * <p>
     * Returns <code>false</code> if entry already has expiration time or doesn't exist,
     * otherwise returns <code>true</code>.
     *
     * @param key map key
     * @param time time expire date
     * <p>
     *
     * @return returns <code>false</code> if entry already has expiration time or doesn't exist,
     *         otherwise returns <code>true</code>.
     */
    Single<Boolean> expireEntryIfLess(K key, Instant time);

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
    Single<Integer> expireEntries(Set<K> keys, Duration ttl);

    /**
     * Updates time to live of specified entries by keys.
     * Entries expires when specified time to live was reached.
     * <p>
     * Returns amount of updated entries.
     *
     * @param keys map keys
     * @param time time expire date for key\value entries.
     * <p>
     *
     * @return amount of updated entries.
     */
    Single<Integer> expireEntries(Set<K> keys, Instant time);

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
    Single<Integer> expireEntriesIfGreater(Set<K> keys, Duration ttl);

    /**
     * Sets time to live of specified entries by keys only if it's greater than timeout set before.
     * Entries expire when specified time to live was reached.
     * <p>
     * Returns amount of updated entries.
     *
     * @param keys map keys
     * @param time time expire date for key\value entry.
     * <p>
     *
     * @return amount of updated entries.
     */
    Single<Integer> expireEntriesIfGreater(Set<K> keys, Instant time);

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
    Single<Integer> expireEntriesIfLess(Set<K> keys, Duration ttl);

    /**
     * Sets time to live of specified entries by keys only if it's less than timeout set before.
     * Entries expire when specified time to live was reached.
     * <p>
     * Returns amount of updated entries.
     *
     * @param keys map keys
     * @param time time expire date for key\value entry.
     * <p>
     *
     * @return amount of updated entries.
     */
    Single<Integer> expireEntriesIfLess(Set<K> keys, Instant time);

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
    Single<Integer> expireEntriesIfNotSet(Set<K> keys, Duration ttl);

    /**
     * Sets time to live of specified entries by keys.
     * If these parameters weren't set before.
     * Entries expire when specified time to live was reached.
     * <p>
     * Returns amount of updated entries.
     *
     * @param keys map keys
     * @param time time expire date for key\value entry.
     * <p>
     *
     * @return amount of updated entries.
     */
    Single<Integer> expireEntriesIfNotSet(Set<K> keys, Instant time);

    /**
     * Adds object event listener
     *
     * @see org.redisson.api.listener.TrackingListener
     * @see org.redisson.api.listener.MapPutListener
     * @see org.redisson.api.listener.MapRemoveListener
     * @see org.redisson.api.listener.MapExpiredListener
     * @see org.redisson.api.ExpiredObjectListener
     * @see org.redisson.api.DeletedObjectListener
     *
     * @param listener object event listener
     * @return listener id
     */
    Single<Integer> addListener(ObjectListener listener);

    /**
     * If the specified key is not already associated
     * with a value, attempts to compute its value using the given mapping function and enters it into this map .
     * <p>
     * Stores value mapped by key with specified time to live.
     * Entry expires after specified time to live.
     *
     * @param key - map key
     * @param ttl - time to live for key\value entry.
     *              If <code>0</code> then stores infinitely.
     * @param mappingFunction the mapping function to compute a value
     * @return current associated value
     */
    Maybe<V> computeIfAbsent(K key, Duration ttl, Function<? super K, ? extends V> mappingFunction);

    /**
     * If the specified key is not already associated
     * with a value, attempts to compute its value using the given mapping function and enters it into this map .
     * <p>
     * Stores value mapped by key with specified time to live.
     * Entry expires after specified time to live.
     *
     * @param key - map key
     * @param time - time expire date
     * @param mappingFunction the mapping function to compute a value
     * @return current associated value
     */
    Maybe<V> computeIfAbsent(K key, Instant time, Function<? super K, ? extends V> mappingFunction);

    /**
     * Computes a new mapping for the specified key and its current mapped value.
     * <p>
     * Stores value mapped by key with specified time to live.
     * Entry expires after specified time to live.
     *
     * @param key - map key
     * @param ttl - time to live for key\value entry.
     *              If <code>0</code> then stores infinitely.
     * @param remappingFunction - function to compute a value
     * @return the new value associated with the specified key, or {@code null} if none
     */
    Maybe<V> compute(K key, Duration ttl, BiFunction<? super K, ? super V, ? extends V> remappingFunction);

    /**
     * Computes a new mapping for the specified key and its current mapped value.
     * <p>
     * Stores value mapped by key with specified time to live.
     * Entry expires after specified time to live.
     *
     * @param key - map key
     * @param time - time expire date
     * @param remappingFunction - function to compute a value
     * @return the new value associated with the specified key, or {@code null} if none
     */
    Maybe<V> compute(K key, Instant time, BiFunction<? super K, ? super V, ? extends V> remappingFunction);
}
