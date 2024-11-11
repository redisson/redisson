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

import java.time.Duration;
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
 * @param <V> value type
 * @param <L> label type
 */
public interface RTimeSeries<V, L> extends RExpirable, Iterable<V>, RTimeSeriesAsync<V, L>, RDestroyable {

    /**
     * Adds element to this time-series collection
     * by specified <code>timestamp</code>.
     *
     * @param timestamp object timestamp
     * @param object object itself
     */
    void add(long timestamp, V object);

    /**
     * Adds element with <code>label</code> to this time-series collection
     * by specified <code>timestamp</code>.
     *
     * @param timestamp object timestamp
     * @param object object itself
     * @param label object label
     */
    void add(long timestamp, V object, L label);

    /**
     * Adds all elements contained in the specified map to this time-series collection.
     * Map contains of timestamp mapped by object.
     *
     * @param objects - map of elements to add
     */
    void addAll(Map<Long, V> objects);

    /**
     * Adds all entries collection to this time-series collection.
     *
     * @param entries collection of time series entries
     */
    void addAll(Collection<TimeSeriesEntry<V, L>> entries);

    /**
     * Use {@link #add(long, Object, Duration)} instead
     *
     * @param timestamp - object timestamp
     * @param object - object itself
     * @param timeToLive - time to live interval
     * @param timeUnit - unit of time to live interval
     */
    @Deprecated
    void add(long timestamp, V object, long timeToLive, TimeUnit timeUnit);

    /**
     * Adds element to this time-series collection
     * by specified <code>timestamp</code>.
     *
     * @param timestamp object timestamp
     * @param object object itself
     * @param timeToLive time to live interval
     */
    void add(long timestamp, V object, Duration timeToLive);

    /**
     * Adds element with <code>label</code> to this time-series collection
     * by specified <code>timestamp</code>.
     *
     * @param timestamp object timestamp
     * @param object object itself
     * @param label object label
     * @param timeToLive time to live interval
     */
    void add(long timestamp, V object, L label, Duration timeToLive);

    /**
     * Use {@link #addAll(Map, Duration)} instead
     *
     * @param objects - map of elements to add
     * @param timeToLive - time to live interval
     * @param timeUnit - unit of time to live interval
     */
    @Deprecated
    void addAll(Map<Long, V> objects, long timeToLive, TimeUnit timeUnit);

    /**
     * Adds all elements contained in the specified map to this time-series collection.
     * Map contains of timestamp mapped by object.
     *
     * @param objects map of elements to add
     * @param timeToLive time to live interval
     */
    void addAll(Map<Long, V> objects, Duration timeToLive);

    /**
     * Adds all time series entries collection to this time-series collection.
     * Specified time to live interval applied to all entries defined in collection.
     *
     * @param entries collection of time series entries
     * @param timeToLive time to live interval
     */
    void addAll(Collection<TimeSeriesEntry<V, L>> entries, Duration timeToLive);

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
     * Returns time series entry by specified <code>timestamp</code> or <code>null</code> if it doesn't exist.
     *
     * @param timestamp object timestamp
     * @return time series entry
     */
    TimeSeriesEntry<V, L> getEntry(long timestamp);

    /**
     * Removes object by specified <code>timestamp</code>.
     *
     * @param timestamp - object timestamp
     * @return <code>true</code> if an element was removed as a result of this call
     */
    boolean remove(long timestamp);

    /**
     * Removes and returns object by specified <code>timestamp</code>.
     *
     * @param timestamp - object timestamp
     * @return object or <code>null</code> if it doesn't exist
     */
    V getAndRemove(long timestamp);

    /**
     * Removes and returns entry by specified <code>timestamp</code>.
     *
     * @param timestamp - object timestamp
     * @return entry or <code>null</code> if it doesn't exist
     */
    TimeSeriesEntry<V, L> getAndRemoveEntry(long timestamp);

    /**
     * Removes and returns the head elements
     *
     * @param count - elements amount
     * @return collection of head elements
     */
    Collection<V> pollFirst(int count);

    /**
     * Removes and returns head entries
     *
     * @param count - entries amount
     * @return collection of head entries
     */
    Collection<TimeSeriesEntry<V, L>> pollFirstEntries(int count);

    /**
     * Removes and returns the tail elements or {@code null} if this time-series collection is empty.
     *
     * @param count - elements amount
     * @return the tail element or {@code null} if this time-series collection is empty
     */
    Collection<V> pollLast(int count);

    /**
     * Removes and returns tail entries
     *
     * @param count - entries amount
     * @return collection of tail entries
     */
    Collection<TimeSeriesEntry<V, L>> pollLastEntries(int count);

    /**
     * Removes and returns the head element or {@code null} if this time-series collection is empty.
     *
     * @return the head element,
     *         or {@code null} if this time-series collection is empty
     */
    V pollFirst();

    /**
     * Removes and returns head entry or {@code null} if this time-series collection is empty.
     *
     * @return the head entry,
     *         or {@code null} if this time-series collection is empty
     */
    TimeSeriesEntry<V, L> pollFirstEntry();

    /**
     * Removes and returns the tail element or {@code null} if this time-series collection is empty.
     *
     * @return the tail element or {@code null} if this time-series collection is empty
     */
    V pollLast();

    /**
     * Removes and returns the tail entry or {@code null} if this time-series collection is empty.
     *
     * @return the tail entry or {@code null} if this time-series collection is empty
     */
    TimeSeriesEntry<V, L> pollLastEntry();

    /**
     * Returns the tail element or {@code null} if this time-series collection is empty.
     *
     * @return the tail element or {@code null} if this time-series collection is empty
     */
    V last();

    /**
     * Returns the tail entry or {@code null} if this time-series collection is empty.
     *
     * @return the tail entry or {@code null} if this time-series collection is empty
     */
    TimeSeriesEntry<V, L> lastEntry();

    /**
     * Returns the head element or {@code null} if this time-series collection is empty.
     *
     * @return the head element or {@code null} if this time-series collection is empty
     */
    V first();

    /**
     * Returns the head entry or {@code null} if this time-series collection is empty.
     *
     * @return the head entry or {@code null} if this time-series collection is empty
     */
    TimeSeriesEntry<V, L> firstEntry();

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
     * Returns the tail entries of this time-series collection.
     *
     * @param count - entries amount
     * @return the tail entries
     */
    Collection<TimeSeriesEntry<V, L>> lastEntries(int count);

    /**
     * Returns the head elements of this time-series collection.
     *
     * @param count - elements amount
     * @return the head elements
     */
    Collection<V> first(int count);

    /**
     * Returns the head entries of this time-series collection.
     *
     * @param count - entries amount
     * @return the head entries
     */
    Collection<TimeSeriesEntry<V, L>> firstEntries(int count);

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
     * Returns ordered elements of this time-series collection within timestamp range. Including boundary values.
     *
     * @param startTimestamp start timestamp
     * @param endTimestamp end timestamp
     * @param limit result size limit
     * @return elements collection
     */
    Collection<V> range(long startTimestamp, long endTimestamp, int limit);

    /**
     * Returns elements of this time-series collection in reverse order within timestamp range. Including boundary values.
     *
     * @param startTimestamp - start timestamp
     * @param endTimestamp - end timestamp
     * @return elements collection
     */
    Collection<V> rangeReversed(long startTimestamp, long endTimestamp);

    /**
     * Returns elements of this time-series collection in reverse order within timestamp range. Including boundary values.
     *
     * @param startTimestamp start timestamp
     * @param endTimestamp end timestamp
     * @param limit result size limit
     * @return elements collection
     */
    Collection<V> rangeReversed(long startTimestamp, long endTimestamp, int limit);

    /**
     * Returns ordered entries of this time-series collection within timestamp range. Including boundary values.
     *
     * @param startTimestamp - start timestamp
     * @param endTimestamp - end timestamp
     * @return elements collection
     */
    Collection<TimeSeriesEntry<V, L>> entryRange(long startTimestamp, long endTimestamp);

    /**
     * Returns ordered entries of this time-series collection within timestamp range. Including boundary values.
     *
     * @param startTimestamp start timestamp
     * @param endTimestamp end timestamp
     * @param limit result size limit
     * @return elements collection
     */
    Collection<TimeSeriesEntry<V, L>> entryRange(long startTimestamp, long endTimestamp, int limit);

    /**
     * Returns entries of this time-series collection in reverse order within timestamp range. Including boundary values.
     *
     * @param startTimestamp - start timestamp
     * @param endTimestamp - end timestamp
     * @return elements collection
     */
    Collection<TimeSeriesEntry<V, L>> entryRangeReversed(long startTimestamp, long endTimestamp);

    /**
     * Returns entries of this time-series collection in reverse order within timestamp range. Including boundary values.
     *
     * @param startTimestamp start timestamp
     * @param endTimestamp end timestamp
     * @param limit result size limit
     * @return elements collection
     */
    Collection<TimeSeriesEntry<V, L>> entryRangeReversed(long startTimestamp, long endTimestamp, int limit);

    /**
     * Returns stream of elements in this time-series collection.
     * Elements are loaded in batch. Batch size is 10.
     *
     * @return stream of elements
     */
    Stream<V> stream();

    /**
     * Returns stream of elements in this time-series collection.
     * Elements are loaded in batch. Batch size is defined by <code>count</code> param.
     *
     * @param count - size of elements batch
     * @return stream of elements
     */
    Stream<V> stream(int count);

    /**
     * Returns an iterator over elements in this time-series collection.
     * Elements are loaded in batch. Batch size is defined by <code>count</code> param.
     *
     * @param count - size of elements batch
     * @return iterator
     */
    Iterator<V> iterator(int count);

    /**
     * Adds object event listener
     *
     * @see org.redisson.api.listener.TrackingListener
     * @see org.redisson.api.listener.ScoredSortedSetAddListener
     * @see org.redisson.api.listener.ScoredSortedSetRemoveListener
     * @see org.redisson.api.ExpiredObjectListener
     * @see org.redisson.api.DeletedObjectListener
     *
     * @param listener - object event listener
     * @return listener id
     */
    @Override
    int addListener(ObjectListener listener);

}
