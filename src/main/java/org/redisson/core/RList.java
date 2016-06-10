/**
 * Copyright 2014 Nikita Koksharov, Nickolay Borbit
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
package org.redisson.core;

import java.util.List;
import java.util.RandomAccess;

/**
 * Distributed and concurrent implementation of {@link java.util.List}
 *
 * @author Nikita Koksharov
 *
 * @param <V> the type of elements held in this collection
 */
public interface RList<V> extends List<V>, RExpirable, RListAsync<V>, RandomAccess {

    /**
     * Add <code>element</code> after <code>elementToFind</code>
     * 
     * @param elementToFind
     * @param element
     * @return new list size
     */
    Integer addAfter(V elementToFind, V element);
    
    /**
     * Add <code>element</code> before <code>elementToFind</code>
     * 
     * @param elementToFind
     * @param element
     * @return new list size
     */
    Integer addBefore(V elementToFind, V element);
    
    /**
     * Set <code>element</code> at <code>index</code>.
     * Works faster than {@link #set(int, Object)} but 
     * doesn't return previous element.
     * 
     * @param index
     * @param element
     */
    void fastSet(int index, V element);

    RList<V> subList(int fromIndex, int toIndex);

    /**
     * Read all elements at once
     *
     * @return
     */
    List<V> readAll();

    /**
     * Trim list and remains elements only in specified range
     * <tt>fromIndex</tt>, inclusive, and <tt>toIndex</tt>, inclusive.
     *
     * @param fromIndex
     * @param toIndex
     * @return
     */
    void trim(int fromIndex, int toIndex);

    void fastRemove(int index);
    
}
