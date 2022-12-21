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

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;

import java.util.concurrent.TimeUnit;

/**
 * RxJava2 interface of Redis based implementation of {@link java.util.concurrent.TransferQueue}
 *
 * @author Nikita Koksharov
 * @param <V> the type of elements held in this collection
 */
public interface RTransferQueueRx<V> extends RBlockingQueueRx<V> {

    /**
     * Tries to transfer the element to waiting consumer
     * which invoked {@link #take} or {@link #poll} method
     * at the moment of transfer.
     *
     * @param e element to transfer
     * @return {@code true} if element was transferred, otherwise
     *         {@code false}
     */
    Single<Boolean> tryTransfer(V e);

    /**
     * Transfers the element to waiting consumer
     * which invoked {@link #take} or {@link #poll} method
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
    Completable transfer(V e);

    /**
     * Transfers the element to waiting consumer
     * which invoked {@link #take} or {@link #poll} method
     * at the moment of transfer.
     * Waits up to defined <code>timeout</code> if necessary for a consumer.
     *
     * @param e the element to transfer
     * @param timeout the maximum time to wait
     * @param unit the time unit
     * @return <code>true</code> if the element was transferred and <code>false</code>
     *         otherwise
     */
    Single<Boolean> tryTransfer(V e, long timeout, TimeUnit unit);

}
