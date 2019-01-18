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
 * Reactive interface for AtomicDouble object
 *
 * @author Nikita Koksharov
 *
 */
public interface RAtomicDoubleReactive extends RExpirableReactive {

    /**
     * Atomically sets the value to the given updated value
     * only if the current value {@code ==} the expected value.
     *
     * @param expect the expected value
     * @param update the new value
     * @return true if successful; or false if the actual value
     *         was not equal to the expected value.
     */
    Mono<Boolean> compareAndSet(double expect, double update);

    /**
     * Atomically adds the given value to the current value.
     *
     * @param delta the value to add
     * @return the updated value
     */
    Mono<Double> addAndGet(double delta);

    /**
     * Atomically decrements the current value by one.
     *
     * @return the updated value
     */
    Mono<Double> decrementAndGet();

    /**
     * Returns current value.
     *
     * @return current value
     */
    Mono<Double> get();

    /**
     * Returns and deletes object
     * 
     * @return the current value
     */
    Mono<Double> getAndDelete();
    
    /**
     * Atomically adds the given value to the current value.
     *
     * @param delta the value to add
     * @return the updated value
     */
    Mono<Double> getAndAdd(double delta);

    /**
     * Atomically sets the given value and returns the old value.
     *
     * @param newValue the new value
     * @return the old value
     */
    Mono<Double> getAndSet(double newValue);

    /**
     * Atomically increments the current value by one.
     *
     * @return the updated value
     */
    Mono<Double> incrementAndGet();

    /**
     * Atomically increments the current value by one.
     *
     * @return the old value
     */
    Mono<Double> getAndIncrement();

    /**
     * Atomically decrements by one the current value.
     *
     * @return the previous value
     */
    Mono<Double> getAndDecrement();

    /**
     * Atomically sets the given value.
     *
     * @param newValue the new value
     * @return void
     */
    Mono<Void> set(double newValue);

}
