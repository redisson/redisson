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
package org.redisson.api;

import java.util.List;

import org.reactivestreams.Publisher;
import org.redisson.client.codec.Codec;

/**
 * Interface for using pipeline feature.
 *
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
    <V> RSetCacheReactive<V> getSetCache(String name);

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
    <V> RSetCacheReactive<V> getSetCache(String name, Codec codec);

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
    <K, V> RMapCacheReactive<K, V> getMapCache(String name, Codec codec);

    /**
     * Returns map-based cache instance by <code>name</code>.
     * Supports entry eviction with a given TTL value.
     *
     * <p>If eviction is not required then it's better to use regular map {@link #getMap(String)}.</p>
     *
     * @param name
     * @return
     */
    <K, V> RMapCacheReactive<K, V> getMapCache(String name);

    /**
     * Returns object holder by name
     *
     * @param name of object
     * @return
     */
    <V> RBucketReactive<V> getBucket(String name);

    <V> RBucketReactive<V> getBucket(String name, Codec codec);

    /**
     * Returns HyperLogLog object
     *
     * @param name of object
     * @return
     */
    <V> RHyperLogLogReactive<V> getHyperLogLog(String name);

    <V> RHyperLogLogReactive<V> getHyperLogLog(String name, Codec codec);

    /**
     * Returns list instance by name.
     *
     * @param name of list
     * @return
     */
    <V> RListReactive<V> getList(String name);

    <V> RListReactive<V> getList(String name, Codec codec);

    /**
     * Returns map instance by name.
     *
     * @param name of map
     * @return
     */
    <K, V> RMapReactive<K, V> getMap(String name);

    <K, V> RMapReactive<K, V> getMap(String name, Codec codec);

    /**
     * Returns set instance by name.
     *
     * @param name of set
     * @return
     */
    <V> RSetReactive<V> getSet(String name);

    <V> RSetReactive<V> getSet(String name, Codec codec);

    /**
     * Returns topic instance by name.
     *
     * @param name of topic
     * @return
     */
    <M> RTopicReactive<M> getTopic(String name);

    <M> RTopicReactive<M> getTopic(String name, Codec codec);

    /**
     * Returns queue instance by name.
     *
     * @param name of queue
     * @return
     */
    <V> RQueueReactive<V> getQueue(String name);

    <V> RQueueReactive<V> getQueue(String name, Codec codec);

    /**
     * Returns blocking queue instance by name.
     *
     * @param name of queue
     * @return
     */
    <V> RBlockingQueueReactive<V> getBlockingQueue(String name);

    <V> RBlockingQueueReactive<V> getBlockingQueue(String name, Codec codec);

    /**
     * Returns deque instance by name.
     *
     * @param name of deque
     * @return
     */
    <V> RDequeReactive<V> getDequeReactive(String name);

    <V> RDequeReactive<V> getDequeReactive(String name, Codec codec);

    /**
     * Returns "atomic long" instance by name.
     *
     * @param name of the "atomic long"
     * @return
     */
    RAtomicLongReactive getAtomicLongReactive(String name);

    /**
     * Returns Redis Sorted Set instance by name
     *
     * @param name
     * @return
     */
    <V> RScoredSortedSetReactive<V> getScoredSortedSet(String name);

    <V> RScoredSortedSetReactive<V> getScoredSortedSet(String name, Codec codec);

    /**
     * Returns String based Redis Sorted Set instance by name
     * All elements are inserted with the same score during addition,
     * in order to force lexicographical ordering
     *
     * @param name
     * @return
     */
    RLexSortedSetReactive getLexSortedSet(String name);

    /**
     * Returns bitSet instance by name.
     *
     * @param name of bitSet
     * @return
     */
    RBitSetReactive getBitSet(String name);

    /**
     * Returns script operations object
     *
     * @return
     */
    RScriptReactive getScript();

    /**
     * Returns keys operations.
     * Each of Redis/Redisson object associated with own key
     *
     * @return
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
    Publisher<List<?>> execute();

}
