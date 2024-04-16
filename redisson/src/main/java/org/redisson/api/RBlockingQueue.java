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

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Distributed implementation of {@link BlockingQueue}
 *
 * @author Nikita Koksharov
 * @param <V> the type of elements held in this collection
 */
public interface RBlockingQueue<V> extends BlockingQueue<V>, RQueue<V>, RBlockingQueueAsync<V> {

    /**
     * Retrieves and removes first available head element of <b>any</b> queue,
     * waiting up to the specified wait time if necessary for an element to become available
     * in any of defined queues <b>including</b> queue itself.
     *
     * @param queueNames - queue names. Queue name itself is always included
     * @param timeout how long to wait before giving up, in units of
     *        {@code unit}
     * @param unit a {@code TimeUnit} determining how to interpret the
     *        {@code timeout} parameter
     * @return the head of this queue, or {@code null} if the
     *         specified waiting time elapses before an element is available
     * @throws InterruptedException if interrupted while waiting
     */
    V pollFromAny(long timeout, TimeUnit unit, String... queueNames) throws InterruptedException;

    /**
     * Retrieves and removes first available head element of <b>any</b> queue,
     * waiting up to the specified wait time if necessary for an element to become available
     * in any of defined queues <b>including</b> queue itself.
     *
     * @param queueNames queue names. Queue name itself is always included
     * @param timeout how long to wait before giving up, in units of
     *        {@code unit}
     * @return the head of this queue, or {@code null} if the
     *         specified waiting time elapses before an element is available
     * @throws InterruptedException if interrupted while waiting
     */
    Entry<String, V> pollFromAnyWithName(Duration timeout, String... queueNames) throws InterruptedException;

    /**
     * Retrieves and removes first available head elements of <b>any</b> queue,
     * waiting up to the specified wait time if necessary for an element to become available
     * in any of defined queues <b>including</b> queue itself.
     *
     * <p>
     * Requires <b>Redis 7.0.0 and higher.</b>
     *
     * @param duration how long to wait before giving up
     * @param count elements amount
     * @param queueNames name of queues
     * @return the head elements
     */
    Map<String, List<V>> pollFirstFromAny(Duration duration, int count, String... queueNames) throws InterruptedException;

    /**
     * Retrieves and removes first available tail elements of <b>any</b> queue,
     * waiting up to the specified wait time if necessary for an element to become available
     * in any of defined queues <b>including</b> queue itself.
     *
     * <p>
     * Requires <b>Redis 7.0.0 and higher.</b>
     *
     * @param duration how long to wait before giving up
     * @param count elements amount
     * @param queueNames name of queues
     * @return the tail elements
     */
    Map<String, List<V>> pollLastFromAny(Duration duration, int count, String... queueNames) throws InterruptedException;

    /**
     * Retrieves and removes last available tail element of this queue and adds it at the head of <code>queueName</code>,
     * waiting up to the specified wait time if necessary for an element to become available.
     *
     * @param queueName - names of destination queue
     * @param timeout how long to wait before giving up, in units of
     *        {@code unit}
     * @param unit a {@code TimeUnit} determining how to interpret the
     *        {@code timeout} parameter
     * @return the tail of this queue, or {@code null} if the
     *         specified waiting time elapses before an element is available
     * @throws InterruptedException if interrupted while waiting
     */
    V pollLastAndOfferFirstTo(String queueName, long timeout, TimeUnit unit) throws InterruptedException;

    /**
     * Retrieves and removes last available tail element of <b>any</b> queue and adds it at the head of <code>queueName</code>,
     * waiting if necessary for an element to become available
     * in any of defined queues <b>including</b> queue itself.
     *
     * @param queueName - names of destination queue
     * @return the tail of this queue, or {@code null} if the
     *         specified waiting time elapses before an element is available
     * @throws InterruptedException if interrupted while waiting
     */
    V takeLastAndOfferFirstTo(String queueName) throws InterruptedException;

    /**
     * Subscribes on elements appeared in this queue.
     * Continuously invokes {@link #takeAsync()} method to get a new element.
     * <p>
     * NOTE: don't call blocking methods in the elements listener
     *
     * @param consumer - queue elements listener
     * @return listenerId - id of listener
     */
    int subscribeOnElements(Consumer<V> consumer);

    /**
     * Un-subscribes defined listener.
     *
     * @param listenerId - id of listener
     */
    void unsubscribe(int listenerId);

}
