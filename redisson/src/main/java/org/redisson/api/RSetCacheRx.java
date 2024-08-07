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
import java.util.Set;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.core.Single;

/**
 * RxJava2 interface for RSetCache object
 *
 * @author Nikita Koksharov
 *
 * @param <V> value
 */
public interface RSetCacheRx<V> extends RCollectionRx<V>, RDestroyable {

    /**
     * Returns <code>RPermitExpirableSemaphore</code> instance associated with <code>value</code>
     * 
     * @param value - set value
     * @return RPermitExpirableSemaphore object
     */
    RPermitExpirableSemaphoreRx getPermitExpirableSemaphore(V value);

    /**
     * Returns <code>RSemaphore</code> instance associated with <code>value</code>
     * 
     * @param value - set value
     * @return RSemaphore object
     */
    RSemaphoreRx getSemaphore(V value);
    
    /**
     * Returns <code>RLock</code> instance associated with <code>value</code>
     * 
     * @param value - set value
     * @return RLock object
     */
    RLockRx getFairLock(V value);
    
    /**
     * Returns <code>RReadWriteLock</code> instance associated with <code>value</code>
     * 
     * @param value - set value
     * @return RReadWriteLock object
     */
    RReadWriteLockRx getReadWriteLock(V value);
    
    /**
     * Returns lock instance associated with <code>value</code>
     * 
     * @param value - set value
     * @return RLock object
     */
    RLockRx getLock(V value);
    
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
    Single<Boolean> add(V value, long ttl, TimeUnit unit);

    /**
     * Returns the number of elements in cache.
     * This number can reflects expired elements too
     * due to non realtime cleanup process.
     *
     */
    @Override
    Single<Integer> size();

    /**
     * Read all elements at once
     *
     * @return values
     */
    Single<Set<V>> readAll();

    /**
     * Tries to add elements only if none of them in set.
     *
     * @param values - values to add
     * @return <code>true</code> if elements successfully added,
     *          otherwise <code>false</code>.
     */
    Single<Boolean> tryAdd(V... values);

    /**
     * Use {@link #addIfAbsent(Duration, Object)} instead
     *
     * @param values - values to add
     * @param ttl - time to live for value.
     *              If <code>0</code> then stores infinitely.
     * @param unit - time unit
     * @return <code>true</code> if elements successfully added,
     *          otherwise <code>false</code>.
     */
    @Deprecated
    Single<Boolean> tryAdd(long ttl, TimeUnit unit, V... values);

    /**
     * Adds element to this set only if has not been added before.
     * <p>
     * Requires <b>Redis 3.0.2 and higher.</b>
     *
     * @param ttl - object ttl
     * @param object - object itself
     * @return <code>true</code> if element added and <code>false</code> if not.
     */
    Single<Boolean> addIfAbsent(Duration ttl, V object);

    /**
     * Adds element to this set only if it's already exists.
     * <p>
     * Requires <b>Redis 3.0.2 and higher.</b>
     *
     * @param ttl - object ttl
     * @param object - object itself
     * @return <code>true</code> if element added and <code>false</code> if not.
     */
    Single<Boolean> addIfExists(Duration ttl, V object);

    /**
     * Adds element to this set only if new ttl less than current ttl of existed element.
     * <p>
     * Requires <b>Redis 6.2.0 and higher.</b>
     *
     * @param ttl - object ttl
     * @param object - object itself
     * @return <code>true</code> if element added and <code>false</code> if not.
     */
    Single<Boolean> addIfLess(Duration ttl, V object);

    /**
     * Adds element to this set only if new ttl greater than current ttl of existed element.
     * <p>
     * Requires <b>Redis 6.2.0 and higher.</b>
     *
     * @param ttl - object ttl
     * @param object - object itself
     * @return <code>true</code> if element added and <code>false</code> if not.
     */
    Single<Boolean> addIfGreater(Duration ttl, V object);

    /**
     * Adds all elements contained in the specified map to this sorted set.
     * Map contains of ttl mapped by object.
     *
     * @param objects - map of elements to add
     * @return amount of added elements, not including already existing in this sorted set
     */
    Single<Integer> addAll(Map<V, Duration> objects);

    /**
     * Adds elements to this set only if they haven't been added before.
     * <p>
     * Requires <b>Redis 3.0.2 and higher.</b>
     *
     * @param objects map of elements to add
     * @return amount of added elements
     */
    Single<Integer> addAllIfAbsent(Map<V, Duration> objects);

    /**
     * Adds elements to this set only if they already exist.
     * <p>
     * Requires <b>Redis 3.0.2 and higher.</b>
     *
     * @param objects map of elements to add
     * @return amount of added elements
     */
    Single<Integer> addAllIfExist(Map<V, Duration> objects);

    /**
     * Adds elements to this set only if new ttl greater than current ttl of existed elements.
     * <p>
     * Requires <b>Redis 6.2.0 and higher.</b>
     *
     * @param objects map of elements to add
     * @return amount of added elements
     */
    Single<Integer> addAllIfGreater(Map<V, Duration> objects);

    /**
     * Adds elements to this set only if new ttl less than current ttl of existed elements.
     * <p>
     * Requires <b>Redis 6.2.0 and higher.</b>
     *
     * @param objects map of elements to add
     * @return amount of added elements
     */
    Single<Integer> addAllIfLess(Map<V, Duration> objects);

    /**
     * Adds object event listener
     *
     * @see org.redisson.api.listener.TrackingListener
     * @see org.redisson.api.listener.SetAddListener
     * @see org.redisson.api.listener.SetRemoveListener
     * @see org.redisson.api.ExpiredObjectListener
     * @see org.redisson.api.DeletedObjectListener
     *
     * @param listener - object event listener
     * @return listener id
     */
    Single<Integer> addListener(ObjectListener listener);

}
