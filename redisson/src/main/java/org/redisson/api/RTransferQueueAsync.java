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

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Async interface for Redis based implementation of {@link java.util.concurrent.TransferQueue}
 *
 * @author Nikita Koksharov
 *
 */
public interface RTransferQueueAsync<V> extends RBlockingQueueAsync<V> {

        /**
     * Tries to transfer the element to waiting consumer
     * which invoked {@link #takeAsync} or {@link #pollAsync} method
     * at the moment of transfer.
     *
     * @param e element to transfer
     * @return {@code true} if element was transferred, otherwise
     *         {@code false}
     */
    RFuture<Boolean> tryTransferAsync(V e);

    /**
     * Transfers the element to waiting consumer
     * which invoked {@link #takeAsync} or {@link #pollAsync} method
     * at the moment of transfer.
     * Waits if necessary for a consumer.
     *
     * @param e the element to transfer
     * @throws ClassCastException if the class of the specified element
     *         prevents it from being added to this queue
     * @throws NullPointerException if the specified element is null
     * @throws IllegalArgumentException if some property of the specified
     *         element prevents it from being added to this queue
     */
    RFuture<Void> transferAsync(V e);

    /**
     * Transfers the element to waiting consumer
     * which invoked {@link #takeAsync} or {@link #pollAsync} method
     * at the moment of transfer.
     * Waits up to defined <code>timeout</code> if necessary for a consumer.
     *
     * @param e the element to transfer
     * @param timeout the maximum time to wait
     * @param unit the time unit
     * @return <code>true</code> if the element was transferred and <code>false</code>
     *         otherwise
     */
    RFuture<Boolean> tryTransferAsync(V e, long timeout, TimeUnit unit);

    /**
     * Returns all queue elements at once
     *
     * @return elements
     */
    List<V> readAll();

}
