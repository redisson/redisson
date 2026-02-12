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

import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Reactive interface of Redis based {@link java.util.concurrent.Semaphore}.
 * <p>
 * Works in non-fair mode. Therefore order of acquiring is unpredictable.
 *
 * @author Nikita Koksharov
 *
 */
public interface RSemaphoreReactive extends RExpirableReactive {

    /**
     * Acquires a permit.
     * Waits if necessary until a permit became available.
     *
     * @return <code>true</code> if a permit was acquired and <code>false</code>
     *         otherwise
     */
    Mono<Boolean> tryAcquire();
    
    /**
     * Tries to acquire defined amount of currently available <code>permits</code>.
     *
     * @param permits the number of permits to acquire
     * @return <code>true</code> if permits were acquired and <code>false</code>
     *         otherwise
     */
    Mono<Boolean> tryAcquire(int permits);

    /**
     * Acquires a permit.
     * Waits if necessary until a permit became available.
     * 
     * @return void
     *
     */
    Mono<Void> acquire();

    /**
     * Acquires defined amount of <code>permits</code>.
     * Waits if necessary until all permits became available.
     *
     * @param permits the number of permits to acquire
     * @throws IllegalArgumentException if <code>permits</code> is negative
     * @return void
     */
    Mono<Void> acquire(int permits);

    /**
     * Releases a permit.
     *
     * @return void
     */
    Mono<Void> release();

    /**
     * Releases defined amount of <code>permits</code>.
     *
     * @param permits amount
     * @return void
     */
    Mono<Void> release(int permits);

    /**
     * Releases defined amount of <code>permits</code> only if semaphore exists.
     * Increases the number of available permits by <code>permits</code> amount.
     *
     * @param permits amount of permits
     */
    Mono<Boolean> releaseIfExists(int permits);

    /**
     * Tries to set number of permits.
     *
     * @param permits number of permits
     * @return <code>true</code> if permits has been set successfully, otherwise <code>false</code>.  
     */
    Mono<Boolean> trySetPermits(int permits);

    /**
     * Tries to set number of permits with defined time to live.
     *
     * @param timeToLive time to live
     * @param permits number of permits
     * @return <code>true</code> if permits has been set successfully, otherwise <code>false</code>.
     */
    Mono<Boolean> trySetPermits(int permits, Duration timeToLive);

    /**
     * Use {@link #tryAcquire(Duration)} instead
     *
     * @param waitTime the maximum time to wait
     * @param unit the time unit
     * @return <code>true</code> if a permit was acquired and <code>false</code>
     *         otherwise
     */
    @Deprecated
    Mono<Boolean> tryAcquire(long waitTime, TimeUnit unit);
    
    /**
     * Tries to acquire currently available permit.
     * Waits up to defined <code>waitTime</code> if necessary until a permit became available.
     *
     * @param waitTime the maximum time to wait
     * @return <code>true</code> if a permit was acquired and <code>false</code>
     *         otherwise
     */
    Mono<Boolean> tryAcquire(Duration waitTime);

    /**
     * Use {@link #tryAcquire(int, Duration)} instead
     *
     * @param permits amount of permits
     * @param waitTime the maximum time to wait
     * @param unit the time unit
     * @return <code>true</code> if permits were acquired and <code>false</code>
     *         otherwise
     */
    @Deprecated
    Mono<Boolean> tryAcquire(int permits, long waitTime, TimeUnit unit);

    /**
     * Tries to acquire defined amount of currently available <code>permits</code>.
     * Waits up to defined <code>waitTime</code> if necessary until all permits became available.
     *
     * @param permits amount of permits
     * @param waitTime the maximum time to wait
     * @param unit the time unit
     * @return <code>true</code> if permits were acquired and <code>false</code>
     *         otherwise
     */
    Mono<Boolean> tryAcquire(int permits, Duration waitTime);

    /**
     * Increases or decreases the number of available permits by defined value.
     *
     * @param permits amount of permits to add/remove
     */
    Mono<Void> addPermits(int permits);

    /**
     * Returns amount of available permits.
     *
     * @return number of permits
     */
    Mono<Integer> availablePermits();

    /**
     * Acquires and returns all permits that are immediately available.
     *
     * @return number of permits
     */
    Mono<Integer> drainPermits();

}
