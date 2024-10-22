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
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * JSON Store which stores each entry as key and value. Both are POJO objects.
 * Value is stored as JSON datatype in Redis.
 * <p>
 * The implementation is available in Redisson PRO only.
 *
 * @author Nikita Koksharov
 *
 * @param <K> the type of key
 * @param <V> the type of value
 *
 */
public interface RJsonStoreReactive<K, V> extends RExpirableReactive {

    /**
     * Gets value by specified key and JSONPath
     *
     * @param key   entry key
     * @param codec entry value codec
     * @param paths JSON paths
     * @param <T>   the type of object
     * @return entry value
     */
    <T> Mono<T> get(K key, JsonCodec codec, String... paths);

    /**
     * Sets value by specified key and JSONPath only if previous value is empty.
     *
     * @param key entry key
     * @param path JSON path
     * @param value entry value
     * @return {@code true} if successful, or {@code false} if
     *         value was already set
     */
    Mono<Boolean> setIfAbsent(K key, String path, Object value);

    /**
     * Sets value by specified key and JSONPath only if previous value is non-empty.
     *
     * @param key entry key
     * @param path JSON path
     * @param value object
     * @return {@code true} if successful, or {@code false} if
     *         element wasn't set
     */
    Mono<Boolean> setIfExists(K key, String path, Object value);

    /**
     * Atomically sets the value to the given updated value
     * by specified key and JSONPath, only if serialized state of
     * the current value equals to serialized state of the expected value.
     *
     * @param key entry key
     * @param path JSON path
     * @param expect the expected value
     * @param update the new value
     * @return {@code true} if successful; or {@code false} if the actual value
     *         was not equal to the expected value.
     */
    Mono<Boolean> compareAndSet(K key, String path, Object expect, Object update);

    /**
     * Retrieves current value stored by specified key and JSONPath then
     * replaces it with new value.
     *
     * @param key entry key
     * @param codec entry value codec
     * @param path JSON path
     * @param newValue value to set
     * @return previous value
     */
    <T> Mono<T> getAndSet(K key, JsonCodec codec, String path, Object newValue);

    /**
     * Stores value by specified key and JSONPath.
     *
     * @param key entry key
     * @param path JSON path
     * @param value value to set
     */
    Mono<Void> set(K key, String path, Object value);

    /**
     * Returns size of string data by specified key and JSONPath
     *
     * @param key entry key
     * @param path JSON path
     * @return size of string
     */
    Mono<Long> stringSize(K key, String path);

    /**
     * Returns list of string data size by specified key and JSONPath.
     * Compatible only with enhanced syntax starting with '$' character.
     *
     * @param key entry key
     * @param path JSON path
     * @return list of string data sizes
     */
    Mono<List<Long>> stringSizeMulti(K key, String path);

    /**
     * Appends string data to element specified by specified key and JSONPath.
     * Returns new size of string data.
     *
     * @param key entry key
     * @param path JSON path
     * @param value data
     * @return size of string data
     */
    Mono<Long> stringAppend(K key, String path, Object value);

    /**
     * Appends string data to elements specified by specified key and JSONPath.
     * Returns new size of string data.
     * Compatible only with enhanced syntax starting with '$' character.
     *
     * @param key entry key
     * @param path JSON path
     * @param value data
     * @return list of string data sizes
     */
    Mono<List<Long>> stringAppendMulti(K key, String path, Object value);

    /**
     * Appends values to array by specified key and JSONPath.
     * Returns new size of array.
     *
     * @param key entry key
     * @param path JSON path
     * @param values values to append
     * @return size of array
     */
    Mono<Long> arrayAppend(K key, String path, Object... values);

    /**
     * Appends values to arrays by specified key and JSONPath.
     * Returns new size of arrays.
     * Compatible only with enhanced syntax starting with '$' character.
     *
     * @param key entry key
     * @param path JSON path
     * @param values values to append
     * @return list of arrays size
     */
    Mono<List<Long>> arrayAppendMulti(K key, String path, Object... values);

    /**
     * Returns index of object in array by specified key and JSONPath.
     * -1 means object not found.
     *
     * @param key entry key
     * @param path JSON path
     * @param value value to search
     * @return index in array
     */
    Mono<Long> arrayIndex(K key, String path, Object value);

    /**
     * Returns index of object in arrays by specified key and JSONPath.
     * -1 means object not found.
     * Compatible only with enhanced syntax starting with '$' character.
     *
     * @param key entry key
     * @param path JSON path
     * @param value value to search
     * @return list of index in arrays
     */
    Mono<List<Long>> arrayIndexMulti(K key, String path, Object value);

    /**
     * Returns index of object in array by specified key and JSONPath
     * in range between <code>start</code> (inclusive) and <code>end</code> (exclusive) indexes.
     * -1 means object not found.
     *
     * @param key entry key
     * @param path JSON path
     * @param value value to search
     * @param start start index, inclusive
     * @param end end index, exclusive
     * @return index in array
     */
    Mono<Long> arrayIndex(K key, String path, Object value, Long start, Long end);

    /**
     * Returns index of object in arrays by specified key and JSONPath
     * in range between <code>start</code> (inclusive) and <code>end</code> (exclusive) indexes.
     * -1 means object not found.
     * Compatible only with enhanced syntax starting with '$' character.
     *
     * @param key entry key
     * @param path JSON path
     * @param value value to search
     * @param start start index, inclusive
     * @param end end index, exclusive
     * @return list of index in arrays
     */
    Mono<List<Long>> arrayIndexMulti(K key, String path, Object value, Long start, Long end);

    /**
     * Inserts values into array by specified key and JSONPath.
     * Values are inserted at defined <code>index</code>.
     *
     * @param key entry key
     * @param path JSON path
     * @param index array index at which values are inserted
     * @param values values to insert
     * @return size of array
     */
    Mono<Long> arrayInsert(K key, String path, Long index, Object... values);

    /**
     * Inserts values into arrays by specified key and JSONPath.
     * Values are inserted at defined <code>index</code>.
     * Compatible only with enhanced syntax starting with '$' character.
     *
     * @param key entry key
     * @param path JSON path
     * @param index array index at which values are inserted
     * @param values values to insert
     * @return list of arrays size
     */
    Mono<List<Long>> arrayInsertMulti(K key, String path, Long index, Object... values);

    /**
     * Returns size of array by specified key and JSONPath.
     *
     * @param key entry key
     * @param path JSON path
     * @return size of array
     */
    Mono<Long> arraySize(K key, String path);

    /**
     * Returns size of arrays by specified key and JSONPath.
     * Compatible only with enhanced syntax starting with '$' character.
     *
     * @param key entry key
     * @param path JSON path
     * @return list of arrays size
     */
    Mono<List<Long>> arraySizeMulti(K key, String path);

    /**
     * Polls last element of array by specified key and JSONPath.
     *
     * @param key entry key
     * @param codec object codec
     * @param path JSON path
     * @return last element
     *
     * @param <T> the type of object
     */
    <T> Mono<T> arrayPollLast(K key, JsonCodec codec, String path);

    /**
     * Polls last element of arrays by specified key and JSONPath.
     * Compatible only with enhanced syntax starting with '$' character.
     *
     * @param key entry key
     * @param codec object codec
     * @param path JSON path
     * @return list of last elements
     *
     * @param <T> the type of object
     */
    <T> Mono<List<T>> arrayPollLastMulti(K key, JsonCodec codec, String path);

    /**
     * Polls first element of array by specified key and JSONPath.
     *
     * @param key entry key
     * @param codec object codec
     * @param path JSON path
     * @return first element
     *
     * @param <T> the type of object
     */
    <T> Mono<T> arrayPollFirst(K key, JsonCodec codec, String path);

    /**
     * Polls first element of arrays by specified key and JSONPath.
     * Compatible only with enhanced syntax starting with '$' character.
     *
     * @param key entry key
     * @param codec object codec
     * @param path JSON path
     * @return list of first elements
     *
     * @param <T> the type of object
     */
    <T> Mono<List<T>> arrayPollFirstMulti(K key, JsonCodec codec, String path);

    /**
     * Pops element located at index of array by specified key and JSONPath.
     *
     * @param key entry key
     * @param codec object codec
     * @param path JSON path
     * @param index array index
     * @return element
     *
     * @param <T> the type of object
     */
    <T> Mono<T> arrayPop(K key, JsonCodec codec, String path, Long index);

    /**
     * Pops elements located at index of arrays by specified key and JSONPath.
     * Compatible only with enhanced syntax starting with '$' character.
     *
     * @param key entry key
     * @param codec object codec
     * @param path JSON path
     * @param index array index
     * @return list of elements
     *
     * @param <T> the type of object
     */
    <T> Mono<List<T>> arrayPopMulti(K key, JsonCodec codec, String path, Long index);

    /**
     * Trims array by specified key and JSONPath in range
     * between <code>start</code> (inclusive) and <code>end</code> (inclusive) indexes.
     *
     * @param key entry key
     * @param path JSON path
     * @param start start index, inclusive
     * @param end end index, inclusive
     * @return length of array
     */
    Mono<Long> arrayTrim(K key, String path, Long start, Long end);

    /**
     * Trims arrays by specified key and JSONPath in range
     * between <code>start</code> (inclusive) and <code>end</code> (inclusive) indexes.
     * Compatible only with enhanced syntax starting with '$' character.
     *
     * @param key entry key
     * @param path JSON path
     * @param start start index, inclusive
     * @param end end index, inclusive
     * @return length of array
     */
    Mono<List<Long>> arrayTrimMulti(K key, String path, Long start, Long end);

    /**
     * Clears value by specified key
     *
     * @param key entry key
     * @return {@code true} if successful, or {@code false} if
     *         entry doesn't exist
     */
    Mono<Boolean> clear(K key);

    /**
     * Clears json containers by specified keys.
     *
     * @param keys entry keys
     * @return number of cleared containers
     */
    Mono<Long> clear(Set<K> keys);

    /**
     * Clears json container by specified keys and JSONPath.
     * Compatible only with enhanced syntax starting with '$' character.
     *
     * @param path JSON path
     * @param keys entry keys
     * @return number of cleared containers
     */
    Mono<Long> clear(String path, Set<K> keys);

    /**
     * Increments the current value specified by key and JSONPath.
     *
     * @param key entry key
     * @param path JSON path
     * @param delta increment value
     * @return the updated value
     */
    <T extends Number> Mono<T> incrementAndGet(K key, String path, T delta);

    /**
     * Increments the current values specified by key and JSONPath.
     * Compatible only with enhanced syntax starting with '$' character.
     *
     * @param key entry key
     * @param path JSON path
     * @param delta increment value
     * @return list of updated value
     */
    <T extends Number> Mono<List<T>> incrementAndGetMulti(K key, String path, T delta);

    /**
     * Merges value into element by the specified key and JSONPath.
     *
     * @param key entry key
     * @param path JSON path
     * @param value value to merge
     */
    Mono<Void> merge(K key, String path, Object value);

    /**
     * Returns keys amount in JSON container by specified key
     *
     * @param key entry key
     * @return keys amount
     */
    Mono<Long> countKeys(K key);

    /**
     * Returns keys amount in JSON container specified by key and JSONPath
     *
     * @param key entry key
     * @param path JSON path
     * @return keys amount
     */
    Mono<Long> countKeys(K key, String path);

    /**
     * Returns list of keys amount in JSON containers specified by key and JSONPath
     *
     * @param key entry key
     * @param path JSON path
     * @return list of keys amount
     */
    Mono<List<Long>> countKeysMulti(K key, String path);

    /**
     * Returns list of keys in JSON container by specified key
     *
     * @return list of keys
     */
    Mono<List<String>> getKeys(K key);

    /**
     * Returns list of keys in JSON container by specified key and JSONPath
     *
     * @param path JSON path
     * @return list of keys
     */
    Mono<List<String>> getKeys(K key, String path);

    /**
     * Returns list of keys in JSON containers by specified key and JSONPath
     *
     * @param path JSON path
     * @return list of keys
     */
    List<Mono<List<String>>> getKeysMulti(K key, String path);

    /**
     * Toggle Mono<Boolean> value by specified key and JSONPath
     *
     * @param path JSON path
     * @return new Mono<Boolean> value
     */
    Mono<Boolean> toggle(K key, String path);

    /**
     * Toggle Mono<Boolean> values by specified key and JSONPath
     *
     * @param path JSON path
     * @return list of Mono<Boolean> values
     */
    List<Mono<Boolean>> toggleMulti(K key, String path);

    /**
     * Returns type of value
     *
     * @return type of element
     */
    Mono<JsonType> getType(K key);

    /**
     * Returns type of element specified by key and JSONPath
     *
     * @param path JSON path
     * @return type of element
     */
    Mono<JsonType> getType(K key, String path);

    /**
     * Deletes entry by specified key
     *
     * @param key entry key
     * @return {@code true} if successful, or {@code false} if
     *         entry doesn't exist
     */
    Mono<Boolean> delete(K key);

    /**
     * Deletes entries by specified keys
     *
     * @param keys entry keys
     * @return number of deleted elements
     */
    Mono<Long> delete(Set<K> keys);

    /**
     * Deletes JSON elements specified by keys and JSONPath
     *
     * @param path JSON path
     * @param keys entry keys
     * @return number of deleted elements
     */
    Mono<Long> delete(String path, Set<K> keys);

    /**
     * Returns size of entry in bytes specified by key.
     *
     * @param key entry key
     * @return entry size
     */
    Mono<Long> sizeInMemory(K key);

    /**
     * Retrieves value by specified key.
     *
     * @param key entry key
     * @return element
     */
    Mono<V> get(K key);

    /**
     * Retrieves values by specified keys.
     *
     * @param keys entry keys
     * @return map with entries where value mapped by key
     */
    Mono<Map<K, V>> get(Set<K> keys);

    /**
     * Retrieves values by specified keys and JSONPath.
     *
     * @param path JSON path
     * @param keys entry keys
     * @return map with entries where value mapped by key
     */
    Mono<Map<K, V>> get(String path, Set<K> keys);

    /**
     * Retrieves entry value by specified key and removes it.
     *
     * @param key entry key
     * @return element
     */
    Mono<V> getAndDelete(K key);

    /**
     * Sets value only if entry doesn't exist.
     *
     * @param key entry key
     * @param value value to set
     * @return {@code true} if successful, or {@code false} if
     *         element was already set
     */
    Mono<Boolean> setIfAbsent(K key, V value);

    /**
     * Sets value with defined duration only if entry doesn't exist.
     *
     * @param key entry key
     * @param value value to set
     * @param duration expiration duration
     * @return {@code true} if successful, or {@code false} if
     *         element was already set
     */
    Mono<Boolean> setIfAbsent(K key, V value, Duration duration);

    /**
     * Sets value only if entry already exists.
     *
     * @param key entry key
     * @param value value to set
     * @return {@code true} if successful, or {@code false} if
     *         element wasn't set
     */
    Mono<Boolean> setIfExists(K key, V value);

    /**
     * Sets <code>value</code> with expiration <code>duration</code> only if entry already exists.
     *
     * @param key entry key
     * @param value value to set
     * @param duration expiration duration
     * @return {@code true} if successful, or {@code false} if
     *         element wasn't set
     */
    Mono<Boolean> setIfExists(K key, V value, Duration duration);

    /**
     * Atomically sets the value to the given updated value
     * by specified key only if serialized state of the current value equals
     * to serialized state of the expected value.
     *
     * @param key entry key
     * @param expect the expected value
     * @param update the new value
     * @return {@code true} if successful; or {@code false} if the actual value
     *         was not equal to the expected value.
     */
    Mono<Boolean> compareAndSet(K key, V expect, V update);

    /**
     * Retrieves current value by specified key and replaces it with new value.
     *
     * @param key entry key
     * @param newValue value to set
     * @return previous value
     */
    Mono<V> getAndSet(K key, V newValue);

    /**
     * Retrieves current value by specified key and replaces it
     * with value and defines expiration <code>duration</code>.
     *
     * @param key entry key
     * @param value value to set
     * @param duration expiration duration
     * @return previous value
     */
    Mono<V> getAndSet(K key, V value, Duration duration);

    /**
     * Retrieves current value by specified key and sets an expiration duration for it.
     * <p>
     * Requires <b>Redis 6.2.0 and higher.</b>
     *
     * @param key entry key
     * @param duration of object time to live interval
     * @return value
     */
    Mono<V> getAndExpire(K key, Duration duration);

    /**
     * Retrieves current value by specified key and sets an expiration date for it.
     * <p>
     * Requires <b>Redis 6.2.0 and higher.</b>
     *
     * @param key entry key
     * @param time of exact object expiration moment
     * @return value
     */
    Mono<V> getAndExpire(K key, Instant time);

    /**
     * Retrieves current value by specified key and clears expiration date set before.
     * <p>
     * Requires <b>Redis 6.2.0 and higher.</b>
     *
     * @param key entry key
     * @return value
     */
    Mono<V> getAndClearExpire(K key);

    /**
     * Stores value by specified key.
     *
     * @param key entry key
     * @param value value to set
     */
    Mono<Void> set(K key, V value);

    /**
     * Stores values by specified keys.
     *
     * @param entries entries to store
     */
    Mono<Void> set(Map<K, V> entries);

    /**
     * Stores values by specified keys and JSONPath.
     *
     * @param path JSONPath
     * @param entries entries to store
     */
    Mono<Void> set(String path, Map<K, V> entries);

    /**
     * Stores value by specified key with defined expiration duration.
     *
     * @param key entry key
     * @param value value to set
     * @param duration expiration duration
     */
    Mono<Void> set(K key, V value, Duration duration);

    /**
     * Stores values by specified keys with defined expiration duration.
     *
     * @param entries entries to store
     * @param duration expiration duration
     */
    Mono<Void> set(Map<K, V> entries, Duration duration);

    /**
     * Sets value by specified key and keep existing TTL.
     * <p>
     * Requires <b>Redis 6.0.0 and higher.</b>
     *
     * @param value value to set
     */
    Mono<Void> setAndKeepTTL(K key, V value);

    /**
     * Adds object event listener
     *
     * @see ExpiredObjectListener
     * @see DeletedObjectListener
     * @see org.redisson.api.listener.SetObjectListener
     *
     * @param listener object event listener
     * @return listener id
     */
    Mono<Integer> addListener(ObjectListener listener);

    /**
     * Remaining time to live of map entry associated with a <code>key</code>.
     *
     * @param key - map key
     * @return time in milliseconds
     *          -2 if the key does not exist.
     *          -1 if the key exists but has no associated expire.
     */
    Mono<Long> remainTimeToLive(K key);

    /**
     * Returns <code>true</code> if this map contains map entry
     * mapped by specified <code>key</code>, otherwise <code>false</code>
     *
     * @param key - map key
     * @return <code>true</code> if this map contains map entry
     *          mapped by specified <code>key</code>, otherwise <code>false</code>
     */
    Mono<Boolean> containsKey(Object key);

    /**
     * Read all keys at once
     *
     * @return keys
     */
    Mono<Set<K>> readAllKeySet();

    /**
     * Returns entries amount in store
     *
     * @return entries amount
     */
    Mono<Integer> size();

    /**
     * Returns <code>RCountDownLatch</code> instance associated with key
     *
     * @param key - map key
     * @return countdownlatch
     */
    RCountDownLatchReactive getCountDownLatch(K key);

    /**
     * Returns <code>RPermitExpirableSemaphore</code> instance associated with key
     *
     * @param key - map key
     * @return permitExpirableSemaphore
     */
    RPermitExpirableSemaphoreReactive getPermitExpirableSemaphore(K key);

    /**
     * Returns <code>RSemaphore</code> instance associated with key
     *
     * @param key - map key
     * @return semaphore
     */
    RSemaphoreReactive getSemaphore(K key);

    /**
     * Returns <code>RLock</code> instance associated with key
     *
     * @param key - map key
     * @return fairlock
     */
    RLockReactive getFairLock(K key);

    /**
     * Returns <code>RReadWriteLock</code> instance associated with key
     *
     * @param key - map key
     * @return readWriteLock
     */
    RReadWriteLockReactive getReadWriteLock(K key);

    /**
     * Returns <code>RLock</code> instance associated with key
     *
     * @param key - map key
     * @return lock
     */
    RLockReactive getLock(K key);

}
