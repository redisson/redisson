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

import org.redisson.api.queue.DequeMoveArgs;

import java.util.List;

/**
 * Distributed async implementation of {@link java.util.Deque}
 *
 * @author Nikita Koksharov
 *
 * @param <V> the type of elements held in this collection
 */
public interface RDequeAsync<V> extends RQueueAsync<V> {

    /**
     * Adds element at the head of existing deque.
     *
     * @param elements - elements to add
     * @return length of the list
     */
    RFuture<Integer> addFirstIfExistsAsync(V... elements);

    /**
     * Adds elements at the head of deque.
     *
     * @param elements - elements to add
     * @return length of the deque
     */
    RFuture<Integer> addFirstAsync(V... elements);

    /**
     * Adds element at the tail of existing deque.
     *
     * @param elements - elements to add
     * @return length of the list
     */
    RFuture<Integer> addLastIfExistsAsync(V... elements);

    /**
     * Adds elements at the tail of deque.
     *
     * @param elements - elements to add
     * @return length of the deque
     */
    RFuture<Integer> addLastAsync(V... elements);

    /**
     * Removes last occurrence of element <code>o</code>
     * 
     * @param o - element
     * @return <code>true</code> if object has been removed otherwise <code>false</code>
     */
    RFuture<Boolean> removeLastOccurrenceAsync(Object o);

    /**
     * Retrieves and removes the last element of deque.
     * Returns <code>null</code> if there are no elements in deque.
     * 
     * @return element
     */
    RFuture<V> removeLastAsync();

    /**
     * Retrieves and removes the first element of deque.
     * Returns <code>null</code> if there are no elements in deque.
     * 
     * @return element
     */
    RFuture<V> removeFirstAsync();

    /**
     * Removes first occurrence of element <code>o</code>
     * 
     * @param o - element to remove
     * @return <code>true</code> if object has been removed otherwise <code>false</code>
     */
    RFuture<Boolean> removeFirstOccurrenceAsync(Object o);

    /**
     * Adds element at the head of this deque.
     * 
     * @param e - element to add
     * @return void
     */
    RFuture<Void> pushAsync(V e);

    /**
     * Retrieves and removes element at the head of this deque.
     * Returns <code>null</code> if there are no elements in deque.
     * 
     * @return element
     */
    RFuture<V> popAsync();

    /**
     * Retrieves and removes element at the tail of this deque.
     * Returns <code>null</code> if there are no elements in deque.
     * 
     * @return element
     */
    RFuture<V> pollLastAsync();

    /**
     * Retrieves and removes element at the head of this deque.
     * Returns <code>null</code> if there are no elements in deque.
     * 
     * @return element
     */
    RFuture<V> pollFirstAsync();

    /**
     * Returns element at the tail of this deque 
     * or <code>null</code> if there are no elements in deque.
     * 
     * @return element
     */
    RFuture<V> peekLastAsync();

    /**
     * Returns element at the head of this deque 
     * or <code>null</code> if there are no elements in deque.
     * 
     * @return element
     */
    RFuture<V> peekFirstAsync();

    /**
     * Adds element at the tail of this deque.
     * 
     * @param e - element to add
     * @return <code>true</code> if element was added to this deque otherwise <code>false</code>
     */
    RFuture<Boolean> offerLastAsync(V e);

    /**
     * Returns element at the tail of this deque 
     * or <code>null</code> if there are no elements in deque.
     * 
     * @return element
     */
    RFuture<V> getLastAsync();

    /**
     * Adds element at the tail of this deque.
     * 
     * @param e - element to add
     * @return void
     */
    RFuture<Void> addLastAsync(V e);

    /**
     * Adds element at the head of this deque.
     * 
     * @param e - element to add
     * @return void
     */
    RFuture<Void> addFirstAsync(V e);

    /**
     * Adds element at the head of this deque.
     * 
     * @param e - element to add
     * @return <code>true</code> if element was added to this deque otherwise <code>false</code>
     */
    RFuture<Boolean> offerFirstAsync(V e);

    /**
     * Retrieves and removes the head elements of this queue.
     * Elements amount limited by <code>limit</code> param.
     *
     * @return list of head elements
     */
    RFuture<List<V>> pollFirstAsync(int limit);

    /**
     * Retrieves and removes the tail elements of this queue.
     * Elements amount limited by <code>limit</code> param.
     *
     * @return list of tail elements
     */
    RFuture<List<V>> pollLastAsync(int limit);

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
    RFuture<V> moveAsync(DequeMoveArgs args);

}
