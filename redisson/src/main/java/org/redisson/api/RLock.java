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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

/**
 * Redis based implementation of {@link java.util.concurrent.locks.Lock}
 * Implements re-entrant lock.
 *
 * @author Nikita Koksharov
 *
 */
public interface RLock extends Lock, RLockAsync, RObservable {

    /**
     * Returns name of object
     *
     * @return name - name of object
     */
    String getName();
    
    /**
     * Acquires the lock with defined <code>leaseTime</code>.
     * Waits if necessary until lock became available.
     *
     * Lock will be released automatically after defined <code>leaseTime</code> interval.
     *
     * @param leaseTime the maximum time to hold the lock after it's acquisition,
     *        if it hasn't already been released by invoking <code>unlock</code>.
     *        If leaseTime is -1, hold the lock until explicitly unlocked.
     * @param unit the time unit
     * @throws InterruptedException - if the thread is interrupted
     */
    void lockInterruptibly(long leaseTime, TimeUnit unit) throws InterruptedException;

    /**
     * Tries to acquire the lock with defined <code>leaseTime</code>.
     * Waits up to defined <code>waitTime</code> if necessary until the lock became available.
     *
     * Lock will be released automatically after defined <code>leaseTime</code> interval.
     *
     * @param waitTime the maximum time to acquire the lock
     * @param leaseTime lease time
     * @param unit time unit
     * @return <code>true</code> if lock is successfully acquired,
     *          otherwise <code>false</code> if lock is already set.
     * @throws InterruptedException - if the thread is interrupted
     */
    boolean tryLock(long waitTime, long leaseTime, TimeUnit unit) throws InterruptedException;

    /**
     * Acquires the lock with defined <code>leaseTime</code>.
     * Waits if necessary until lock became available.
     *
     * Lock will be released automatically after defined <code>leaseTime</code> interval.
     *
     * @param leaseTime the maximum time to hold the lock after it's acquisition,
     *        if it hasn't already been released by invoking <code>unlock</code>.
     *        If leaseTime is -1, hold the lock until explicitly unlocked.
     * @param unit the time unit
     *
     */
    void lock(long leaseTime, TimeUnit unit);

    /**
     * Unlocks the lock independently of its state
     *
     * @return <code>true</code> if lock existed and now unlocked
     *          otherwise <code>false</code>
     */
    boolean forceUnlock();

    /**
     * Checks if the lock locked by any thread
     *
     * @return <code>true</code> if locked otherwise <code>false</code>
     */
    boolean isLocked();

    /**
     * Checks if the lock is held by thread with defined <code>threadId</code>
     *
     * @param threadId Thread ID of locking thread
     * @return <code>true</code> if held by thread with given id
     *          otherwise <code>false</code>
     */
    boolean isHeldByThread(long threadId);

    /**
     * Checks if this lock is held by the current thread
     *
     * @return <code>true</code> if held by current thread
     * otherwise <code>false</code>
     */
    boolean isHeldByCurrentThread();

    /**
     * Number of holds on this lock by the current thread
     *
     * @return holds or <code>0</code> if this lock is not held by current thread
     */
    int getHoldCount();

    /**
     * Remaining time to live of the lock
     *
     * @return time in milliseconds
     *          -2 if the lock does not exist.
     *          -1 if the lock exists but has no associated expire.
     */
    long remainTimeToLive();
    
}
