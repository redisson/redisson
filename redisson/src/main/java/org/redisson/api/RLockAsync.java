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

/**
 * Distributed implementation of {@link java.util.concurrent.locks.Lock}
 *
 * @author Nikita Koksharov
 *
 */

public interface RLockAsync extends RExpirableAsync {

    /**
     * Unlocks lock independently of state
     *
     * @return <code>true</code> if unlocked otherwise <code>false</code>
     */
    RFuture<Boolean> forceUnlockAsync();
    
    RFuture<Void> unlockAsync();
    
    RFuture<Void> unlockAsync(long threadId);
    
    RFuture<Boolean> tryLockAsync();

    RFuture<Void> lockAsync();

    RFuture<Void> lockAsync(long threadId);
    
    /**
     * Acquires the lock.
     *
     * <p>If the lock is not available then the current thread becomes
     * disabled for thread scheduling purposes and lies dormant until the
     * lock has been acquired.
     *
     * If the lock is acquired, it is held until <code>unlock</code> is invoked,
     * or until leaseTime milliseconds have passed
     * since the lock was granted - whichever comes first.
     *
     * @param leaseTime the maximum time to hold the lock after granting it,
     *        before automatically releasing it if it hasn't already been released by invoking <code>unlock</code>.
     *        If leaseTime is -1, hold the lock until explicitly unlocked.
     * @param unit the time unit of the {@code leaseTime} argument
     * @return void
     */
    RFuture<Void> lockAsync(long leaseTime, TimeUnit unit);
    
    RFuture<Void> lockAsync(long leaseTime, TimeUnit unit, long threadId);
    
    RFuture<Boolean> tryLockAsync(long threadId);
    
    RFuture<Boolean> tryLockAsync(long waitTime, TimeUnit unit);

    RFuture<Boolean> tryLockAsync(long waitTime, long leaseTime, TimeUnit unit);

    RFuture<Boolean> tryLockAsync(long waitTime, long leaseTime, TimeUnit unit, long threadId);
    
    /**
     * Number of holds on this lock by the current thread
     *
     * @return holds or <code>0</code> if this lock is not held by current thread
     */
    RFuture<Integer> getHoldCountAsync();
    
}
