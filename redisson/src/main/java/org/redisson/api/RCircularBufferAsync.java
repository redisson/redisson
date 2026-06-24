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
 * Async interface for {@link RCircularBuffer}.
 *
 * @param <V> value type
 *
 * @author Nikita Koksharov
 *
 */
public interface RCircularBufferAsync<V> extends RExpirableAsync {

    /**
     * Sets capacity of this buffer only if it wasn't set before.
     *
     * @param capacity buffer capacity
     * @return {@code true} if capacity set successfully,
     *         {@code false} if capacity already set
     */
    RFuture<Boolean> trySetCapacityAsync(int capacity);

    /**
     * Sets capacity of this buffer and overrides the current value.
     * <p>
     * The new capacity is applied to the underlying ring on the next write
     * operation ({@link #addAsync(Object)}, {@link #addAllAsync(Collection)} or
     * {@link #setAsync(int, Object[])}), at which point the ring is resized and
     * the oldest values that no longer fit are discarded.
     *
     * @param capacity buffer capacity
     * @return void
     */
    RFuture<Void> setCapacityAsync(int capacity);

    /**
     * Returns capacity of this buffer.
     *
     * @return buffer capacity, or {@code 0} if capacity wasn't set
     */
    RFuture<Integer> capacityAsync();

    /**
     * Returns the remaining capacity of this buffer, that is the number of
     * values that can be added before the oldest values start being evicted.
     *
     * @return remaining capacity
     */
    RFuture<Integer> remainingCapacityAsync();

    /**
     * Adds the specified value to the tail of this buffer.
     * <p>
     * If the buffer is full the oldest value is overwritten.
     * Buffer capacity must be defined through {@link #trySetCapacityAsync(int)},
     * {@link #setCapacityAsync(int)} or {@link #setAsync(int, Object[])} before usage.
     *
     * @param value value to add
     * @return {@code true} if value was added
     */
    RFuture<Boolean> addAsync(V value);

    /**
     * Adds the specified values to the tail of this buffer in iteration order.
     * <p>
     * Values wrap around and overwrite the oldest values once the buffer is full.
     * Buffer capacity must be defined through {@link #trySetCapacityAsync(int)},
     * {@link #setCapacityAsync(int)} or {@link #setAsync(int, Object[])} before usage.
     *
     * @param values values to add
     * @return {@code true} if at least one value was added
     */
    RFuture<Boolean> addAllAsync(Collection<? extends V> values);

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
    RFuture<Long> setAsync(int size, V... values);

    /**
     * Returns the value stored at the specified ring index.
     *
     * @param index ring index
     * @return value stored at the specified ring index, or {@code null} if absent
     */
    RFuture<V> getAsync(long index);

    /**
     * Returns the {@code count} most recently added values.
     *
     * @param count number of values to return
     * @param reverse if {@code true} values are returned newest-first,
     *                otherwise in insertion order (oldest-first)
     * @return most recently added values
     */
    RFuture<List<V>> lastItemsAsync(int count, boolean reverse);

    /**
     * Returns values stored in the specified ring index range (inclusive).
     *
     * @param startIndex start ring index
     * @param endIndex end ring index
     * @return values stored in the specified ring index range
     */
    RFuture<List<V>> rangeAsync(long startIndex, long endIndex);

    /**
     * Returns all retained values in insertion order (oldest-first).
     *
     * @return all retained values
     */
    RFuture<List<V>> readAllAsync();

    /**
     * Returns the number of values currently stored in this buffer.
     *
     * @return number of stored values
     */
    RFuture<Integer> sizeAsync();

    /**
     * Returns the sum of the numeric values currently stored in this buffer.
     *
     * @return sum of values, or {@code null} if the buffer is empty
     */
    RFuture<Double> sumAsync();

    /**
     * Returns the sum of the numeric values stored in the specified ring index range.
     *
     * @param startIndex start ring index
     * @param endIndex end ring index
     * @return sum of values
     */
    RFuture<Double> sumAsync(long startIndex, long endIndex);

    /**
     * Returns the minimum numeric value currently stored in this buffer.
     *
     * @return minimum value, or {@code null} if the buffer is empty
     */
    RFuture<Double> minAsync();

    /**
     * Returns the minimum numeric value stored in the specified ring index range.
     *
     * @param startIndex start ring index
     * @param endIndex end ring index
     * @return minimum value
     */
    RFuture<Double> minAsync(long startIndex, long endIndex);

    /**
     * Returns the maximum numeric value currently stored in this buffer.
     *
     * @return maximum value, or {@code null} if the buffer is empty
     */
    RFuture<Double> maxAsync();

    /**
     * Returns the maximum numeric value stored in the specified ring index range.
     *
     * @param startIndex start ring index
     * @param endIndex end ring index
     * @return maximum value
     */
    RFuture<Double> maxAsync(long startIndex, long endIndex);

    /**
     * Removes all values from this buffer while keeping the configured capacity.
     *
     * @return void
     */
    RFuture<Void> clearAsync();

    /**
     * Returns {@code true} if this buffer contains no values.
     *
     * @return {@code true} if this buffer is empty
     */
    RFuture<Boolean> isEmptyAsync();

    /**
     * Returns {@code true} if this buffer is full, that is the next {@code add}
     * will overwrite the oldest value.
     *
     * @return {@code true} if this buffer is full
     */
    RFuture<Boolean> isFullAsync();

    /**
     * Returns the most recently added value without removing it.
     *
     * @return the newest value, or {@code null} if this buffer is empty
     */
    RFuture<V> peekLastAsync();

    /**
     * Returns the oldest retained value without removing it, that is the value
     * that will be overwritten next.
     *
     * @return the oldest value, or {@code null} if this buffer is empty
     */
    RFuture<V> peekFirstAsync();

    /**
     * Returns the values stored at the specified ring indexes.
     *
     * @param indexes ring indexes
     * @return values stored at the specified ring indexes
     */
    RFuture<List<V>> getAsync(long... indexes);

    /**
     * Returns the number of values equal to the specified value currently stored in this buffer.
     *
     * @param value value to match
     * @return number of matching values
     */
    RFuture<Long> countAsync(V value);

    /**
     * Returns {@code true} if this buffer contains the specified value.
     *
     * @param value value to match
     * @return {@code true} if the value is present
     */
    RFuture<Boolean> containsAsync(V value);

    /**
     * Returns the average of the numeric values currently stored in this buffer.
     *
     * @return average value, or {@code null} if the buffer is empty
     */
    RFuture<Double> averageAsync();

    /**
     * Returns the bitwise AND of the numeric values currently stored in this buffer.
     *
     * @return bitwise AND result, or {@code null} if the buffer is empty
     */
    RFuture<Long> bitAndAsync();

    /**
     * Returns the bitwise AND of the numeric values stored in the specified ring index range.
     *
     * @param startIndex start ring index
     * @param endIndex end ring index
     * @return bitwise AND result
     */
    RFuture<Long> bitAndAsync(long startIndex, long endIndex);

    /**
     * Returns the bitwise OR of the numeric values currently stored in this buffer.
     *
     * @return bitwise OR result, or {@code null} if the buffer is empty
     */
    RFuture<Long> bitOrAsync();

    /**
     * Returns the bitwise OR of the numeric values stored in the specified ring index range.
     *
     * @param startIndex start ring index
     * @param endIndex end ring index
     * @return bitwise OR result
     */
    RFuture<Long> bitOrAsync(long startIndex, long endIndex);

    /**
     * Returns the bitwise XOR of the numeric values currently stored in this buffer.
     *
     * @return bitwise XOR result, or {@code null} if the buffer is empty
     */
    RFuture<Long> bitXorAsync();

    /**
     * Returns the bitwise XOR of the numeric values stored in the specified ring index range.
     *
     * @param startIndex start ring index
     * @param endIndex end ring index
     * @return bitwise XOR result
     */
    RFuture<Long> bitXorAsync(long startIndex, long endIndex);

}
