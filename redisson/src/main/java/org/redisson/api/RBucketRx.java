/**
 * Copyright (c) 2013-2021 Nikita Koksharov
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
     * Tries to set element atomically into empty holder.
     * 
     * @param value - value to set
     * @return {@code true} if successful, or {@code false} if
     *         element was already set
     */
    Single<Boolean> trySet(V value);

    /**
     * Tries to set element atomically into empty holder with defined <code>timeToLive</code> interval.
     * 
     * @param value - value to set
     * @param timeToLive - time to live interval
     * @param timeUnit - unit of time to live interval
     * @return {@code true} if successful, or {@code false} if
     *         element was already set
     */
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
     * Sets value only if it's already exists.
     *
     * @param value - value to set
     * @param timeToLive - time to live interval
     * @param timeUnit - unit of time to live interval
     * @return {@code true} if successful, or {@code false} if
     *         element wasn't set
     */
    Single<Boolean> setIfExists(V value, long timeToLive, TimeUnit timeUnit);

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
     * Retrieves current element in the holder and replaces it with <code>newValue</code>. 
     * 
     * @param newValue - value to set
     * @return previous value
     */
    Maybe<V> getAndSet(V newValue);
    
    /**
     * Retrieves current element in the holder and replaces it with <code>newValue</code> with defined <code>timeToLive</code> interval. 
     * 
     * @param value - value to set
     * @param timeToLive - time to live interval
     * @param timeUnit - unit of time to live interval
     * @return previous value
     */
    Maybe<V> getAndSet(V value, long timeToLive, TimeUnit timeUnit);

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
     * Stores element into the holder with defined <code>timeToLive</code> interval.
     * 
     * @param value - value to set
     * @param timeToLive - time to live interval
     * @param timeUnit - unit of time to live interval
     * @return void
     */
    Completable set(V value, long timeToLive, TimeUnit timeUnit);

    /**
     * Set value and keep existing TTL.
     * <p>
     * Requires <b>Redis 6.0.0 and higher.</b>
     *
     * @param value - value to set
     * @return void
     */
    Completable setAndKeepTTL(V value);

}
