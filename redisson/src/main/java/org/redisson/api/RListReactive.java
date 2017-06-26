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

import java.util.Collection;

import org.reactivestreams.Publisher;

/**
 *  list functions
 *
 * @author Nikita Koksharov
 *
 * @param <V> the type of elements held in this collection
 */
// TODO add sublist support
public interface RListReactive<V> extends RCollectionReactive<V> {

    Publisher<V> descendingIterator();

    Publisher<V> descendingIterator(int startIndex);

    Publisher<V> iterator(int startIndex);

    Publisher<Long> lastIndexOf(Object o);

    Publisher<Long> indexOf(Object o);

    Publisher<Integer> add(long index, V element);

    Publisher<Integer> addAll(long index, Collection<? extends V> coll);

    Publisher<Void> fastSet(long index, V element);

    Publisher<V> set(long index, V element);

    Publisher<V> get(long index);

    Publisher<V> remove(long index);

}
