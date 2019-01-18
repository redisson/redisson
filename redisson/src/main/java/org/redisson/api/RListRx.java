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

import io.reactivex.Flowable;

/**
 *  list functions
 *
 * @author Nikita Koksharov
 *
 * @param <V> the type of elements held in this collection
 */
// TODO add sublist support
public interface RListRx<V> extends RCollectionRx<V>, RSortableRx<List<V>> {

    /**
     * Loads elements by specified <code>indexes</code>
     * 
     * @param indexes of elements
     * @return elements
     */
    Flowable<List<V>> get(int ...indexes);
    
    /**
     * Add <code>element</code> after <code>elementToFind</code>
     * 
     * @param elementToFind - object to find
     * @param element - object to add
     * @return new list size
     */
    Flowable<Integer> addAfter(V elementToFind, V element);
    
    /**
     * Add <code>element</code> before <code>elementToFind</code>
     * 
     * @param elementToFind - object to find
     * @param element - object to add
     * @return new list size
     */
    Flowable<Integer> addBefore(V elementToFind, V element);
    
    Flowable<V> descendingIterator();

    Flowable<V> descendingIterator(int startIndex);

    Flowable<V> iterator(int startIndex);

    Flowable<Integer> lastIndexOf(Object o);

    Flowable<Integer> indexOf(Object o);

    Flowable<Void> add(int index, V element);

    Flowable<Boolean> addAll(int index, Collection<? extends V> coll);

    Flowable<Void> fastSet(int index, V element);

    Flowable<V> set(int index, V element);

    Flowable<V> get(int index);

    Flowable<V> remove(int index);
    
    /**
     * Read all elements at once
     *
     * @return list of values
     */
    Flowable<List<V>> readAll();

    /**
     * Trim list and remains elements only in specified range
     * <code>fromIndex</code>, inclusive, and <code>toIndex</code>, inclusive.
     *
     * @param fromIndex - from index
     * @param toIndex - to index
     * @return void
     */
    Flowable<Void> trim(int fromIndex, int toIndex);

    /**
     * Remove object by specified index
     * 
     * @param index - index of object
     * @return void
     */
    Flowable<Void> fastRemove(int index);
    
}
