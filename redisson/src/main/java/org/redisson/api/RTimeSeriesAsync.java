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
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Async interface for Redis based time-series collection.
 *
 * @author Nikita Koksharov
 *
 */
public interface RTimeSeriesAsync<V> extends RExpirableAsync {

    /**
     * Adds element to this time-series collection
     * by specified <code>timestamp</code>.
     *
     * @param timestamp - object timestamp
     * @param object - object itself
     * @return void
     */
    RFuture<Void> addAsync(long timestamp, V object);

    /**
     * Adds all elements contained in the specified map to this time-series collection.
     * Map contains of timestamp mapped by object.
     *
     * @param objects - map of elements to add
     * @return void
     */
    RFuture<Void> addAllAsync(Map<Long, V> objects);

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
    RFuture<Void> addAsync(long timestamp, V object, long timeToLive, TimeUnit timeUnit);

    /**
     * Adds all elements contained in the specified map to this time-series collection.
     * Map contains of timestamp mapped by object.
     *
     * @param objects - map of elements to add
     * @param timeToLive - time to live interval
     * @param timeUnit - unit of time to live interval
     * @return void
     */
    RFuture<Void> addAllAsync(Map<Long, V> objects, long timeToLive, TimeUnit timeUnit);

    /**
     * Returns size of this set.
     *
     * @return size
     */
    RFuture<Integer> sizeAsync();

    /**
     * Returns object by specified <code>timestamp</code> or <code>null</code> if it doesn't exist.
     *
     * @param timestamp - object timestamp
     * @return object
     */
    RFuture<V> getAsync(long timestamp);

    /**
     * Removes object by specified <code>timestamp</code>.
     *
     * @param timestamp - object timestamp
     * @return <code>true</code> if an element was removed as a result of this call
     */
    RFuture<Boolean> removeAsync(long timestamp);

    /**
     * Removes and returns the head elements or {@code null} if this time-series collection is empty.
     *
     * @param count - elements amount
     * @return the head element,
     *         or {@code null} if this time-series collection is empty
     */
    RFuture<Collection<V>> pollFirstAsync(int count);

    /**
     * Removes and returns the tail elements or {@code null} if this time-series collection is empty.
     *
     * @param count - elements amount
     * @return the tail element or {@code null} if this time-series collection is empty
     */
    RFuture<Collection<V>> pollLastAsync(int count);

    /**
     * Removes and returns the head element or {@code null} if this time-series collection is empty.
     *
     * @return the head element,
     *         or {@code null} if this time-series collection is empty
     */
    RFuture<V> pollFirstAsync();

    /**
     * Removes and returns the tail element or {@code null} if this time-series collection is empty.
     *
     * @return the tail element or {@code null} if this time-series collection is empty
     */
    RFuture<V> pollLastAsync();

    /**
     * Returns the tail element or {@code null} if this time-series collection is empty.
     *
     * @return the tail element or {@code null} if this time-series collection is empty
     */
    RFuture<V> lastAsync();

    /**
     * Returns the head element or {@code null} if this time-series collection is empty.
     *
     * @return the head element or {@code null} if this time-series collection is empty
     */
    RFuture<V> firstAsync();

    /**
     * Returns timestamp of the head timestamp or {@code null} if this time-series collection is empty.
     *
     * @return timestamp or {@code null} if this time-series collection is empty
     */
    RFuture<Long> firstTimestampAsync();

    /**
     * Returns timestamp of the tail element or {@code null} if this time-series collection is empty.
     *
     * @return timestamp or {@code null} if this time-series collection is empty
     */
    RFuture<Long> lastTimestampAsync();

    /**
     * Returns the tail elements of this time-series collection.
     *
     * @param count - elements amount
     * @return the tail elements
     */
    RFuture<Collection<V>> lastAsync(int count);

    /**
     * Returns the head elements of this time-series collection.
     *
     * @param count - elements amount
     * @return the head elements
     */
    RFuture<Collection<V>> firstAsync(int count);

    /**
     * Removes values within timestamp range. Including boundary values.
     *
     * @param startTimestamp - start timestamp
     * @param endTimestamp - end timestamp
     * @return number of removed elements
     */
    RFuture<Integer> removeRangeAsync(long startTimestamp, long endTimestamp);

    /**
     * Returns ordered elements of this time-series collection within timestamp range. Including boundary values.
     *
     * @param startTimestamp - start timestamp
     * @param endTimestamp - end timestamp
     * @return elements collection
     */
    RFuture<Collection<V>> rangeAsync(long startTimestamp, long endTimestamp);

    /**
     * Returns elements of this time-series collection in reverse order within timestamp range. Including boundary values.
     *
     * @param startTimestamp - start timestamp
     * @param endTimestamp - end timestamp
     * @return elements collection
     */
    RFuture<Collection<V>> rangeReversedAsync(long startTimestamp, long endTimestamp);

    /**
     * Returns ordered entries of this time-series collection within timestamp range. Including boundary values.
     *
     * @param startTimestamp - start timestamp
     * @param endTimestamp - end timestamp
     * @return elements collection
     */
    RFuture<Collection<TimeSeriesEntry<V>>> entryRangeAsync(long startTimestamp, long endTimestamp);

    /**
     * Returns entries of this time-series collection in reverse order within timestamp range. Including boundary values.
     *
     * @param startTimestamp - start timestamp
     * @param endTimestamp - end timestamp
     * @return elements collection
     */
    RFuture<Collection<TimeSeriesEntry<V>>> entryRangeReversedAsync(long startTimestamp, long endTimestamp);

}
