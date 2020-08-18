/**
 * Copyright (c) 2013-2020 Nikita Koksharov
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
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Redis based time-series collection.
 *
 * @author Nikita Koksharov
 *
 */
public interface RTimeSeries<V> extends RExpirable, Iterable<V>, RTimeSeriesAsync<V>, RDestroyable {

    /**
     * Adds element to this time-series collection
     * by specified <code>timestamp</code>.
     *
     * @param timestamp - object timestamp
     * @param object - object itself
     */
    void add(long timestamp, V object);

    /**
     * Adds all elements contained in the specified map to this time-series collection.
     * Map contains of timestamp mapped by object.
     *
     * @param objects - map of elements to add
     */
    void addAll(Map<Long, V> objects);

    /**
     * Adds element to this time-series collection
     * by specified <code>timestamp</code>.
     *
     * @param timestamp - object timestamp
     * @param object - object itself
     * @param timeToLive - time to live interval
     * @param timeUnit - unit of time to live interval
     */
    void add(long timestamp, V object, long timeToLive, TimeUnit timeUnit);

    /**
     * Adds all elements contained in the specified map to this time-series collection.
     * Map contains of timestamp mapped by object.
     *
     * @param objects - map of elements to add
     * @param timeToLive - time to live interval
     * @param timeUnit - unit of time to live interval
     */
    void addAll(Map<Long, V> objects, long timeToLive, TimeUnit timeUnit);

    /**
     * Returns size of this set.
     *
     * @return size
     */
    int size();

    /**
     * Returns object by specified <code>timestamp</code> or <code>null</code> if it doesn't exist.
     *
     * @param timestamp - object timestamp
     * @return object
     */
    V get(long timestamp);

    /**
     * Removes object by specified <code>timestamp</code>.
     *
     * @param timestamp - object timestamp
     * @return <code>true</code> if an element was removed as a result of this call
     */
    boolean remove(long timestamp);

    /**
     * Removes and returns the head elements or {@code null} if this time-series collection is empty.
     *
     * @param count - elements amount
     * @return the head element,
     *         or {@code null} if this time-series collection is empty
     */
    Collection<V> pollFirst(int count);

    /**
     * Removes and returns the tail elements or {@code null} if this time-series collection is empty.
     *
     * @param count - elements amount
     * @return the tail element or {@code null} if this time-series collection is empty
     */
    Collection<V> pollLast(int count);

    /**
     * Removes and returns the head element or {@code null} if this time-series collection is empty.
     *
     * @return the head element,
     *         or {@code null} if this time-series collection is empty
     */
    V pollFirst();

    /**
     * Removes and returns the tail element or {@code null} if this time-series collection is empty.
     *
     * @return the tail element or {@code null} if this time-series collection is empty
     */
    V pollLast();

    /**
     * Returns the tail element or {@code null} if this time-series collection is empty.
     *
     * @return the tail element or {@code null} if this time-series collection is empty
     */
    V last();

    /**
     * Returns the head element or {@code null} if this time-series collection is empty.
     *
     * @return the head element or {@code null} if this time-series collection is empty
     */
    V first();

    /**
     * Returns timestamp of the head timestamp or {@code null} if this time-series collection is empty.
     *
     * @return timestamp or {@code null} if this time-series collection is empty
     */
    Long firstTimestamp();

    /**
     * Returns timestamp of the tail element or {@code null} if this time-series collection is empty.
     *
     * @return timestamp or {@code null} if this time-series collection is empty
     */
    Long lastTimestamp();

    /**
     * Returns the tail elements of this time-series collection.
     *
     * @param count - elements amount
     * @return the tail elements
     */
    Collection<V> last(int count);

    /**
     * Returns the head elements of this time-series collection.
     *
     * @param count - elements amount
     * @return the head elements
     */
    Collection<V> first(int count);

    /**
     * Removes values within timestamp range. Including boundary values.
     *
     * @param startTimestamp - start timestamp
     * @param endTimestamp - end timestamp
     * @return number of removed elements
     */
    int removeRange(long startTimestamp, long endTimestamp);

    /**
     * Returns ordered elements of this time-series collection within timestamp range. Including boundary values.
     *
     * @param startTimestamp - start timestamp
     * @param endTimestamp - end timestamp
     * @return elements collection
     */
    Collection<V> range(long startTimestamp, long endTimestamp);

    /**
     * Returns elements of this time-series collection in reverse order within timestamp range. Including boundary values.
     *
     * @param startTimestamp - start timestamp
     * @param endTimestamp - end timestamp
     * @return elements collection
     */
    Collection<V> rangeReversed(long startTimestamp, long endTimestamp);

    /**
     * Returns ordered entries of this time-series collection within timestamp range. Including boundary values.
     *
     * @param startTimestamp - start timestamp
     * @param endTimestamp - end timestamp
     * @return elements collection
     */
    Collection<TimeSeriesEntry<V>> entryRange(long startTimestamp, long endTimestamp);

    /**
     * Returns entries of this time-series collection in reverse order within timestamp range. Including boundary values.
     *
     * @param startTimestamp - start timestamp
     * @param endTimestamp - end timestamp
     * @return elements collection
     */
    Collection<TimeSeriesEntry<V>> entryRangeReversed(long startTimestamp, long endTimestamp);

    /**
     * Returns stream of elements in this time-series collection.
     * Elements are loaded in batch. Batch size is 10.
     *
     * @return stream of elements
     */
    Stream<V> stream();

    /**
     * Returns stream of elements in this time-series collection.
     * If <code>pattern</code> is not null then only elements match this pattern are loaded.
     *
     * @param pattern - search pattern
     * @return stream of elements
     */
    Stream<V> stream(String pattern);

    /**
     * Returns stream of elements in this time-series collection.
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
     * Returns an iterator over elements in this set.
     * If <code>pattern</code> is not null then only elements match this pattern are loaded.
     *
     * @param pattern - search pattern
     * @return iterator
     */
    Iterator<V> iterator(String pattern);

    /**
     * Returns an iterator over elements in this time-series collection.
     * Elements are loaded in batch. Batch size is defined by <code>count</code> param.
     *
     * @param count - size of elements batch
     * @return iterator
     */
    Iterator<V> iterator(int count);

    /**
     * Returns an iterator over elements in this time-series collection.
     * Elements are loaded in batch. Batch size is defined by <code>count</code> param.
     * If pattern is not null then only elements match this pattern are loaded.
     *
     * @param pattern - search pattern
     * @param count - size of elements batch
     * @return iterator
     */
    Iterator<V> iterator(String pattern, int count);

}
