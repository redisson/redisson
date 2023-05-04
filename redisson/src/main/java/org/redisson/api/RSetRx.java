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
import java.util.Set;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

/**
 * RxJava2 interface for Redis based implementation of {@link java.util.Set}
 *
 * @author Nikita Koksharov
 *
 * @param <V> type of value
 */
public interface RSetRx<V> extends RCollectionRx<V>, RSortableRx<Set<V>> {

    /**
     * Adds all elements contained in the specified collection.
     * Returns number of added elements.
     *
     * @param c - collection of elements to add
     * @return number of added elements
     */
    Single<Integer> addAllCounted(Collection<? extends V> c);

    /**
     * Removes all elements contained in the specified collection.
     * Returns number of removed elements.
     *
     * @param c - collection of elements to add
     * @return number of removed elements
     */
    Single<Integer> removeAllCounted(Collection<? extends V> c);

    /**
     * Returns <code>RPermitExpirableSemaphore</code> instance associated with <code>value</code>
     * 
     * @param value - set value
     * @return RPermitExpirableSemaphore object
     */
    RPermitExpirableSemaphoreRx getPermitExpirableSemaphore(V value);

    /**
     * Returns <code>RSemaphore</code> instance associated with <code>value</code>
     * 
     * @param value - set value
     * @return RSemaphore object
     */
    RSemaphoreRx getSemaphore(V value);
    
    /**
     * Returns <code>RLock</code> instance associated with <code>value</code>
     * 
     * @param value - set value
     * @return RLock object
     */
    RLockRx getFairLock(V value);
    
    /**
     * Returns <code>RReadWriteLock</code> instance associated with <code>value</code>
     * 
     * @param value - set value
     * @return RReadWriteLock object
     */
    RReadWriteLockRx getReadWriteLock(V value);
    
    /**
     * Returns lock instance associated with <code>value</code>
     * 
     * @param value - set value
     * @return RLock object
     */
    RLockRx getLock(V value);
    
    /**
     * Returns elements iterator fetches elements in a batch.
     * Batch size is defined by <code>count</code> param.
     * 
     * @param count - size of elements batch
     * @return iterator
     */
    Flowable<V> iterator(int count);
    
    /**
     * Returns elements iterator fetches elements in a batch.
     * Batch size is defined by <code>count</code> param.
     * If pattern is not null then only elements match this pattern are loaded.
     * 
     * @param pattern - search pattern
     * @param count - size of elements batch
     * @return iterator
     */
    Flowable<V> iterator(String pattern, int count);
    
    /**
     * Returns elements iterator.
     * If <code>pattern</code> is not null then only elements match this pattern are loaded.
     * 
     * @param pattern - search pattern
     * @return iterator
     */
    Flowable<V> iterator(String pattern);
    
    /**
     * Removes and returns random elements limited by <code>amount</code>
     *
     * @param amount of random elements
     * @return random elements
     */
    Single<Set<V>> removeRandom(int amount);
    
    /**
     * Removes and returns random element
     *
     * @return random element
     */
    Maybe<V> removeRandom();

    /**
     * Returns random element
     *
     * @return random element
     */
    Maybe<V> random();

    /**
     * Returns random elements from set limited by <code>count</code>
     *
     * @param count - values amount to return
     * @return random elements
     */
    Single<Set<V>> random(int count);


    /**
     * Move a member from this set to the given destination set in async mode.
     *
     * @param destination the destination set
     * @param member the member to move
     * @return true if the element is moved, false if the element is not a
     * member of this set or no operation was performed
     */
    Single<Boolean> move(String destination, V member);

    /**
     * Read all elements at once
     *
     * @return values
     */
    Single<Set<V>> readAll();
    
    /**
     * Union sets specified by name and write to current set.
     * If current set already exists, it is overwritten.
     *
     * @param names - name of sets
     * @return size of union
     */
    Single<Integer> union(String... names);

    /**
     * Union sets specified by name with current set.
     * Without current set state change.
     *
     * @param names - name of sets
     * @return size of union
     */
    Single<Set<V>> readUnion(String... names);
    
    /**
     * Diff sets specified by name and write to current set.
     * If current set already exists, it is overwritten.
     *
     * @param names - name of sets
     * @return size of diff
     */
    Single<Integer> diff(String... names);
    
    /**
     * Diff sets specified by name with current set.
     * Without current set state change.
     * 
     * @param names - name of sets
     * @return values
     */
    Single<Set<V>> readDiff(String... names);
    
    /**
     * Intersection sets specified by name and write to current set.
     * If current set already exists, it is overwritten.
     *
     * @param names - name of sets
     * @return size of intersection
     */
    Single<Integer> intersection(String... names);

    /**
     * Counts elements of set as a result of sets intersection with current set.
     * <p>
     * Requires <b>Redis 7.0.0 and higher.</b>
     *
     * @param names - name of sets
     * @return amount of elements
     */
    Single<Integer> countIntersection(String... names);

    /**
     * Counts elements of set as a result of sets intersection with current set.
     * <p>
     * Requires <b>Redis 7.0.0 and higher.</b>
     *
     * @param names - name of sets
     * @param limit - sets intersection limit
     * @return amount of elements
     */
    Single<Integer> countIntersection(int limit, String... names);

    /**
     * Intersection sets specified by name with current set.
     * Without current set state change.
     *
     * @param names - name of sets
     * @return values
     */
    Single<Set<V>> readIntersection(String... names);

    /**
     * Tries to add elements only if none of them in set.
     *
     * @param values - values to add
     * @return <code>true</code> if elements successfully added,
     *          otherwise <code>false</code>.
     */
    Single<Boolean> tryAdd(V... values);

    /**
     * Check if each element is contained in the specified collection.
     * Returns contained elements.
     * <p>
     * Requires <b>Redis 6.2.0 and higher.</b>
     *
     * @param c - collection to check
     * @return contained elements
     */
    Single<List<V>> containsEach(Collection<V> c);

    /**
     * Adds object event listener
     *
     * @see org.redisson.api.listener.SetAddListener
     * @see org.redisson.api.listener.SetRemoveListener
     * @see org.redisson.api.listener.SetRemoveRandomListener
     * @see org.redisson.api.ExpiredObjectListener
     * @see org.redisson.api.DeletedObjectListener
     *
     * @param listener - object event listener
     * @return listener id
     */
    Single<Integer> addListener(ObjectListener listener);

}
