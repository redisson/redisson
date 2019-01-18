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

import java.util.concurrent.TimeUnit;

import org.redisson.client.codec.Codec;

import reactor.core.publisher.Mono;

/**
 * Reactive interface for Redis pipeline feature.
 * <p>
 * All method invocations on objects
 * from this interface are batched to separate queue and could be executed later
 * with <code>execute()</code> method.
 *
 *
 * @author Nikita Koksharov
 *
 */
public interface RBatchReactive {

    /**
     * Returns stream instance by <code>name</code>
     * <p>
     * Requires <b>Redis 5.0.0 and higher.</b>
     * 
     * @param <K> type of key
     * @param <V> type of value
     * @param name of stream
     * @return RStream object
     */
    <K, V> RStreamReactive<K, V> getStream(String name);
    
    /**
     * Returns stream instance by <code>name</code>
     * using provided <code>codec</code> for entries.
     * <p>
     * Requires <b>Redis 5.0.0 and higher.</b>
     * 
     * @param <K> type of key
     * @param <V> type of value
     * @param name - name of stream
     * @param codec - codec for entry
     * @return RStream object
     */
    <K, V> RStreamReactive<K, V> getStream(String name, Codec codec);
    
    /**
     * Returns geospatial items holder instance by <code>name</code>.
     * 
     * @param <V> type of value
     * @param name - name of object
     * @return Geo object
     */
    <V> RGeoReactive<V> getGeo(String name);
    
    /**
     * Returns geospatial items holder instance by <code>name</code>
     * using provided codec for geospatial members.
     * 
     * @param <V> type of value
     * @param name - name of object
     * @param codec - codec for value
     * @return Geo object
     */
    <V> RGeoReactive<V> getGeo(String name, Codec codec);
    
    /**
     * Returns Set based Multimap instance by name.
     * 
     * @param <K> type of key
     * @param <V> type of value
     * @param name - name of object
     * @return SetMultimap object
     */
    <K, V> RSetMultimapReactive<K, V> getSetMultimap(String name);

    /**
     * Returns Set based Multimap instance by name
     * using provided codec for both map keys and values.
     *
     * @param <K> type of key
     * @param <V> type of value
     * @param name - name of object
     * @param codec - codec for keys and values
     * @return SetMultimap object
     */
    <K, V> RSetMultimapReactive<K, V> getSetMultimap(String name, Codec codec);
    
    /**
     * Returns set-based cache instance by <code>name</code>.
     * Uses map (value_hash, value) under the hood for minimal memory consumption.
     * Supports value eviction with a given TTL value.
     *
     * <p>If eviction is not required then it's better to use regular map {@link #getSet(String, Codec)}.</p>
     *
     * @param <V> type of value
     * @param name - name of object
     * @return SetCache object
     */
    <V> RSetCacheReactive<V> getSetCache(String name);

    /**
     * Returns set-based cache instance by <code>name</code>
     * using provided <code>codec</code> for values.
     * Uses map (value_hash, value) under the hood for minimal memory consumption.
     * Supports value eviction with a given TTL value.
     *
     * <p>If eviction is not required then it's better to use regular map {@link #getSet(String, Codec)}.</p>
     *
     * @param <V> type of value
     * @param name - name of object
     * @param codec - codec for values
     * @return SetCache object
     */
    <V> RSetCacheReactive<V> getSetCache(String name, Codec codec);

    /**
     * Returns map-based cache instance by <code>name</code>
     * using provided <code>codec</code> for both cache keys and values.
     * Supports entry eviction with a given TTL value.
     *
     * <p>If eviction is not required then it's better to use regular map {@link #getMap(String, Codec)}.</p>
     *
     * @param <K> type of key
     * @param <V> type of value
     * @param name - name of object
     * @param codec - codec for keys and values
     * @return MapCache object
     */
    <K, V> RMapCacheReactive<K, V> getMapCache(String name, Codec codec);

    /**
     * Returns map-based cache instance by <code>name</code>.
     * Supports entry eviction with a given TTL value.
     *
     * <p>If eviction is not required then it's better to use regular map {@link #getMap(String)}.</p>
     *
     * @param <K> type of key
     * @param <V> type of value
     * @param name - name of object
     * @return MapCache object
     */
    <K, V> RMapCacheReactive<K, V> getMapCache(String name);

    /**
     * Returns object holder by name
     *
     * @param <V> type of value
     * @param name - name of object
     * @return Bucket object
     */
    <V> RBucketReactive<V> getBucket(String name);

    <V> RBucketReactive<V> getBucket(String name, Codec codec);

    /**
     * Returns HyperLogLog object by name
     *
     * @param <V> type of value
     * @param name - name of object
     * @return HyperLogLog object
     */
    <V> RHyperLogLogReactive<V> getHyperLogLog(String name);

    <V> RHyperLogLogReactive<V> getHyperLogLog(String name, Codec codec);

    /**
     * Returns list instance by name.
     *
     * @param <V> type of value
     * @param name - name of object
     * @return List object
     */
    <V> RListReactive<V> getList(String name);

    <V> RListReactive<V> getList(String name, Codec codec);

    /**
     * Returns List based MultiMap instance by name.
     *
     * @param <K> type of key
     * @param <V> type of value
     * @param name - name of object
     * @return ListMultimap object
     */
    <K, V> RListMultimapReactive<K, V> getListMultimap(String name);

    /**
     * Returns List based MultiMap instance by name
     * using provided codec for both map keys and values.
     *
     * @param <K> type of key
     * @param <V> type of value
     * @param name - name of object
     * @param codec - codec for keys and values
     * @return ListMultimap object
     */
    <K, V> RListMultimapReactive<K, V> getListMultimap(String name, Codec codec);
    
    /**
     * Returns map instance by name.
     *
     * @param <K> type of key
     * @param <V> type of value
     * @param name - name of object
     * @return Map object
     */
    <K, V> RMapReactive<K, V> getMap(String name);

    <K, V> RMapReactive<K, V> getMap(String name, Codec codec);

    /**
     * Returns set instance by name.
     * 
     * @param <V> type of value
     * @param name - name of object
     * @return Set object
     */
    <V> RSetReactive<V> getSet(String name);

    <V> RSetReactive<V> getSet(String name, Codec codec);

    /**
     * Returns topic instance by name.
     *
     * @param name - name of object
     * @return Topic object
     */
    RTopicReactive getTopic(String name);

    RTopicReactive getTopic(String name, Codec codec);

    /**
     * Returns queue instance by name.
     *
     * @param <V> type of value
     * @param name - name of object
     * @return Queue object
     */
    <V> RQueueReactive<V> getQueue(String name);

    <V> RQueueReactive<V> getQueue(String name, Codec codec);

    /**
     * Returns blocking queue instance by name.
     * 
     * @param <V> type of value
     * @param name - name of object
     * @return BlockingQueue object
     */
    <V> RBlockingQueueReactive<V> getBlockingQueue(String name);

    <V> RBlockingQueueReactive<V> getBlockingQueue(String name, Codec codec);

    /**
     * Returns blocking deque instance by name.
     * 
     * @param <V> type of value
     * @param name - name of object
     * @return BlockingDeque object
     */
    <V> RBlockingDequeReactive<V> getBlockingDeque(String name);

    <V> RBlockingDequeReactive<V> getBlockingDeque(String name, Codec codec);
    
    /**
     * Returns deque instance by name.
     * 
     * @param <V> type of value
     * @param name - name of object
     * @return Deque object
     */
    <V> RDequeReactive<V> getDeque(String name);

    <V> RDequeReactive<V> getDeque(String name, Codec codec);

    /**
     * Returns "atomic long" instance by name.
     * 
     * @param name - name of object
     * @return AtomicLong object
     */
    RAtomicLongReactive getAtomicLong(String name);

    /**
     * Returns atomicDouble instance by name.
     *
     * @param name - name of object
     * @return AtomicDouble object
     */
    RAtomicDoubleReactive getAtomicDouble(String name);
    
    /**
     * Returns Redis Sorted Set instance by name
     * 
     * @param <V> type of value
     * @param name - name of object
     * @return ScoredSortedSet object
     */
    <V> RScoredSortedSetReactive<V> getScoredSortedSet(String name);

    <V> RScoredSortedSetReactive<V> getScoredSortedSet(String name, Codec codec);

    /**
     * Returns String based Redis Sorted Set instance by name
     * All elements are inserted with the same score during addition,
     * in order to force lexicographical ordering
     *
     * @param name - name of object
     * @return LexSortedSet object
     */
    RLexSortedSetReactive getLexSortedSet(String name);

    /**
     * Returns bitSet instance by name.
     *
     * @param name of bitSet
     * @return BitSet object
     */
    RBitSetReactive getBitSet(String name);

    /**
     * Returns script operations object
     *
     * @return Script object
     */
    RScriptReactive getScript();

    /**
     * Returns script operations object using provided codec.
     * 
     * @param codec - codec for params and result
     * @return Script object
     */
    RScriptReactive getScript(Codec codec);
    
    /**
     * Returns keys operations.
     * Each of Redis/Redisson object associated with own key
     *
     * @return Keys object
     */
    RKeysReactive getKeys();

    /**
     * Executes all operations accumulated during Reactive methods invocations Reactivehronously.
     *
     * In cluster configurations operations grouped by slot ids
     * so may be executed on different servers. Thus command execution order could be changed
     *
     * @return List with result object for each command
     */
    Mono<BatchResult<?>> execute();

    /*
     * Use BatchOptions#atomic
     */
    @Deprecated
    RBatchReactive atomic();

    /*
     * Use BatchOptions#skipResult
     */
    @Deprecated
    RBatchReactive skipResult();

    /*
     * Use BatchOptions#syncSlaves
     */
    @Deprecated
    RBatchReactive syncSlaves(int slaves, long timeout, TimeUnit unit);
    
    /*
     * Use BatchOptions#responseTimeout
     */
    @Deprecated
    RBatchReactive timeout(long timeout, TimeUnit unit);

    /*
     * Use BatchOptions#retryInterval
     */
    @Deprecated
    RBatchReactive retryInterval(long retryInterval, TimeUnit unit);

    /*
     * Use BatchOptions#retryAttempts
     */
    @Deprecated
    RBatchReactive retryAttempts(int retryAttempts);

}
