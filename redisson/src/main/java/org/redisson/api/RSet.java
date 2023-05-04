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

import org.redisson.api.mapreduce.RCollectionMapReduce;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Redis based implementation of {@link java.util.Set}
 *
 * @author Nikita Koksharov
 *
 * @param <V> type of value
 */
public interface RSet<V> extends Set<V>, RExpirable, RSetAsync<V>, RSortable<Set<V>> {

    /**
     * Adds all elements contained in the specified collection.
     * Returns number of added elements.
     *
     * @param c - collection of elements to add
     * @return number of added elements
     */
    int addAllCounted(Collection<? extends V> c);

    /**
     * Removes all elements contained in the specified collection.
     * Returns number of removed elements.
     *
     * @param c - collection of elements to add
     * @return number of removed elements
     */
    int removeAllCounted(Collection<? extends V> c);

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
     * Returns stream of elements fetches elements in a batch.
     * Batch size is defined by <code>count</code> param.
     * 
     * @param count - size of elements batch
     * @return stream of elements
     */
    Stream<V> stream(int count);
    
    /**
     * Returns stream of elements fetches elements in a batch.
     * Batch size is defined by <code>count</code> param.
     * If pattern is not null then only elements match this pattern are loaded.
     * 
     * @param pattern - search pattern
     * @param count - size of elements batch
     * @return stream of elements
     */
    Stream<V> stream(String pattern, int count);
    
    /**
     * Returns stream of elements.
     * If pattern is not null then only elements match this pattern are loaded.
     * 
     * @param pattern - search pattern
     * @return stream of elements
     */
    Stream<V> stream(String pattern);
    
    /**
     * Returns elements iterator fetches elements in a batch.
     * Batch size is defined by <code>count</code> param.
     * 
     * @param count - size of elements batch
     * @return iterator
     */
    Iterator<V> iterator(int count);
    
    /**
     * Returns elements iterator fetches elements in a batch.
     * Batch size is defined by <code>count</code> param.
     * If pattern is not null then only elements match this pattern are loaded.
     * 
     * @param pattern - search pattern
     * @param count - size of elements batch
     * @return iterator
     */
    Iterator<V> iterator(String pattern, int count);
    
    /**
     * Returns elements iterator.
     * If <code>pattern</code> is not null then only elements match this pattern are loaded.
     * 
     * @param pattern - search pattern
     * @return iterator
     */
    Iterator<V> iterator(String pattern);

    /**
     * Returns element iterator that can be shared across multiple applications.
     * Creating multiple iterators on the same object with this method will result in a single shared iterator.
     * See {@linkplain RSet#distributedIterator(String, String, int)} for creating different iterators.
     * @param count batch size
     * @return shared elements iterator
     */
    Iterator<V> distributedIterator(int count);

    /**
     * Returns iterator over elements that match specified pattern. Iterator can be shared across multiple applications.
     * Creating multiple iterators on the same object with this method will result in a single shared iterator.
     * See {@linkplain RSet#distributedIterator(String, String, int)} for creating different iterators.
     * @param pattern element pattern
     * @return shared elements iterator
     */
    Iterator<V> distributedIterator(String pattern);

    /**
     * Returns iterator over elements that match specified pattern. Iterator can be shared across multiple applications.
     * Creating multiple iterators on the same object with this method will result in a single shared iterator.
     * Iterator name must be resolved to the same hash slot as set name.
     * @param pattern element pattern
     * @param count batch size
     * @param iteratorName redis object name to which cursor will be saved
     * @return shared elements iterator
     */
    Iterator<V> distributedIterator(String iteratorName, String pattern, int count);

    /**
     * Returns <code>RMapReduce</code> object associated with this object
     * 
     * @param <KOut> output key
     * @param <VOut> output value
     * @return MapReduce instance
     */
    <KOut, VOut> RCollectionMapReduce<V, KOut, VOut> mapReduce();
    
    /**
     * Removes and returns random elements limited by <code>amount</code>
     * 
     * @param amount of random elements
     * @return random elements
     */
    Set<V> removeRandom(int amount);
    
    /**
     * Removes and returns random element
     *
     * @return random element
     */
    V removeRandom();

    /**
     * Returns random element
     *
     * @return random element
     */
    V random();

    /**
     * Returns random elements from set limited by <code>count</code>
     *
     * @param count - values amount to return
     * @return random elements
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

    /**
     * Counts elements of set as a result of sets intersection with current set.
     * <p>
     * Requires <b>Redis 7.0.0 and higher.</b>
     *
     * @param names - name of sets
     * @return amount of elements
     */
    Integer countIntersection(String... names);

    /**
     * Counts elements of set as a result of sets intersection with current set.
     * <p>
     * Requires <b>Redis 7.0.0 and higher.</b>
     *
     * @param names - name of sets
     * @param limit - sets intersection limit
     * @return amount of elements
     */
    Integer countIntersection(int limit, String... names);

    /**
     * Tries to add elements only if none of them in set.
     *
     * @param values - values to add
     * @return <code>true</code> if elements successfully added,
     *          otherwise <code>false</code>.
     */
    boolean tryAdd(V... values);

    /**
     * Check if each element is contained in the specified collection.
     * Returns contained elements.
     * <p>
     * Requires <b>Redis 6.2.0 and higher.</b>
     *
     * @param c - collection to check
     * @return contained elements
     */
    List<V> containsEach(Collection<V> c);

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
    int addListener(ObjectListener listener);

}
