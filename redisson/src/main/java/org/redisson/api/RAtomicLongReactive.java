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

import reactor.core.publisher.Mono;

/**
 * Reactive interface for AtomicLong object
 *
 * @author Nikita Koksharov
 *
 */
public interface RAtomicLongReactive extends RExpirableReactive {

    /**
     * Atomically sets the value to the given updated value
     * only if the current value {@code ==} the expected value.
     *
     * @param expect the expected value
     * @param update the new value
     * @return true if successful; or false if the actual value
     *         was not equal to the expected value.
     */
    Mono<Boolean> compareAndSet(long expect, long update);

    /**
     * Atomically adds the given value to the current value.
     *
     * @param delta the value to add
     * @return the updated value
     */
    Mono<Long> addAndGet(long delta);

    /**
     * Atomically decrements the current value by one.
     *
     * @return the updated value
     */
    Mono<Long> decrementAndGet();

    /**
     * Returns current value.
     *
     * @return the current value
     */
    Mono<Long> get();

    /**
     * Returns and deletes object
     * 
     * @return the current value
     */
    Mono<Long> getAndDelete();
    
    /**
     * Atomically adds the given value to the current value.
     *
     * @param delta the value to add
     * @return the old value before the add
     */
    Mono<Long> getAndAdd(long delta);

    /**
     * Atomically sets the given value and returns the old value.
     *
     * @param newValue the new value
     * @return the old value
     */
    Mono<Long> getAndSet(long newValue);

    /**
     * Atomically increments the current value by one.
     *
     * @return the updated value
     */
    Mono<Long> incrementAndGet();

    /**
     * Atomically increments the current value by one.
     *
     * @return the old value
     */
    Mono<Long> getAndIncrement();

    /**
     * Atomically decrements by one the current value.
     *
     * @return the previous value
     */
    Mono<Long> getAndDecrement();

    /**
     * Atomically sets the given value.
     *
     * @param newValue the new value
     * @return void
     */
    Mono<Void> set(long newValue);

}
