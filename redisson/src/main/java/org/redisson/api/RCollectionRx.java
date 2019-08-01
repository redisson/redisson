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

import org.reactivestreams.Publisher;

import io.reactivex.Flowable;
import io.reactivex.Single;

/**
 * Common RxJava2 interface for collection object
 * 
 * @author Nikita Koksharov
 *
 * @param <V> value
 */
public interface RCollectionRx<V> extends RExpirableRx {

    /**
     * Returns iterator over collection elements
     * 
     * @return iterator
     */
    Flowable<V> iterator();

    /**
     * Retains only the elements in this collection that are contained in the
     * specified collection (optional operation).
     *
     * @param c collection containing elements to be retained in this collection
     * @return <code>true</code> if this collection changed as a result of the call
     */
    Single<Boolean> retainAll(Collection<?> c);

    /**
     * Removes all of this collection's elements that are also contained in the
     * specified collection (optional operation).
     *
     * @param c collection containing elements to be removed from this collection
     * @return <code>true</code> if this collection changed as a result of the
     *         call
     */
    Single<Boolean> removeAll(Collection<?> c);

    /**
     * Returns <code>true</code> if this collection contains encoded state of the specified element.
     *
     * @param o element whose presence in this collection is to be tested
     * @return <code>true</code> if this collection contains the specified
     *         element and <code>false</code> otherwise
     */
    Single<Boolean> contains(V o);

    /**
     * Returns <code>true</code> if this collection contains all of the elements
     * in the specified collection.
     *
     * @param  c collection to be checked for containment in this collection
     * @return <code>true</code> if this collection contains all of the elements
     *         in the specified collection
     */
    Single<Boolean> containsAll(Collection<?> c);

    /**
     * Removes a single instance of the specified element from this
     * collection, if it is present (optional operation).
     *
     * @param o element to be removed from this collection, if present
     * @return <code>true</code> if an element was removed as a result of this call
     */
    Single<Boolean> remove(V o);

    /**
     * Returns number of elements in this collection.
     *
     * @return size of collection
     */
    Single<Integer> size();

    /**
     * Adds element into this collection.
     * 
     * @param e - element to add
     * @return <code>true</code> if an element was added 
     *          and <code>false</code> if it is already present
     */
    Single<Boolean> add(V e);

    /**
     * Adds all elements contained in the specified collection
     * 
     * @param c - collection of elements to add
     * @return <code>true</code> if at least one element was added 
     *          and <code>false</code> if all elements are already present
     */
    Single<Boolean> addAll(Publisher<? extends V> c);
    
    /**
     * Adds all elements contained in the specified collection
     * 
     * @param c - collection of elements to add
     * @return <code>true</code> if at least one element was added 
     *          and <code>false</code> if all elements are already present
     */
    Single<Boolean> addAll(Collection<? extends V> c);

}
