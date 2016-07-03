/**
 * Copyright 2016 Nikita Koksharov
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
package org.redisson.core;

import java.util.List;

import org.redisson.client.RedisException;
import org.redisson.client.codec.Codec;

import io.netty.util.concurrent.Future;

/**
 * Interface for using pipeline feature.
 * <p/>
 * All method invocations on objects
 * from this interface are batched to separate queue and could be executed later
 * with <code>execute()</code> or <code>executeAsync()</code> methods.
 * <p/>
 * Please be ware, atomicity <b>is not</b> guaranteed.
 *
 *
 * @author Nikita Koksharov
 *
 */
public interface RBatch {

    /**
     * Returns geospatial items holder instance by <code>name</code>.
     * 
     * @param name
     * @return
     */
    <V> RGeoAsync<V> getGeo(String name);

    /**
     * Returns geospatial items holder instance by <code>name</code>
     * using provided codec for geospatial members.
     *
     * @param name
     * @param geospatial member codec
     * @return
     */
    <V> RGeoAsync<V> getGeo(String name, Codec codec);
    
    /**
     * Returns Set based MultiMap instance by name.
     *
     * @param name
     * @return
     */
    <K, V> RMultimapAsync<K, V> getSetMultimap(String name);

    /**
     * Returns Set based MultiMap instance by name
     * using provided codec for both map keys and values.
     *
     * @param name
     * @param codec
     * @return
     */
    <K, V> RMultimapAsync<K, V> getSetMultimap(String name, Codec codec);
    
    /**
     * Returns Set based Multimap instance by name.
     * Supports key-entry eviction with a given TTL value.
     * 
     * <p>If eviction is not required then it's better to use regular map {@link #getSetMultimap(String)}.</p>
     * 
     * @param name
     * @return
     */
    <K, V> RMultimapCacheAsync<K, V> getSetMultimapCache(String name);

    /**
     * Returns Set based Multimap instance by name
     * using provided codec for both map keys and values.
     * Supports key-entry eviction with a given TTL value.
     * 
     * <p>If eviction is not required then it's better to use regular map {@link #getSetMultimap(String, Codec)}.</p>
     * 
     * @param name
     * @return
     */
    <K, V> RMultimapCacheAsync<K, V> getSetMultimapCache(String name, Codec codec);
    
    /**
     * Returns set-based cache instance by <code>name</code>.
     * Uses map (value_hash, value) under the hood for minimal memory consumption.
     * Supports value eviction with a given TTL value.
     *
     * <p>If eviction is not required then it's better to use regular map {@link #getSet(String, Codec)}.</p>
     *
     * @param name
     * @param codec
     * @return
     */
    <V> RSetCacheAsync<V> getSetCache(String name);

    /**
     * Returns set-based cache instance by <code>name</code>
     * using provided <code>codec</code> for values.
     * Uses map (value_hash, value) under the hood for minimal memory consumption.
     * Supports value eviction with a given TTL value.
     *
     * <p>If eviction is not required then it's better to use regular map {@link #getSet(String, Codec)}.</p>
     *
     * @param name
     * @param codec
     * @return
     */
    <V> RSetCacheAsync<V> getSetCache(String name, Codec codec);

    /**
     * Returns map-based cache instance by <code>name</code>
     * using provided <code>codec</code> for both cache keys and values.
     * Supports entry eviction with a given TTL value.
     *
     * <p>If eviction is not required then it's better to use regular map {@link #getMap(String, Codec)}.</p>
     *
     * @param name
     * @param codec
     * @return
     */
    <K, V> RMapCacheAsync<K, V> getMapCache(String name, Codec codec);

    /**
     * Returns map-based cache instance by <code>name</code>.
     * Supports entry eviction with a given TTL value.
     *
     * <p>If eviction is not required then it's better to use regular map {@link #getMap(String)}.</p>
     *
     * @param name
     * @return
     */
    <K, V> RMapCacheAsync<K, V> getMapCache(String name);

    /**
     * Returns object holder by <code>name</code>
     *
     * @param name of object
     * @return
     */
    <V> RBucketAsync<V> getBucket(String name);

    <V> RBucketAsync<V> getBucket(String name, Codec codec);

    /**
     * Returns HyperLogLog object
     *
     * @param name of object
     * @return
     */
    <V> RHyperLogLogAsync<V> getHyperLogLog(String name);

    <V> RHyperLogLogAsync<V> getHyperLogLog(String name, Codec codec);

    /**
     * Returns list instance by name.
     *
     * @param name of list
     * @return
     */
    <V> RListAsync<V> getList(String name);

    <V> RListAsync<V> getList(String name, Codec codec);

    /**
     * Returns List based MultiMap instance by name.
     *
     * @param name
     * @return
     */
    <K, V> RMultimapAsync<K, V> getListMultimap(String name);

    /**
     * Returns List based MultiMap instance by name
     * using provided codec for both map keys and values.
     *
     * @param name
     * @param codec
     * @return
     */
    <K, V> RMultimapAsync<K, V> getListMultimap(String name, Codec codec);
    
    /**
     * Returns List based Multimap instance by name.
     * Supports key-entry eviction with a given TTL value.
     * 
     * <p>If eviction is not required then it's better to use regular map {@link #getSetMultimap(String)}.</p>
     * 
     * @param name
     * @return
     */
    <K, V> RMultimapAsync<K, V> getListMultimapCache(String name);
    
    /**
     * Returns List based Multimap instance by name
     * using provided codec for both map keys and values.
     * Supports key-entry eviction with a given TTL value.
     * 
     * <p>If eviction is not required then it's better to use regular map {@link #getSetMultimap(String, Codec)}.</p>
     * 
     * @param name
     * @return
     */
    <K, V> RMultimapAsync<K, V> getListMultimapCache(String name, Codec codec);
    
    /**
     * Returns map instance by name.
     *
     * @param name of map
     * @return
     */
    <K, V> RMapAsync<K, V> getMap(String name);

    <K, V> RMapAsync<K, V> getMap(String name, Codec codec);

    /**
     * Returns set instance by name.
     *
     * @param name of set
     * @return
     */
    <V> RSetAsync<V> getSet(String name);

    <V> RSetAsync<V> getSet(String name, Codec codec);

    /**
     * Returns topic instance by name.
     *
     * @param name of topic
     * @return
     */
    <M> RTopicAsync<M> getTopic(String name);

    <M> RTopicAsync<M> getTopic(String name, Codec codec);

    /**
     * Returns queue instance by name.
     *
     * @param name of queue
     * @return
     */
    <V> RQueueAsync<V> getQueue(String name);

    <V> RQueueAsync<V> getQueue(String name, Codec codec);

    /**
     * Returns blocking queue instance by name.
     *
     * @param name of queue
     * @return
     */
    <V> RBlockingQueueAsync<V> getBlockingQueue(String name);

    <V> RBlockingQueueAsync<V> getBlockingQueue(String name, Codec codec);

    /**
     * Returns deque instance by name.
     *
     * @param name of deque
     * @return
     */
    <V> RDequeAsync<V> getDeque(String name);

    <V> RDequeAsync<V> getDeque(String name, Codec codec);

    /**
     * Returns blocking deque instance by name.
     *
     * @param name of queue
     * @return
     */
    <V> RBlockingDequeAsync<V> getBlockingDeque(String name);

    <V> RBlockingDequeAsync<V> getBlockingDeque(String name, Codec codec);

    /**
     * Returns atomicLong instance by name.
     *
     * @param name
     * @return
     */
    RAtomicLongAsync getAtomicLong(String name);

    /**
     * Returns atomicDouble instance by name.
     *
     * @param name
     * @return
     */
    RAtomicDoubleAsync getAtomicDouble(String name);

    /**
     * Returns Redis Sorted Set instance by name
     *
     * @param name
     * @return
     */
    <V> RScoredSortedSetAsync<V> getScoredSortedSet(String name);

    <V> RScoredSortedSetAsync<V> getScoredSortedSet(String name, Codec codec);

    /**
     * Returns String based Redis Sorted Set instance by name
     * All elements are inserted with the same score during addition,
     * in order to force lexicographical ordering
     *
     * @param name
     * @return
     */
    RLexSortedSetAsync getLexSortedSet(String name);

    RBitSetAsync getBitSet(String name);

    /**
     * Returns script operations object
     *
     * @return
     */
    RScriptAsync getScript();

    /**
     * Returns keys operations.
     * Each of Redis/Redisson object associated with own key
     *
     * @return
     */
    RKeysAsync getKeys();

    /**
     * Executes all operations accumulated during async methods invocations.
     *
     * If cluster configuration used then operations are grouped by slot ids
     * and may be executed on different servers. Thus command execution order could be changed
     *
     * @return List with result object for each command
     * @throws RedisException in case of any error
     *
     */
    List<?> execute() throws RedisException;

    /**
     * Executes all operations accumulated during async methods invocations asynchronously.
     *
     * In cluster configurations operations grouped by slot ids
     * so may be executed on different servers. Thus command execution order could be changed
     *
     * @return List with result object for each command
     */
    Future<List<?>> executeAsync();

}
