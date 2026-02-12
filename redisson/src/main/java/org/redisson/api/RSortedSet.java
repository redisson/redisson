/**
 * Copyright (c) 2013-2026 Nikita Koksharov
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

import java.time.Duration;
import java.util.*;

import org.redisson.api.mapreduce.RCollectionMapReduce;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <V> value type
 */
public interface RSortedSet<V> extends SortedSet<V>, RExpirable {

    /**
     * Returns <code>RMapReduce</code> object associated with this object
     * 
     * @param <KOut> output key
     * @param <VOut> output value
     * @return MapReduce instance
     */
    <KOut, VOut> RCollectionMapReduce<V, KOut, VOut> mapReduce();

    Collection<V> readAll();
    
    RFuture<Collection<V>> readAllAsync();

    /**
     * Removes and returns the head element or {@code null} if this sorted set is empty.
     *
     * @return the head element,
     *         or {@code null} if this sorted set is empty
     */
    V pollFirst();

    /**
     * Removes and returns the head elements of this sorted set.
     * <p>
     * Requires <b>Redis 6.2.0 and higher.</b>
     *
     * @param count - elements amount
     * @return the head elements of this sorted set
     */
    Collection<V> pollFirst(int count);

    /**
     * Removes and returns the head element or {@code null} if this sorted set is empty.
     *
     * @param duration how long to wait before giving up
     * @return the head element,
     *         or {@code null} if this sorted set is empty
     */
    V pollFirst(Duration duration);

    /**
     * Removes and returns the head elements.
     * <p>
     * Requires <b>Redis 7.0.0 and higher.</b>
     *
     * @param duration how long to wait before giving up
     * @param count elements amount
     * @return the head elements
     */
    List<V> pollFirst(Duration duration, int count);

    /**
     * Removes and returns the head element or {@code null} if this sorted set is empty.
     *
     * @return the head element,
     *         or {@code null} if this sorted set is empty
     */
    RFuture<V> pollFirstAsync();

    /**
     * Removes and returns the head elements of this sorted set.
     * <p>
     * Requires <b>Redis 6.2.0 and higher.</b>
     *
     * @param count - elements amount
     * @return the head elements of this sorted set
     */
    RFuture<Collection<V>> pollFirstAsync(int count);

    /**
     * Removes and returns the head element or {@code null} if this sorted set is empty.
     *
     * @param duration how long to wait before giving up
     * @return the head element,
     *         or {@code null} if this sorted set is empty
     */
    RFuture<V> pollFirstAsync(Duration duration);

    /**
     * Removes and returns the head elements.
     * <p>
     * Requires <b>Redis 7.0.0 and higher.</b>
     *
     * @param duration how long to wait before giving up
     * @param count elements amount
     * @return the head elements
     */
    RFuture<List<V>> pollFirstAsync(Duration duration, int count);

    /**
     * Removes and returns the tail element or {@code null} if this sorted set is empty.
     *
     * @return the tail element or {@code null} if this sorted set is empty
     */
    V pollLast();

    /**
     * Removes and returns the tail elements of this sorted set.
     * <p>
     * Requires <b>Redis 6.2.0 and higher.</b>
     *
     * @param count - elements amount
     * @return the tail elements of this sorted set
     */
    Collection<V> pollLast(int count);

    /**
     * Removes and returns the tail element or {@code null} if this sorted set is empty.
     *
     * @param duration how long to wait before giving up
     * @return the tail element,
     *         or {@code null} if this sorted set is empty
     */
    V pollLast(Duration duration);

    /**
     * Removes and returns the tail elements.
     * <p>
     * Requires <b>Redis 7.0.0 and higher.</b>
     *
     * @param duration how long to wait before giving up
     * @param count elements amount
     * @return the tail elements
     */
    List<V> pollLast(Duration duration, int count);

    /**
     * Removes and returns the tail element or {@code null} if this sorted set is empty.
     *
     * @return the tail element or {@code null} if this sorted set is empty
     */
    RFuture<V> pollLastAsync();

    /**
     * Removes and returns the tail elements of this sorted set.
     * <p>
     * Requires <b>Redis 6.2.0 and higher.</b>
     *
     * @param count - elements amount
     * @return the tail elements of this sorted set
     */
    RFuture<Collection<V>> pollLastAsync(int count);

    /**
     * Removes and returns the tail element or {@code null} if this sorted set is empty.
     *
     * @param duration how long to wait before giving up
     * @return the tail element,
     *         or {@code null} if this sorted set is empty
     */
    RFuture<V> pollLastAsync(Duration duration);

    /**
     * Removes and returns the tail elements.
     * <p>
     * Requires <b>Redis 7.0.0 and higher.</b>
     *
     * @param duration how long to wait before giving up
     * @param count elements amount
     * @return the tail elements
     */
    RFuture<List<V>> pollLastAsync(Duration duration, int count);

    RFuture<Boolean> addAsync(V value);
    
    RFuture<Boolean> removeAsync(Object value);
    
    /**
     * Sets new comparator only if current set is empty
     *
     * @param comparator for values
     * @return <code>true</code> if new comparator setted
     *         <code>false</code> otherwise
     */
    boolean trySetComparator(Comparator<? super V> comparator);

    /**
     * Returns element iterator that can be shared across multiple applications.
     * Creating multiple iterators on the same object with this method will result in a single shared iterator.
     * See {@linkplain RList#distributedIterator(String, int)} for creating different iterators.
     * @param count batch size
     * @return shared elements iterator
     */
    Iterator<V> distributedIterator(int count);

    /**
     * Returns iterator over elements that match specified pattern. Iterator can be shared across multiple applications.
     * Creating multiple iterators on the same object with this method will result in a single shared iterator.
     * Iterator name must be resolved to the same hash slot as list name.
     * @param count batch size
     * @param iteratorName redis object name to which cursor will be saved
     * @return shared elements iterator
     */
    Iterator<V> distributedIterator(String iteratorName, int count);

}
