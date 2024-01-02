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

import java.util.concurrent.TimeUnit;

/**
 * Redis based implementation of {@link java.util.concurrent.CountDownLatch}
 *
 * It has an advantage over {@link java.util.concurrent.CountDownLatch} --
 * count can be set via {@link #trySetCount} method.
 *
 * @author Nikita Koksharov
 *
 */
public interface RCountDownLatch extends RObject, RCountDownLatchAsync {

    /**
     * Waits until counter reach zero.
     *
     * @throws InterruptedException if the current thread was interrupted
     */
    void await() throws InterruptedException;

    /**
     * Waits until counter reach zero or up to defined <code>timeout</code>.
     *
     * @param timeout the maximum time to wait
     * @param unit the time unit
     * @return <code>true</code> if the count reached zero and <code>false</code>
     *         if timeout reached before the count reached zero
     * @throws InterruptedException if the current thread was interrupted
     */
    boolean await(long timeout, TimeUnit unit) throws InterruptedException;

    /**
     * Decrements the counter of the latch.
     * Notifies all waiting threads when count reaches zero.
     */
    void countDown();

    /**
     * Returns value of current count.
     *
     * @return current count
     */
    long getCount();

    /**
     * Sets new count value only if previous count already has reached zero
     * or is not set at all.
     *
     * @param count - number of times {@link #countDown} must be invoked
     *        before threads can pass through {@link #await}
     * @return <code>true</code> if new count setted
     *         <code>false</code> if previous count has not reached zero
     */
    boolean trySetCount(long count);

}
