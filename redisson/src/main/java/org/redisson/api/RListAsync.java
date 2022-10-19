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
    RFuture<List<V>> getAsync(int... indexes);
    
    /**
     * Inserts <code>element</code> after <code>elementToFind</code>
     * 
     * @param elementToFind - object to find
     * @param element - object to add
     * @return new list size
     */
    RFuture<Integer> addAfterAsync(V elementToFind, V element);
    
    /**
     * Inserts <code>element</code> before <code>elementToFind</code>
     * 
     * @param elementToFind - object to find
     * @param element - object to add
     * @return new list size
     */
    RFuture<Integer> addBeforeAsync(V elementToFind, V element);
    
    /**
     * Inserts <code>element</code> at <code>index</code>. 
     * Subsequent elements are shifted. 
     * 
     * @param index - index number
     * @param element - element to insert
     * @return {@code true} if list was changed
     */
    RFuture<Boolean> addAsync(int index, V element);
    
    /**
     * Inserts <code>elements</code> at <code>index</code>. 
     * Subsequent elements are shifted. 
     * 
     * @param index - index number
     * @param elements - elements to insert
     * @return {@code true} if list changed
     *      or {@code false} if element isn't found
     */
    RFuture<Boolean> addAllAsync(int index, Collection<? extends V> elements);

    /**
     * Returns last index of <code>element</code> or 
     * -1 if element isn't found
     * 
     * @param element to find
     * @return index of -1 if element isn't found
     */
    RFuture<Integer> lastIndexOfAsync(Object element);

    /**
     * Returns last index of <code>element</code> or 
     * -1 if element isn't found
     * 
     * @param element to find
     * @return index of -1 if element isn't found
     */
    RFuture<Integer> indexOfAsync(Object element);

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

    /**
     * Set <code>element</code> at <code>index</code> and returns previous element.
     * 
     * @param index - index of object
     * @param element - object
     * @return previous element or <code>null</code> if element wasn't set.
     */
    RFuture<V> setAsync(int index, V element);

    /**
     * Get element at <code>index</code>
     * 
     * @param index - index of object
     * @return element
     */
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

    /**
     * Removes element at <code>index</code>.
     * Works faster than {@link #removeAsync(Object, int)} but 
     * doesn't return element.
     * 
     * @param index - index of object
     * @return void
     */
    RFuture<Void> fastRemoveAsync(int index);

    /**
     * Removes element at <code>index</code>.
     * 
     * @param index - index of object
     * @return element or <code>null</code> if element wasn't set.
     */
    RFuture<V> removeAsync(int index);
    
    /**
     * Removes up to <code>count</code> occurrences of <code>element</code> 
     * 
     * @param element - element to find
     * @param count - amount occurrences
     * @return {@code true} if at least one element removed; 
     *      or {@code false} if element isn't found
     */
    RFuture<Boolean> removeAsync(Object element, int count);
    
    /**
     * Returns range of values from 0 index to <code>toIndex</code>. Indexes are zero based. 
     * <code>-1</code> means the last element, <code>-2</code> means penultimate and so on.
     * 
     * @param toIndex - end index
     * @return elements
     */
    RFuture<List<V>> rangeAsync(int toIndex);
    
    /**
     * Returns range of values from <code>fromIndex</code> to <code>toIndex</code> index including.
     * Indexes are zero based. <code>-1</code> means the last element, <code>-2</code> means penultimate and so on.
     * 
     * @param fromIndex - start index
     * @param toIndex - end index
     * @return elements
     */
    RFuture<List<V>> rangeAsync(int fromIndex, int toIndex);

    /**
     * Adds object event listener
     *
     * @see org.redisson.api.ExpiredObjectListener
     * @see org.redisson.api.DeletedObjectListener
     * @see org.redisson.api.listener.ListAddListener
     * @see org.redisson.api.listener.ListInsertListener
     * @see org.redisson.api.listener.ListSetListener
     * @see org.redisson.api.listener.ListRemoveListener
     * @see org.redisson.api.listener.ListTrimListener
     *
     * @param listener - object event listener
     * @return listener id
     */
    RFuture<Integer> addListenerAsync(ObjectListener listener);

}
