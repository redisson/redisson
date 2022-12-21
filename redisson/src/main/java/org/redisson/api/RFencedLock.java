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
 * Redis based implementation of Fenced Lock with reentrancy support.
 * <p>
 * Each lock acquisition increases fencing token. It should be
 * checked if it's greater or equal with the previous one by
 * the service guarded by this lock and reject operation if condition is false.
 *
 * @author Nikita Koksharov
 *
 */
public interface RFencedLock extends RLock, RFencedLockAsync {

    /**
     * Returns current fencing token
     *
     * @return fencing token
     */
    Long getToken();

    /**
     * Acquires the lock and returns increased fencing token.
     * Waits if necessary until lock became available.
     *
     * @return fencing token
     */
    Long lockAndGetToken();

    /**
     * Acquires the lock with defined <code>leaseTime</code> and
     * returns increased fencing token.
     * Waits if necessary until lock became available.
     * <p>
     * Lock will be released automatically after defined <code>leaseTime</code> interval.
     *
     * @param leaseTime the maximum time to hold the lock after it's acquisition,
     *        if it hasn't already been released by invoking <code>unlock</code>.
     *        If leaseTime is -1, hold the lock until explicitly unlocked.
     * @param unit the time unit
     *
     * @return fencing token
     */
    Long lockAndGetToken(long leaseTime, TimeUnit unit);

    /**
     * Tries to acquire the lock and returns increased fencing token if operation successful.
     *
     * @return fencing token if lock acquired otherwise <code>null</code>
     */
    Long tryLockAndGetToken();

    /**
     * Tries to acquire the lock and returns increased fencing token.
     * Waits up to defined <code>waitTime</code> if necessary until the lock became available.
     *
     * @param waitTime the maximum time to acquire the lock
     * @param unit time unit
     * @return fencing token if lock is successfully acquired,
     *          otherwise <code>null</code> if lock is already set.
     */
    Long tryLockAndGetToken(long waitTime, TimeUnit unit);

    /**
     * Tries to acquire the lock with defined <code>leaseTime</code>
     * and returns increased fencing token.
     * Waits up to defined <code>waitTime</code> if necessary until the lock became available.
     * <p>
     * Lock will be released automatically after defined <code>leaseTime</code> interval.
     *
     * @param waitTime the maximum time to acquire the lock
     * @param leaseTime lease time
     * @param unit time unit
     * @return fencing token if lock is successfully acquired,
     *          otherwise <code>null</code> if lock is already set.
     */
    Long tryLockAndGetToken(long waitTime, long leaseTime, TimeUnit unit);

}
