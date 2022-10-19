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

import java.util.Deque;
import java.util.List;

/**
 * Distributed implementation of {@link java.util.Deque}
 *
 * @author Nikita Koksharov
 *
 * @param <V> the type of elements held in this collection
 */
public interface RDeque<V> extends Deque<V>, RQueue<V>, RDequeAsync<V> {

    /**
     * Adds element at the head of existing deque.
     *
     * @param elements - elements to add
     * @return length of the list
     */
    int addFirstIfExists(V... elements);

    /**
     * Adds elements at the head of deque.
     *
     * @param elements - elements to add
     * @return length of the deque
     */
    int addFirst(V... elements);

    /**
     * Adds element at the tail of existing deque.
     *
     * @param elements - elements to add
     * @return length of the list
     */
    int addLastIfExists(V... elements);

    /**
     * Adds elements at the tail of deque.
     *
     * @param elements - elements to add
     * @return length of the deque
     */
    int addLast(V... elements);

    /**
     * Retrieves and removes the tail elements of this queue.
     * Elements amount limited by <code>limit</code> param.
     *
     * @return list of tail elements
     */
    List<V> pollLast(int limit);

    /**
     * Retrieves and removes the head elements of this queue.
     * Elements amount limited by <code>limit</code> param.
     *
     * @return list of head elements
     */
    List<V> pollFirst(int limit);

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
    V move(DequeMoveArgs args);

}
