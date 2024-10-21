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

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.redisson.codec.JsonCodec;

import java.util.List;

/**
 * Redis JSON datatype holder. Data is stored as JSON object in Redis
 *
 * @author Nikita Koksharov
 * @param <V> the type of object
 */
public interface RJsonBucketRx<V> extends RBucketRx<V> {

    /**
     * Get Json object/objects by JSONPath
     *
     * @param codec object codec
     * @param paths JSON paths
     * @return object
     *
     * @param <T> the type of object
     */
    <T> Maybe<T> get(JsonCodec codec, String... paths);

    /**
     * Sets Json object by JSONPath only if previous value is empty
     *
     * @param path JSON path
     * @param value object
     * @return {@code true} if successful, or {@code false} if
     *         value was already set
     */
    Single<Boolean> setIfAbsent(String path, Object value);

    /**
     * Use {@link #setIfAbsent(String, Object)} instead
     *
     * @param path  JSON path
     * @param value object
     * @return {@code true} if successful, or {@code false} if
     * value was already set
     */
    @Deprecated
    Single<Boolean> trySet(String path, Object value);

    /**
     * Sets Json object by JSONPath only if previous value is non-empty
     *
     * @param path JSON path
     * @param value object
     * @return {@code true} if successful, or {@code false} if
     *         element wasn't set
     */
    Single<Boolean> setIfExists(String path, Object value);

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
    Single<Boolean> compareAndSet(String path, Object expect, Object update);

    /**
     * Retrieves current value of element specified by JSONPath
     * and replaces it with <code>newValue</code>.
     *
     * @param codec object codec
     * @param path JSON path
     * @param newValue value to set
     * @return previous value
     */
    <T> Maybe<T> getAndSet(JsonCodec codec, String path, Object newValue);

    /**
     * Stores object into element by specified JSONPath.
     *
     * @param path JSON path
     * @param value value to set
     * @return void
     */
    Completable set(String path, Object value);

    /**
     * Returns size of string data by JSONPath
     *
     * @param path JSON path
     * @return size of string
     */
    Maybe<Long> stringSize(String path);

    /**
     * Returns list of string data size by JSONPath.
     * Compatible only with enhanced syntax starting with '$' character.
     *
     * @param path JSON path
     * @return list of string data sizes
     */
    Single<List<Long>> stringSizeMulti(String path);

    /**
     * Appends string data to element specified by JSONPath.
     * Returns new size of string data.
     *
     * @param path JSON path
     * @param value data
     * @return size of string data
     */
    Single<Long> stringAppend(String path, Object value);

    /**
     * Appends string data to elements specified by JSONPath.
     * Returns new size of string data.
     * Compatible only with enhanced syntax starting with '$' character.
     *
     * @param path JSON path
     * @param value data
     * @return list of string data sizes
     */
    Single<List<Long>> stringAppendMulti(String path, Object value);

    /**
     * Appends values to array specified by JSONPath.
     * Returns new size of array.
     *
     * @param path JSON path
     * @param values values to append
     * @return size of array
     */
    Single<Long> arrayAppend(String path, Object... values);

    /**
     * Appends values to arrays specified by JSONPath.
     * Returns new size of arrays.
     * Compatible only with enhanced syntax starting with '$' character.
     *
     * @param path JSON path
     * @param values values to append
     * @return list of arrays size
     */
    Single<List<Long>> arrayAppendMulti(String path, Object... values);

    /**
     * Returns index of object in array specified by JSONPath.
     * -1 means object not found.
     *
     * @param path JSON path
     * @param value value to search
     * @return index in array
     */
    Single<Long> arrayIndex(String path, Object value);

    /**
     * Returns index of object in arrays specified by JSONPath.
     * -1 means object not found.
     * Compatible only with enhanced syntax starting with '$' character.
     *
     * @param path JSON path
     * @param value value to search
     * @return list of index in arrays
     */
    Single<List<Long>> arrayIndexMulti(String path, Object value);

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
    Single<Long> arrayIndex(String path, Object value, Long start, Long end);

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
    Single<List<Long>> arrayIndexMulti(String path, Object value, Long start, Long end);

    /**
     * Inserts values into array specified by JSONPath.
     * Values are inserted at defined <code>index</code>.
     *
     * @param path JSON path
     * @param index array index at which values are inserted
     * @param values values to insert
     * @return size of array
     */
    Single<Long> arrayInsert(String path, Long index, Object... values);

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
    Single<List<Long>> arrayInsertMulti(String path, Long index, Object... values);

    /**
     * Returns size of array specified by JSONPath.
     *
     * @param path JSON path
     * @return size of array
     */
    Single<Long> arraySize(String path);

    /**
     * Returns size of arrays specified by JSONPath.
     * Compatible only with enhanced syntax starting with '$' character.
     *
     * @param path JSON path
     * @return list of arrays size
     */
    Single<List<Long>> arraySizeMulti(String path);

    /**
     * Polls last element of array specified by JSONPath.
     *
     * @param codec object codec
     * @param path JSON path
     * @return last element
     *
     * @param <T> the type of object
     */
    <T> Maybe<T> arrayPollLast(JsonCodec codec, String path);

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
    <T> Single<List<T>> arrayPollLastMulti(JsonCodec codec, String path);

    /**
     * Polls first element of array specified by JSONPath.
     *
     * @param codec object codec
     * @param path JSON path
     * @return first element
     *
     * @param <T> the type of object
     */
    <T> Maybe<T> arrayPollFirst(JsonCodec codec, String path);

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
    <T> Single<List<T>> arrayPollFirstMulti(JsonCodec codec, String path);

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
    <T> Maybe<T> arrayPop(JsonCodec codec, String path, Long index);

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
    <T> Single<List<T>> arrayPopMulti(JsonCodec codec, String path, Long index);

    /**
     * Trims array specified by JSONPath in range
     * between <code>start</code> (inclusive) and <code>end</code> (inclusive) indexes.
     *
     * @param path JSON path
     * @param start start index, inclusive
     * @param end end index, inclusive
     * @return length of array
     */
    Single<Long> arrayTrim(String path, Long start, Long end);

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
    Single<List<Long>> arrayTrimMulti(String path, Long start, Long end);

    /**
     * Clears json container.
     *
     * @return number of cleared containers
     */
    Single<Long> clear();

    /**
     * Clears json container specified by JSONPath.
     * Compatible only with enhanced syntax starting with '$' character.
     *
     * @param path JSON path
     * @return number of cleared containers
     */
    Single<Long> clear(String path);

    /**
     * Increments the current value specified by JSONPath by <code>delta</code>.
     *
     * @param path JSON path
     * @param delta increment value
     * @return the updated value
     */
    <T extends Number> Maybe<T> incrementAndGet(String path, T delta);

    /**
     * Increments the current values specified by JSONPath by <code>delta</code>.
     * Compatible only with enhanced syntax starting with '$' character.
     *
     * @param path JSON path
     * @param delta increment value
     * @return list of updated value
     */
    <T extends Number> Single<List<T>> incrementAndGetMulti(String path, T delta);

    /**
     * Merges object into element by the specified JSONPath.
     *
     * @param path JSON path
     * @param value value to merge
     */
    Completable merge(String path, Object value);

    /**
     * Returns keys amount in JSON container
     *
     * @return keys amount
     */
    Single<Long> countKeys();

    /**
     * Returns keys amount in JSON container specified by JSONPath
     *
     * @param path JSON path
     * @return keys amount
     */
    Single<Long> countKeys(String path);

    /**
     * Returns list of keys amount in JSON containers specified by JSONPath
     *
     * @param path JSON path
     * @return list of keys amount
     */
    Single<List<Long>> countKeysMulti(String path);

    /**
     * Returns list of keys in JSON container
     *
     * @return list of keys
     */
    Single<List<String>> getKeys();

    /**
     * Returns list of keys in JSON container specified by JSONPath
     *
     * @param path JSON path
     * @return list of keys
     */
    Single<List<String>> getKeys(String path);

    /**
     * Returns list of keys in JSON containers specified by JSONPath
     *
     * @param path JSON path
     * @return list of keys
     */
    Single<List<List<String>>> getKeysMulti(String path);

    /**
     * Toggle Single<Boolean> value specified by JSONPath
     *
     * @param path JSON path
     * @return new Single<Boolean> value
     */
    Single<Boolean> toggle(String path);

    /**
     * Toggle Single<Boolean> values specified by JSONPath
     *
     * @param path JSON path
     * @return list of Single<Boolean> values
     */
    Single<List<Boolean>> toggleMulti(String path);

    /**
     * Returns type of element
     *
     * @return type of element
     */
    Single<JsonType> getType();

    /**
     * Returns type of element specified by JSONPath
     *
     * @param path JSON path
     * @return type of element
     */
    Single<JsonType> getType(String path);

    /**
     * Deletes JSON elements specified by JSONPath
     *
     * @param path JSON path
     * @return number of deleted elements
     */
    Single<Long> delete(String path);

}
