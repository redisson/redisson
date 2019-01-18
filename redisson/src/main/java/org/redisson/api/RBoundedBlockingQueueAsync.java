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

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Distributed async implementation of bounded {@link BlockingQueue}
 *
 * @author Nikita Koksharov
 * @param <V> the type of elements held in this collection
 */
public interface RBoundedBlockingQueueAsync<V> extends RBlockingQueueAsync<V> {

    /**
     * Sets queue capacity only if it is not set before.
     *
     * @param capacity - queue capacity
     * @return <code>true</code> if capacity set successfully
     *         <code>false</code> if capacity already set
     */
    RFuture<Boolean> trySetCapacityAsync(int capacity);

    /**
     * Inserts the specified element into this queue, waiting up to the
     * specified wait time if necessary for space to become available.
     *
     * @param e the element to add
     * @param timeout how long to wait before giving up, in units of
     *        {@code unit}
     * @param unit a {@code TimeUnit} determining how to interpret the
     *        {@code timeout} parameter
     * @return {@code true} if successful, or {@code false} if
     *         the specified waiting time elapses before space is available
     * @throws ClassCastException if the class of the specified element
     *         prevents it from being added to this queue
     * @throws NullPointerException if the specified element is null
     */
    RFuture<Boolean> offerAsync(V e, long timeout, TimeUnit unit);

}
