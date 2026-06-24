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

import org.redisson.api.annotation.EmptyAsAbsent;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.List;

/**
 * Reactive interface for {@link RCircularBuffer}.
 *
 * @param <V> value type
 *
 * @author Nikita Koksharov
 *
 */
public interface RCircularBufferReactive<V> extends RExpirableReactive {

    /**
     * Sets capacity of this buffer only if it wasn't set before.
     *
     * @param capacity buffer capacity
     * @return {@code true} if capacity set successfully,
     *         {@code false} if capacity already set
     */
    Mono<Boolean> trySetCapacity(int capacity);

    /**
     * Sets capacity of this buffer and overrides the current value.
     *
     * @param capacity buffer capacity
     * @return void
     */
    Mono<Void> setCapacity(int capacity);

    /**
     * Returns capacity of this buffer.
     *
     * @return buffer capacity, or {@code 0} if capacity wasn't set
     */
    Mono<Integer> capacity();

    /**
     * Returns the remaining capacity of this buffer.
     *
     * @return remaining capacity
     */
    Mono<Integer> remainingCapacity();

    /**
     * Adds the specified value to the tail of this buffer.
     *
     * @param value value to add
     * @return {@code true} if value was added
     */
    Mono<Boolean> add(V value);

    /**
     * Adds the specified values to the tail of this buffer in iteration order.
     *
     * @param values values to add
     * @return {@code true} if at least one value was added
     */
    Mono<Boolean> addAll(Collection<? extends V> values);

    /**
     * Writes the specified values into a ring of the given {@code size} and
     * (re)configures this buffer capacity to {@code size}.
     *
     * @param size ring size, becomes the new buffer capacity
     * @param values values to write, must contain at least one value
     * @return array index where the last value was written
     */
    Mono<Long> set(int size, V... values);

    /**
     * Returns the value stored at the specified ring index.
     *
     * @param index ring index
     * @return value stored at the specified ring index, or {@code null} if absent
     */
    Mono<V> get(long index);

    /**
     * Returns the {@code count} most recently added values.
     *
     * @param count number of values to return
     * @param reverse if {@code true} values are returned newest-first,
     *                otherwise in insertion order (oldest-first)
     * @return most recently added values
     */
    @EmptyAsAbsent
    Mono<List<V>> lastItems(int count, boolean reverse);

    /**
     * Returns values stored in the specified ring index range (inclusive).
     *
     * @param startIndex start ring index
     * @param endIndex end ring index
     * @return values stored in the specified ring index range
     */
    @EmptyAsAbsent
    Mono<List<V>> range(long startIndex, long endIndex);

    /**
     * Returns all retained values in insertion order (oldest-first).
     *
     * @return all retained values
     */
    @EmptyAsAbsent
    Mono<List<V>> readAll();

    /**
     * Returns the number of values currently stored in this buffer.
     *
     * @return number of stored values
     */
    Mono<Integer> size();

    /**
     * Returns the sum of the numeric values currently stored in this buffer.
     *
     * @return sum of values, or {@code null} if the buffer is empty
     */
    Mono<Double> sum();

    /**
     * Returns the sum of the numeric values stored in the specified ring index range.
     *
     * @param startIndex start ring index
     * @param endIndex end ring index
     * @return sum of values
     */
    Mono<Double> sum(long startIndex, long endIndex);

    /**
     * Returns the minimum numeric value currently stored in this buffer.
     *
     * @return minimum value, or {@code null} if the buffer is empty
     */
    Mono<Double> min();

    /**
     * Returns the minimum numeric value stored in the specified ring index range.
     *
     * @param startIndex start ring index
     * @param endIndex end ring index
     * @return minimum value
     */
    Mono<Double> min(long startIndex, long endIndex);

    /**
     * Returns the maximum numeric value currently stored in this buffer.
     *
     * @return maximum value, or {@code null} if the buffer is empty
     */
    Mono<Double> max();

    /**
     * Returns the maximum numeric value stored in the specified ring index range.
     *
     * @param startIndex start ring index
     * @param endIndex end ring index
     * @return maximum value
     */
    Mono<Double> max(long startIndex, long endIndex);

    /**
     * Removes all values from this buffer while keeping the configured capacity.
     *
     * @return void
     */
    Mono<Void> clear();

    /**
     * Returns {@code true} if this buffer contains no values.
     *
     * @return {@code true} if this buffer is empty
     */
    Mono<Boolean> isEmpty();

    /**
     * Returns {@code true} if this buffer is full.
     *
     * @return {@code true} if this buffer is full
     */
    Mono<Boolean> isFull();

    /**
     * Returns the most recently added value without removing it.
     *
     * @return the newest value, or {@code null} if this buffer is empty
     */
    Mono<V> peekLast();

    /**
     * Returns the oldest retained value without removing it.
     *
     * @return the oldest value, or {@code null} if this buffer is empty
     */
    Mono<V> peekFirst();

    /**
     * Returns the values stored at the specified ring indexes.
     *
     * @param indexes ring indexes
     * @return values stored at the specified ring indexes
     */
    @EmptyAsAbsent
    Mono<List<V>> get(long... indexes);

    /**
     * Returns the number of values equal to the specified value currently stored in this buffer.
     *
     * @param value value to match
     * @return number of matching values
     */
    Mono<Long> count(V value);

    /**
     * Returns {@code true} if this buffer contains the specified value.
     *
     * @param value value to match
     * @return {@code true} if the value is present
     */
    Mono<Boolean> contains(V value);

    /**
     * Returns the average of the numeric values currently stored in this buffer.
     *
     * @return average value, or {@code null} if the buffer is empty
     */
    Mono<Double> average();

    /**
     * Returns the bitwise AND of the numeric values currently stored in this buffer.
     *
     * @return bitwise AND result, or {@code null} if the buffer is empty
     */
    Mono<Long> bitAnd();

    /**
     * Returns the bitwise AND of the numeric values stored in the specified ring index range.
     *
     * @param startIndex start ring index
     * @param endIndex end ring index
     * @return bitwise AND result
     */
    Mono<Long> bitAnd(long startIndex, long endIndex);

    /**
     * Returns the bitwise OR of the numeric values currently stored in this buffer.
     *
     * @return bitwise OR result, or {@code null} if the buffer is empty
     */
    Mono<Long> bitOr();

    /**
     * Returns the bitwise OR of the numeric values stored in the specified ring index range.
     *
     * @param startIndex start ring index
     * @param endIndex end ring index
     * @return bitwise OR result
     */
    Mono<Long> bitOr(long startIndex, long endIndex);

    /**
     * Returns the bitwise XOR of the numeric values currently stored in this buffer.
     *
     * @return bitwise XOR result, or {@code null} if the buffer is empty
     */
    Mono<Long> bitXor();

    /**
     * Returns the bitwise XOR of the numeric values stored in the specified ring index range.
     *
     * @param startIndex start ring index
     * @param endIndex end ring index
     * @return bitwise XOR result
     */
    Mono<Long> bitXor(long startIndex, long endIndex);

}
