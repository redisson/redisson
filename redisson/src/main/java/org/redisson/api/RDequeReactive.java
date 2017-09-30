/**
 * Copyright 2016 Nikita Koksharov
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
 * {@link java.util.Deque} backed by Redis
 *
 * @author Nikita Koksharov
 *
 * @param <V> the type of elements held in this collection
 */
public interface RDequeReactive<V> extends RQueueReactive<V> {

    Publisher<V> descendingIterator();

    Publisher<Boolean> removeLastOccurrence(Object o);

    Publisher<V> removeLast();

    Publisher<V> removeFirst();

    Publisher<Boolean> removeFirstOccurrence(Object o);

    Publisher<Void> push(V e);

    Publisher<V> pop();

    Publisher<V> pollLast();

    Publisher<V> pollFirst();

    Publisher<V> peekLast();

    Publisher<V> peekFirst();

    Publisher<Integer> offerLast(V e);

    Publisher<V> getLast();

    Publisher<Void> addLast(V e);

    Publisher<Void> addFirst(V e);

    Publisher<Boolean> offerFirst(V e);

}
