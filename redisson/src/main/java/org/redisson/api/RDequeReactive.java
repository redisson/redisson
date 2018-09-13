/**
 * Copyright 2018 Nikita Koksharov
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

import org.reactivestreams.Publisher;

/**
 * Distributed reactive implementation of {@link java.util.Deque}
 *
 * @author Nikita Koksharov
 *
 * @param <V> the type of elements held in this collection
 */
public interface RDequeReactive<V> extends RQueueReactive<V> {

    Publisher<V> descendingIterator();

    /**
     * Removes last occurrence of element <code>o</code>
     * 
     * @param o - element
     * @return <code>true</code> if object has been removed otherwise <code>false</code>
     */
    Publisher<Boolean> removeLastOccurrence(Object o);

    /**
     * Retrieves and removes the last element of deque.
     * Returns <code>null</code> if there are no elements in deque.
     * 
     * @return element
     */
    Publisher<V> removeLast();

    /**
     * Retrieves and removes the first element of deque.
     * Returns <code>null</code> if there are no elements in deque.
     * 
     * @return element
     */
    Publisher<V> removeFirst();

    /**
     * Removes first occurrence of element <code>o</code>
     * 
     * @param o - element to remove
     * @return <code>true</code> if object has been removed otherwise <code>false</code>
     */
    Publisher<Boolean> removeFirstOccurrence(Object o);

    /**
     * Adds element at the head of this deque.
     * 
     * @param e - element to add
     * @return void
     */
    Publisher<Void> push(V e);

    /**
     * Retrieves and removes element at the head of this deque.
     * Returns <code>null</code> if there are no elements in deque.
     * 
     * @return element
     */
    Publisher<V> pop();

    /**
     * Retrieves and removes element at the tail of this deque.
     * Returns <code>null</code> if there are no elements in deque.
     * 
     * @return element
     */
    Publisher<V> pollLast();

    /**
     * Retrieves and removes element at the head of this deque.
     * Returns <code>null</code> if there are no elements in deque.
     * 
     * @return element
     */
    Publisher<V> pollFirst();

    /**
     * Returns element at the tail of this deque 
     * or <code>null</code> if there are no elements in deque.
     * 
     * @return element
     */
    Publisher<V> peekLast();

    /**
     * Returns element at the head of this deque 
     * or <code>null</code> if there are no elements in deque.
     * 
     * @return element
     */
    Publisher<V> peekFirst();

    /**
     * Adds element at the tail of this deque.
     * 
     * @param e - element to add
     * @return <code>true</code> if element was added to this deque otherwise <code>false</code>
     */
    Publisher<Integer> offerLast(V e);

    /**
     * Returns element at the tail of this deque 
     * or <code>null</code> if there are no elements in deque.
     * 
     * @return element
     */
    Publisher<V> getLast();

    /**
     * Adds element at the tail of this deque.
     * 
     * @param e - element to add
     * @return void
     */
    Publisher<Void> addLast(V e);

    /**
     * Adds element at the head of this deque.
     * 
     * @param e - element to add
     * @return void
     */
    Publisher<Void> addFirst(V e);

    /**
     * Adds element at the head of this deque.
     * 
     * @param e - element to add
     * @return <code>true</code> if element was added to this deque otherwise <code>false</code>
     */
    Publisher<Boolean> offerFirst(V e);

}
