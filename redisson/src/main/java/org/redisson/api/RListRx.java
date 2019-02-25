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
    Flowable<List<V>> get(int...indexes);
    
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

    /**
     * Returns last index of <code>element</code> or 
     * -1 if element isn't found
     * 
     * @param element to find
     * @return index of -1 if element isn't found
     */
    Flowable<Integer> lastIndexOf(Object element);

    /**
     * Returns last index of <code>element</code> or 
     * -1 if element isn't found
     * 
     * @param element to find
     * @return index of -1 if element isn't found
     */
    Flowable<Integer> indexOf(Object element);

    /**
     * Inserts <code>element</code> at <code>index</code>. 
     * Subsequent elements are shifted. 
     * 
     * @param index - index number
     * @param element - element to insert
     * @return {@code true} if list was changed
     */
    Flowable<Void> add(int index, V element);

    /**
     * Inserts <code>elements</code> at <code>index</code>. 
     * Subsequent elements are shifted. 
     * 
     * @param index - index number
     * @param elements - elements to insert
     * @return {@code true} if list changed
     *      or {@code false} if element isn't found
     */
    Flowable<Boolean> addAll(int index, Collection<? extends V> elements);

    /**
     * Set <code>element</code> at <code>index</code>.
     * Works faster than {@link #set(int, Object)} but 
     * doesn't return previous element.
     * 
     * @param index - index of object
     * @param element - object
     * @return void
     */
    Flowable<Void> fastSet(int index, V element);

    /**
     * Set <code>element</code> at <code>index</code> and returns previous element.
     * 
     * @param index - index of object
     * @param element - object
     * @return previous element or <code>null</code> if element wasn't set.
     */
    Flowable<V> set(int index, V element);

    /**
     * Get element at <code>index</code>
     * 
     * @param index - index of object
     * @return element
     */
    Flowable<V> get(int index);

    /**
     * Removes element at <code>index</code>.
     * 
     * @param index - index of object
     * @return element or <code>null</code> if element wasn't set.
     */
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
