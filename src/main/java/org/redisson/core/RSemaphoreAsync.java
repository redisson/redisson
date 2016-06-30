/**
 * Copyright 2016 Nikita Koksharov
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
package org.redisson.core;

import java.util.concurrent.TimeUnit;

import io.netty.util.concurrent.Future;

/**
 * Distributed and concurrent implementation of {@link java.util.concurrent.Semaphore}.
 * <p/>
 * Works in non-fair mode. Therefore order of acquiring is unpredictable.
 * 
 * @author Nikita Koksharov
 *
 */
public interface RSemaphoreAsync extends RExpirableAsync {
    
    /**
     * Acquires a permit only if one is available at the
     * time of invocation.
     *
     * <p>Acquires a permit, if one is available and returns immediately,
     * with the value {@code true},
     * reducing the number of available permits by one.
     *
     * <p>If no permit is available then this method will return
     * immediately with the value {@code false}.
     *
     * @return {@code true} if a permit was acquired and {@code false}
     *         otherwise
     */
    Future<Boolean> tryAcquireAsync();
    
    /**
     * Acquires the given number of permits only if all are available at the
     * time of invocation.
     *
     * <p>Acquires a permits, if all are available and returns immediately,
     * with the value {@code true},
     * reducing the number of available permits by given number of permitss.
     *
     * <p>If no permits are available then this method will return
     * immediately with the value {@code false}.
     *
     * @param permits the number of permits to acquire
     * @return {@code true} if a permit was acquired and {@code false}
     *         otherwise
     */
    Future<Boolean> tryAcquireAsync(int permits);

    /**
     * Acquires a permit from this semaphore.
     *
     * <p>Acquires a permit, if one is available and returns immediately,
     * reducing the number of available permits by one.
     *
     */
    Future<Void> acquireAsync();

    /**
     * Acquires the given number of permits, if they are available,
     * and returns immediately, reducing the number of available permits
     * by the given amount.
     *
     * @param permits the number of permits to acquire
     * @throws IllegalArgumentException if {@code permits} is negative
     */
    Future<Void> acquireAsync(int permits);

    /**
     * Releases a permit, returning it to the semaphore.
     *
     * <p>Releases a permit, increasing the number of available permits by
     * one. If any threads of Redisson client are trying to acquire a permit,
     * then one is selected and given the permit that was just released.
     *
     * <p>There is no requirement that a thread that releases a permit must
     * have acquired that permit by calling {@link #acquire}.
     * Correct usage of a semaphore is established by programming convention
     * in the application.
     */
    Future<Void> releaseAsync();

    /**
     * Releases the given number of permits, returning them to the semaphore.
     *
     * <p>Releases the given number of permits, increasing the number of available permits by
     * the given number of permits. If any threads of Redisson client are trying to
     * acquire a permits, then next threads is selected and tries to acquire the permits that was just released.
     *
     * <p>There is no requirement that a thread that releases a permits must
     * have acquired that permit by calling {@link #acquire}.
     * Correct usage of a semaphore is established by programming convention
     * in the application.
     */
    Future<Void> releaseAsync(int permits);

    /**
     * Sets new number of permits.
     *
     * @param count - number of times {@link #countDown} must be invoked
     *        before threads can pass through {@link #await}
     */
    Future<Void> setPermitsAsync(int permits);

    /**
     * <p>Acquires a permit, if one is available and returns immediately,
     * with the value {@code true},
     * reducing the number of available permits by one.
     *
     * <p>If a permit is acquired then the value {@code true} is returned.
     *
     * <p>If the specified waiting time elapses then the value {@code false}
     * is returned.  If the time is less than or equal to zero, the method
     * will not wait at all.
     *
     * @param waitTime the maximum time to wait for a permit
     * @param unit the time unit of the {@code timeout} argument
     * @return {@code true} if a permit was acquired and {@code false}
     *         if the waiting time elapsed before a permit was acquired
     */
    Future<Boolean> tryAcquireAsync(long waitTime, TimeUnit unit);
    
    /**
     * Acquires the given number of permits only if all are available
     * within the given waiting time.
     *
     * <p>Acquires a permits, if all are available and returns immediately,
     * with the value {@code true},
     * reducing the number of available permits by one.
     *
     * <p>If a permits is acquired then the value {@code true} is returned.
     *
     * <p>If the specified waiting time elapses then the value {@code false}
     * is returned.  If the time is less than or equal to zero, the method
     * will not wait at all.
     *
     * @param permits
     * @param waitTime the maximum time to wait for a available permits
     * @param unit the time unit of the {@code timeout} argument
     * @return {@code true} if a permit was acquired and {@code false}
     *         if the waiting time elapsed before a permit was acquired
     * @throws InterruptedException if the current thread is interrupted
     */
    Future<Boolean> tryAcquireAsync(int permits, long waitTime, TimeUnit unit);

}
