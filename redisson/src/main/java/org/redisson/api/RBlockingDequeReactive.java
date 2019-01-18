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

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive interface for Redis based BlockingDeque object
 *
 * @author Nikita Koksharov
 * @param <V> the type of elements held in this collection
 */
public interface RBlockingDequeReactive<V> extends RDequeReactive<V>, RBlockingQueueReactive<V> {

    /**
     * Retrieves and removes first available head element of <b>any</b> queue in reactive mode,
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
     */
    Mono<V> pollFirstFromAny(long timeout, TimeUnit unit, String ... queueNames);

    /**
     * Retrieves and removes first available tail element of <b>any</b> queue in reactive mode,
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
     */
    Mono<V> pollLastFromAny(long timeout, TimeUnit unit, String ... queueNames);

    /**
     * Adds value to the head of queue.
     * 
     * @param e value
     * @return void
     */
    Mono<Void> putFirst(V e);

    /**
     * Adds value to the tail of queue.
     * 
     * @param e value
     * @return void
     */
    Mono<Void> putLast(V e);

    /**
     * Retrieves and removes value at the tail of queue. If necessary waits up to defined <code>timeout</code> for an element become available.
     * 
     * @param timeout how long to wait before giving up, in units of
     *        {@code unit}
     * @param unit a {@code TimeUnit} determining how to interpret the
     *        {@code timeout} parameter
     * @return the element at the head of this queue, or {@code null} if the
     *         specified waiting time elapses before an element is available
     */
    Mono<V> pollLast(long timeout, TimeUnit unit);

    /**
     * Retrieves and removes value at the tail of queue. Waits for an element become available.
     * 
     * @return the tail element of this queue
     */
    Mono<V> takeLast();

    /**
     * Retrieves and removes value at the head of queue. If necessary waits up to defined <code>timeout</code> for an element become available.
     * 
     * @param timeout how long to wait before giving up, in units of
     *        {@code unit}
     * @param unit a {@code TimeUnit} determining how to interpret the
     *        {@code timeout} parameter
     * @return the element at the tail of this queue, or {@code null} if the
     *         specified waiting time elapses before an element is available
     */
    Mono<V> pollFirst(long timeout, TimeUnit unit);

    /**
     * Retrieves and removes value at the head of queue. Waits for an element become available.
     * 
     * @return the head element of this queue
     */
    Mono<V> takeFirst();

    /**
     * Retrieves and removes continues stream of elements from the head of this queue. 
     * Waits for next element become available.
     * 
     * @return stream of head elements
     */
    Flux<V> takeFirstElements();
    
    /**
     * Retrieves and removes continues stream of elements from the tail of this queue. 
     * Waits for next element become available.
     * 
     * @return stream of tail elements
     */
    Flux<V> takeLastElements();
    
}
