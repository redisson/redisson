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

import java.util.List;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

/**
 * RxJava2 interface for Semaphore object with lease time parameter support for each acquired permit.
 * 
 * <p>Each permit identified by own id and could be released only using its id.
 * Permit id is a 128-bits unique random identifier generated each time during acquiring.
 *   
 * <p>Works in non-fair mode. Therefore order of acquiring is unpredictable.
 * 
 * @author Nikita Koksharov
 *
 */
public interface RPermitExpirableSemaphoreRx extends RExpirableRx {
    
    /**
     * Acquires a permit from this semaphore, blocking until one is
     * available, or the thread is {@linkplain Thread#interrupt interrupted}.
     *
     * <p>Acquires a permit, if one is available and returns its id,
     * reducing the number of available permits by one.
     *
     * <p>If no permit is available then the current thread becomes
     * disabled for thread scheduling purposes and lies dormant until
     * one of two things happens:
     * <ul>
     * <li>Some other thread invokes the {@link #release(String)} method for this
     * semaphore and the current thread is next to be assigned a permit; or
     * <li>Some other thread {@linkplain Thread#interrupt interrupts}
     * the current thread.
     * </ul>
     * 
     * @return permit id
     */
    Single<String> acquire();

    /**
     * Acquires defined amount of <code>permits</code> from this semaphore, blocking until enough permits are
     * available, or the thread is {@linkplain Thread#interrupt interrupted}.
     *
     * <p>Acquires <code>permits</code> permits, if they are available and returns their ids,
     * reducing the number of available permits by <code>permits</code>.
     *
     * <p>If not enough permits are available then the current thread becomes
     * disabled for thread scheduling purposes and lies dormant until
     * one of two things happens:
     * <ul>
     * <li>Some other thread invokes the {@link #release(String)} method for this
     * semaphore and the current thread is next to be assigned a permit; or
     * <li>Some other thread {@linkplain Thread#interrupt interrupts}
     * the current thread.
     * </ul>
     * 
     * @param permits - the number of permits to acquire
     * @return permits ids
     */
    Single<List<String>> acquire(int permits);

    /**
     * Acquires a permit with defined lease time from this semaphore, 
     * blocking until one is available, 
     * or the thread is {@linkplain Thread#interrupt interrupted}.
     * 
     * <p>Acquires a permit, if one is available and returns its id,
     * reducing the number of available permits by one.
     *
     * <p>If no permit is available then the current thread becomes
     * disabled for thread scheduling purposes and lies dormant until
     * one of two things happens:
     * <ul>
     * <li>Some other thread invokes the {@link #release} method for this
     * semaphore and the current thread is next to be assigned a permit; or
     * <li>Some other thread {@linkplain Thread#interrupt interrupts}
     * the current thread.
     * </ul>
     * 
     * @param leaseTime - permit lease time
     * @param unit - time unit
     * @return permit id
     */
    Single<String> acquire(long leaseTime, TimeUnit unit);

    /**
     * Acquires defined amount of <code>permits</code> with defined lease time from this semaphore, 
     * blocking until enough permits are available, 
     * or the thread is {@linkplain Thread#interrupt interrupted}.
     *
     * <p>Acquires <code>permits</code> permits, if they are available and returns their ids,
     * reducing the number of available permits by <code>permits</code>.
     *
     * <p>If not enough permits are available then the current thread becomes
     * disabled for thread scheduling purposes and lies dormant until
     * one of two things happens:
     * <ul>
     * <li>Some other thread invokes the {@link #release} method for this
     * semaphore and the current thread is next to be assigned a permit; or
     * <li>Some other thread {@linkplain Thread#interrupt interrupts}
     * the current thread.
     * </ul>
     *
     * @param permits - the number of permits to acquire
     * @param leaseTime - permit lease time
     * @param unit - time unit
     * @return permits ids
     */
    Single<List<String>> acquire(int permits, long leaseTime, TimeUnit unit);

    /**
     * Acquires a permit only if one is available at the
     * time of invocation.
     *
     * <p>Acquires a permit, if one is available and returns immediately,
     * with the permit id,
     * reducing the number of available permits by one.
     *
     * <p>If no permit is available then this method will return
     * immediately with the value {@code null}.
     *
     * @return permit id if a permit was acquired and {@code null}
     *         otherwise
     */
    Maybe<String> tryAcquire();

    /**
     * Acquires defined amount of <code>permits</code> only if they are available at the
     * time of invocation.
     *
     * <p>Acquires <code>permits</code> permits, if they are available and returns immediately,
     * with the permits ids,
     * reducing the number of available permits by <code>permits</code>.
     *
     * <p>If not enough permits are available then this method will return
     * immediately with empty collection.
     *
     * @param permits - the number of permits to acquire
     * @return permits ids if permit were acquired and empty collection
     *         otherwise
     */
    Single<List<String>> tryAcquire(int permits);

    /**
     * Acquires a permit from this semaphore, if one becomes available
     * within the given waiting time and the current thread has not
     * been {@linkplain Thread#interrupt interrupted}.
     *
     * <p>Acquires a permit, if one is available and returns immediately,
     * with the permit id,
     * reducing the number of available permits by one.
     *
     * <p>If no permit is available then the current thread becomes
     * disabled for thread scheduling purposes and lies dormant until
     * one of three things happens:
     * <ul>
     * <li>Some other thread invokes the {@link #release(String)} method for this
     * semaphore and the current thread is next to be assigned a permit; or
     * <li>Some other thread {@linkplain Thread#interrupt interrupts}
     * the current thread; or
     * <li>The specified waiting time elapses.
     * </ul>
     *
     * <p>If a permit is acquired then the permit id is returned.
     *
     * <p>If the specified waiting time elapses then the value {@code null}
     * is returned. If the time is less than or equal to zero, the method
     * will not wait at all.
     * 
     * @param waitTime the maximum time to wait for a permit
     * @param unit the time unit of the {@code timeout} argument
     * @return permit id if a permit was acquired and {@code null}
     *         if the waiting time elapsed before a permit was acquired
     */
    Maybe<String> tryAcquire(long waitTime, TimeUnit unit);

    /**
     * Acquires a permit with defined lease time from this semaphore,
     * if one becomes available
     * within the given waiting time and the current thread has not
     * been {@linkplain Thread#interrupt interrupted}.
     *
     * <p>Acquires a permit, if one is available and returns immediately,
     * with the permit id,
     * reducing the number of available permits by one.
     *
     * <p>If no permit is available then the current thread becomes
     * disabled for thread scheduling purposes and lies dormant until
     * one of three things happens:
     * <ul>
     * <li>Some other thread invokes the {@link #release(String)} method for this
     * semaphore and the current thread is next to be assigned a permit; or
     * <li>Some other thread {@linkplain Thread#interrupt interrupts}
     * the current thread; or
     * <li>The specified waiting time elapses.
     * </ul>
     *
     * <p>If a permit is acquired then the permit id is returned.
     *
     * <p>If the specified waiting time elapses then the value {@code null}
     * is returned. If the time is less than or equal to zero, the method
     * will not wait at all.
     * 
     * @param waitTime the maximum time to wait for a permit
     * @param leaseTime permit lease time
     * @param unit the time unit of the {@code timeout} argument
     * @return permit id if a permit was acquired and {@code null}
     *         if the waiting time elapsed before a permit was acquired
     */
    Maybe<String> tryAcquire(long waitTime, long leaseTime, TimeUnit unit);

    /**
     * Acquires defined amount of <code>permits</code> with defined lease time from this semaphore,
     * if enough permits become available
     * within the given waiting time and the current thread has not
     * been {@linkplain Thread#interrupt interrupted}.
     *
     * <p>Acquires <code>permits</code> permits, if they are available and returns immediately,
     * with the permits ids,
     * reducing the number of available permits by <code>permits</code>.
     *
     * <p>If not enough permits are available then the current thread becomes
     * disabled for thread scheduling purposes and lies dormant until
     * one of three things happens:
     * <ul>
     * <li>Some other thread invokes the {@link #release(String)} method for this
     * semaphore and the current thread is next to be assigned a permit; or
     * <li>Some other thread {@linkplain Thread#interrupt interrupts}
     * the current thread; or
     * <li>The specified waiting time elapses.
     * </ul>
     *
     * <p>If permit are acquired then permits ids are returned.
     *
     * <p>If the specified waiting time elapses then the empty collection
     * is returned. If the time is less than or equal to zero, the method
     * will not wait at all.
     * 
     * @param permits the number of permits to acquire
     * @param waitTime the maximum time to wait for permits
     * @param leaseTime permits lease time
     * @param unit the time unit of the {@code timeout} argument
     * @return permits ids if permit were acquired and empty collection
     *         if the waiting time elapsed before permits were acquired
     */
    Single<List<String>> tryAcquire(int permits, long waitTime, long leaseTime, TimeUnit unit);

    /**
     * Releases a permit by its id, returning it to the semaphore.
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
     * @param permitId - permit id
     * @return {@code true} if a permit has been released and {@code false}
     *         otherwise
     */
    Single<Boolean> tryRelease(String permitId);

    /**
     * Releases permits by their ids, returning them to the semaphore.
     *
     * <p>Releases <code>permits</code> permits, increasing the number of available permits
     * by released amount. If any threads of Redisson client are trying to acquire a permit,
     * then one is selected and given one of the permits that were just released.
     *
     * <p>There is no requirement that a thread that releases permits must
     * have acquired that permit by calling {@link #acquire()}.
     * Correct usage of a semaphore is established by programming convention
     * in the application.
     *
     * @param permitsIds - permits ids
     * @return amount of released permits
     */
    Single<Integer> tryRelease(List<String> permitsIds);

    /**
     * Releases a permit by its id, returning it to the semaphore.
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
     * <p>Throws an exception if permit id doesn't exist or has already been release
     * 
     * @param permitId - permit id
     * @return void
     */
    Completable release(String permitId);

    /**
     * Releases permits by their ids, returning them to the semaphore.
     *
     * <p>Releases <code>permits</code> permits, increasing the number of available permits
     * by released amount. If any threads of Redisson client are trying to acquire a permit,
     * then one is selected and given the permit that were just released.
     *
     * <p>There is no requirement that a thread that releases permits must
     * have acquired that permit by calling {@link #acquire()}.
     * Correct usage of a semaphore is established by programming convention
     * in the application.
     *
     * <p>Throws an exception if permit id doesn't exist or has already been release
     *
     * @param permitsIds - permits ids
     * @return void
     */
    Completable release(List<String> permitsIds);

    /**
     * Returns the current number of available permits.
     *
     * @return number of available permits
     */
    Single<Integer> availablePermits();

    /**
     * Returns the number of permits.
     *
     * @return number of permits
     */
    Single<Integer> getPermits();

    /**
     * Returns the number of acquired permits.
     *
     * @return number of acquired permits
     */
    Single<Integer> acquiredPermits();

    /**
     * Sets number of permits.
     *
     * @param permits - number of permits
     * @return <code>true</code> if permits has been set successfully, otherwise <code>false</code>.  
     */
    Single<Boolean> trySetPermits(int permits);

    /**
     * Sets the number of permits to the provided value.
     * Calculates the <code>delta</code> between the given <code>permits</code> value and the
     * current number of permits, then increases the number of available permits by <code>delta</code>.
     *
     * @param permits - number of permits
     */
    Single<Void> setPermits(int permits);

    /**
     * Increases or decreases the number of available permits by defined value. 
     *
     * @param permits - number of permits to add/remove
     * @return void
     */
    Completable addPermits(int permits);

    /**
     * Overrides and updates lease time for defined permit id.
     * 
     * @param permitId - permit id
     * @param leaseTime - permit lease time, use -1 to make it permanent
     * @param unit - the time unit of the {@code timeout} argument
     * @return <code>true</code> if permits has been updated successfully, otherwise <code>false</code>.
     */
    Single<Boolean> updateLeaseTime(String permitId, long leaseTime, TimeUnit unit);
    
    /**
     * Returns lease time of the permitId
     *
     * @param permitId permit id
     * @return return lease time in millis, use -1 to make it permanent
     * @throws IllegalArgumentException if permit id doesn't exist or has already been released.
     */
    Single<Long> getLeaseTime(String permitId);
    
}
