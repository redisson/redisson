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

import org.redisson.codec.JsonCodec;

import java.util.List;

/**
 * Redis JSON datatype interface. Data is stored as JSON object in Redis
 *
 * @author Nikita Koksharov
 * @param <V> the type of object
 */
public interface RJsonBucketAsync<V> extends RBucketAsync<V> {

    /**
     * Get Json object/objects by JSONPath
     *
     * @param codec object codec
     * @param paths JSON paths
     * @return object
     *
     * @param <T> the type of object
     */
    <T> RFuture<T> getAsync(JsonCodec codec, String... paths);

    /**
     * Sets Json object by JSONPath only if previous value is empty
     *
     * @param path JSON path
     * @param value object
     * @return {@code true} if successful, or {@code false} if
     *         value was already set
     */
    RFuture<Boolean> setIfAbsentAsync(String path, Object value);

    /**
     * Use {@link #setIfAbsentAsync(String, Object)} instead
     *
     * @param path JSON path
     * @param value object
     * @return {@code true} if successful, or {@code false} if
     *         value was already set
     */
    @Deprecated
    RFuture<Boolean> trySetAsync(String path, Object value);

    /**
     * Sets Json object by JSONPath only if previous value is non-empty
     *
     * @param path JSON path
     * @param value object
     * @return {@code true} if successful, or {@code false} if
     *         element wasn't set
     */
    RFuture<Boolean> setIfExistsAsync(String path, Object value);

    /**
     * Atomically sets the value to the given updated value
     * by given JSONPath, only if serialized state of
     * the current value equals to serialized state of the expected value.
     *
     * @param path JSON path
     * @param expect the expected value
     * @param update the new value
     * @return {@code true} if successful; or {@code false} if the actual value
     *         was not equal to the expected value.
     */
    RFuture<Boolean> compareAndSetAsync(String path, Object expect, Object update);

    /**
     * Retrieves current value of element specified by JSONPath
     * and replaces it with <code>newValue</code>.
     *
     * @param codec object codec
     * @param path JSON path
     * @param newValue value to set
     * @return previous value
     */
    <T> RFuture<T> getAndSetAsync(JsonCodec codec, String path, Object newValue);

    /**
     * Stores object into element by specified JSONPath.
     *
     * @param path JSON path
     * @param value value to set
     * @return void
     */
    RFuture<Void> setAsync(String path, Object value);

    /**
     * Returns size of string data by JSONPath
     *
     * @param path JSON path
     * @return size of string
     */
    RFuture<Long> stringSizeAsync(String path);

    /**
     * Returns list of string data size by JSONPath.
     * Compatible only with enhanced syntax starting with '$' character.
     *
     * @param path JSON path
     * @return list of string data sizes
     */
    RFuture<List<Long>> stringSizeMultiAsync(String path);

    /**
     * Appends string data to element specified by JSONPath.
     * Returns new size of string data.
     *
     * @param path JSON path
     * @param value data
     * @return size of string data
     */
    RFuture<Long> stringAppendAsync(String path, Object value);

    /**
     * Appends string data to elements specified by JSONPath.
     * Returns new size of string data.
     * Compatible only with enhanced syntax starting with '$' character.
     *
     * @param path JSON path
     * @param value data
     * @return list of string data sizes
     */
    RFuture<List<Long>> stringAppendMultiAsync(String path, Object value);

    /**
     * Appends values to array specified by JSONPath.
     * Returns new size of array.
     *
     * @param path JSON path
     * @param values values to append
     * @return size of array
     */
    RFuture<Long> arrayAppendAsync(String path, Object... values);

    /**
     * Appends values to arrays specified by JSONPath.
     * Returns new size of arrays.
     * Compatible only with enhanced syntax starting with '$' character.
     *
     * @param path JSON path
     * @param values values to append
     * @return list of arrays size
     */
    RFuture<List<Long>> arrayAppendMultiAsync(String path, Object... values);

    /**
     * Returns index of object in array specified by JSONPath.
     * -1 means object not found.
     *
     * @param path JSON path
     * @param value value to search
     * @return index in array
     */
    RFuture<Long> arrayIndexAsync(String path, Object value);

    /**
     * Returns index of object in arrays specified by JSONPath.
     * -1 means object not found.
     * Compatible only with enhanced syntax starting with '$' character.
     *
     * @param path JSON path
     * @param value value to search
     * @return list of index in arrays
     */
    RFuture<List<Long>> arrayIndexMultiAsync(String path, Object value);

    /**
     * Returns index of object in array specified by JSONPath
     * in range between <code>start</code> (inclusive) and <code>end</code> (exclusive) indexes.
     * -1 means object not found.
     *
     * @param path JSON path
     * @param value value to search
     * @param start start index, inclusive
     * @param end end index, exclusive
     * @return index in array
     */
    RFuture<Long> arrayIndexAsync(String path, Object value, long start, long end);

    /**
     * Returns index of object in arrays specified by JSONPath
     * in range between <code>start</code> (inclusive) and <code>end</code> (exclusive) indexes.
     * -1 means object not found.
     * Compatible only with enhanced syntax starting with '$' character.
     *
     * @param path JSON path
     * @param value value to search
     * @param start start index, inclusive
     * @param end end index, exclusive
     * @return list of index in arrays
     */
    RFuture<List<Long>> arrayIndexMultiAsync(String path, Object value, long start, long end);

    /**
     * Inserts values into array specified by JSONPath.
     * Values are inserted at defined <code>index</code>.
     *
     * @param path JSON path
     * @param index array index at which values are inserted
     * @param values values to insert
     * @return size of array
     */
    RFuture<Long> arrayInsertAsync(String path, long index, Object... values);

    /**
     * Inserts values into arrays specified by JSONPath.
     * Values are inserted at defined <code>index</code>.
     * Compatible only with enhanced syntax starting with '$' character.
     *
     * @param path JSON path
     * @param index array index at which values are inserted
     * @param values values to insert
     * @return list of arrays size
     */
    RFuture<List<Long>> arrayInsertMultiAsync(String path, long index, Object... values);

    /**
     * Returns size of array specified by JSONPath.
     *
     * @param path JSON path
     * @return size of array
     */
    RFuture<Long> arraySizeAsync(String path);

    /**
     * Returns size of arrays specified by JSONPath.
     * Compatible only with enhanced syntax starting with '$' character.
     *
     * @param path JSON path
     * @return list of arrays size
     */
    RFuture<List<Long>> arraySizeMultiAsync(String path);

    /**
     * Polls last element of array specified by JSONPath.
     *
     * @param codec object codec
     * @param path JSON path
     * @return last element
     *
     * @param <T> the type of object
     */
    <T> RFuture<T> arrayPollLastAsync(JsonCodec codec, String path);

    /**
     * Polls last element of arrays specified by JSONPath.
     * Compatible only with enhanced syntax starting with '$' character.
     *
     * @param codec object codec
     * @param path JSON path
     * @return list of last elements
     *
     * @param <T> the type of object
     */
    <T> RFuture<List<T>> arrayPollLastMultiAsync(JsonCodec codec, String path);

    /**
     * Polls first element of array specified by JSONPath.
     *
     * @param codec object codec
     * @param path JSON path
     * @return first element
     *
     * @param <T> the type of object
     */
    <T> RFuture<T> arrayPollFirstAsync(JsonCodec codec, String path);

    /**
     * Polls first element of arrays specified by JSONPath.
     * Compatible only with enhanced syntax starting with '$' character.
     *
     * @param codec object codec
     * @param path JSON path
     * @return list of first elements
     *
     * @param <T> the type of object
     */
    <T> RFuture<List<T>> arrayPollFirstMultiAsync(JsonCodec codec, String path);

    /**
     * Pops element located at index of array specified by JSONPath.
     *
     * @param codec object codec
     * @param path JSON path
     * @param index array index
     * @return element
     *
     * @param <T> the type of object
     */
    <T> RFuture<T> arrayPopAsync(JsonCodec codec, String path, long index);

    /**
     * Pops elements located at index of arrays specified by JSONPath.
     * Compatible only with enhanced syntax starting with '$' character.
     *
     * @param codec object codec
     * @param path JSON path
     * @param index array index
     * @return list of elements
     *
     * @param <T> the type of object
     */
    <T> RFuture<List<T>> arrayPopMultiAsync(JsonCodec codec, String path, long index);

    /**
     * Trims array specified by JSONPath in range
     * between <code>start</code> (inclusive) and <code>end</code> (inclusive) indexes.
     *
     * @param path JSON path
     * @param start start index, inclusive
     * @param end end index, inclusive
     * @return length of array
     */
    RFuture<Long> arrayTrimAsync(String path, long start, long end);

    /**
     * Trims arrays specified by JSONPath in range
     * between <code>start</code> (inclusive) and <code>end</code> (inclusive) indexes.
     * Compatible only with enhanced syntax starting with '$' character.
     *
     * @param path JSON path
     * @param start start index, inclusive
     * @param end end index, inclusive
     * @return length of array
     */
    RFuture<List<Long>> arrayTrimMultiAsync(String path, long start, long end);

    /**
     * Clears json container.
     *
     * @return number of cleared containers
     */
    RFuture<Long> clearAsync();

    /**
     * Clears json container specified by JSONPath.
     * Compatible only with enhanced syntax starting with '$' character.
     *
     * @param path JSON path
     * @return number of cleared containers
     */
    RFuture<Long> clearAsync(String path);

    /**
     * Increments the current value specified by JSONPath by <code>delta</code>.
     *
     * @param path JSON path
     * @param delta increment value
     * @return the updated value
     */
    <T extends Number> RFuture<T> incrementAndGetAsync(String path, T delta);

    /**
     * Increments the current values specified by JSONPath by <code>delta</code>.
     * Compatible only with enhanced syntax starting with '$' character.
     *
     * @param path JSON path
     * @param delta increment value
     * @return list of updated value
     */
    <T extends Number> RFuture<List<T>> incrementAndGetMultiAsync(String path, T delta);

    /**
     * Merges object into element by the specified JSONPath.
     *
     * @param path JSON path
     * @param value value to merge
     */
    RFuture<Void> mergeAsync(String path, Object value);

    /**
     * Returns keys amount in JSON container
     *
     * @return keys amount
     */
    RFuture<Long> countKeysAsync();

    /**
     * Returns keys amount in JSON container specified by JSONPath
     *
     * @param path JSON path
     * @return keys amount
     */
    RFuture<Long> countKeysAsync(String path);

    /**
     * Returns list of keys amount in JSON containers specified by JSONPath
     *
     * @param path JSON path
     * @return list of keys amount
     */
    RFuture<List<Long>> countKeysMultiAsync(String path);

    /**
     * Returns list of keys in JSON container
     *
     * @return list of keys
     */
    RFuture<List<String>> getKeysAsync();

    /**
     * Returns list of keys in JSON container specified by JSONPath
     *
     * @param path JSON path
     * @return list of keys
     */
    RFuture<List<String>> getKeysAsync(String path);

    /**
     * Returns list of keys in JSON containers specified by JSONPath
     *
     * @param path JSON path
     * @return list of keys
     */
    RFuture<List<List<String>>> getKeysMultiAsync(String path);

    /**
     * Toggle boolean value specified by JSONPath
     *
     * @param path JSON path
     * @return new boolean value
     */
    RFuture<Boolean> toggleAsync(String path);

    /**
     * Toggle boolean values specified by JSONPath
     *
     * @param path JSON path
     * @return list of boolean values
     */
    RFuture<List<Boolean>> toggleMultiAsync(String path);

    /**
     * Returns type of element
     *
     * @return type of element
     */
    RFuture<JsonType> getTypeAsync();

    /**
     * Returns type of element specified by JSONPath
     *
     * @param path JSON path
     * @return type of element
     */
    RFuture<JsonType> getTypeAsync(String path);

    /**
     * Deletes JSON elements specified by JSONPath
     *
     * @param path JSON path
     * @return number of deleted elements
     */
    RFuture<Long> deleteAsync(String path);

}
