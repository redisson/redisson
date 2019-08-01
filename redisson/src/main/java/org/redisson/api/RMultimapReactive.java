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

import java.util.Set;

import reactor.core.publisher.Mono;

/**
 * Reactive interface for Multimap object
 * 
 * @author Nikita Koksharov
 *
 * @param <K> key type
 * @param <V> value type
 */
public interface RMultimapReactive<K, V> extends RExpirableReactive {

    /**
     * Returns the number of key-value pairs in this multimap.
     *
     * @return size of multimap
     */
    Mono<Integer> size();

    /**
     * Returns {@code true} if this multimap contains at least one key-value pair
     * with the key {@code key}.
     * 
     * @param key - map key
     * @return <code>true</code> if contains a key
     */
    Mono<Boolean> containsKey(Object key);

    /**
     * Returns {@code true} if this multimap contains at least one key-value pair
     * with the value {@code value}.
     * 
     * @param value - map value
     * @return <code>true</code> if contains a value
     */
    Mono<Boolean> containsValue(Object value);

    /**
     * Returns {@code true} if this multimap contains at least one key-value pair
     * with the key {@code key} and the value {@code value}.
     * 
     * @param key - map key
     * @param value - map value
     * @return <code>true</code> if contains an entry
     */
    Mono<Boolean> containsEntry(Object key, Object value);

    /**
     * Stores a key-value pair in this multimap.
     *
     * <p>Some multimap implementations allow duplicate key-value pairs, in which
     * case {@code put} always adds a new key-value pair and increases the
     * multimap size by 1. Other implementations prohibit duplicates, and storing
     * a key-value pair that's already in the multimap has no effect.
     *
     * @param key - map key
     * @param value - map value
     * @return {@code true} if the method increased the size of the multimap, or
     *     {@code false} if the multimap already contained the key-value pair and
     *     doesn't allow duplicates
     */
    Mono<Boolean> put(K key, V value);

    /**
     * Removes a single key-value pair with the key {@code key} and the value
     * {@code value} from this multimap, if such exists. If multiple key-value
     * pairs in the multimap fit this description, which one is removed is
     * unspecified.
     *
     * @param key - map key
     * @param value - map value
     * @return {@code true} if the multimap changed
     */
    Mono<Boolean> remove(Object key, Object value);

    // Bulk Operations

    /**
     * Stores a key-value pair in this multimap for each of {@code values}, all
     * using the same key, {@code key}. Equivalent to (but expected to be more
     * efficient than): <pre>   {@code
     *
     *   for (V value : values) {
     *     put(key, value);
     *   }}</pre>
     *
     * <p>In particular, this is a no-op if {@code values} is empty.
     *
     * @param key - map key
     * @param values - map values
     * @return {@code true} if the multimap changed
     */
    Mono<Boolean> putAll(K key, Iterable<? extends V> values);

    /**
     * Returns the number of key-value pairs in this multimap.
     *
     * @return keys amount
     */
    Mono<Integer> keySize();

    /**
     * Removes <code>keys</code> from map by one operation
     *
     * Works faster than <code>RMultimap.remove</code> but not returning
     * the value associated with <code>key</code>
     *
     * @param keys - map keys
     * @return the number of keys that were removed from the hash, not including specified but non existing keys
     */
    Mono<Long> fastRemove(K... keys);

    /**
     * Read all keys at once
     *
     * @return keys
     */
    Mono<Set<K>> readAllKeySet();

    
}
