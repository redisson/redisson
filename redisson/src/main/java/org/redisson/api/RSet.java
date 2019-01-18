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

import java.util.Iterator;
import java.util.Set;
import java.util.stream.Stream;

import org.redisson.api.mapreduce.RCollectionMapReduce;

/**
 * Distributed and concurrent implementation of {@link java.util.Set}
 *
 * @author Nikita Koksharov
 *
 * @param <V> value
 */
public interface RSet<V> extends Set<V>, RExpirable, RSetAsync<V>, RSortable<Set<V>> {

    /**
     * Returns <code>RCountDownLatch</code> instance associated with <code>value</code>
     * 
     * @param value - set value
     * @return RCountDownLatch object
     */
    RCountDownLatch getCountDownLatch(V value);
    
    /**
     * Returns <code>RPermitExpirableSemaphore</code> instance associated with <code>value</code>
     * 
     * @param value - set value
     * @return RPermitExpirableSemaphore object
     */
    RPermitExpirableSemaphore getPermitExpirableSemaphore(V value);

    /**
     * Returns <code>RSemaphore</code> instance associated with <code>value</code>
     * 
     * @param value - set value
     * @return RSemaphore object
     */
    RSemaphore getSemaphore(V value);
    
    /**
     * Returns <code>RLock</code> instance associated with <code>value</code>
     * 
     * @param value - set value
     * @return RLock object
     */
    RLock getFairLock(V value);
    
    /**
     * Returns <code>RReadWriteLock</code> instance associated with <code>value</code>
     * 
     * @param value - set value
     * @return RReadWriteLock object
     */
    RReadWriteLock getReadWriteLock(V value);
    
    /**
     * Returns lock instance associated with <code>value</code>
     * 
     * @param value - set value
     * @return RLock object
     */
    RLock getLock(V value);
    
    /**
     * Returns stream of elements in this set.
     * Elements are loaded in batch. Batch size is defined by <code>count</code> param. 
     * 
     * @param count - size of elements batch
     * @return stream of elements
     */
    Stream<V> stream(int count);
    
    /**
     * Returns stream of elements in this set.
     * Elements are loaded in batch. Batch size is defined by <code>count</code> param.
     * If pattern is not null then only elements match this pattern are loaded.
     * 
     * @param pattern - search pattern
     * @param count - size of elements batch
     * @return stream of elements
     */
    Stream<V> stream(String pattern, int count);
    
    /**
     * Returns stream of elements in this set matches <code>pattern</code>. 
     * 
     * @param pattern - search pattern
     * @return stream of elements
     */
    Stream<V> stream(String pattern);
    
    /**
     * Returns an iterator over elements in this set.
     * Elements are loaded in batch. Batch size is defined by <code>count</code> param. 
     * 
     * @param count - size of elements batch
     * @return iterator
     */
    Iterator<V> iterator(int count);
    
    /**
     * Returns an iterator over elements in this set.
     * Elements are loaded in batch. Batch size is defined by <code>count</code> param.
     * If pattern is not null then only elements match this pattern are loaded.
     * 
     * @param pattern - search pattern
     * @param count - size of elements batch
     * @return iterator
     */
    Iterator<V> iterator(String pattern, int count);
    
    /**
     * Returns iterator over elements in this set matches <code>pattern</code>. 
     * 
     * @param pattern - search pattern
     * @return iterator
     */
    Iterator<V> iterator(String pattern);
    
    /**
     * Returns <code>RMapReduce</code> object associated with this object
     * 
     * @param <KOut> output key
     * @param <VOut> output value
     * @return MapReduce instance
     */
    <KOut, VOut> RCollectionMapReduce<V, KOut, VOut> mapReduce();
    
    /**
     * Removes and returns random elements from set
     * 
     * @param amount of random values
     * @return random values
     */
    Set<V> removeRandom(int amount);
    
    /**
     * Removes and returns random element from set
     *
     * @return value
     */
    V removeRandom();

    /**
     * Returns random element from set
     *
     * @return value
     */
    V random();

    /**
     * Returns random elements from set limited by <code>count</code>
     *
     * @param count - values amount to return
     * @return value
     */
    Set<V> random(int count);
    
    /**
     * Move a member from this set to the given destination set in.
     *
     * @param destination the destination set
     * @param member the member to move
     * @return true if the element is moved, false if the element is not a
     * member of this set or no operation was performed
     */
    boolean move(String destination, V member);

    /**
     * Read all elements at once
     *
     * @return values
     */
    Set<V> readAll();

    /**
     * Union sets specified by name and write to current set.
     * If current set already exists, it is overwritten.
     *
     * @param names - name of sets
     * @return size of union
     */
    int union(String... names);

    /**
     * Union sets specified by name with current set
     * without current set state change.
     * 
     * @param names - name of sets
     * @return values
     */
    Set<V> readUnion(String... names);

    /**
     * Diff sets specified by name and write to current set.
     * If current set already exists, it is overwritten.
     *
     * @param names - name of sets
     * @return values
     */
    int diff(String... names);

    /**
     * Diff sets specified by name with current set.
     * Without current set state change.
     * 
     * @param names - name of sets
     * @return values
     */

    Set<V> readDiff(String... names);
    /**
     * Intersection sets specified by name and write to current set.
     * If current set already exists, it is overwritten.
     *
     * @param names - name of sets
     * @return size of intersection
     */
    int intersection(String... names);

    /**
     * Intersection sets specified by name with current set
     * without current set state change.
     * 
     * @param names - name of sets
     * @return values
     */
    Set<V> readIntersection(String... names);

}
