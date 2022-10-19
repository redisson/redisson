/**
 * Copyright (c) 2013-2022 Nikita Koksharov
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
import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * Object holder. Max size of object is 512MB
 *
 * @author Nikita Koksharov
 *
 * @param <V> - the type of object
 */
public interface RBucket<V> extends RExpirable, RBucketAsync<V> {

    /**
     * Returns size of object in bytes.
     * 
     * @return object size
     */
    long size();
    
    /**
     * Retrieves element stored in the holder.
     * 
     * @return element
     */
    V get();

    /**
     * Retrieves element in the holder and removes it.
     * 
     * @return element
     */
    V getAndDelete();

    /**
     * Use {@link #setIfAbsent(Object)} instead
     *
     * @param value - value to set
     * @return {@code true} if successful, or {@code false} if
     *         element was already set
     */
    @Deprecated
    boolean trySet(V value);

    /**
     * Use {@link #setIfAbsent(Object, Duration)} instead
     * 
     * @param value - value to set
     * @param timeToLive - time to live interval
     * @param timeUnit - unit of time to live interval
     * @return {@code true} if successful, or {@code false} if
     *         element was already set
     */
    @Deprecated
    boolean trySet(V value, long timeToLive, TimeUnit timeUnit);

    /**
     * Sets value only if object holder doesn't exist.
     *
     * @param value - value to set
     * @return {@code true} if successful, or {@code false} if
     *         element was already set
     */
    boolean setIfAbsent(V value);

    /**
     * Sets value with defined duration only if object holder doesn't exist.
     *
     * @param value value to set
     * @param duration expiration duration
     * @return {@code true} if successful, or {@code false} if
     *         element was already set
     */
    boolean setIfAbsent(V value, Duration duration);

    /**
     * Sets value only if object holder already exists.
     *
     * @param value - value to set
     * @return {@code true} if successful, or {@code false} if
     *         element wasn't set
     */
    boolean setIfExists(V value);

    /**
     * Sets value only if object holder already exists.
     *
     * @param value - value to set
     * @param timeToLive - time to live interval
     * @param timeUnit - unit of time to live interval
     * @return {@code true} if successful, or {@code false} if
     *         element wasn't set
     */
    boolean setIfExists(V value, long timeToLive, TimeUnit timeUnit);

    /**
     * Atomically sets the value to the given updated value
     * only if serialized state of the current value equals 
     * to serialized state of the expected value.
     *
     * @param expect the expected value
     * @param update the new value
     * @return {@code true} if successful; or {@code false} if the actual value
     *         was not equal to the expected value.
     */
    boolean compareAndSet(V expect, V update);

    /**
     * Retrieves current element in the holder and replaces it with <code>newValue</code>. 
     * 
     * @param newValue - value to set
     * @return previous value
     */
    V getAndSet(V newValue);

    /**
     * Retrieves current element in the holder and replaces it with <code>newValue</code> with defined <code>timeToLive</code> interval. 
     * 
     * @param value - value to set
     * @param timeToLive - time to live interval
     * @param timeUnit - unit of time to live interval
     * @return previous value
     */
    V getAndSet(V value, long timeToLive, TimeUnit timeUnit);

    /**
     * Retrieves current element in the holder and sets an expiration duration for it.
     * <p>
     * Requires <b>Redis 6.2.0 and higher.</b>
     *
     * @param duration of object time to live interval
     * @return element
     */
    V getAndExpire(Duration duration);

    /**
     * Retrieves current element in the holder and sets an expiration date for it.
     * <p>
     * Requires <b>Redis 6.2.0 and higher.</b>
     *
     * @param time of exact object expiration moment
     * @return element
     */
    V getAndExpire(Instant time);

    /**
     * Retrieves current element in the holder and clears expiration date set before.
     * <p>
     * Requires <b>Redis 6.2.0 and higher.</b>
     *
     * @return element
     */
    V getAndClearExpire();

    /**
     * Stores element into the holder. 
     * 
     * @param value - value to set
     */
    void set(V value);

    /**
     * Stores element into the holder with defined <code>timeToLive</code> interval.
     * 
     * @param value - value to set
     * @param timeToLive - time to live interval
     * @param timeUnit - unit of time to live interval
     */
    void set(V value, long timeToLive, TimeUnit timeUnit);

    /**
     * Set value and keep existing TTL.
     * <p>
     * Requires <b>Redis 6.0.0 and higher.</b>
     *
     * @param value - value to set
     */
    void setAndKeepTTL(V value);

    /**
     * Adds object event listener
     *
     * @see org.redisson.api.ExpiredObjectListener
     * @see org.redisson.api.DeletedObjectListener
     * @see org.redisson.api.listener.SetObjectListener
     *
     * @param listener - object event listener
     * @return listener id
     */
    int addListener(ObjectListener listener);

}
