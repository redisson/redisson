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

import org.redisson.api.ts.TimeSeriesAddArgs;
import org.redisson.api.ts.TimeSeriesBucket;
import org.redisson.api.ts.TimeSeriesReadArgs;
import org.redisson.api.ts.TimeSeriesInfo;
import org.redisson.api.ts.TimeSeriesAggregationArgs;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Async interface for Redis based time-series collection.
 *
 * @author Nikita Koksharov
 *
 * @param <V> value type
 * @param <L> label type
 *
 */
public interface RTimeSeriesAsync<V, L> extends RExpirableAsync {

    /**
     * Adds element to this time-series collection
     * by specified <code>timestamp</code>.
     *
     * @param timestamp object timestamp
     * @param object object itself
     * @return void
     */
    RFuture<Void> addAsync(long timestamp, V object);

    /**
     * Adds element with <code>label</code> to this time-series collection
     * by specified <code>timestamp</code>.
     *
     * @param timestamp object timestamp
     * @param object object itself
     * @param label object label
     */
    RFuture<Void> addAsync(long timestamp, V object, L label);

    /**
     * Adds all elements contained in the specified map to this time-series collection.
     * Map contains of timestamp mapped by object.
     *
     * @param objects - map of elements to add
     * @return void
     */
    RFuture<Void> addAllAsync(Map<Long, V> objects);

    /**
     * Adds all entries collection to this time-series collection.
     *
     * @param entries collection of time series entries
     * @return void
     */
    RFuture<Void>  addAllAsync(Collection<TimeSeriesEntry<V, L>> entries);

    /**
     * Use {@link #addAsync(long, Object, Duration)} instead
     *
     * @param timestamp - object timestamp
     * @param object - object itself
     * @param timeToLive - time to live interval
     * @param timeUnit - unit of time to live interval
     * @return void
     */
    @Deprecated
    RFuture<Void> addAsync(long timestamp, V object, long timeToLive, TimeUnit timeUnit);

    /**
     * Adds element to this time-series collection
     * by specified <code>timestamp</code>.
     *
     * @param timestamp object timestamp
     * @param object object itself
     * @param timeToLive time to live interval
     */
    RFuture<Void> addAsync(long timestamp, V object, Duration timeToLive);

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
    RFuture<Void> addAsync(long timestamp, V object, L label, Duration timeToLive);

    /**
     * Use {@link #addAllAsync(Map, Duration)} instead
     *
     * @param objects - map of elements to add
     * @param timeToLive - time to live interval
     * @param timeUnit - unit of time to live interval
     * @return void
     */
    @Deprecated
    RFuture<Void> addAllAsync(Map<Long, V> objects, long timeToLive, TimeUnit timeUnit);

    /**
     * Adds all elements contained in the specified map to this time-series collection.
     * Map contains of timestamp mapped by object.
     *
     * @param objects map of elements to add
     * @param timeToLive time to live interval
     */
    RFuture<Void> addAllAsync(Map<Long, V> objects, Duration timeToLive);

    /**
     * Adds all time series entries collection to this time-series collection.
     * Specified time to live interval applied to all entries defined in collection.
     *
     * @param entries collection of time series entries
     * @param timeToLive time to live interval
     * @return void
     */
    RFuture<Void> addAllAsync(Collection<TimeSeriesEntry<V, L>> entries, Duration timeToLive);

    /**
     * Adds entry to this time-series collection
     * if an entry with the same timestamp doesn't exist.
     * <p>
     * Expired entries aren't taken into account.
     *
     * @param entry entry arguments
     * @return <code>true</code> if entry was added
     */
    RFuture<Boolean> addIfAbsentAsync(TimeSeriesAddArgs<V, ? super L> entry);

    /**
     * Adds entries to this time-series collection
     * skipping those whose timestamp is already used by an existing entry.
     * <p>
     * Expired entries aren't taken into account.
     *
     * @param entries entries arguments
     * @return amount of added entries
     */
    RFuture<Integer> addAllIfAbsentAsync(Collection<? extends TimeSeriesAddArgs<V, ? super L>> entries);

    /**
     * Adds entry to this time-series collection
     * replacing all entries with the same timestamp if they exist.
     *
     * @param entry entry arguments
     * @return <code>true</code> if a new entry was created,
     *         <code>false</code> if existing entries were replaced
     */
    RFuture<Boolean> addOrReplaceAsync(TimeSeriesAddArgs<V, ? super L> entry);

    /**
     * Adds entries to this time-series collection
     * replacing all entries with the same timestamps if they exist.
     *
     * @param entries entries arguments
     * @return amount of created entries
     */
    RFuture<Integer> addAllOrReplaceAsync(Collection<? extends TimeSeriesAddArgs<V, ? super L>> entries);

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
     * Returns time series entry by specified <code>timestamp</code> or <code>null</code> if it doesn't exist.
     *
     * @param timestamp object timestamp
     * @return time series entry
     */
    RFuture<TimeSeriesEntry<V, L>> getEntryAsync(long timestamp);

    /**
     * Returns all objects stored by the specified <code>timestamp</code>
     * in the order they were added.
     * <p>
     * Unlike {@link #getAsync(long)} this returns every object sharing the timestamp,
     * not only the first one.
     *
     * @param timestamp objects timestamp
     * @return collection of objects
     */
    RFuture<Collection<V>> getAllAsync(long timestamp);

    /**
     * Returns all time series entries stored by the specified <code>timestamp</code>
     * in the order they were added.
     *
     * @param timestamp entries timestamp
     * @return collection of time series entries
     */
    RFuture<Collection<TimeSeriesEntry<V, L>>> getAllEntriesAsync(long timestamp);

    /**
     * Removes all objects stored by the specified <code>timestamp</code>.
     *
     * @param timestamp objects timestamp
     * @return number of removed objects
     */
    RFuture<Integer> removeAllAsync(long timestamp);

    /**
     * Removes and returns all objects stored by the specified <code>timestamp</code>
     * in the order they were added.
     *
     * @param timestamp objects timestamp
     * @return collection of objects
     */
    RFuture<Collection<V>> getAndRemoveAllAsync(long timestamp);

    /**
     * Removes and returns all time series entries stored by the specified
     * <code>timestamp</code> in the order they were added.
     *
     * @param timestamp entries timestamp
     * @return collection of time series entries
     */
    RFuture<Collection<TimeSeriesEntry<V, L>>> getAndRemoveAllEntriesAsync(long timestamp);

    /**
     * Removes object by specified <code>timestamp</code>.
     *
     * @param timestamp - object timestamp
     * @return <code>true</code> if an element was removed as a result of this call
     */
    RFuture<Boolean> removeAsync(long timestamp);

    /**
     * Removes and returns object by specified <code>timestamp</code>.
     *
     * @param timestamp - object timestamp
     * @return object or <code>null</code> if it doesn't exist
     */
    RFuture<V> getAndRemoveAsync(long timestamp);

    /**
     * Removes and returns entry by specified <code>timestamp</code>.
     *
     * @param timestamp - object timestamp
     * @return entry or <code>null</code> if it doesn't exist
     */
    RFuture<TimeSeriesEntry<V, L>> getAndRemoveEntryAsync(long timestamp);

    /**
     * Removes and returns the head elements
     *
     * @param count - elements amount
     * @return collection of head elements
     */
    RFuture<Collection<V>> pollFirstAsync(int count);

    /**
     * Removes and returns head entries
     *
     * @param count - entries amount
     * @return collection of head entries
     */
    RFuture<Collection<TimeSeriesEntry<V, L>>> pollFirstEntriesAsync(int count);

    /**
     * Removes and returns the tail elements or {@code null} if this time-series collection is empty.
     *
     * @param count - elements amount
     * @return the tail element or {@code null} if this time-series collection is empty
     */
    RFuture<Collection<V>> pollLastAsync(int count);

    /**
     * Removes and returns tail entries
     *
     * @param count - entries amount
     * @return collection of tail entries
     */
    RFuture<Collection<TimeSeriesEntry<V, L>>> pollLastEntriesAsync(int count);

    /**
     * Removes and returns the head element or {@code null} if this time-series collection is empty.
     *
     * @return the head element,
     *         or {@code null} if this time-series collection is empty
     */
    RFuture<V> pollFirstAsync();

    /**
     * Removes and returns head entry or {@code null} if this time-series collection is empty.
     *
     * @return the head entry,
     *         or {@code null} if this time-series collection is empty
     */
    RFuture<TimeSeriesEntry<V, L>> pollFirstEntryAsync();

    /**
     * Removes and returns the tail element or {@code null} if this time-series collection is empty.
     *
     * @return the tail element or {@code null} if this time-series collection is empty
     */
    RFuture<V> pollLastAsync();

    /**
     * Removes and returns the tail entry or {@code null} if this time-series collection is empty.
     *
     * @return the tail entry or {@code null} if this time-series collection is empty
     */
    RFuture<TimeSeriesEntry<V, L>> pollLastEntryAsync();

    /**
     * Returns the tail element or {@code null} if this time-series collection is empty.
     *
     * @return the tail element or {@code null} if this time-series collection is empty
     */
    RFuture<V> lastAsync();

    /**
     * Returns the tail entry or {@code null} if this time-series collection is empty.
     *
     * @return the tail entry or {@code null} if this time-series collection is empty
     */
    RFuture<TimeSeriesEntry<V, L>> lastEntryAsync();

    /**
     * Returns the head element or {@code null} if this time-series collection is empty.
     *
     * @return the head element or {@code null} if this time-series collection is empty
     */
    RFuture<V> firstAsync();

    /**
     * Returns the head entry or {@code null} if this time-series collection is empty.
     *
     * @return the head entry or {@code null} if this time-series collection is empty
     */
    RFuture<TimeSeriesEntry<V, L>> firstEntryAsync();

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
     * Returns the tail entries of this time-series collection.
     *
     * @param count - entries amount
     * @return the tail entries
     */
    RFuture<Collection<TimeSeriesEntry<V, L>>> lastEntriesAsync(int count);

    /**
     * Returns the head elements of this time-series collection.
     *
     * @param count - elements amount
     * @return the head elements
     */
    RFuture<Collection<V>> firstAsync(int count);

    /**
     * Returns the head entries of this time-series collection.
     *
     * @param count - entries amount
     * @return the head entries
     */
    RFuture<Collection<TimeSeriesEntry<V, L>>> firstEntriesAsync(int count);

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
     * Returns ordered elements of this time-series collection within timestamp range. Including boundary values.
     *
     * @param startTimestamp start timestamp
     * @param endTimestamp end timestamp
     * @param limit result size limit, <code>0</code> for no limit; a negative
     *              limit returns an empty result
     * @return elements collection
     */
    RFuture<Collection<V>> rangeAsync(long startTimestamp, long endTimestamp, int limit);

    /**
     * Returns elements of this time-series collection in reverse order within timestamp range. Including boundary values.
     *
     * @param startTimestamp - start timestamp
     * @param endTimestamp - end timestamp
     * @return elements collection
     */
    RFuture<Collection<V>> rangeReversedAsync(long startTimestamp, long endTimestamp);

    /**
     * Returns elements of this time-series collection in reverse order within timestamp range. Including boundary values.
     *
     * @param startTimestamp start timestamp
     * @param endTimestamp end timestamp
     * @param limit result size limit, <code>0</code> for no limit; a negative
     *              limit returns an empty result
     * @return elements collection
     */
    RFuture<Collection<V>> rangeReversedAsync(long startTimestamp, long endTimestamp, int limit);

    /**
     * Returns ordered entries of this time-series collection within timestamp range. Including boundary values.
     *
     * @param startTimestamp - start timestamp
     * @param endTimestamp - end timestamp
     * @return elements collection
     */
    RFuture<Collection<TimeSeriesEntry<V, L>>> entryRangeAsync(long startTimestamp, long endTimestamp);

    /**
     * Returns ordered entries of this time-series collection within timestamp range. Including boundary values.
     *
     * @param startTimestamp start timestamp
     * @param endTimestamp end timestamp
     * @param limit result size limit, <code>0</code> for no limit; a negative
     *              limit returns an empty result
     * @return elements collection
     */
    RFuture<Collection<TimeSeriesEntry<V, L>>> entryRangeAsync(long startTimestamp, long endTimestamp, int limit);

    /**
     * Returns entries of this time-series collection in reverse order within timestamp range. Including boundary values.
     *
     * @param startTimestamp - start timestamp
     * @param endTimestamp - end timestamp
     * @return elements collection
     */
    RFuture<Collection<TimeSeriesEntry<V, L>>> entryRangeReversedAsync(long startTimestamp, long endTimestamp);

    /**
     * Returns entries of this time-series collection in reverse order within timestamp range. Including boundary values.
     *
     * @param startTimestamp start timestamp
     * @param endTimestamp end timestamp
     * @param limit result size limit, <code>0</code> for no limit; a negative
     *              limit returns an empty result
     * @return elements collection
     */
    RFuture<Collection<TimeSeriesEntry<V, L>>> entryRangeReversedAsync(long startTimestamp, long endTimestamp, int limit);

    /**
     * Adds object event listener
     *
     * @see org.redisson.api.listener.TrackingListener
     * @see org.redisson.api.listener.ScoredSortedSetAddListener
     * @see org.redisson.api.listener.ScoredSortedSetRemoveListener
     * @see org.redisson.api.ExpiredObjectListener
     * @see org.redisson.api.DeletedObjectListener
     *
     * @param listener object event listener
     * @return listener id
     */
    @Override
    RFuture<Integer> addListenerAsync(ObjectListener listener);

    /**
     * Returns ordered elements carrying <code>label</code> within timestamp range. Including boundary values.
     * <p>
     * A <code>null</code> <code>label</code> selects the elements that carry no label at all.
     * Labels are matched on their encoded form, so the label type needs no ordering, only a
     * codec that encodes equal labels identically.
     *
     * @param startTimestamp start timestamp
     * @param endTimestamp end timestamp
     * @param label label to match, or <code>null</code> for elements without one
     * @return elements collection
     */
    RFuture<Collection<V>> rangeByLabelAsync(long startTimestamp, long endTimestamp, L label);

    /**
     * Returns ordered elements carrying <code>label</code> within timestamp range. Including boundary values.
     * <p>
     * A <code>null</code> <code>label</code> selects the elements that carry no label at all.
     * Labels are matched on their encoded form, so the label type needs no ordering, only a
     * codec that encodes equal labels identically.
     *
     * @param startTimestamp start timestamp
     * @param endTimestamp end timestamp
     * @param label label to match, or <code>null</code> for elements without one
     * @param limit result size limit, <code>0</code> for no limit; a negative
     *              limit returns an empty result
     * @return elements collection
     */
    RFuture<Collection<V>> rangeByLabelAsync(long startTimestamp, long endTimestamp, L label, int limit);

    /**
     * Returns elements carrying <code>label</code> in reverse order within timestamp range. Including boundary values.
     * <p>
     * A <code>null</code> <code>label</code> selects the elements that carry no label at all.
     * Labels are matched on their encoded form, so the label type needs no ordering, only a
     * codec that encodes equal labels identically.
     *
     * @param startTimestamp start timestamp
     * @param endTimestamp end timestamp
     * @param label label to match, or <code>null</code> for elements without one
     * @return elements collection
     */
    RFuture<Collection<V>> rangeReversedByLabelAsync(long startTimestamp, long endTimestamp, L label);

    /**
     * Returns elements carrying <code>label</code> in reverse order within timestamp range. Including boundary values.
     * <p>
     * A <code>null</code> <code>label</code> selects the elements that carry no label at all.
     * Labels are matched on their encoded form, so the label type needs no ordering, only a
     * codec that encodes equal labels identically.
     *
     * @param startTimestamp start timestamp
     * @param endTimestamp end timestamp
     * @param label label to match, or <code>null</code> for elements without one
     * @param limit result size limit, <code>0</code> for no limit; a negative
     *              limit returns an empty result
     * @return elements collection
     */
    RFuture<Collection<V>> rangeReversedByLabelAsync(long startTimestamp, long endTimestamp, L label, int limit);

    /**
     * Returns ordered entries carrying <code>label</code> within timestamp range. Including boundary values.
     * <p>
     * A <code>null</code> <code>label</code> selects the elements that carry no label at all.
     * Labels are matched on their encoded form, so the label type needs no ordering, only a
     * codec that encodes equal labels identically.
     *
     * @param startTimestamp start timestamp
     * @param endTimestamp end timestamp
     * @param label label to match, or <code>null</code> for elements without one
     * @return entries collection
     */
    RFuture<Collection<TimeSeriesEntry<V, L>>> entryRangeByLabelAsync(long startTimestamp, long endTimestamp, L label);

    /**
     * Returns ordered entries carrying <code>label</code> within timestamp range. Including boundary values.
     * <p>
     * A <code>null</code> <code>label</code> selects the elements that carry no label at all.
     * Labels are matched on their encoded form, so the label type needs no ordering, only a
     * codec that encodes equal labels identically.
     *
     * @param startTimestamp start timestamp
     * @param endTimestamp end timestamp
     * @param label label to match, or <code>null</code> for elements without one
     * @param limit result size limit, <code>0</code> for no limit; a negative
     *              limit returns an empty result
     * @return entries collection
     */
    RFuture<Collection<TimeSeriesEntry<V, L>>> entryRangeByLabelAsync(long startTimestamp, long endTimestamp, L label, int limit);

    /**
     * Returns entries carrying <code>label</code> in reverse order within timestamp range. Including boundary values.
     * <p>
     * A <code>null</code> <code>label</code> selects the elements that carry no label at all.
     * Labels are matched on their encoded form, so the label type needs no ordering, only a
     * codec that encodes equal labels identically.
     *
     * @param startTimestamp start timestamp
     * @param endTimestamp end timestamp
     * @param label label to match, or <code>null</code> for elements without one
     * @return entries collection
     */
    RFuture<Collection<TimeSeriesEntry<V, L>>> entryRangeReversedByLabelAsync(long startTimestamp, long endTimestamp, L label);

    /**
     * Returns entries carrying <code>label</code> in reverse order within timestamp range. Including boundary values.
     * <p>
     * A <code>null</code> <code>label</code> selects the elements that carry no label at all.
     * Labels are matched on their encoded form, so the label type needs no ordering, only a
     * codec that encodes equal labels identically.
     *
     * @param startTimestamp start timestamp
     * @param endTimestamp end timestamp
     * @param label label to match, or <code>null</code> for elements without one
     * @param limit result size limit, <code>0</code> for no limit; a negative
     *              limit returns an empty result
     * @return entries collection
     */
    RFuture<Collection<TimeSeriesEntry<V, L>>> entryRangeReversedByLabelAsync(long startTimestamp, long endTimestamp, L label, int limit);

    /**
     * Removes elements carrying <code>label</code> within timestamp range. Including boundary values.
     * <p>
     * A <code>null</code> <code>label</code> selects the elements that carry no label at all.
     * Labels are matched on their encoded form, so the label type needs no ordering, only a
     * codec that encodes equal labels identically.
     *
     * @param startTimestamp start timestamp
     * @param endTimestamp end timestamp
     * @param label label to match, or <code>null</code> for elements without one
     * @return number of removed elements
     */
    RFuture<Integer> removeRangeByLabelAsync(long startTimestamp, long endTimestamp, L label);

    /**
     * Returns the distinct labels carried by the elements of this time-series collection.
     * <p>
     * There is no label index, so the whole collection is walked and the result is
     * proportional to the number of distinct labels it holds. Use
     * {@link #labelsAsync(long, long)} to bound both to a timestamp range.
     *
     * @return labels set
     */
    RFuture<Set<L>> labelsAsync();

    /**
     * Returns the distinct labels carried by the elements within timestamp range.
     * Including boundary values.
     *
     * @param startTimestamp start timestamp
     * @param endTimestamp end timestamp
     * @return labels set
     */
    RFuture<Set<L>> labelsAsync(long startTimestamp, long endTimestamp);

    /**
     * Aggregates the entries of a timestamp range into time buckets, computing every
     * aggregation asked for in a single pass over the range.
     * <p>
     * The values are read as numbers by the script, so the collection's codec has to encode
     * numbers as text; aggregating under a binary codec, which the default one is, throws
     * {@link IllegalStateException} naming it. Buckets holding no entry are not reported.
     * <pre>
     *     collection.aggregate(TimeSeriesAggregationArgs.between(from, to)
     *                                                   .bucket(Duration.ofMinutes(5))
     *                                                   .avg().max().count());
     * </pre>
     *
     * @param args aggregation arguments
     * @return buckets in ascending timestamp order
     */
    RFuture<Collection<TimeSeriesBucket>> aggregateAsync(TimeSeriesAggregationArgs<? super L> args);

    /**
     * Reports the entries whose timestamp is strictly greater than the one given, oldest
     * first.
     * <p>
     * <b>This does not block.</b> It reports whatever is already there and returns; a sorted
     * set has no server side wait for "entries after a timestamp". The caller owns the loop.
     * <pre>
     *     collection.readTail(TimeSeriesReadArgs.after(cursor).count(100));
     * </pre>
     *
     * @param args read arguments
     * @return entries collection
     */
    RFuture<Collection<TimeSeriesEntry<V, L>>> readTailAsync(TimeSeriesReadArgs<? super L> args);

    /**
     * Returns what the collection can report about itself, in one round trip: how many
     * entries it holds live and in total, its first and last timestamp, its memory usage, its
     * own time to live, and how many entry ids it has issued.
     *
     * @return collection info
     */
    RFuture<TimeSeriesInfo> infoAsync();

    /**
     * Adds the entry only if its value is smaller than the value already held at that
     * timestamp.
     * <p>
     * Nothing held there means it is added. Several live entries there count as holding the
     * smallest of their values, and a call that stores replaces all of them with the one
     * entry, so used on its own this method never leaves more than one entry per timestamp.
     * Entries that have expired are dropped either way.
     * <p>
     * The values are compared as numbers by the script, so the collection's codec has to
     * encode numbers as text; a binary codec, which the default one is, throws
     * {@link IllegalStateException} naming it. A stored or incoming value that is not a
     * finite number fails the call naming its timestamp.
     *
     * @param entry entry to add
     * @return <code>true</code> if the value was stored
     */
    RFuture<Boolean> addIfLessAsync(TimeSeriesAddArgs<V, ? super L> entry);

    /**
     * Adds the entry only if its value is larger than the value already held at that
     * timestamp.
     * <p>
     * Nothing held there means it is added. Several live entries there count as holding the
     * largest of their values, and a call that stores replaces all of them with the one
     * entry, so used on its own this method never leaves more than one entry per timestamp.
     * Entries that have expired are dropped either way.
     * <p>
     * The values are compared as numbers by the script, so the collection's codec has to
     * encode numbers as text; a binary codec, which the default one is, throws
     * {@link IllegalStateException} naming it. A stored or incoming value that is not a
     * finite number fails the call naming its timestamp.
     *
     * @param entry entry to add
     * @return <code>true</code> if the value was stored
     */
    RFuture<Boolean> addIfGreaterAsync(TimeSeriesAddArgs<V, ? super L> entry);

    /**
     * Adds the entry, summing its value with whatever is already held at that timestamp.
     * <p>
     * Nothing held there means it is added as it is. Several live entries there are summed
     * together with it and replaced by the one entry, so used on its own this method never
     * leaves more than one entry per timestamp. Entries that have expired take no part.
     * <p>
     * The values are compared as numbers by the script, so the collection's codec has to
     * encode numbers as text; a binary codec, which the default one is, throws
     * {@link IllegalStateException} naming it. A stored or incoming value that is not a
     * finite number fails the call naming its timestamp.
     *
     * @param entry entry to add
     * @return <code>true</code> if no entry was held at that timestamp
     */
    RFuture<Boolean> addAndSumAsync(TimeSeriesAddArgs<V, ? super L> entry);

}
