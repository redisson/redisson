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

/**
 * Common async interface for collection object
 * 
 * @author Nikita Koksharov
 *
 * @param <V> type of value
 */
public interface RCollectionAsync<V> extends RExpirableAsync {

    /**
     * Retains only the elements in this collection that are contained in the
     * specified collection.
     *
     * @param c collection containing elements to be retained in this collection
     * @return <code>true</code> if this collection changed as a result of the call
     */
    RFuture<Boolean> retainAllAsync(Collection<?> c);

    /**
     * Removes all of this collection's elements that are also contained in the
     * specified collection.
     *
     * @param c collection containing elements to be removed from this collection
     * @return <code>true</code> if this collection changed as a result of the
     *         call
     */
    RFuture<Boolean> removeAllAsync(Collection<?> c);

    /**
     * Returns <code>true</code> if this collection contains encoded state of the specified element.
     *
     * @param o element whose presence in this collection is to be tested
     * @return <code>true</code> if this collection contains the specified
     *         element and <code>false</code> otherwise
     */
    RFuture<Boolean> containsAsync(Object o);

    /**
     * Returns <code>true</code> if this collection contains all of the elements
     * in the specified collection.
     *
     * @param  c collection to be checked for containment in this collection
     * @return <code>true</code> if this collection contains all of the elements
     *         in the specified collection
     */
    RFuture<Boolean> containsAllAsync(Collection<?> c);

    /**
     * Removes a single instance of the specified element from this
     * collection, if it is present.
     *
     * @param o element to be removed from this collection, if present
     * @return <code>true</code> if an element was removed as a result of this call
     */
    RFuture<Boolean> removeAsync(Object o);

    /**
     * Returns number of elements in this collection.
     *
     * @return size of collection
     */
    RFuture<Integer> sizeAsync();

    /**
     * Adds element into this collection.
     * 
     * @param e - element to add
     * @return <code>true</code> if an element was added 
     *          and <code>false</code> if it is already present
     */
    RFuture<Boolean> addAsync(V e);

    /**
     * Adds all elements contained in the specified collection
     * 
     * @param c - collection of elements to add
     * @return <code>true</code> if at least one element was added 
     *          and <code>false</code> if all elements are already present
     */
    RFuture<Boolean> addAllAsync(Collection<? extends V> c);

}
