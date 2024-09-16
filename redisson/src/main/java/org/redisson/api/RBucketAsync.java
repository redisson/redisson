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
import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * Async implementation of object holder. Max size of object is 512MB
 *
 * @author Nikita Koksharov
 *
 * @param <V> - the type of object
 */
public interface RBucketAsync<V> extends RExpirableAsync {

    /**
     * Returns size of object in bytes
     * 
     * @return object size
     */
    RFuture<Long> sizeAsync();
    
    /**
     * Retrieves element stored in the holder.
     * 
     * @return element
     */
    RFuture<V> getAsync();
    
    /**
     * Retrieves element in the holder and removes it.
     * 
     * @return element
     */
    RFuture<V> getAndDeleteAsync();

    /**
     * Use {@link #setIfAbsentAsync(Object)} instead
     * 
     * @param value - value to set
     * @return {@code true} if successful, or {@code false} if
     *         element was already set
     */
    @Deprecated
    RFuture<Boolean> trySetAsync(V value);

    /**
     * Use {@link #setIfAbsentAsync(Object, Duration)} instead
     * 
     * @param value - value to set
     * @param timeToLive - time to live interval
     * @param timeUnit - unit of time to live interval
     * @return {@code true} if successful, or {@code false} if
     *         element was already set
     */
    @Deprecated
    RFuture<Boolean> trySetAsync(V value, long timeToLive, TimeUnit timeUnit);

    /**
     * Sets value only if object holder doesn't exist.
     *
     * @param value - value to set
     * @return {@code true} if successful, or {@code false} if
     *         element was already set
     */
    RFuture<Boolean> setIfAbsentAsync(V value);

    /**
     * Sets value with defined duration only if object holder doesn't exist.
     *
     * @param value value to set
     * @param duration expiration duration
     * @return {@code true} if successful, or {@code false} if
     *         element was already set
     */
    RFuture<Boolean> setIfAbsentAsync(V value, Duration duration);

    /**
     * Sets value only if it's already exists.
     *
     * @param value - value to set
     * @return {@code true} if successful, or {@code false} if
     *         element wasn't set
     */
    RFuture<Boolean> setIfExistsAsync(V value);

    /**
     * Use {@link #setIfExistsAsync(Object, Duration)} instead
     *
     * @param value - value to set
     * @param timeToLive - time to live interval
     * @param timeUnit - unit of time to live interval
     * @return {@code true} if successful, or {@code false} if
     *         element wasn't set
     */
    RFuture<Boolean> setIfExistsAsync(V value, long timeToLive, TimeUnit timeUnit);

    /**
     * Sets <code>value</code> with expiration <code>duration</code> only if object holder already exists.
     *
     * @param value value to set
     * @param duration expiration duration
     * @return {@code true} if successful, or {@code false} if
     *         element wasn't set
     */
    RFuture<Boolean> setIfExistsAsync(V value, Duration duration);

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
    RFuture<Boolean> compareAndSetAsync(V expect, V update);

    /**
     * Retrieves current element in the holder and replaces it with <code>newValue</code>. 
     * 
     * @param newValue - value to set
     * @return previous value
     */
    RFuture<V> getAndSetAsync(V newValue);

    /**
     * Use {@link #getAndSetAsync(Object, Duration)} instead
     * 
     * @param value - value to set
     * @param timeToLive - time to live interval
     * @param timeUnit - unit of time to live interval
     * @return previous value
     */
    RFuture<V> getAndSetAsync(V value, long timeToLive, TimeUnit timeUnit);

    /**
     * Retrieves current element in the holder and replaces it
     * with <code>value</code> with defined expiration <code>duration</code>.
     *
     * @param value value to set
     * @param duration expiration duration
     * @return previous value
     */
    RFuture<V> getAndSetAsync(V value, Duration duration);

    /**
     * Retrieves current element in the holder and sets an expiration duration for it.
     * <p>
     * Requires <b>Redis 6.2.0 and higher.</b>
     *
     * @param duration of object time to live interval
     * @return element
     */
    RFuture<V> getAndExpireAsync(Duration duration);

    /**
     * Retrieves current element in the holder and sets an expiration date for it.
     * <p>
     * Requires <b>Redis 6.2.0 and higher.</b>
     *
     * @param time of exact object expiration moment
     * @return element
     */
    RFuture<V> getAndExpireAsync(Instant time);

    /**
     * Retrieves current element in the holder and clears expiration date set before.
     * <p>
     * Requires <b>Redis 6.2.0 and higher.</b>
     *
     * @return element
     */
    RFuture<V> getAndClearExpireAsync();

    /**
     * Stores element into the holder. 
     * 
     * @param value - value to set
     * @return void
     */
    RFuture<Void> setAsync(V value);

    /**
     * Use {@link #setAsync(Object, Duration)} instead
     * 
     * @param value - value to set
     * @param timeToLive - time to live interval
     * @param timeUnit - unit of time to live interval
     * @return void
     */
    RFuture<Void> setAsync(V value, long timeToLive, TimeUnit timeUnit);

    /**
     * Stores <code>value</code> into the holder with defined expiration <code>duration</code>.
     *
     * @param value value to set
     * @param duration expiration duration
     */
    RFuture<Void> setAsync(V value, Duration duration);

    /**
     * Set value and keep existing TTL.
     * <p>
     * Requires <b>Redis 6.0.0 and higher.</b>
     *
     * @param value - value to set
     * @return void
     */
    RFuture<Void> setAndKeepTTLAsync(V value);

    /**
     * Adds object event listener
     *
     * @see org.redisson.api.listener.TrackingListener
     * @see org.redisson.api.ExpiredObjectListener
     * @see org.redisson.api.DeletedObjectListener
     * @see org.redisson.api.listener.SetObjectListener
     *
     * @param listener - object event listener
     * @return listener id
     */
    RFuture<Integer> addListenerAsync(ObjectListener listener);

    /**
     * Returns the common part of the data stored in this bucket
     * and a bucket defined by the <code>name</code>
     *
     * @param name second bucket
     * @return common part of the data
     */
    RFuture<V> findCommonAsync(String name);

    /**
     * Returns the length of the common part of the data stored in this bucket
     * and a bucket defined by the <code>name</code>
     *
     * @param name second bucket
     * @return common part of the data
     */
    RFuture<Long> findCommonLengthAsync(String name);

}
