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

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.redisson.api.bucket.CompareAndDeleteArgs;
import org.redisson.api.bucket.CompareAndSetArgs;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;


/**
 * Reactive implementation of object holder. Max size of object is 512MB
 *
 * @author Nikita Koksharov
 *
 * @param <V> - the type of object
 */
public interface RBucketRx<V> extends RExpirableRx {

    /**
     * Returns size of object in bytes
     * 
     * @return object size
     */
    Single<Long> size();

    /**
     * Sets value only if object holder doesn't exist.
     *
     * @param value - value to set
     * @return {@code true} if successful, or {@code false} if
     *         element was already set
     */
    Single<Boolean> setIfAbsent(V value);

    /**
     * Sets value with defined duration only if object holder doesn't exist.
     *
     * @param value value to set
     * @param duration expiration duration
     * @return {@code true} if successful, or {@code false} if
     *         element was already set
     */
    Single<Boolean> setIfAbsent(V value, Duration duration);

    /**
     * Use {@link #setIfAbsent(Object)} instead
     * 
     * @param value - value to set
     * @return {@code true} if successful, or {@code false} if
     *         element was already set
     */
    @Deprecated
    Single<Boolean> trySet(V value);

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
    Single<Boolean> trySet(V value, long timeToLive, TimeUnit timeUnit);

    /**
     * Sets value only if it's already exists.
     *
     * @param value - value to set
     * @return {@code true} if successful, or {@code false} if
     *         element wasn't set
     */
    Single<Boolean> setIfExists(V value);

    /**
     * Use {@link #setIfExists(Object, Duration)} instead
     *
     * @param value - value to set
     * @param timeToLive - time to live interval
     * @param timeUnit - unit of time to live interval
     * @return {@code true} if successful, or {@code false} if
     *         element wasn't set
     */
    @Deprecated
    Single<Boolean> setIfExists(V value, long timeToLive, TimeUnit timeUnit);

    /**
     * Sets <code>value</code> with expiration <code>duration</code> only if object holder already exists.
     *
     * @param value value to set
     * @param duration expiration duration
     * @return {@code true} if successful, or {@code false} if
     *         element wasn't set
     */
    Single<Boolean> setIfExists(V value, Duration duration);

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
    Single<Boolean> compareAndSet(V expect, V update);

    /**
     * Atomically sets the value if the condition specified in args is met.
     * <p>
     * Supports multiple comparison modes:
     * <ul>
     *   <li>{@link CompareAndSetArgs#expected(Object)} - compatible with any Redis/Valkey version</li>
     *   <li>{@link CompareAndSetArgs#unexpected(Object)} - compatible with any Redis/Valkey version</li>
     *   <li>{@link CompareAndSetArgs#expectedDigest(String)} - requires Redis 8.4+</li>
     *   <li>{@link CompareAndSetArgs#unexpectedDigest(String)} - requires Redis 8.4+</li>
     * </ul>
     *
     * @param args compare-and-set arguments containing condition and new value
     * @return {@code true} if successful, {@code false} if condition was not met
     */
    Single<Boolean> compareAndSet(CompareAndSetArgs<V> args);

    /**
     * Conditionally deletes the bucket based on value comparison.
     * <p>
     * <ul>
     *    <li> {@link CompareAndDeleteArgs#expected(Object)} - compatible with any Redis/Valkey version</li>
     *    <li> {@link CompareAndDeleteArgs#unexpected(Object)} - compatible with any Redis/Valkey version</li>
     *    <li> {@link CompareAndDeleteArgs#expectedDigest(String)} - requires Redis 8.4+</li>
     *    <li> {@link CompareAndDeleteArgs#unexpectedDigest(String)} - requires Redis 8.4+</li>
     * </ul>
     *
     * @param args comparison arguments
     * @return {@code true} if bucket was deleted, {@code false} otherwise
     */
    Single<Boolean> compareAndDelete(CompareAndDeleteArgs<V> args);

    /**
     * Retrieves current element in the holder and replaces it with <code>newValue</code>. 
     * 
     * @param newValue - value to set
     * @return previous value
     */
    Maybe<V> getAndSet(V newValue);
    
    /**
     * Use {@link #getAndSet(Object, Duration)} instead
     * 
     * @param value - value to set
     * @param timeToLive - time to live interval
     * @param timeUnit - unit of time to live interval
     * @return previous value
     */
    @Deprecated
    Maybe<V> getAndSet(V value, long timeToLive, TimeUnit timeUnit);

    /**
     * Retrieves current element in the holder and replaces it
     * with <code>value</code> with defined expiration <code>duration</code>.
     *
     * @param value value to set
     * @param duration expiration duration
     * @return previous value
     */
    Maybe<V> getAndSet(V value, Duration duration);

    /**
     * Retrieves current element in the holder and sets an expiration duration for it.
     * <p>
     * Requires <b>Redis 6.2.0 and higher.</b>
     *
     * @param duration of object time to live interval
     * @return element
     */
    Maybe<V> getAndExpire(Duration duration);

    /**
     * Retrieves current element in the holder and sets an expiration date for it.
     * <p>
     * Requires <b>Redis 6.2.0 and higher.</b>
     *
     * @param time of exact object expiration moment
     * @return element
     */
    Maybe<V> getAndExpire(Instant time);

    /**
     * Retrieves current element in the holder and clears expiration date set before.
     * <p>
     * Requires <b>Redis 6.2.0 and higher.</b>
     *
     * @return element
     */
    Maybe<V> getAndClearExpire();

    /**
     * Retrieves element stored in the holder.
     * 
     * @return element
     */
    Maybe<V> get();
    
    /**
     * Retrieves element in the holder and removes it.
     * 
     * @return element
     */
    Maybe<V> getAndDelete();

    /**
     * Stores element into the holder. 
     * 
     * @param value - value to set
     * @return void
     */
    Completable set(V value);

    /**
     * Use {@link #set(Object, Duration)} instead
     * 
     * @param value - value to set
     * @param timeToLive - time to live interval
     * @param timeUnit - unit of time to live interval
     * @return void
     */
    @Deprecated
    Completable set(V value, long timeToLive, TimeUnit timeUnit);

    /**
     * Stores <code>value</code> into the holder with defined expiration <code>duration</code>.
     *
     * @param value value to set
     * @param duration expiration duration
     */
    Completable set(V value, Duration duration);

    /**
     * Set value and keep existing TTL.
     * <p>
     * Requires <b>Redis 6.0.0 and higher.</b>
     *
     * @param value - value to set
     * @return void
     */
    Completable setAndKeepTTL(V value);

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
    Single<Integer> addListener(ObjectListener listener);

    /**
     * Returns the common part of the data stored in this bucket
     * and a bucket defined by the <code>name</code>
     *
     * @param name second bucket
     * @return common part of the data
     */
    Single<V> findCommon(String name);

    /**
     * Returns the length of the common part of the data stored in this bucket
     * and a bucket defined by the <code>name</code>
     *
     * @param name second bucket
     * @return common part of the data
     */
    Single<Long> findCommonLength(String name);

    /**
     * Returns the hash digest of the value stored in this bucket as a hexadecimal string.
     * The digest is computed using the XXH3 hash algorithm.
     * <p>
     * Requires <b>Redis 8.4.0 or higher</b>.
     *
     * @return hash digest as hexadecimal string, or empty Maybe if the bucket doesn't exist
     */
    Maybe<String> getDigest();

}
