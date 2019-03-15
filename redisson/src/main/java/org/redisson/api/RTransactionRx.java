/**
 * Copyright (c) 2013-2019 Nikita Koksharov
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

import org.redisson.client.codec.Codec;

import io.reactivex.Completable;

/**
 * RxJava2 interface for transaction object allows to execute transactions over Redisson objects.
 * Uses locks for write operations and maintains data modification operations list till the commit/rollback operation.
 * <p>
 * Transaction isolation level: <b>READ_COMMITTED</b>
 *
 * @author Nikita Koksharov
 *
 */
public interface RTransactionRx {

    /**
     * Returns transactional object holder instance by name.
     *
     * @param <V> type of value
     * @param name - name of object
     * @return Bucket object
     */
    <V> RBucketRx<V> getBucket(String name);
    
    /**
     * Returns transactional object holder instance by name
     * using provided codec for object.
     *
     * @param <V> type of value
     * @param name - name of object
     * @param codec - codec for values
     * @return Bucket object
     */
    <V> RBucketRx<V> getBucket(String name, Codec codec);

    /**
     * Returns transactional map instance by name.
     *
     * @param <K> type of key
     * @param <V> type of value
     * @param name - name of object
     * @return Map object
     */
    <K, V> RMapRx<K, V> getMap(String name);

    /**
     * Returns transactional map instance by name
     * using provided codec for both map keys and values.
     *
     * @param <K> type of key
     * @param <V> type of value
     * @param name - name of object
     * @param codec - codec for keys and values
     * @return Map object
     */
    <K, V> RMapRx<K, V> getMap(String name, Codec codec);
    
    /**
     * Returns transactional set instance by name.
     * 
     * @param <V> type of value
     * @param name - name of object
     * @return Set object
     */
    <V> RSetRx<V> getSet(String name);
    
    /**
     * Returns transactional set instance by name
     * using provided codec for set objects.
     * 
     * @param <V> type of value
     * @param name - name of object
     * @param codec - codec for values
     * @return Set object
     */
    <V> RSetRx<V> getSet(String name, Codec codec);
    
    /**
     * Returns transactional set-based cache instance by <code>name</code>.
     * Supports value eviction with a given TTL value.
     *
     * <p>If eviction is not required then it's better to use regular map {@link #getSet(String)}.</p>
     * 
     * @param <V> type of value
     * @param name - name of object
     * @return SetCache object
     */
    <V> RSetCacheRx<V> getSetCache(String name);
    
    /**
     * Returns transactional set-based cache instance by <code>name</code>.
     * Supports value eviction with a given TTL value.
     *
     * <p>If eviction is not required then it's better to use regular map {@link #getSet(String, Codec)}.</p>
     * 
     * @param <V> type of value
     * @param name - name of object
     * @param codec - codec for values
     * @return SetCache object
     */
    <V> RSetCacheRx<V> getSetCache(String name, Codec codec);
    
    /**
     * Returns transactional map-based cache instance by name.
     * Supports entry eviction with a given MaxIdleTime and TTL settings.
     * <p>
     * If eviction is not required then it's better to use regular map {@link #getMap(String)}.</p>
     *
     * @param <K> type of key
     * @param <V> type of value
     * @param name - name of object
     * @return MapCache object
     */
    <K, V> RMapCacheRx<K, V> getMapCache(String name);

    /**
     * Returns transactional map-based cache instance by <code>name</code>
     * using provided <code>codec</code> for both cache keys and values.
     * Supports entry eviction with a given MaxIdleTime and TTL settings.
     * <p>
     * If eviction is not required then it's better to use regular map {@link #getMap(String, Codec)}.
     *
     * @param <K> type of key
     * @param <V> type of value
     * @param name - object name
     * @param codec - codec for keys and values
     * @return MapCache object
     */
    <K, V> RMapCacheRx<K, V> getMapCache(String name, Codec codec);
    
    /**
     * Commits all changes made on this transaction.
     * 
     * @return void
     */
    Completable commit();
    
    /**
     * Rollback all changes made on this transaction.
     * @return void
     */
    Completable rollback();

}
