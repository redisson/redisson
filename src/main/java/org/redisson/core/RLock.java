/**
 * Copyright 2014 Nikita Koksharov, Nickolay Borbit
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

import java.util.concurrent.locks.Lock;

/**
 * Distributed implementation of {@link java.util.concurrent.locks.Lock}
 * Implements reentrant lock.
 * Use {@link RLock#getHoldCount()} to get a holds count.
 *
 * @author Nikita Koksharov
 *
 */
public interface RLock extends Lock, RObject {

    /**
     * Unlocks lock independently of state
     *
     */
    void forceUnlock();

    /**
     * Checks if this lock locked by any thread in Redisson cluster
     *
     * @return <code>true</code> if locked otherwise <code>false</code>
     */
    boolean isLocked();

    /**
     * Checks if this lock is held by the current thread
     *
     * @return @return <code>true</code> if held by current thread
     * otherwise <code>false</code>
     */
    boolean isHeldByCurrentThread();

    /**
     * Number of holds on this lock by the current thread
     *
     * @return holds or <code>0</code> if this lock is not held by current thread
     */
    int getHoldCount();

}
