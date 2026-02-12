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

import org.redisson.api.queue.DequeMoveArgs;

import java.time.Duration;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * {@link BlockingDeque} backed by Redis
 *
 * @author Nikita Koksharov
 * @param <V> the type of elements held in this collection
 */
public interface RBlockingDeque<V> extends BlockingDeque<V>, RBlockingQueue<V>, RDeque<V>, RBlockingDequeAsync<V> {

    /**
     * Retrieves and removes first available head element of <b>any</b> queue,
     * waiting up to the specified wait time if necessary for an element to become available
     * in any of defined queues <b>including</b> queue own.
     * 
     * @param queueNames - names of queue
     * @param timeout how long to wait before giving up, in units of
     *        {@code unit}
     * @param unit a {@code TimeUnit} determining how to interpret the
     *        {@code timeout} parameter
     * @return the head of this queue, or {@code null} if the
     *         specified waiting time elapses before an element is available
     * @throws InterruptedException if interrupted while waiting
     */
    V pollFirstFromAny(long timeout, TimeUnit unit, String... queueNames) throws InterruptedException;

    /**
     * Retrieves and removes first available tail element of <b>any</b> queue,
     * waiting up to the specified wait time if necessary for an element to become available
     * in any of defined queues <b>including</b> queue own.
     * 
     * @param queueNames - names of queue
     * @param timeout how long to wait before giving up, in units of
     *        {@code unit}
     * @param unit a {@code TimeUnit} determining how to interpret the
     *        {@code timeout} parameter
     * @return the head of this queue, or {@code null} if the
     *         specified waiting time elapses before an element is available
     * @throws InterruptedException if interrupted while waiting
     */
    V pollLastFromAny(long timeout, TimeUnit unit, String... queueNames) throws InterruptedException;

    V move(Duration timeout, DequeMoveArgs args);

    /**
     * Use {@link #subscribeOnFirstElements(Function)} instead.
     *
     * @param consumer - queue elements listener
     * @return listenerId - id of listener
     */
    @Deprecated
    int subscribeOnFirstElements(Consumer<V> consumer);

    /**
     * Use {@link #subscribeOnLastElements(Function)} instead.
     *
     * @param consumer - queue elements listener
     * @return listenerId - id of listener
     */
    @Deprecated
    int subscribeOnLastElements(Consumer<V> consumer);

    /**
     * Use {@link #subscribeOnLastElements(Function)} instead.
     * Continuously invokes {@link #takeFirstAsync()} method to get a new element.
     * <p>
     * NOTE: don't call blocking methods in the elements listener
     *
     * @param consumer - queue elements listener
     * @return listenerId - id of listener
     */
    int subscribeOnFirstElements(Function<V, CompletionStage<Void>> consumer);

    /**
     * Subscribes on last elements appeared in this queue.
     * Continuously invokes {@link #takeLastAsync()} method to get a new element.
     * <p>
     * NOTE: don't call blocking methods in the elements listener
     *
     * @param consumer - queue elements listener
     * @return listenerId - id of listener
     */
    int subscribeOnLastElements(Function<V, CompletionStage<Void>> consumer);

}
