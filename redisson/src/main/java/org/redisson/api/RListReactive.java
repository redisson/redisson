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

import java.util.Collection;
import java.util.List;

import org.reactivestreams.Publisher;

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
    Publisher<List<V>> get(int ...indexes);
    
    /**
     * Add <code>element</code> after <code>elementToFind</code>
     * 
     * @param elementToFind - object to find
     * @param element - object to add
     * @return new list size
     */
    Publisher<Integer> addAfter(V elementToFind, V element);
    
    /**
     * Add <code>element</code> before <code>elementToFind</code>
     * 
     * @param elementToFind - object to find
     * @param element - object to add
     * @return new list size
     */
    Publisher<Integer> addBefore(V elementToFind, V element);
    
    Publisher<V> descendingIterator();

    Publisher<V> descendingIterator(int startIndex);

    Publisher<V> iterator(int startIndex);

    Publisher<Integer> lastIndexOf(Object o);

    Publisher<Integer> indexOf(Object o);

    Publisher<Void> add(int index, V element);

    Publisher<Boolean> addAll(int index, Collection<? extends V> coll);

    Publisher<Void> fastSet(int index, V element);

    Publisher<V> set(int index, V element);

    Publisher<V> get(int index);

    Publisher<V> remove(int index);
    
    /**
     * Read all elements at once
     *
     * @return list of values
     */
    Publisher<List<V>> readAll();

    /**
     * Trim list and remains elements only in specified range
     * <code>fromIndex</code>, inclusive, and <code>toIndex</code>, inclusive.
     *
     * @param fromIndex - from index
     * @param toIndex - to index
     * @return void
     */
    Publisher<Void> trim(int fromIndex, int toIndex);

    /**
     * Remove object by specified index
     * 
     * @param index - index of object
     * @return void
     */
    Publisher<Void> fastRemove(int index);
    
}
