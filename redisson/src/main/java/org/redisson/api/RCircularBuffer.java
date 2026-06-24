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

import java.util.Collection;
import java.util.List;

/**
 * Fixed-capacity circular (ring) buffer backed by the Redis array type.
 * <p>
 * New writes are appended to the tail and wrap around with modular arithmetic,
 * overwriting the oldest values once the buffer is full. Each value is addressed
 * by the ring slot it was written into; evicting the oldest value is simply the
 * next write reusing its slot, so surviving values are never renumbered and an
 * absolute index keeps referring to the same value until that slot is overwritten.
 * <p>
 * This stable addressing is the main difference from the LIST-backed
 * {@link RRingBuffer}, which is kept full with {@code RPUSH} + {@code LPOP} and
 * therefore shifts every surviving element down one position on each eviction, so
 * there a given index does not denote the same value over time. Both structures
 * support relative reads such as the newest-first window
 * ({@link #lastItems(int, boolean)}); the array additionally keeps absolute slot
 * indexes valid across evictions ({@link #get(long)}), performs the
 * wrap-and-truncate in a single native command, and supports server-side
 * aggregation over the window ({@link #sum()}, {@link #min()}, {@link #max()},
 * {@link #average()}, {@link #count(Object)}).
 * <p>
 * Typical use cases are last-N alarms, recent fraud scores, access history,
 * remote logs, device events and bounded sensor histories.
 * <p>
 * Buffer capacity must be defined through {@link #trySetCapacity(int)},
 * {@link #setCapacity(int)} or {@link #set(int, Object[])} before
 * {@link #add(Object)}/{@link #addAll(Collection)} usage.
 * <p>
 * Requires <b>Redis 8.8 or higher.</b>
 *
 * @param <V> value type
 *
 * @author Nikita Koksharov
 *
 */
public interface RCircularBuffer<V> extends RExpirable, RCircularBufferAsync<V> {

    /**
     * Sets capacity of this buffer only if it wasn't set before.
     *
     * @param capacity buffer capacity
     * @return {@code true} if capacity set successfully,
     *         {@code false} if capacity already set
     */
    boolean trySetCapacity(int capacity);

    /**
     * Sets capacity of this buffer and overrides the current value.
     * <p>
     * The new capacity is applied to the underlying ring on the next write
     * operation ({@link #add(Object)}, {@link #addAll(Collection)} or
     * {@link #set(int, Object[])}), at which point the ring is resized and the
     * oldest values that no longer fit are discarded.
     *
     * @param capacity buffer capacity
     */
    void setCapacity(int capacity);

    /**
     * Returns capacity of this buffer.
     *
     * @return buffer capacity, or {@code 0} if capacity wasn't set
     */
    int capacity();

    /**
     * Returns the remaining capacity of this buffer, that is the number of
     * values that can be added before the oldest values start being evicted.
     *
     * @return remaining capacity
     */
    int remainingCapacity();

    /**
     * Adds the specified value to the tail of this buffer.
     * <p>
     * If the buffer is full the oldest value is overwritten.
     * Buffer capacity must be defined before usage.
     *
     * @param value value to add
     * @return {@code true} if value was added
     */
    boolean add(V value);

    /**
     * Adds the specified values to the tail of this buffer in iteration order.
     * <p>
     * Values wrap around and overwrite the oldest values once the buffer is full.
     * Buffer capacity must be defined before usage.
     *
     * @param values values to add
     * @return {@code true} if at least one value was added
     */
    boolean addAll(Collection<? extends V> values);

    /**
     * Writes the specified values into a ring of the given {@code size} and
     * (re)configures this buffer capacity to {@code size}.
     * <p>
     * This is a direct mapping of the native {@code ARRING} command. Values are
     * written at consecutive ring positions starting from the current insert
     * cursor and wrap around as needed.
     *
     * @param size ring size, becomes the new buffer capacity
     * @param values values to write, must contain at least one value
     * @return array index where the last value was written
     */
    long set(int size, V... values);

    /**
     * Returns the value stored at the specified ring index.
     *
     * @param index ring index
     * @return value stored at the specified ring index, or {@code null} if absent
     */
    V get(long index);

    /**
     * Returns the {@code count} most recently added values.
     *
     * @param count number of values to return
     * @param reverse if {@code true} values are returned newest-first,
     *                otherwise in insertion order (oldest-first)
     * @return most recently added values
     */
    List<V> lastItems(int count, boolean reverse);

    /**
     * Returns values stored in the specified ring index range (inclusive).
     *
     * @param startIndex start ring index
     * @param endIndex end ring index
     * @return values stored in the specified ring index range
     */
    List<V> range(long startIndex, long endIndex);

    /**
     * Returns all retained values in insertion order (oldest-first).
     *
     * @return all retained values
     */
    List<V> readAll();

    /**
     * Returns the number of values currently stored in this buffer.
     *
     * @return number of stored values
     */
    int size();

    /**
     * Returns the sum of the numeric values currently stored in this buffer.
     *
     * @return sum of values, or {@code null} if the buffer is empty
     */
    Double sum();

    /**
     * Returns the sum of the numeric values stored in the specified ring index range.
     *
     * @param startIndex start ring index
     * @param endIndex end ring index
     * @return sum of values
     */
    Double sum(long startIndex, long endIndex);

    /**
     * Returns the minimum numeric value currently stored in this buffer.
     *
     * @return minimum value, or {@code null} if the buffer is empty
     */
    Double min();

    /**
     * Returns the minimum numeric value stored in the specified ring index range.
     *
     * @param startIndex start ring index
     * @param endIndex end ring index
     * @return minimum value
     */
    Double min(long startIndex, long endIndex);

    /**
     * Returns the maximum numeric value currently stored in this buffer.
     *
     * @return maximum value, or {@code null} if the buffer is empty
     */
    Double max();

    /**
     * Returns the maximum numeric value stored in the specified ring index range.
     *
     * @param startIndex start ring index
     * @param endIndex end ring index
     * @return maximum value
     */
    Double max(long startIndex, long endIndex);

    /**
     * Removes all values from this buffer while keeping the configured capacity.
     */
    void clear();

    /**
     * Returns {@code true} if this buffer contains no values.
     *
     * @return {@code true} if this buffer is empty
     */
    boolean isEmpty();

    /**
     * Returns {@code true} if this buffer is full, that is the next {@link #add(Object)}
     * will overwrite the oldest value.
     *
     * @return {@code true} if this buffer is full
     */
    boolean isFull();

    /**
     * Returns the most recently added value without removing it.
     *
     * @return the newest value, or {@code null} if this buffer is empty
     */
    V peekLast();

    /**
     * Returns the oldest retained value without removing it, that is the value
     * that will be overwritten next.
     *
     * @return the oldest value, or {@code null} if this buffer is empty
     */
    V peekFirst();

    /**
     * Returns the values stored at the specified ring indexes.
     *
     * @param indexes ring indexes
     * @return values stored at the specified ring indexes
     */
    List<V> get(long... indexes);

    /**
     * Returns the number of values equal to the specified value currently stored in this buffer.
     *
     * @param value value to match
     * @return number of matching values
     */
    long count(V value);

    /**
     * Returns {@code true} if this buffer contains the specified value.
     *
     * @param value value to match
     * @return {@code true} if the value is present
     */
    boolean contains(V value);

    /**
     * Returns the average of the numeric values currently stored in this buffer.
     *
     * @return average value, or {@code null} if the buffer is empty
     */
    Double average();

    /**
     * Returns the bitwise AND of the numeric values currently stored in this buffer.
     *
     * @return bitwise AND result, or {@code null} if the buffer is empty
     */
    Long bitAnd();

    /**
     * Returns the bitwise AND of the numeric values stored in the specified ring index range.
     *
     * @param startIndex start ring index
     * @param endIndex end ring index
     * @return bitwise AND result
     */
    Long bitAnd(long startIndex, long endIndex);

    /**
     * Returns the bitwise OR of the numeric values currently stored in this buffer.
     *
     * @return bitwise OR result, or {@code null} if the buffer is empty
     */
    Long bitOr();

    /**
     * Returns the bitwise OR of the numeric values stored in the specified ring index range.
     *
     * @param startIndex start ring index
     * @param endIndex end ring index
     * @return bitwise OR result
     */
    Long bitOr(long startIndex, long endIndex);

    /**
     * Returns the bitwise XOR of the numeric values currently stored in this buffer.
     *
     * @return bitwise XOR result, or {@code null} if the buffer is empty
     */
    Long bitXor();

    /**
     * Returns the bitwise XOR of the numeric values stored in the specified ring index range.
     *
     * @param startIndex start ring index
     * @param endIndex end ring index
     * @return bitwise XOR result
     */
    Long bitXor(long startIndex, long endIndex);

}
