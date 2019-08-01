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

import java.util.Set;

import io.reactivex.Flowable;

/**
 * RxJava2 interface for RSetCache object
 *
 * @author Nikita Koksharov
 *
 * @param <V> value
 */
public interface RSetRx<V> extends RCollectionRx<V>, RSortableRx<Set<V>> {

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
     * Returns an iterator over elements in this set.
     * Elements are loaded in batch. Batch size is defined by <code>count</code> param. 
     * 
     * @param count - size of elements batch
     * @return iterator
     */
    Flowable<V> iterator(int count);
    
    /**
     * Returns an iterator over elements in this set.
     * Elements are loaded in batch. Batch size is defined by <code>count</code> param.
     * If pattern is not null then only elements match this pattern are loaded.
     * 
     * @param pattern - search pattern
     * @param count - size of elements batch
     * @return iterator
     */
    Flowable<V> iterator(String pattern, int count);
    
    /**
     * Returns iterator over elements in this set matches <code>pattern</code>. 
     * 
     * @param pattern - search pattern
     * @return iterator
     */
    Flowable<V> iterator(String pattern);
    
    /**
     * Removes and returns random elements from set
     * in async mode
     * 
     * @param amount of random values
     * @return random values
     */
    Flowable<Set<V>> removeRandom(int amount);
    
    /**
     * Removes and returns random element from set
     * in async mode
     *
     * @return value
     */
    Flowable<V> removeRandom();

    /**
     * Returns random element from set
     * in async mode
     *
     * @return value
     */
    Flowable<V> random();

    /**
     * Move a member from this set to the given destination set in async mode.
     *
     * @param destination the destination set
     * @param member the member to move
     * @return true if the element is moved, false if the element is not a
     * member of this set or no operation was performed
     */
    Flowable<Boolean> move(String destination, V member);

    /**
     * Read all elements at once
     *
     * @return values
     */
    Flowable<Set<V>> readAll();
    
    /**
     * Union sets specified by name and write to current set.
     * If current set already exists, it is overwritten.
     *
     * @param names - name of sets
     * @return size of union
     */
    Flowable<Long> union(String... names);

    /**
     * Union sets specified by name with current set.
     * Without current set state change.
     *
     * @param names - name of sets
     * @return size of union
     */
    Flowable<Set<V>> readUnion(String... names);
    
    /**
     * Diff sets specified by name and write to current set.
     * If current set already exists, it is overwritten.
     *
     * @param names - name of sets
     * @return size of diff
     */
    Flowable<Long> diff(String... names);
    
    /**
     * Diff sets specified by name with current set.
     * Without current set state change.
     * 
     * @param names - name of sets
     * @return values
     */
    Flowable<Set<V>> readDiff(String... names);
    
    /**
     * Intersection sets specified by name and write to current set.
     * If current set already exists, it is overwritten.
     *
     * @param names - name of sets
     * @return size of intersection
     */
    Flowable<Long> intersection(String... names);

    /**
     * Intersection sets specified by name with current set.
     * Without current set state change.
     *
     * @param names - name of sets
     * @return values
     */
    Flowable<Set<V>> readIntersection(String... names);

}
