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

import java.util.Collection;
import java.util.List;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 *  list functions
 *
 * @author Nikita Koksharov
 *
 * @param <V> the type of elements held in this collection
 */
// TODO add sublist support
public interface RListReactive<V> extends RCollectionReactive<V>, RSortableReactive<List<V>> {

    /**
     * Loads elements by specified <code>indexes</code>
     * 
     * @param indexes of elements
     * @return elements
     */
    Mono<List<V>> get(int ...indexes);
    
    /**
     * Add <code>element</code> after <code>elementToFind</code>
     * 
     * @param elementToFind - object to find
     * @param element - object to add
     * @return new list size
     */
    Mono<Integer> addAfter(V elementToFind, V element);
    
    /**
     * Add <code>element</code> before <code>elementToFind</code>
     * 
     * @param elementToFind - object to find
     * @param element - object to add
     * @return new list size
     */
    Mono<Integer> addBefore(V elementToFind, V element);
    
    Flux<V> descendingIterator();

    Flux<V> descendingIterator(int startIndex);

    Flux<V> iterator(int startIndex);

    Mono<Integer> lastIndexOf(Object o);

    Mono<Integer> indexOf(Object o);

    Mono<Void> add(int index, V element);

    Mono<Boolean> addAll(int index, Collection<? extends V> coll);

    Mono<Void> fastSet(int index, V element);

    Mono<V> set(int index, V element);

    Mono<V> get(int index);

    Mono<V> remove(int index);
    
    /**
     * Read all elements at once
     *
     * @return list of values
     */
    Mono<List<V>> readAll();

    /**
     * Trim list and remains elements only in specified range
     * <code>fromIndex</code>, inclusive, and <code>toIndex</code>, inclusive.
     *
     * @param fromIndex - from index
     * @param toIndex - to index
     * @return void
     */
    Mono<Void> trim(int fromIndex, int toIndex);

    /**
     * Remove object by specified index
     * 
     * @param index - index of object
     * @return void
     */
    Mono<Void> fastRemove(int index);
    
}
