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

import java.util.List;
import java.util.RandomAccess;

import org.redisson.api.mapreduce.RCollectionMapReduce;

/**
 * Distributed and concurrent implementation of {@link java.util.List}
 *
 * @author Nikita Koksharov
 *
 * @param <V> the type of elements held in this collection
 */
public interface RList<V> extends List<V>, RExpirable, RListAsync<V>, RSortable<List<V>>, RandomAccess {

    /**
     * Loads elements by specified <code>indexes</code>
     * 
     * @param indexes of elements
     * @return list of elements
     */
    List<V> get(int...indexes);
    
    /**
     * Returns <code>RMapReduce</code> object associated with this map
     * 
     * @param <KOut> output key
     * @param <VOut> output value
     * @return MapReduce instance
     */
    <KOut, VOut> RCollectionMapReduce<V, KOut, VOut> mapReduce();
    
    /**
     * Add <code>element</code> after <code>elementToFind</code>
     * 
     * @param elementToFind - object to find
     * @param element - object to add
     * @return new list size
     */
    int addAfter(V elementToFind, V element);
    
    /**
     * Add <code>element</code> before <code>elementToFind</code>
     * 
     * @param elementToFind - object to find
     * @param element - object to add
     * @return new list size
     */
    int addBefore(V elementToFind, V element);
    
    /**
     * Set <code>element</code> at <code>index</code>.
     * Works faster than {@link #set(int, Object)} but 
     * doesn't return previous element.
     * 
     * @param index - index of object
     * @param element - object to set
     */
    void fastSet(int index, V element);

    RList<V> subList(int fromIndex, int toIndex);

    /**
     * Read all elements at once
     *
     * @return list of values
     */
    List<V> readAll();

    /**
     * Trim list and remains elements only in specified range
     * <code>fromIndex</code>, inclusive, and <code>toIndex</code>, inclusive.
     *
     * @param fromIndex - from index
     * @param toIndex - to index
     */
    void trim(int fromIndex, int toIndex);

    /**
     * Returns range of values from 0 index to <code>toIndex</code>. Indexes are zero based. 
     * <code>-1</code> means the last element, <code>-2</code> means penultimate and so on.
     * 
     * @param toIndex - end index
     * @return elements
     */
    List<V> range(int toIndex);
    
    /**
     * Returns range of values from <code>fromIndex</code> to <code>toIndex</code> index including.
     * Indexes are zero based. <code>-1</code> means the last element, <code>-2</code> means penultimate and so on.
     * 
     * @param fromIndex - start index
     * @param toIndex - end index
     * @return elements
     */
    List<V> range(int fromIndex, int toIndex);
    
    /**
     * Remove object by specified index
     * 
     * @param index - index of object
     */
    void fastRemove(int index);
    
    /**
     * Removes up to <code>count</code> occurrences of <code>element</code> 
     * 
     * @param element - element to find
     * @param count - amount occurrences
     * @return {@code true} if at least one element removed; 
     *      or {@code false} if element isn't found
     */
    boolean remove(Object element, int count);
    
}
