/**
 * Copyright (c) 2013-2022 Nikita Koksharov
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

/**
 * Async interface for Lock object
 *
 * @author Nikita Koksharov
 *
 */
public interface RLockAsync {

    /**
     * Unlocks the lock independently of its state
     *
     * @return <code>true</code> if lock existed and now unlocked
     *          otherwise <code>false</code>
     */
    RFuture<Boolean> forceUnlockAsync();
    
    /**
     * Unlocks the lock 
     * 
     * @return void
     */
    RFuture<Void> unlockAsync();

    /**
     * Unlocks the lock. Throws {@link IllegalMonitorStateException} 
     * if lock isn't locked by thread with specified <code>threadId</code>.
     * 
     * @param threadId id of thread
     * @return void
     */
    RFuture<Void> unlockAsync(long threadId);

    /**
     * Tries to acquire the lock.
     * 
     * @return <code>true</code> if lock acquired otherwise <code>false</code>
     */
    RFuture<Boolean> tryLockAsync();

    /**
     * Acquires the lock.
     * Waits if necessary until lock became available.
     * 
     * @return void
     */
    RFuture<Void> lockAsync();

    /**
     * Acquires the lock by thread with defined <code>threadId</code>.
     * Waits if necessary until lock became available.
     * 
     * @param threadId id of thread
     * @return void
     */
    RFuture<Void> lockAsync(long threadId);
    
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
     * @return void
     */
    RFuture<Void> lockAsync(long leaseTime, TimeUnit unit);

    /**
     * Acquires the lock with defined <code>leaseTime</code> and <code>threadId</code>.
     * Waits if necessary until lock became available.
     *
     * Lock will be released automatically after defined <code>leaseTime</code> interval.
     *
     * @param leaseTime the maximum time to hold the lock after it's acquisition,
     *        if it hasn't already been released by invoking <code>unlock</code>.
     *        If leaseTime is -1, hold the lock until explicitly unlocked.
     * @param unit the time unit
     * @param threadId id of thread
     * @return void
     */
    RFuture<Void> lockAsync(long leaseTime, TimeUnit unit, long threadId);
    
    /**
     * Tries to acquire the lock by thread with specified <code>threadId</code>.
     * 
     * @param threadId id of thread
     * @return <code>true</code> if lock acquired otherwise <code>false</code>
     */
    RFuture<Boolean> tryLockAsync(long threadId);

    /**
     * Tries to acquire the lock.
     * Waits up to defined <code>waitTime</code> if necessary until the lock became available.
     *
     * @param waitTime the maximum time to acquire the lock
     * @param unit time unit
     * @return <code>true</code> if lock is successfully acquired,
     *          otherwise <code>false</code> if lock is already set.
     */
    RFuture<Boolean> tryLockAsync(long waitTime, TimeUnit unit);

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
     */
    RFuture<Boolean> tryLockAsync(long waitTime, long leaseTime, TimeUnit unit);

    /**
     * Tries to acquire the lock by thread with specified <code>threadId</code> and  <code>leaseTime</code>.
     * Waits up to defined <code>waitTime</code> if necessary until the lock became available.
     *
     * Lock will be released automatically after defined <code>leaseTime</code> interval.
     * 
     * @param threadId id of thread
     * @param waitTime time interval to acquire lock
     * @param leaseTime time interval after which lock will be released automatically 
     * @param unit the time unit of the {@code waitTime} and {@code leaseTime} arguments
     * @return <code>true</code> if lock acquired otherwise <code>false</code>
     */
    RFuture<Boolean> tryLockAsync(long waitTime, long leaseTime, TimeUnit unit, long threadId);
    
    /**
     * Number of holds on this lock by the current thread
     *
     * @return holds or <code>0</code> if this lock is not held by current thread
     */
    RFuture<Integer> getHoldCountAsync();
    
    /**
     * Checks if the lock locked by any thread
     *
     * @return <code>true</code> if locked otherwise <code>false</code>
     */
    RFuture<Boolean> isLockedAsync();
    
    /**
     * Remaining time to live of the lock
     *
     * @return time in milliseconds
     *          -2 if the lock does not exist.
     *          -1 if the lock exists but has no associated expire.
     */
    RFuture<Long> remainTimeToLiveAsync();
    
}
