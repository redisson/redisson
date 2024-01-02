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

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Async set functions
 *
 * @author Nikita Koksharov
 *
 * @param <V> value
 */
public interface RSetCacheAsync<V> extends RSetAsync<V> {

    /**
     * Stores value with specified time to live.
     * Value expires after specified time to live.
     *
     * @param value to add
     * @param ttl - time to live for key\value entry.
     *              If <code>0</code> then stores infinitely.
     * @param unit - time unit
     * @return <code>true</code> if value has been added. <code>false</code>
     *          if value already been in collection.
     */
    RFuture<Boolean> addAsync(V value, long ttl, TimeUnit unit);

    /**
     * Returns the number of elements in cache.
     * This number can reflects expired elements too
     * due to non realtime cleanup process.
     *
     * @return size of set
     */
    @Override
    RFuture<Integer> sizeAsync();

    /**
     * Use {@link #addIfAbsentAsync(Duration, Object)} instead
     *
     * @param values - values to add
     * @param ttl - time to live for value.
     *              If <code>0</code> then stores infinitely.
     * @param unit - time unit
     * @return <code>true</code> if elements successfully added,
     *          otherwise <code>false</code>.
     */
    @Deprecated
    RFuture<Boolean> tryAddAsync(long ttl, TimeUnit unit, V... values);

    /**
     * Adds element to this set only if has not been added before.
     * <p>
     * Requires <b>Redis 3.0.2 and higher.</b>
     *
     * @param ttl - object ttl
     * @param object - object itself
     * @return <code>true</code> if element added and <code>false</code> if not.
     */
    RFuture<Boolean> addIfAbsentAsync(Duration ttl, V object);

    /**
     * Adds element to this set only if it's already exists.
     * <p>
     * Requires <b>Redis 3.0.2 and higher.</b>
     *
     * @param ttl - object ttl
     * @param object - object itself
     * @return <code>true</code> if element added and <code>false</code> if not.
     */
    RFuture<Boolean> addIfExistsAsync(Duration ttl, V object);

    /**
     * Adds element to this set only if new ttl less than current ttl of existed element.
     * <p>
     * Requires <b>Redis 6.2.0 and higher.</b>
     *
     * @param ttl - object ttl
     * @param object - object itself
     * @return <code>true</code> if element added and <code>false</code> if not.
     */
    RFuture<Boolean> addIfLessAsync(Duration ttl, V object);

    /**
     * Adds element to this set only if new ttl greater than current ttl of existed element.
     * <p>
     * Requires <b>Redis 6.2.0 and higher.</b>
     *
     * @param ttl - object ttl
     * @param object - object itself
     * @return <code>true</code> if element added and <code>false</code> if not.
     */
    RFuture<Boolean> addIfGreaterAsync(Duration ttl, V object);

    /**
     * Adds all elements contained in the specified map to this sorted set.
     * Map contains of ttl mapped by object.
     *
     * @param objects - map of elements to add
     * @return amount of added elements, not including already existing in this sorted set
     */
    RFuture<Integer> addAllAsync(Map<V, Duration> objects);

    /**
     * Adds elements to this set only if they haven't been added before.
     * <p>
     * Requires <b>Redis 3.0.2 and higher.</b>
     *
     * @param objects map of elements to add
     * @return amount of added elements
     */
    RFuture<Integer> addAllIfAbsentAsync(Map<V, Duration> objects);

    /**
     * Adds elements to this set only if they already exist.
     * <p>
     * Requires <b>Redis 3.0.2 and higher.</b>
     *
     * @param objects map of elements to add
     * @return amount of added elements
     */
    RFuture<Integer> addAllIfExistAsync(Map<V, Duration> objects);

    /**
     * Adds elements to this set only if new ttl greater than current ttl of existed elements.
     * <p>
     * Requires <b>Redis 6.2.0 and higher.</b>
     *
     * @param objects map of elements to add
     * @return amount of added elements
     */
    RFuture<Integer> addAllIfGreaterAsync(Map<V, Duration> objects);

    /**
     * Adds elements to this set only if new ttl less than current ttl of existed elements.
     * <p>
     * Requires <b>Redis 6.2.0 and higher.</b>
     *
     * @param objects map of elements to add
     * @return amount of added elements
     */
    RFuture<Integer> addAllIfLessAsync(Map<V, Duration> objects);

}
