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

import java.util.concurrent.TimeUnit;

/**
 * Reactive interface of {@link java.util.concurrent.CountDownLatch}
 *
 * It has an advantage over {@link java.util.concurrent.CountDownLatch} --
 * count can be set via {@link #trySetCount} method.
 *
 * @author Nikita Koksharov
 *
 */
public interface RCountDownLatchReactive extends RObjectRx {

    /**
     * Causes the current thread to wait until the latch has counted down to
     * zero.
     *
     * <p>If the current count is zero then this method returns immediately.
     *
     * <p>If the current count is greater than zero then the current
     * thread becomes disabled for thread scheduling purposes and lies
     * dormant until the count reaches zero due to invocations of the
     * {@link #countDown} method.
     *
     */
    Mono<Void> await();

    /**
     * Causes the current thread to wait until the latch has counted down to
     * zero or the specified waiting time elapses.
     *
     * <p>If the current count is zero then this method returns immediately
     * with the value {@code true}.
     *
     * <p>If the current count is greater than zero then the current
     * thread becomes disabled for thread scheduling purposes and lies
     * dormant until the count reaches zero due to invocations of the
     * {@link #countDown()} method or the specified waiting time elapses.
     *
     * <p>If the count reaches zero then the method returns with the
     * value {@code true}.
     *
     * <p>If the specified waiting time elapses then the value {@code false}
     * is returned.  If the time is less than or equal to zero, the method
     * will not wait at all.
     *
     * @param waitTime the maximum time to wait
     * @param unit the time unit of the {@code timeout} argument
     * @return {@code true} if the count reached zero and {@code false}
     *         if the waiting time elapsed before the count reached zero
     */
    Mono<Boolean> await(long waitTime, TimeUnit unit);

    /**
     * Decrements the count of the latch, releasing all waiting threads if
     * the count reaches zero.
     *
     * <p>If the current count is greater than zero then it is decremented.
     * If the new count is zero then all waiting threads are re-enabled for
     * thread scheduling purposes.
     *
     * <p>If the current count equals zero then nothing happens.
     * 
     * @return void
     */
    Mono<Void> countDown();

    /**
     * Returns the current count.
     *
     * <p>This method is typically used for debugging and testing purposes.
     *
     * @return the current count
     */
    Mono<Long> getCount();

    /**
     * Sets new count value only if previous count already has reached zero
     * or is not set at all.
     *
     * @param count - number of times <code>countDown</code> must be invoked
     *        before threads can pass through <code>await</code>
     * @return <code>true</code> if new count setted
     *         <code>false</code> if previous count has not reached zero
     */
    Mono<Boolean> trySetCount(long count);

}
