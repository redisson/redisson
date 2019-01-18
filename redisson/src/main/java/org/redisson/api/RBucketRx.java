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

import java.util.concurrent.TimeUnit;

import io.reactivex.Flowable;


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
    Flowable<Long> size();
    
    /**
     * Tries to set element atomically into empty holder.
     * 
     * @param value - value to set
     * @return {@code true} if successful, or {@code false} if
     *         element was already set
     */
    Flowable<Boolean> trySet(V value);

    /**
     * Tries to set element atomically into empty holder with defined <code>timeToLive</code> interval.
     * 
     * @param value - value to set
     * @param timeToLive - time to live interval
     * @param timeUnit - unit of time to live interval
     * @return {@code true} if successful, or {@code false} if
     *         element was already set
     */
    Flowable<Boolean> trySet(V value, long timeToLive, TimeUnit timeUnit);

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
    Flowable<Boolean> compareAndSet(V expect, V update);

    /**
     * Retrieves current element in the holder and replaces it with <code>newValue</code>. 
     * 
     * @param newValue - value to set
     * @return previous value
     */
    Flowable<V> getAndSet(V newValue);

    /**
     * Retrieves element stored in the holder.
     * 
     * @return element
     */
    Flowable<V> get();
    
    /**
     * Retrieves element in the holder and removes it.
     * 
     * @return element
     */
    Flowable<V> getAndDelete();

    /**
     * Stores element into the holder. 
     * 
     * @param value - value to set
     * @return void
     */
    Flowable<Void> set(V value);

    /**
     * Stores element into the holder with defined <code>timeToLive</code> interval.
     * 
     * @param value - value to set
     * @param timeToLive - time to live interval
     * @param timeUnit - unit of time to live interval
     * @return void
     */
    Flowable<Void> set(V value, long timeToLive, TimeUnit timeUnit);

}
