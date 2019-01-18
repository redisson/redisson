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
 * RxJava2 interface for Semaphore object
 * 
 * @author Nikita Koksharov
 *
 */
public interface RSemaphoreRx extends RExpirableRx {

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
    Flowable<Boolean> tryAcquire();
    
    /**
     * Acquires the given number of permits only if all are available at the
     * time of invocation.
     *
     * <p>Acquires a permits, if all are available and returns immediately,
     * with the value {@code true},
     * reducing the number of available permits by given number of permits.
     *
     * <p>If no permits are available then this method will return
     * immediately with the value {@code false}.
     *
     * @param permits the number of permits to acquire
     * @return {@code true} if a permit was acquired and {@code false}
     *         otherwise
     */
    Flowable<Boolean> tryAcquire(int permits);

    /**
     * Acquires a permit from this semaphore.
     *
     * <p>Acquires a permit, if one is available and returns immediately,
     * reducing the number of available permits by one.
     * 
     * @return void
     *
     */
    Flowable<Void> acquire();

    /**
     * Acquires the given number of permits, if they are available,
     * and returns immediately, reducing the number of available permits
     * by the given amount.
     *
     * @param permits the number of permits to acquire
     * @throws IllegalArgumentException if {@code permits} is negative
     * @return void
     */
    Flowable<Void> acquire(int permits);

    /**
     * Releases a permit, returning it to the semaphore.
     *
     * <p>Releases a permit, increasing the number of available permits by
     * one. If any threads of Redisson client are trying to acquire a permit,
     * then one is selected and given the permit that was just released.
     *
     * <p>There is no requirement that a thread that releases a permit must
     * have acquired that permit by calling {@link #acquire()}.
     * Correct usage of a semaphore is established by programming convention
     * in the application.
     * 
     * @return void
     */
    Flowable<Void> release();

    /**
     * Releases the given number of permits, returning them to the semaphore.
     *
     * <p>Releases the given number of permits, increasing the number of available permits by
     * the given number of permits. If any threads of Redisson client are trying to
     * acquire a permits, then next threads is selected and tries to acquire the permits that was just released.
     *
     * <p>There is no requirement that a thread that releases a permits must
     * have acquired that permit by calling {@link #acquire()}.
     * Correct usage of a semaphore is established by programming convention
     * in the application.
     *
     * @param permits amount
     * @return void
     */
    Flowable<Void> release(int permits);

    /**
     * Sets number of permits.
     *
     * @param permits - number of permits
     * @return <code>true</code> if permits has been set successfully, otherwise <code>false</code>.  
     */
    Flowable<Boolean> trySetPermits(int permits);

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
    Flowable<Boolean> tryAcquire(long waitTime, TimeUnit unit);
    
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
     * @param permits amount
     * @param waitTime the maximum time to wait for a available permits
     * @param unit the time unit of the {@code timeout} argument
     * @return {@code true} if a permit was acquired and {@code false}
     *         if the waiting time elapsed before a permit was acquired
     */
    Flowable<Boolean> tryAcquire(int permits, long waitTime, TimeUnit unit);

    /**
     * Shrinks the number of available permits by the indicated
     * reduction. This method can be useful in subclasses that use
     * semaphores to track resources that become unavailable. This
     * method differs from {@link #acquire()} in that it does not block
     * waiting for permits to become available.
     *
     * @param permits - reduction the number of permits to remove
     * @return void
     * @throws IllegalArgumentException if {@code reduction} is negative
     */
    Flowable<Void> reducePermits(int permits);

    
}
