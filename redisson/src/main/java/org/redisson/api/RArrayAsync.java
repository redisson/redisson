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

import org.redisson.api.array.ArrayEntry;
import org.redisson.api.array.ArrayGrepArgs;
import org.redisson.api.array.ArrayInfo;

import java.util.List;
import java.util.Map;

/**
 * Async interface for Array object.
 *
 * @param <V> value type
 *
 * @author lamnt2008
 *
 */
public interface RArrayAsync<V> extends RExpirableAsync {

    /**
     * Returns value stored at the specified array index.
     *
     * @param index array index
     * @return value stored at the specified array index
     */
    RFuture<V> getAsync(long index);

    /**
     * Returns values stored at the specified array indexes.
     *
     * @param indexes array indexes
     * @return values stored at the specified array indexes
     */
    RFuture<List<V>> getAsync(long... indexes);

    /**
     * Sets value at the specified array index.
     *
     * @param index array index
     * @param value value to set
     * @return number of values set
     */
    RFuture<Long> setAsync(long index, V value);

    /**
     * Sets values starting at the specified array index.
     *
     * @param index start array index
     * @param values values to set
     * @return number of values set
     */
    RFuture<Long> setAsync(long index, V... values);

    /**
     * Sets values at the specified array indexes.
     *
     * @param entries map of array indexes and values
     * @return number of values set
     */
    RFuture<Long> setAsync(Map<Long, V> entries);

    /**
     * Deletes values stored at the specified array indexes.
     *
     * @param indexes array indexes
     * @return number of deleted values
     */
    RFuture<Long> deleteAsync(long... indexes);

    /**
     * Deletes values stored in the specified array index range.
     *
     * @param startIndex start array index
     * @param endIndex end array index
     * @return number of deleted values
     */
    RFuture<Long> deleteRangeAsync(long startIndex, long endIndex);

    /**
     * Deletes values stored in the specified array index ranges.
     * Arguments should contain start and end index pairs.
     *
     * @param startEndIndexes start and end array index pairs
     * @return number of deleted values
     */
    RFuture<Long> deleteRangesAsync(long... startEndIndexes);

    /**
     * Returns number of values stored in this array.
     *
     * @return number of values
     */
    RFuture<Long> countAsync();

    /**
     * Returns number of values stored in the specified array index range.
     *
     * @param startIndex start array index
     * @param endIndex end array index
     * @return number of values
     */
    RFuture<Long> countAsync(long startIndex, long endIndex);

    /**
     * Returns number of values equal to the specified value in the specified array index range.
     *
     * @param startIndex start array index
     * @param endIndex end array index
     * @param value value to match
     * @return number of matching values
     */
    RFuture<Long> countMatchesAsync(long startIndex, long endIndex, V value);

    /**
     * Returns array length.
     *
     * @return array length
     */
    RFuture<Long> lengthAsync();

    /**
     * Returns values stored in the specified array index range.
     *
     * @param startIndex start array index
     * @param endIndex end array index
     * @return values stored in the specified array index range
     */
    RFuture<List<V>> rangeAsync(long startIndex, long endIndex);

    /**
     * Returns entries stored in the specified array index range.
     *
     * @param startIndex start array index
     * @param endIndex end array index
     * @return entries stored in the specified array index range
     */
    RFuture<List<ArrayEntry<V>>> scanAsync(long startIndex, long endIndex);

    /**
     * Returns entries stored in the specified array index range.
     *
     * @param startIndex start array index
     * @param endIndex end array index
     * @param limit maximum number of entries
     * @return entries stored in the specified array index range
     */
    RFuture<List<ArrayEntry<V>>> scanAsync(long startIndex, long endIndex, long limit);

    /**
     * Inserts values at consecutive indexes starting at the current insert index.
     *
     * @param values values to insert
     * @return array index where the last value was inserted
     */
    RFuture<Long> insertAsync(V... values);

    /**
     * Inserts values into a ring buffer with the specified size.
     * Values are written at consecutive ring positions and wrap around as needed.
     *
     * @param size ring buffer size
     * @param values values to insert
     * @return array index where the last value was inserted
     */
    RFuture<Long> ringAsync(long size, V... values);

    /**
     * Returns the next array index used by {@code insertAsync(...)} or {@code ringAsync(...)}.
     *
     * @return next insert index, or {@code null} if insert cursor is exhausted
     */
    RFuture<Long> nextAsync();

    /**
     * Sets current insert index.
     *
     * @param index array index
     * @return {@code true} if index was set, {@code false} otherwise
     */
    RFuture<Boolean> seekAsync(long index);

    /**
     * Returns last inserted values.
     *
     * @param count values amount
     * @return last inserted values
     */
    RFuture<List<V>> lastItemsAsync(long count);

    /**
     * Returns last inserted values.
     *
     * @param count values amount
     * @param reverse {@code true} to return values in reverse order
     * @return last inserted values
     */
    RFuture<List<V>> lastItemsAsync(long count, boolean reverse);

    /**
     * Returns array information.
     *
     * @return array information
     */
    RFuture<ArrayInfo> getInfoAsync();

    /**
     * Returns array information.
     *
     * @param full {@code true} to include full statistics
     * @return array information
     */
    RFuture<ArrayInfo> getInfoAsync(boolean full);

    /**
     * Returns indexes of values matching the specified arguments.
     *
     * @param args grep arguments
     * @return indexes of matching values
     */
    RFuture<List<Long>> grepAsync(ArrayGrepArgs args);

    /**
     * Returns indexes of values matching the specified arguments in the specified array index range.
     *
     * @param startIndex start array index
     * @param endIndex end array index
     * @param args grep arguments
     * @return indexes of matching values
     */
    RFuture<List<Long>> grepAsync(long startIndex, long endIndex, ArrayGrepArgs args);

    /**
     * Returns entries matching the specified arguments.
     *
     * @param args grep arguments
     * @return matching entries
     */
    RFuture<List<ArrayEntry<V>>> grepEntriesAsync(ArrayGrepArgs args);

    /**
     * Returns entries matching the specified arguments in the specified array index range.
     *
     * @param startIndex start array index
     * @param endIndex end array index
     * @param args grep arguments
     * @return matching entries
     */
    RFuture<List<ArrayEntry<V>>> grepEntriesAsync(long startIndex, long endIndex, ArrayGrepArgs args);

    /**
     * Returns sum of numeric values in the specified array index range.
     *
     * @param startIndex start array index
     * @param endIndex end array index
     * @return sum of values
     */
    RFuture<Double> sumAsync(long startIndex, long endIndex);

    /**
     * Returns minimum numeric value in the specified array index range.
     *
     * @param startIndex start array index
     * @param endIndex end array index
     * @return minimum value
     */
    RFuture<Double> minAsync(long startIndex, long endIndex);

    /**
     * Returns maximum numeric value in the specified array index range.
     *
     * @param startIndex start array index
     * @param endIndex end array index
     * @return maximum value
     */
    RFuture<Double> maxAsync(long startIndex, long endIndex);

    /**
     * Returns bitwise AND result for numeric values in the specified array index range.
     *
     * @param startIndex start array index
     * @param endIndex end array index
     * @return bitwise AND result
     */
    RFuture<Long> bitAndAsync(long startIndex, long endIndex);

    /**
     * Returns bitwise OR result for numeric values in the specified array index range.
     *
     * @param startIndex start array index
     * @param endIndex end array index
     * @return bitwise OR result
     */
    RFuture<Long> bitOrAsync(long startIndex, long endIndex);

    /**
     * Returns bitwise XOR result for numeric values in the specified array index range.
     *
     * @param startIndex start array index
     * @param endIndex end array index
     * @return bitwise XOR result
     */
    RFuture<Long> bitXorAsync(long startIndex, long endIndex);

}
