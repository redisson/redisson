/**
 * Copyright (c) 2013-2026 Nikita Koksharov
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

import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Reactive API for lease-based cache operations.
 * <p>
 * Lease token is a millisecond timestamp (epoch millis).
 *
 * @author nhancdt2602
 *
 * @param <K> key type
 * @param <V> value type
 */
public interface RLeasedMapReactive<K, V> {

    /**
     * Returns the cached value mapped by defined {@code key} or {@code null} if value is absent.
     * <p>
     * If value is absent then tries to acquire a lease and returns it together with {@code null} value.
     * Lease is automatically released after {@code leaseTimeToLive} timeout.
     *
     * @param key map key
     * @param leaseTimeToLive lease time to live
     * @return cached value or lease on miss
     */
    Mono<LeaseGetResult<K, V>> getWithLease(K key, Duration leaseTimeToLive);

    /**
     * Invalidates the entry mapped by {@code key} and deletes current lease token (if any).
     *
     * @param key map key
     * @return {@code true} if entry or lease token has been removed, otherwise {@code false}
     */
    Mono<Boolean> removeWithLease(K key);

    /**
     * Stores the specified {@code value} mapped by {@code key} only if the given {@code leaseToken} is still valid.
     *
     * @param key map key
     * @param value map value
     * @param leaseToken lease token (millisecond timestamp) returned by {@link #getWithLease(Object, Duration)}
     * @return {@code true} if value has been stored, otherwise {@code false}
     */
    Mono<Boolean> putWithLease(K key, V value, long leaseToken);

    /**
     * Stores the specified {@code value} mapped by {@code key} only if the given {@code leaseToken} is still valid.
     *
     * @param key map key
     * @param value map value
     * @param ttl time to live for key/value entry. If {@link Duration#ZERO} then stores infinitely.
     * @param leaseToken lease token (millisecond timestamp) returned by {@link #getWithLease(Object, Duration)}
     * @return {@code true} if value has been stored, otherwise {@code false}
     */
    Mono<Boolean> putWithLease(K key, V value, Duration ttl, long leaseToken);

    /**
     * Stores the specified {@code value} mapped by {@code key} only if the given {@code leaseToken} is still valid.
     *
     * @param key map key
     * @param value map value
     * @param ttl time to live for key/value entry. If {@link Duration#ZERO} then stores infinitely.
     * @param maxIdleTime max idle time for key/value entry. If {@link Duration#ZERO} then doesn't affect expiration.
     * @param leaseToken lease token (millisecond timestamp) returned by {@link #getWithLease(Object, Duration)}
     * @return {@code true} if value has been stored, otherwise {@code false}
     */
    Mono<Boolean> putWithLease(K key, V value, Duration ttl, Duration maxIdleTime, long leaseToken);
}

