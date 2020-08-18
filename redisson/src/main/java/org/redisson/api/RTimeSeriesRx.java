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

import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Single;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Rx interface for Redis based time-series collection.
 *
 * @author Nikita Koksharov
 *
 */
public interface RTimeSeriesRx<V> extends RExpirableRx {

    /**
     * Returns iterator over collection elements
     *
     * @return iterator
     */
    Flowable<V> iterator();

    /**
     * Adds element to this time-series collection
     * by specified <code>timestamp</code>.
     *
     * @param timestamp - object timestamp
     * @param object - object itself
     * @return void
     */
    Completable add(long timestamp, V object);

    /**
     * Adds all elements contained in the specified map to this time-series collection.
     * Map contains of timestamp mapped by object.
     *
     * @param objects - map of elements to add
     * @return void
     */
    Completable addAll(Map<Long, V> objects);

    /**
     * Adds element to this time-series collection
     * by specified <code>timestamp</code>.
     *
     * @param timestamp - object timestamp
     * @param object - object itself
     * @param timeToLive - time to live interval
     * @param timeUnit - unit of time to live interval
     * @return void
     */
    Completable add(long timestamp, V object, long timeToLive, TimeUnit timeUnit);

    /**
     * Adds all elements contained in the specified map to this time-series collection.
     * Map contains of timestamp mapped by object.
     *
     * @param objects - map of elements to add
     * @param timeToLive - time to live interval
     * @param timeUnit - unit of time to live interval
     * @return void
     */
    Completable addAll(Map<Long, V> objects, long timeToLive, TimeUnit timeUnit);

    /**
     * Returns size of this set.
     *
     * @return size
     */
    Single<Integer> size();

    /**
     * Returns object by specified <code>timestamp</code> or <code>null</code> if it doesn't exist.
     *
     * @param timestamp - object timestamp
     * @return object
     */
    Maybe<V> get(long timestamp);

    /**
     * Removes object by specified <code>timestamp</code>.
     *
     * @param timestamp - object timestamp
     * @return <code>true</code> if an element was removed as a result of this call
     */
    Single<Boolean> remove(long timestamp);

    /**
     * Removes and returns the head elements or {@code null} if this time-series collection is empty.
     *
     * @param count - elements amount
     * @return the head element,
     *         or {@code null} if this time-series collection is empty
     */
    Single<Collection<V>> pollFirst(int count);

    /**
     * Removes and returns the tail elements or {@code null} if this time-series collection is empty.
     *
     * @param count - elements amount
     * @return the tail element or {@code null} if this time-series collection is empty
     */
    Single<Collection<V>> pollLast(int count);

    /**
     * Removes and returns the head element or {@code null} if this time-series collection is empty.
     *
     * @return the head element,
     *         or {@code null} if this time-series collection is empty
     */
    Maybe<V> pollFirst();

    /**
     * Removes and returns the tail element or {@code null} if this time-series collection is empty.
     *
     * @return the tail element or {@code null} if this time-series collection is empty
     */
    Maybe<V> pollLast();

    /**
     * Returns the tail element or {@code null} if this time-series collection is empty.
     *
     * @return the tail element or {@code null} if this time-series collection is empty
     */
    Maybe<V> last();

    /**
     * Returns the head element or {@code null} if this time-series collection is empty.
     *
     * @return the head element or {@code null} if this time-series collection is empty
     */
    Maybe<V> first();

    /**
     * Returns timestamp of the head timestamp or {@code null} if this time-series collection is empty.
     *
     * @return timestamp or {@code null} if this time-series collection is empty
     */
    Single<Long> firstTimestamp();

    /**
     * Returns timestamp of the tail element or {@code null} if this time-series collection is empty.
     *
     * @return timestamp or {@code null} if this time-series collection is empty
     */
    Single<Long> lastTimestamp();

    /**
     * Returns the tail elements of this time-series collection.
     *
     * @param count - elements amount
     * @return the tail elements
     */
    Single<Collection<V>> last(int count);

    /**
     * Returns the head elements of this time-series collection.
     *
     * @param count - elements amount
     * @return the head elements
     */
    Single<Collection<V>> first(int count);

    /**
     * Removes values within timestamp range. Including boundary values.
     *
     * @param startTimestamp - start timestamp
     * @param endTimestamp - end timestamp
     * @return number of removed elements
     */
    Single<Integer> removeRange(long startTimestamp, long endTimestamp);

    /**
     * Returns ordered elements of this time-series collection within timestamp range. Including boundary values.
     *
     * @param startTimestamp - start timestamp
     * @param endTimestamp - end timestamp
     * @return elements collection
     */
    Single<Collection<V>> range(long startTimestamp, long endTimestamp);

    /**
     * Returns elements of this time-series collection in reverse order within timestamp range. Including boundary values.
     *
     * @param startTimestamp - start timestamp
     * @param endTimestamp - end timestamp
     * @return elements collection
     */
    Single<Collection<V>> rangeReversed(long startTimestamp, long endTimestamp);

    /**
     * Returns ordered entries of this time-series collection within timestamp range. Including boundary values.
     *
     * @param startTimestamp - start timestamp
     * @param endTimestamp - end timestamp
     * @return elements collection
     */
    Single<Collection<TimeSeriesEntry<V>>> entryRange(long startTimestamp, long endTimestamp);

    /**
     * Returns entries of this time-series collection in reverse order within timestamp range. Including boundary values.
     *
     * @param startTimestamp - start timestamp
     * @param endTimestamp - end timestamp
     * @return elements collection
     */
    Single<Collection<TimeSeriesEntry<V>>> entryRangeReversed(long startTimestamp, long endTimestamp);

}
