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

import java.util.List;

import reactor.core.publisher.Mono;

/**
 * Reactive interface for Queue object
 *
 * @author Nikita Koksharov
 *
 * @param <V> the type of elements held in this collection
 */
public interface RQueueReactive<V> extends RCollectionReactive<V> {

    /**
     * Retrieves the head of this queue in async mode.
     * 
     * @return the head of this queue, or {@code null}
     */
    Mono<V> peek();

    /**
     * Retrieves and removes the head of this queue in async mode.
     *
     * @return the head of this queue, or {@code null}
     */
    Mono<V> poll();

    /**
     * Inserts the specified element into this queue.
     *
     * @param e the element to add
     * @return {@code true} if successful, or {@code false}
     * @throws ClassCastException if the class of the specified element
     *         prevents it from being added to this queue
     * @throws NullPointerException if the specified element is null
     */
    Mono<Boolean> offer(V e);

    /**
     * Retrieves and removes last available tail element of this queue queue and adds it at the head of <code>queueName</code>.
     *
     * @param queueName - names of destination queue
     * @return the tail of this queue, or {@code null} if the
     *         specified waiting time elapses before an element is available
     */
    Mono<V> pollLastAndOfferFirstTo(String queueName);
    
    /**
     * Returns all queue elements at once
     * 
     * @return elements
     */
    Mono<List<V>> readAll();

}
