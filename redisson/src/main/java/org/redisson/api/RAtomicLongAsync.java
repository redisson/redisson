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

/**
 * Distributed async implementation of {@link java.util.concurrent.atomic.AtomicLong}
 *
 * @author Nikita Koksharov
 *
 */
public interface RAtomicLongAsync extends RExpirableAsync {

    /**
     * Atomically sets the value to the given updated value
     * only if the current value {@code ==} the expected value.
     *
     * @param expect the expected value
     * @param update the new value
     * @return true if successful; or false if the actual value
     *         was not equal to the expected value.
     */
    RFuture<Boolean> compareAndSetAsync(long expect, long update);

    /**
     * Atomically adds the given value to the current value.
     *
     * @param delta the value to add
     * @return the updated value
     */
    RFuture<Long> addAndGetAsync(long delta);

    /**
     * Atomically decrements the current value by one.
     *
     * @return the updated value
     */
    RFuture<Long> decrementAndGetAsync();

    /**
     * Returns current value.
     *
     * @return the current value
     */
    RFuture<Long> getAsync();
    
    /**
     * Returns and deletes object
     * 
     * @return the current value
     */
    RFuture<Long> getAndDeleteAsync();

    /**
     * Atomically adds the given value to the current value.
     *
     * @param delta the value to add
     * @return the old value before the add
     */
    RFuture<Long> getAndAddAsync(long delta);

    /**
     * Atomically sets the given value and returns the old value.
     *
     * @param newValue the new value
     * @return the old value
     */
    RFuture<Long> getAndSetAsync(long newValue);

    /**
     * Atomically increments the current value by one.
     *
     * @return the updated value
     */
    RFuture<Long> incrementAndGetAsync();

    /**
     * Atomically increments the current value by one.
     *
     * @return the old value
     */
    RFuture<Long> getAndIncrementAsync();

    /**
     * Atomically decrements by one the current value.
     *
     * @return the previous value
     */
    RFuture<Long> getAndDecrementAsync();

    /**
     * Atomically sets the given value.
     *
     * @param newValue the new value
     * @return void
     */
    RFuture<Void> setAsync(long newValue);
    
    /**
     * Atomically sets the given value if current value is less than
     * the special value
     *
     * @param less  compare value
     * @param value newValue
     * @return true when the value update is successful
     */
    RFuture<Boolean> setIfLessAsync(long less, long value);
    
    /**
     * Atomically sets the given value if current value is greater than
     * the special value
     *
     * @param greater  compare value
     * @param value newValue
     * @return true when the value update is successful
     */
    RFuture<Boolean> setIfGreaterAsync(long greater, long value);

    /**
     * Adds object event listener
     *
     * @see org.redisson.api.listener.IncrByListener
     * @see org.redisson.api.ExpiredObjectListener
     * @see org.redisson.api.DeletedObjectListener
     *
     * @param listener - object event listener
     * @return listener id
     */
    RFuture<Integer> addListenerAsync(ObjectListener listener);

}
