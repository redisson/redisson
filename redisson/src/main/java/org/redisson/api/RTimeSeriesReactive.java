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

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Reactive interface for Redis based time-series collection.
 *
 * @author Nikita Koksharov
 *
 * @param <V> value type
 * @param <L> label type
 */
public interface RTimeSeriesReactive<V, L> extends RExpirableReactive {

    /**
     * Returns iterator over collection elements
     *
     * @return iterator
     */
    Flux<V> iterator();

    /**
     * Adds element to this time-series collection
     * by specified <code>timestamp</code>.
     *
     * @param timestamp object timestamp
     * @param object object itself
     * @return void
     */
    Mono<Void> add(long timestamp, V object);

    /**
     * Adds element with <code>label</code> to this time-series collection
     * by specified <code>timestamp</code>.
     *
     * @param timestamp object timestamp
     * @param object object itself
     * @param label object label
     * @return void
     */
    Mono<Void> add(long timestamp, V object, L label);

    /**
     * Adds all elements contained in the specified map to this time-series collection.
     * Map contains of timestamp mapped by object.
     *
     * @param objects - map of elements to add
     * @return void
     */
    Mono<Void> addAll(Map<Long, V> objects);

    /**
     * Adds all entries collection to this time-series collection.
     *
     * @param entries collection of time series entries
     * @return void
     */
    Mono<Void> addAll(Collection<TimeSeriesEntry<V, L>> entries);

    /**
     * Use {@link #add(long, Object, Duration)} instead
     *
     * @param timestamp - object timestamp
     * @param object - object itself
     * @param timeToLive - time to live interval
     * @param timeUnit - unit of time to live interval
     * @return void
     */
    @Deprecated
    Mono<Void> add(long timestamp, V object, long timeToLive, TimeUnit timeUnit);

    /**
     * Adds element to this time-series collection
     * by specified <code>timestamp</code>.
     *
     * @param timestamp object timestamp
     * @param object object itself
     * @param timeToLive time to live interval
     */
    Mono<Void> add(long timestamp, V object, Duration timeToLive);

    /**
     * Adds element with <code>label</code> to this time-series collection
     * by specified <code>timestamp</code>.
     *
     * @param timestamp object timestamp
     * @param object object itself
     * @param label object label
     * @param timeToLive time to live interval
     * @return void
     */
    Mono<Void> add(long timestamp, V object, L label, Duration timeToLive);

    /**
     * Use {@link #addAll(Map, Duration)} instead
     *
     * @param objects - map of elements to add
     * @param timeToLive - time to live interval
     * @param timeUnit - unit of time to live interval
     * @return void
     */
    @Deprecated
    Mono<Void> addAll(Map<Long, V> objects, long timeToLive, TimeUnit timeUnit);

    /**
     * Adds all elements contained in the specified map to this time-series collection.
     * Map contains of timestamp mapped by object.
     *
     * @param objects map of elements to add
     * @param timeToLive time to live interval
     */
    Mono<Void> addAll(Map<Long, V> objects, Duration timeToLive);

    /**
     * Adds all time series entries collection to this time-series collection.
     * Specified time to live interval applied to all entries defined in collection.
     *
     * @param entries collection of time series entries
     * @param timeToLive time to live interval
     * @return void
     */
    Mono<Void> addAll(Collection<TimeSeriesEntry<V, L>> entries, Duration timeToLive);

    /**
     * Returns size of this set.
     *
     * @return size
     */
    Mono<Integer> size();

    /**
     * Returns object by specified <code>timestamp</code> or <code>null</code> if it doesn't exist.
     *
     * @param timestamp - object timestamp
     * @return object
     */
    Mono<V> get(long timestamp);

    /**
     * Returns time series entry by specified <code>timestamp</code> or <code>null</code> if it doesn't exist.
     *
     * @param timestamp object timestamp
     * @return time series entry
     */
    Mono<TimeSeriesEntry<V, L>> getEntry(long timestamp);

    /**
     * Removes object by specified <code>timestamp</code>.
     *
     * @param timestamp - object timestamp
     * @return <code>true</code> if an element was removed as a result of this call
     */
    Mono<Boolean> remove(long timestamp);

    /**
     * Removes and returns object by specified <code>timestamp</code>.
     *
     * @param timestamp - object timestamp
     * @return object or <code>null</code> if it doesn't exist
     */
    Mono<V> getAndRemove(long timestamp);

    /**
     * Removes and returns entry by specified <code>timestamp</code>.
     *
     * @param timestamp - object timestamp
     * @return entry or <code>null</code> if it doesn't exist
     */
    Mono<TimeSeriesEntry<V, L>> getAndRemoveEntry(long timestamp);

    /**
     * Removes and returns the head elements or {@code null} if this time-series collection is empty.
     *
     * @param count - elements amount
     * @return the head element,
     *         or {@code null} if this time-series collection is empty
     */
    Mono<Collection<V>> pollFirst(int count);

    /**
     * Removes and returns head entries
     *
     * @param count - entries amount
     * @return collection of head entries
     */
    Mono<Collection<TimeSeriesEntry<V, L>>> pollFirstEntries(int count);

    /**
     * Removes and returns the tail elements or {@code null} if this time-series collection is empty.
     *
     * @param count - elements amount
     * @return the tail element or {@code null} if this time-series collection is empty
     */
    Mono<Collection<V>> pollLast(int count);

    /**
     * Removes and returns tail entries
     *
     * @param count - entries amount
     * @return collection of tail entries
     */
    Mono<Collection<TimeSeriesEntry<V, L>>> pollLastEntries(int count);

    /**
     * Removes and returns the head element or {@code null} if this time-series collection is empty.
     *
     * @return the head element,
     *         or {@code null} if this time-series collection is empty
     */
    Mono<V> pollFirst();

    /**
     * Removes and returns head entry or {@code null} if this time-series collection is empty.
     *
     * @return the head entry,
     *         or {@code null} if this time-series collection is empty
     */
    Mono<TimeSeriesEntry<V, L>> pollFirstEntry();

    /**
     * Removes and returns the tail element or {@code null} if this time-series collection is empty.
     *
     * @return the tail element or {@code null} if this time-series collection is empty
     */
    Mono<V> pollLast();

    /**
     * Removes and returns the tail entry or {@code null} if this time-series collection is empty.
     *
     * @return the tail entry or {@code null} if this time-series collection is empty
     */
    Mono<TimeSeriesEntry<V, L>> pollLastEntry();

    /**
     * Returns the tail element or {@code null} if this time-series collection is empty.
     *
     * @return the tail element or {@code null} if this time-series collection is empty
     */
    Mono<V> last();

    /**
     * Returns the tail entry or {@code null} if this time-series collection is empty.
     *
     * @return the tail entry or {@code null} if this time-series collection is empty
     */
    Mono<TimeSeriesEntry<V, L>> lastEntry();

    /**
     * Returns the head element or {@code null} if this time-series collection is empty.
     *
     * @return the head element or {@code null} if this time-series collection is empty
     */
    Mono<V> first();

    /**
     * Returns the head entry or {@code null} if this time-series collection is empty.
     *
     * @return the head entry or {@code null} if this time-series collection is empty
     */
    Mono<TimeSeriesEntry<V, L>> firstEntry();

    /**
     * Returns timestamp of the head timestamp or {@code null} if this time-series collection is empty.
     *
     * @return timestamp or {@code null} if this time-series collection is empty
     */
    Mono<Long> firstTimestamp();

    /**
     * Returns timestamp of the tail element or {@code null} if this time-series collection is empty.
     *
     * @return timestamp or {@code null} if this time-series collection is empty
     */
    Mono<Long> lastTimestamp();

    /**
     * Returns the tail elements of this time-series collection.
     *
     * @param count - elements amount
     * @return the tail elements
     */
    Mono<Collection<V>> last(int count);

    /**
     * Returns the tail entries of this time-series collection.
     *
     * @param count - entries amount
     * @return the tail entries
     */
    Mono<Collection<TimeSeriesEntry<V, L>>> lastEntries(int count);

    /**
     * Returns the head elements of this time-series collection.
     *
     * @param count - elements amount
     * @return the head elements
     */
    Mono<Collection<V>> first(int count);

    /**
     * Returns the head entries of this time-series collection.
     *
     * @param count - entries amount
     * @return the head entries
     */
    Mono<Collection<TimeSeriesEntry<V, L>>> firstEntries(int count);

    /**
     * Removes values within timestamp range. Including boundary values.
     *
     * @param startTimestamp - start timestamp
     * @param endTimestamp - end timestamp
     * @return number of removed elements
     */
    Mono<Integer> removeRange(long startTimestamp, long endTimestamp);

    /**
     * Returns ordered elements of this time-series collection within timestamp range. Including boundary values.
     *
     * @param startTimestamp - start timestamp
     * @param endTimestamp - end timestamp
     * @return elements collection
     */
    Mono<Collection<V>> range(long startTimestamp, long endTimestamp);

    /**
     * Returns ordered elements of this time-series collection within timestamp range. Including boundary values.
     *
     * @param startTimestamp start timestamp
     * @param endTimestamp end timestamp
     * @param limit result size limit
     * @return elements collection
     */
    Mono<Collection<V>> range(long startTimestamp, long endTimestamp, int limit);

    /**
     * Returns elements of this time-series collection in reverse order within timestamp range. Including boundary values.
     *
     * @param startTimestamp - start timestamp
     * @param endTimestamp - end timestamp
     * @return elements collection
     */
    Mono<Collection<V>> rangeReversed(long startTimestamp, long endTimestamp);

    /**
     * Returns elements of this time-series collection in reverse order within timestamp range. Including boundary values.
     *
     * @param startTimestamp start timestamp
     * @param endTimestamp end timestamp
     * @param limit result size limit
     * @return elements collection
     */
    Mono<Collection<V>> rangeReversed(long startTimestamp, long endTimestamp, int limit);

    /**
     * Returns ordered entries of this time-series collection within timestamp range. Including boundary values.
     *
     * @param startTimestamp - start timestamp
     * @param endTimestamp - end timestamp
     * @return elements collection
     */
    Mono<Collection<TimeSeriesEntry<V, L>>> entryRange(long startTimestamp, long endTimestamp);

    /**
     * Returns ordered entries of this time-series collection within timestamp range. Including boundary values.
     *
     * @param startTimestamp start timestamp
     * @param endTimestamp end timestamp
     * @param limit result size limit
     * @return elements collection
     */
    Mono<Collection<TimeSeriesEntry<V, L>>> entryRange(long startTimestamp, long endTimestamp, int limit);

    /**
     * Returns entries of this time-series collection in reverse order within timestamp range. Including boundary values.
     *
     * @param startTimestamp - start timestamp
     * @param endTimestamp - end timestamp
     * @return elements collection
     */
    Mono<Collection<TimeSeriesEntry<V, L>>> entryRangeReversed(long startTimestamp, long endTimestamp);

    /**
     * Returns entries of this time-series collection in reverse order within timestamp range. Including boundary values.
     *
     * @param startTimestamp start timestamp
     * @param endTimestamp end timestamp
     * @param limit result size limit
     * @return elements collection
     */
    Mono<Collection<TimeSeriesEntry<V, L>>> entryRangeReversed(long startTimestamp, long endTimestamp, int limit);

}
