/**
 * Copyright (c) 2013-2022 Nikita Koksharov
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
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Redis JSON datatype holder. Data is stored as JSON object in Redis
 *
 * @author Nikita Koksharov
 * @param <V> the type of object
 */
public interface RJsonBucketReactive<V> extends RBucketReactive<V> {

    /**
     * Get Json object/objects by JSONPath
     *
     * @param codec object codec
     * @param paths JSON paths
     * @return object
     *
     * @param <T> the type of object
     */
    <T> Mono<T> get(JsonCodec<T> codec, String... paths);

    /**
     * Sets Json object by JSONPath only if previous value is empty
     *
     * @param path JSON path
     * @param value object
     * @return {@code true} if successful, or {@code false} if
     *         value was already set
     */
    Mono<Boolean> setIfAbsent(String path, Object value);

    /**
     * Use {@link #setIfAbsent(String, Object)} instead
     *
     * @param path JSON path
     * @param value object
     * @return {@code true} if successful, or {@code false} if
     *         value was already set
     */
    @Deprecated
    Mono<Boolean> trySet(String path, Object value);

    /**
     * Sets Json object by JSONPath only if previous value is non-empty
     *
     * @param path JSON path
     * @param value object
     * @return {@code true} if successful, or {@code false} if
     *         element wasn't set
     */
    Mono<Boolean> setIfExists(String path, Object value);

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
    Mono<Boolean> compareAndSet(String path, Object expect, Object update);

    /**
     * Retrieves current value of element specified by JSONPath
     * and replaces it with <code>newValue</code>.
     *
     * @param codec object codec
     * @param path JSON path
     * @param newValue value to set
     * @return previous value
     */
    <T> Mono<T> getAndSet(JsonCodec<T> codec, String path, Object newValue);

    /**
     * Stores object into element by specified JSONPath.
     *
     * @param path JSON path
     * @param value value to set
     * @return void
     */
    Mono<Void> set(String path, Object value);

    /**
     * Returns size of string data by JSONPath
     *
     * @param path JSON path
     * @return size of string
     */
    Mono<Long> stringSize(String path);

    /**
     * Returns list of string data size by JSONPath.
     * Compatible only with enhanced syntax starting with '$' character.
     *
     * @param path JSON path
     * @return list of string data sizes
     */
    Mono<List<Long>> stringSizeMulti(String path);

    /**
     * Appends string data to element specified by JSONPath.
     * Returns new size of string data.
     *
     * @param path JSON path
     * @param value data
     * @return size of string data
     */
    Mono<Long> stringAppend(String path, Object value);

    /**
     * Appends string data to elements specified by JSONPath.
     * Returns new size of string data.
     * Compatible only with enhanced syntax starting with '$' character.
     *
     * @param path JSON path
     * @param value data
     * @return list of string data sizes
     */
    Mono<List<Long>> stringAppendMulti(String path, Object value);

    /**
     * Appends values to array specified by JSONPath.
     * Returns new size of array.
     *
     * @param path JSON path
     * @param values values to append
     * @return size of array
     */
    Mono<Long> arrayAppend(String path, Object... values);

    /**
     * Appends values to arrays specified by JSONPath.
     * Returns new size of arrays.
     * Compatible only with enhanced syntax starting with '$' character.
     *
     * @param path JSON path
     * @param values values to append
     * @return list of arrays size
     */
    Mono<List<Long>> arrayAppendMulti(String path, Object... values);

    /**
     * Returns index of object in array specified by JSONPath.
     * -1 means object not found.
     *
     * @param path JSON path
     * @param value value to search
     * @return index in array
     */
    Mono<Long> arrayIndex(String path, Object value);

    /**
     * Returns index of object in arrays specified by JSONPath.
     * -1 means object not found.
     * Compatible only with enhanced syntax starting with '$' character.
     *
     * @param path JSON path
     * @param value value to search
     * @return list of index in arrays
     */
    Mono<List<Long>> arrayIndexMulti(String path, Object value);

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
    Mono<Long> arrayIndex(String path, Object value, Mono<Long> start, Mono<Long> end);

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
    Mono<List<Long>> arrayIndexMulti(String path, Object value, Mono<Long> start, Mono<Long> end);

    /**
     * Inserts values into array specified by JSONPath.
     * Values are inserted at defined <code>index</code>.
     *
     * @param path JSON path
     * @param index array index at which values are inserted
     * @param values values to insert
     * @return size of array
     */
    Mono<Long> arrayInsert(String path, Mono<Long> index, Object... values);

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
    Mono<List<Long>> arrayInsertMulti(String path, Mono<Long> index, Object... values);

    /**
     * Returns size of array specified by JSONPath.
     *
     * @param path JSON path
     * @return size of array
     */
    Mono<Long> arraySize(String path);

    /**
     * Returns size of arrays specified by JSONPath.
     * Compatible only with enhanced syntax starting with '$' character.
     *
     * @param path JSON path
     * @return list of arrays size
     */
    Mono<List<Long>> arraySizeMulti(String path);

    /**
     * Polls last element of array specified by JSONPath.
     *
     * @param codec object codec
     * @param path JSON path
     * @return last element
     *
     * @param <T> the type of object
     */
    <T> Mono<T> arrayPollLast(JsonCodec<T> codec, String path);

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
    <T> Mono<List<T>> arrayPollLastMulti(JsonCodec<T> codec, String path);

    /**
     * Polls first element of array specified by JSONPath.
     *
     * @param codec object codec
     * @param path JSON path
     * @return first element
     *
     * @param <T> the type of object
     */
    <T> Mono<T> arrayPollFirst(JsonCodec<T> codec, String path);

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
    <T> Mono<List<T>> arrayPollFirstMulti(JsonCodec<T> codec, String path);

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
    <T> Mono<T> arrayPop(JsonCodec<T> codec, String path, Mono<Long> index);

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
    <T> Mono<List<T>> arrayPopMulti(JsonCodec<T> codec, String path, Mono<Long> index);

    /**
     * Trims array specified by JSONPath in range
     * between <code>start</code> (inclusive) and <code>end</code> (inclusive) indexes.
     *
     * @param path JSON path
     * @param start start index, inclusive
     * @param end end index, inclusive
     * @return length of array
     */
    Mono<Long> arrayTrim(String path, Mono<Long> start, Mono<Long> end);

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
    Mono<List<Long>> arrayTrimMulti(String path, Mono<Long> start, Mono<Long> end);

    /**
     * Clears json container.
     *
     * @return number of cleared containers
     */
    Mono<Long> clear();

    /**
     * Clears json container specified by JSONPath.
     * Compatible only with enhanced syntax starting with '$' character.
     *
     * @param path JSON path
     * @return number of cleared containers
     */
    Mono<Long> clear(String path);

    /**
     * Increments the current value specified by JSONPath by <code>delta</code>.
     *
     * @param path JSON path
     * @param delta increment value
     * @return the updated value
     */
    <T extends Number> Mono<T> incrementAndGet(String path, Mono<T> delta);

    /**
     * Increments the current values specified by JSONPath by <code>delta</code>.
     * Compatible only with enhanced syntax starting with '$' character.
     *
     * @param path JSON path
     * @param delta increment value
     * @return list of updated value
     */
    <T extends Number> Mono<List<T>> incrementAndGetMulti(String path, Mono<T> delta);

    /**
     * Returns keys amount in JSON container
     *
     * @return keys amount
     */
    Mono<Long> countKeys();

    /**
     * Returns keys amount in JSON container specified by JSONPath
     *
     * @param path JSON path
     * @return keys amount
     */
    Mono<Long> countKeys(String path);

    /**
     * Returns list of keys amount in JSON containers specified by JSONPath
     *
     * @param path JSON path
     * @return list of keys amount
     */
    Mono<List<Long>> countKeysMulti(String path);

    /**
     * Returns list of keys in JSON container
     *
     * @return list of keys
     */
    Mono<List<String>> getKeys();

    /**
     * Returns list of keys in JSON container specified by JSONPath
     *
     * @param path JSON path
     * @return list of keys
     */
    Mono<List<String>> getKeys(String path);

    /**
     * Returns list of keys in JSON containers specified by JSONPath
     *
     * @param path JSON path
     * @return list of keys
     */
    Mono<List<List<String>>> getKeysMulti(String path);

    /**
     * Toggle Mono<Boolean> value specified by JSONPath
     *
     * @param path JSON path
     * @return new Mono<Boolean> value
     */
    Mono<Boolean> toggle(String path);

    /**
     * Toggle Mono<Boolean> values specified by JSONPath
     *
     * @param path JSON path
     * @return list of Mono<Boolean> values
     */
    Mono<List<Boolean>> toggleMulti(String path);

    /**
     * Returns type of element
     *
     * @return type of element
     */
    Mono<JsonType> getType();

    /**
     * Returns type of element specified by JSONPath
     *
     * @param path JSON path
     * @return type of element
     */
    Mono<JsonType> getType(String path);

    /**
     * Deletes JSON elements specified by JSONPath
     *
     * @param path JSON path
     * @return number of deleted elements
     */
    Mono<Long> delete(String path);

}
