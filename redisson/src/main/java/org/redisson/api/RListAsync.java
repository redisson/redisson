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
import java.util.RandomAccess;

/**
 * Async list functions
 *
 * @author Nikita Koksharov
 *
 * @param <V> the type of elements held in this collection
 */
public interface RListAsync<V> extends RCollectionAsync<V>, RSortableAsync<List<V>>, RandomAccess {

    /**
     * Loads elements by specified <code>indexes</code>
     * 
     * @param indexes of elements
     * @return elements
     */
    RFuture<List<V>> getAsync(int ...indexes);
    
    /**
     * Add <code>element</code> after <code>elementToFind</code>
     * 
     * @param elementToFind - object to find
     * @param element - object to add
     * @return new list size
     */
    RFuture<Integer> addAfterAsync(V elementToFind, V element);
    
    /**
     * Add <code>element</code> before <code>elementToFind</code>
     * 
     * @param elementToFind - object to find
     * @param element - object to add
     * @return new list size
     */
    RFuture<Integer> addBeforeAsync(V elementToFind, V element);
    
    RFuture<Boolean> addAsync(int index, V element);
    
    RFuture<Boolean> addAllAsync(int index, Collection<? extends V> coll);

    RFuture<Integer> lastIndexOfAsync(Object o);

    RFuture<Integer> indexOfAsync(Object o);

    /**
     * Set <code>element</code> at <code>index</code>.
     * Works faster than {@link #setAsync(int, Object)} but 
     * doesn't return previous element.
     * 
     * @param index - index of object
     * @param element - object
     * @return void
     */
    RFuture<Void> fastSetAsync(int index, V element);

    RFuture<V> setAsync(int index, V element);

    RFuture<V> getAsync(int index);

    /**
     * Read all elements at once
     *
     * @return list of values
     */
    RFuture<List<V>> readAllAsync();

    /**
     * Trim list and remains elements only in specified range
     * <code>fromIndex</code>, inclusive, and <code>toIndex</code>, inclusive.
     *
     * @param fromIndex - from index
     * @param toIndex - to index
     * @return void
     */
    RFuture<Void> trimAsync(int fromIndex, int toIndex);

    RFuture<Void> fastRemoveAsync(int index);

    RFuture<V> removeAsync(int index);
    
    RFuture<Boolean> removeAsync(Object o, int count);
    
}
