/**
 * Copyright (c) 2013-2024 Nikita Koksharov
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
import java.util.Set;

/**
 * Async set functions
 *
 * @author Nikita Koksharov
 *
 * @param <V> value
 */
public interface RSetAsync<V> extends RCollectionAsync<V>, RSortableAsync<Set<V>> {

    /**
     * Removes and returns random elements from set
     * in async mode
     * 
     * @param amount of random values
     * @return random values
     */
    RFuture<Set<V>> removeRandomAsync(int amount);
    
    /**
     * Removes and returns random element from set
     * in async mode
     * 
     * @return value
     */
    RFuture<V> removeRandomAsync();

    /**
     * Returns random element from set
     * in async mode
     * 
     * @return value
     */
    RFuture<V> randomAsync();
    
    /**
     * Returns random elements from set limited by <code>count</code>
     *
     * @param count - values amount to return
     * @return value
     */
    RFuture<Set<V>> randomAsync(int count);

    /**
     * Move a member from this set to the given destination set in async mode.
     *
     * @param destination the destination set
     * @param member the member to move
     * @return <code>true</code> if the element is moved, <code>false</code> if the element is not a
     * member of this set or no operation was performed
     */
    RFuture<Boolean> moveAsync(String destination, V member);

    /**
     * Read all elements at once
     *
     * @return values
     */
    RFuture<Set<V>> readAllAsync();

    /**
     * Union sets specified by name and write to current set.
     * If current set already exists, it is overwritten.
     *
     * @param names - name of sets
     * @return size of union
     */
    RFuture<Integer> unionAsync(String... names);

    /**
     * Union sets specified by name with current set.
     * Without current set state change.
     *
     * @param names - name of sets
     * @return values
     */
    RFuture<Set<V>> readUnionAsync(String... names);

    /**
     * Diff sets specified by name and write to current set.
     * If current set already exists, it is overwritten.
     *
     * @param names - name of sets
     * @return size of diff
     */
    RFuture<Integer> diffAsync(String... names);

    /**
     * Diff sets specified by name with current set.
     * Without current set state change.
     * 
     * @param names - name of sets
     * @return values
     */
    RFuture<Set<V>> readDiffAsync(String... names);

    /**
     * Intersection sets specified by name and write to current set.
     * If current set already exists, it is overwritten.
     *
     * @param names - name of sets
     * @return size of intersection
     */
    RFuture<Integer> intersectionAsync(String... names);

    /**
     * Intersection sets specified by name with current set.
     * Without current set state change.
     * 
     * @param names - name of sets
     * @return values
     */
    RFuture<Set<V>> readIntersectionAsync(String... names);

    /**
     * Counts elements of set as a result of sets intersection with current set.
     * <p>
     * Requires <b>Redis 7.0.0 and higher.</b>
     *
     * @param names - name of sets
     * @return amount of elements
     */
    RFuture<Integer> countIntersectionAsync(String... names);

    /**
     * Counts elements of set as a result of sets intersection with current set.
     * <p>
     * Requires <b>Redis 7.0.0 and higher.</b>
     *
     * @param names - name of sets
     * @param limit - sets intersection limit
     * @return amount of elements
     */
    RFuture<Integer> countIntersectionAsync(int limit, String... names);

    /**
     * Tries to add elements only if none of them in set.
     *
     * @param values - values to add
     * @return <code>true</code> if elements successfully added,
     *          otherwise <code>false</code>.
     */
    RFuture<Boolean> tryAddAsync(V... values);

    /**
     * Adds all elements contained in the specified collection.
     * Returns number of added elements.
     *
     * @param c - collection of elements to add
     * @return number of added elements
     */
    RFuture<Integer> addAllCountedAsync(Collection<? extends V> c);

    /**
     * Removes all elements contained in the specified collection.
     * Returns number of removed elements.
     *
     * @param c - collection of elements to add
     * @return number of removed elements
     */
    RFuture<Integer> removeAllCountedAsync(Collection<? extends V> c);

    /**
     * Check if each element is contained in the specified collection.
     * Returns contained elements.
     * <p>
     * Requires <b>Redis 6.2.0 and higher.</b>
     *
     * @param c - collection to check
     * @return contained elements
     */
    RFuture<List<V>> containsEachAsync(Collection<V> c);

    /**
     * Adds object event listener
     *
     * @see org.redisson.api.listener.TrackingListener
     * @see org.redisson.api.listener.SetAddListener
     * @see org.redisson.api.listener.SetRemoveListener
     * @see org.redisson.api.listener.SetRemoveRandomListener
     * @see org.redisson.api.ExpiredObjectListener
     * @see org.redisson.api.DeletedObjectListener
     *
     * @param listener - object event listener
     * @return listener id
     */
    RFuture<Integer> addListenerAsync(ObjectListener listener);

    /**
     * Returns elements iterator fetches elements in a batch.
     * Batch size is defined by <code>count</code> param.
     *
     * @return Asynchronous Iterable object
     */
    AsyncIterator<V> iteratorAsync();

    /**
     * Returns elements iterator fetches elements in a batch.
     * Batch size is defined by <code>count</code> param.
     *
     * @param count - size of elements batch
     * @return Asynchronous Iterable object
     */
    AsyncIterator<V> iteratorAsync(int count);

}
