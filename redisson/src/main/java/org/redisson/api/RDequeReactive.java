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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive interface for Deque object
 *
 * @author Nikita Koksharov
 *
 * @param <V> the type of elements held in this collection
 */
public interface RDequeReactive<V> extends RQueueReactive<V> {

    /**
     * Adds element at the head of existing deque.
     *
     * @param elements - elements to add
     * @return length of the list
     */
    Mono<Integer> addFirstIfExists(V... elements);

    /**
     * Adds element at the tail of existing deque.
     *
     * @param elements - elements to add
     * @return length of the list
     */
    Mono<Integer> addLastIfExists(V... elements);

    /**
     * Adds elements at the head of deque.
     *
     * @param elements - elements to add
     * @return length of the deque
     */
    Mono<Integer> addFirst(V... elements);

    /**
     * Adds elements at the tail of deque.
     *
     * @param elements - elements to add
     * @return length of the deque
     */
    Mono<Integer> addLast(V... elements);

    Flux<V> descendingIterator();

    /**
     * Removes last occurrence of element <code>o</code>
     * 
     * @param o - element
     * @return <code>true</code> if object has been removed otherwise <code>false</code>
     */
    Mono<Boolean> removeLastOccurrence(Object o);

    /**
     * Retrieves and removes the last element of deque.
     * Returns <code>null</code> if there are no elements in deque.
     * 
     * @return element
     */
    Mono<V> removeLast();

    /**
     * Retrieves and removes the first element of deque.
     * Returns <code>null</code> if there are no elements in deque.
     * 
     * @return element
     */
    Mono<V> removeFirst();

    /**
     * Removes first occurrence of element <code>o</code>
     * 
     * @param o - element to remove
     * @return <code>true</code> if object has been removed otherwise <code>false</code>
     */
    Mono<Boolean> removeFirstOccurrence(Object o);

    /**
     * Adds element at the head of this deque.
     * 
     * @param e - element to add
     * @return void
     */
    Mono<Void> push(V e);

    /**
     * Retrieves and removes element at the head of this deque.
     * Returns <code>null</code> if there are no elements in deque.
     * 
     * @return element
     */
    Mono<V> pop();

    /**
     * Retrieves and removes element at the tail of this deque.
     * Returns <code>null</code> if there are no elements in deque.
     * 
     * @return element
     */
    Mono<V> pollLast();

    /**
     * Retrieves and removes element at the head of this deque.
     * Returns <code>null</code> if there are no elements in deque.
     * 
     * @return element
     */
    Mono<V> pollFirst();

    /**
     * Retrieves and removes the tail elements of this queue.
     * Elements amount limited by <code>limit</code> param.
     *
     * @return list of tail elements
     */
    Flux<V> pollLast(int limit);

    /**
     * Retrieves and removes the head elements of this queue.
     * Elements amount limited by <code>limit</code> param.
     *
     * @return list of head elements
     */
    Flux<V> pollFirst(int limit);

    /**
     * Returns element at the tail of this deque 
     * or <code>null</code> if there are no elements in deque.
     * 
     * @return element
     */
    Mono<V> peekLast();

    /**
     * Returns element at the head of this deque 
     * or <code>null</code> if there are no elements in deque.
     * 
     * @return element
     */
    Mono<V> peekFirst();

    /**
     * Adds element at the tail of this deque.
     * 
     * @param e - element to add
     * @return <code>true</code> if element was added to this deque otherwise <code>false</code>
     */
    Mono<Boolean> offerLast(V e);

    /**
     * Returns element at the tail of this deque 
     * or <code>null</code> if there are no elements in deque.
     * 
     * @return element
     */
    Mono<V> getLast();

    /**
     * Adds element at the tail of this deque.
     * 
     * @param e - element to add
     * @return void
     */
    Mono<Void> addLast(V e);

    /**
     * Adds element at the head of this deque.
     * 
     * @param e - element to add
     * @return void
     */
    Mono<Void> addFirst(V e);

    /**
     * Adds element at the head of this deque.
     * 
     * @param e - element to add
     * @return <code>true</code> if element was added to this deque otherwise <code>false</code>
     */
    Mono<Boolean> offerFirst(V e);

    /**
     * Move element from this deque to the given destination deque.
     * Returns moved element.
     * <p>
     * Usage examples:
     * <pre>
     * V element = deque.move(DequeMoveArgs.pollLast()
     *                                 .addFirstTo("deque2"));
     * </pre>
     * <pre>
     * V elements = deque.move(DequeMoveArgs.pollFirst()
     *                                 .addLastTo("deque2"));
     * </pre>
     * <p>
     * Requires <b>Redis 6.2.0 and higher.</b>
     *
     * @param args - arguments object
     * @return moved element
     */
    Mono<V> move(DequeMoveArgs args);


}
