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

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.redisson.api.queue.DequeMoveArgs;

/**
 * RxJava2 interface for Deque object
 *
 * @author Nikita Koksharov
 *
 * @param <V> the type of elements held in this collection
 */
public interface RDequeRx<V> extends RQueueRx<V> {

    /**
     * Adds element at the head of existing deque.
     *
     * @param elements - elements to add
     * @return length of the list
     */
    Single<Integer> addFirstIfExists(V... elements);

    /**
     * Adds element at the tail of existing deque.
     *
     * @param elements - elements to add
     * @return length of the list
     */
    Single<Integer> addLastIfExists(V... elements);

    /**
     * Adds elements at the head of deque.
     *
     * @param elements - elements to add
     * @return length of the deque
     */
    Single<Integer> addFirst(V... elements);

    /**
     * Adds elements at the tail of deque.
     *
     * @param elements - elements to add
     * @return length of the deque
     */
    Single<Integer> addLast(V... elements);

    Flowable<V> descendingIterator();

    /**
     * Removes last occurrence of element <code>o</code>
     * 
     * @param o - element
     * @return <code>true</code> if object has been removed otherwise <code>false</code>
     */
    Single<Boolean> removeLastOccurrence(Object o);

    /**
     * Retrieves and removes the last element of deque.
     * Returns <code>null</code> if there are no elements in deque.
     * 
     * @return element
     */
    Maybe<V> removeLast();

    /**
     * Retrieves and removes the first element of deque.
     * Returns <code>null</code> if there are no elements in deque.
     * 
     * @return element
     */
    Maybe<V> removeFirst();

    /**
     * Removes first occurrence of element <code>o</code>
     * 
     * @param o - element to remove
     * @return <code>true</code> if object has been removed otherwise <code>false</code>
     */
    Single<Boolean> removeFirstOccurrence(Object o);

    /**
     * Adds element at the head of this deque.
     * 
     * @param e - element to add
     * @return void
     */
    Completable push(V e);

    /**
     * Retrieves and removes element at the head of this deque.
     * Returns <code>null</code> if there are no elements in deque.
     * 
     * @return element
     */
    Maybe<V> pop();

    /**
     * Retrieves and removes element at the tail of this deque.
     * Returns <code>null</code> if there are no elements in deque.
     * 
     * @return element
     */
    Maybe<V> pollLast();

    /**
     * Retrieves and removes element at the head of this deque.
     * Returns <code>null</code> if there are no elements in deque.
     * 
     * @return element
     */
    Maybe<V> pollFirst();

    /**
     * Retrieves and removes the tail elements of this queue.
     * Elements amount limited by <code>limit</code> param.
     *
     * @return list of tail elements
     */
    Flowable<V> pollLast(int limit);

    /**
     * Retrieves and removes the head elements of this queue.
     * Elements amount limited by <code>limit</code> param.
     *
     * @return list of head elements
     */
    Flowable<V> pollFirst(int limit);

    /**
     * Returns element at the tail of this deque 
     * or <code>null</code> if there are no elements in deque.
     * 
     * @return element
     */
    Maybe<V> peekLast();

    /**
     * Returns element at the head of this deque 
     * or <code>null</code> if there are no elements in deque.
     * 
     * @return element
     */
    Maybe<V> peekFirst();

    /**
     * Adds element at the tail of this deque.
     * 
     * @param e - element to add
     * @return <code>true</code> if element was added to this deque otherwise <code>false</code>
     */
    Single<Boolean> offerLast(V e);

    /**
     * Returns element at the tail of this deque 
     * or <code>null</code> if there are no elements in deque.
     * 
     * @return element
     */
    Maybe<V> getLast();

    /**
     * Adds element at the tail of this deque.
     * 
     * @param e - element to add
     * @return void
     */
    Completable addLast(V e);

    /**
     * Adds element at the head of this deque.
     * 
     * @param e - element to add
     * @return void
     */
    Completable addFirst(V e);

    /**
     * Adds element at the head of this deque.
     * 
     * @param e - element to add
     * @return <code>true</code> if element was added to this deque otherwise <code>false</code>
     */
    Single<Boolean> offerFirst(V e);

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
    Maybe<V> move(DequeMoveArgs args);

}
