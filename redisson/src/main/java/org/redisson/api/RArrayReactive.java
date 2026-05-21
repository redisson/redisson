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
import org.redisson.api.array.ArrayEntry;
import org.redisson.api.array.ArrayGrepArgs;
import org.redisson.api.array.ArrayInfo;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Reactive interface for Array object.
 *
 * @param <V> value type
 *
 * @author lamnt2008
 *
 */
public interface RArrayReactive<V> extends RExpirableReactive {

    /**
     * Returns value stored at the specified array index.
     *
     * @param index array index
     * @return value stored at the specified array index
     */
    @EmptyAsAbsent
    Mono<V> get(long index);

    /**
     * Returns values stored at the specified array indexes.
     *
     * @param indexes array indexes
     * @return values stored at the specified array indexes
     */
    Mono<List<V>> get(long... indexes);

    /**
     * Sets value at the specified array index.
     *
     * @param index array index
     * @param value value to set
     * @return number of values set
     */
    Mono<Long> set(long index, V value);

    /**
     * Sets values starting at the specified array index.
     *
     * @param index start array index
     * @param values values to set
     * @return number of values set
     */
    Mono<Long> set(long index, V... values);

    /**
     * Sets values at the specified array indexes.
     *
     * @param entries map of array indexes and values
     * @return number of values set
     */
    Mono<Long> set(Map<Long, V> entries);

    /**
     * Deletes values stored at the specified array indexes.
     *
     * @param indexes array indexes
     * @return number of deleted values
     */
    Mono<Long> delete(long... indexes);

    /**
     * Deletes values stored in the specified array index range.
     *
     * @param startIndex start array index
     * @param endIndex end array index
     * @return number of deleted values
     */
    Mono<Long> deleteRange(long startIndex, long endIndex);

    /**
     * Deletes values stored in the specified array index ranges.
     * Arguments should contain start and end index pairs.
     *
     * @param startEndIndexes start and end array index pairs
     * @return number of deleted values
     */
    Mono<Long> deleteRanges(long... startEndIndexes);

    /**
     * Returns number of values stored in this array.
     *
     * @return number of values
     */
    Mono<Long> count();

    /**
     * Returns number of values stored in the specified array index range.
     *
     * @param startIndex start array index
     * @param endIndex end array index
     * @return number of values
     */
    Mono<Long> count(long startIndex, long endIndex);

    /**
     * Returns number of values equal to the specified value in the specified array index range.
     *
     * @param startIndex start array index
     * @param endIndex end array index
     * @param value value to match
     * @return number of matching values
     */
    Mono<Long> countMatches(long startIndex, long endIndex, V value);

    /**
     * Returns array length.
     *
     * @return array length
     */
    Mono<Long> length();

    /**
     * Returns values stored in the specified array index range.
     *
     * @param startIndex start array index
     * @param endIndex end array index
     * @return values stored in the specified array index range
     */
    Mono<List<V>> range(long startIndex, long endIndex);

    /**
     * Returns entries stored in the specified array index range.
     *
     * @param startIndex start array index
     * @param endIndex end array index
     * @return entries stored in the specified array index range
     */
    Mono<List<ArrayEntry<V>>> scan(long startIndex, long endIndex);

    /**
     * Returns entries stored in the specified array index range.
     *
     * @param startIndex start array index
     * @param endIndex end array index
     * @param limit maximum number of entries
     * @return entries stored in the specified array index range
     */
    Mono<List<ArrayEntry<V>>> scan(long startIndex, long endIndex, long limit);

    /**
     * Inserts values at consecutive indexes starting at the current insert index.
     *
     * @param values values to insert
     * @return array index where the last value was inserted
     */
    Mono<Long> insert(V... values);

    /**
     * Inserts values into a ring buffer with the specified size.
     * Values are written at consecutive ring positions and wrap around as needed.
     *
     * @param size ring buffer size
     * @param values values to insert
     * @return array index where the last value was inserted
     */
    Mono<Long> ring(long size, V... values);

    /**
     * Returns the next array index used by {@code insert(...)} or {@code ring(...)}.
     *
     * @return next insert index, or empty {@link Mono} if insert cursor is exhausted
     */
    @EmptyAsAbsent
    Mono<Long> next();

    /**
     * Sets current insert index.
     *
     * @param index array index
     * @return {@code true} if index was set, {@code false} otherwise
     */
    Mono<Boolean> seek(long index);

    /**
     * Returns last inserted values.
     *
     * @param count values amount
     * @return last inserted values
     */
    Mono<List<V>> lastItems(long count);

    /**
     * Returns last inserted values.
     *
     * @param count values amount
     * @param reverse {@code true} to return values in reverse order
     * @return last inserted values
     */
    Mono<List<V>> lastItems(long count, boolean reverse);

    /**
     * Returns array information.
     *
     * @return array information
     */
    Mono<ArrayInfo> getInfo();

    /**
     * Returns array information.
     *
     * @param full {@code true} to include full statistics
     * @return array information
     */
    Mono<ArrayInfo> getInfo(boolean full);

    /**
     * Returns indexes of values matching the specified arguments.
     *
     * @param args grep arguments
     * @return indexes of matching values
     */
    Mono<List<Long>> grep(ArrayGrepArgs args);

    /**
     * Returns indexes of values matching the specified arguments in the specified array index range.
     *
     * @param startIndex start array index
     * @param endIndex end array index
     * @param args grep arguments
     * @return indexes of matching values
     */
    Mono<List<Long>> grep(long startIndex, long endIndex, ArrayGrepArgs args);

    /**
     * Returns entries matching the specified arguments.
     *
     * @param args grep arguments
     * @return matching entries
     */
    Mono<List<ArrayEntry<V>>> grepEntries(ArrayGrepArgs args);

    /**
     * Returns entries matching the specified arguments in the specified array index range.
     *
     * @param startIndex start array index
     * @param endIndex end array index
     * @param args grep arguments
     * @return matching entries
     */
    Mono<List<ArrayEntry<V>>> grepEntries(long startIndex, long endIndex, ArrayGrepArgs args);

    /**
     * Returns sum of numeric values in the specified array index range.
     *
     * @param startIndex start array index
     * @param endIndex end array index
     * @return sum of values
     */
    @EmptyAsAbsent
    Mono<Double> sum(long startIndex, long endIndex);

    /**
     * Returns minimum numeric value in the specified array index range.
     *
     * @param startIndex start array index
     * @param endIndex end array index
     * @return minimum value
     */
    @EmptyAsAbsent
    Mono<Double> min(long startIndex, long endIndex);

    /**
     * Returns maximum numeric value in the specified array index range.
     *
     * @param startIndex start array index
     * @param endIndex end array index
     * @return maximum value
     */
    @EmptyAsAbsent
    Mono<Double> max(long startIndex, long endIndex);

    /**
     * Returns bitwise AND result for numeric values in the specified array index range.
     *
     * @param startIndex start array index
     * @param endIndex end array index
     * @return bitwise AND result
     */
    @EmptyAsAbsent
    Mono<Long> bitAnd(long startIndex, long endIndex);

    /**
     * Returns bitwise OR result for numeric values in the specified array index range.
     *
     * @param startIndex start array index
     * @param endIndex end array index
     * @return bitwise OR result
     */
    @EmptyAsAbsent
    Mono<Long> bitOr(long startIndex, long endIndex);

    /**
     * Returns bitwise XOR result for numeric values in the specified array index range.
     *
     * @param startIndex start array index
     * @param endIndex end array index
     * @return bitwise XOR result
     */
    @EmptyAsAbsent
    Mono<Long> bitXor(long startIndex, long endIndex);

}
