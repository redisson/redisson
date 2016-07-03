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

import org.redisson.Config;
import org.redisson.client.codec.Codec;
import org.redisson.core.ClusterNode;
import org.redisson.core.Node;
import org.redisson.core.NodesGroup;

/**
 * Main Redisson interface for access
 * to all redisson objects with reactive interface.
 *
 * @author Nikita Koksharov
 *
 */
public interface RedissonReactiveClient {

    /**
     * Returns set-based cache instance by <code>name</code>.
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
     * Returns set-based cache instance by <code>name</code>.
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
     * Returns map-based cache instance by name
     * using provided codec for both cache keys and values.
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
     * Returns map-based cache instance by name.
     * Supports entry eviction with a given TTL value.
     *
     * <p>If eviction is not required then it's better to use regular map {@link #getMap(String)}.</p>
     *
     * @param name
     * @return
     */
    <K, V> RMapCacheReactive<K, V> getMapCache(String name);

    /**
     * Returns object holder instance by name
     *
     * @param name of object
     * @return
     */
    <V> RBucketReactive<V> getBucket(String name);

    /**
     * Returns object holder instance by name
     * using provided codec for object.
     *
     * @param name of object
     * @param object codec
     * @return
     */
    <V> RBucketReactive<V> getBucket(String name, Codec codec);

    /**
     * Returns a list of object holder instances by a key pattern
     */
    <V> List<RBucketReactive<V>> findBuckets(String pattern);

    /**
     * Returns HyperLogLog instance by name.
     *
     * @param name of object
     * @return
     */
    <V> RHyperLogLogReactive<V> getHyperLogLog(String name);

    /**
     * Returns HyperLogLog instance by name
     * using provided codec for hll objects.
     *
     * @param name of object
     * @param object codec
     * @return
     */
    <V> RHyperLogLogReactive<V> getHyperLogLog(String name, Codec codec);

    /**
     * Returns list instance by name.
     *
     * @param name of object
     * @return
     */
    <V> RListReactive<V> getList(String name);

    /**
     * Returns list instance by name
     * using provided codec for list objects.
     *
     * @param name of object
     * @param list object codec
     * @return
     */
    <V> RListReactive<V> getList(String name, Codec codec);

    /**
     * Returns map instance by name.
     *
     * @param name of map
     * @return
     */
    <K, V> RMapReactive<K, V> getMap(String name);

    /**
     * Returns map instance by name
     * using provided codec for both map keys and values.
     *
     * @param name of map
     * @param map key and value codec
     * @return
     */
    <K, V> RMapReactive<K, V> getMap(String name, Codec codec);

    /**
     * Returns set instance by name.
     *
     * @param name of set
     * @return
     */
    <V> RSetReactive<V> getSet(String name);

    /**
     * Returns set instance by name
     * using provided codec for set objects.
     *
     * @param name of set
     * @param set object codec
     * @return
     */
    <V> RSetReactive<V> getSet(String name, Codec codec);

    /**
     * Returns Redis Sorted Set instance by name.
     * This sorted set sorts objects by object score.
     *
     * @param name of scored sorted set
     * @return
     */
    <V> RScoredSortedSetReactive<V> getScoredSortedSet(String name);

    /**
     * Returns Redis Sorted Set instance by name
     * using provided codec for sorted set objects.
     * This sorted set sorts objects by object score.
     *
     * @param name of scored sorted set
     * @param scored sorted set object codec
     * @return
     */
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
     * Returns topic instance by name.
     *
     * @param name of topic
     * @return
     */
    <M> RTopicReactive<M> getTopic(String name);

    /**
     * Returns topic instance by name
     * using provided codec for messages.
     *
     * @param name of topic
     * @param message codec
     * @return
     */
    <M> RTopicReactive<M> getTopic(String name, Codec codec);

    /**
     * Returns topic instance satisfies by pattern name.
     *
     *  Supported glob-style patterns:
     *    h?llo subscribes to hello, hallo and hxllo
     *    h*llo subscribes to hllo and heeeello
     *    h[ae]llo subscribes to hello and hallo, but not hillo
     *
     * @param pattern of the topic
     * @return
     */
    <M> RPatternTopicReactive<M> getPatternTopic(String pattern);

    /**
     * Returns topic instance satisfies by pattern name
     * using provided codec for messages.
     *
     *  Supported glob-style patterns:
     *    h?llo subscribes to hello, hallo and hxllo
     *    h*llo subscribes to hllo and heeeello
     *    h[ae]llo subscribes to hello and hallo, but not hillo
     *
     * @param pattern of the topic
     * @param message codec
     * @return
     */
    <M> RPatternTopicReactive<M> getPatternTopic(String pattern, Codec codec);

    /**
     * Returns queue instance by name.
     *
     * @param name of queue
     * @return
     */
    <V> RQueueReactive<V> getQueue(String name);

    /**
     * Returns queue instance by name
     * using provided codec for queue objects.
     *
     * @param name of queue
     * @param queue objects codec
     * @return
     */
    <V> RQueueReactive<V> getQueue(String name, Codec codec);

    /**
     * Returns blocking queue instance by name.
     *
     * @param name of queue
     * @return
     */
    <V> RBlockingQueueReactive<V> getBlockingQueue(String name);

    /**
     * Returns blocking queue instance by name
     * using provided codec for queue objects.
     *
     * @param name of queue
     * @param queue objects codec
     * @return
     */
    <V> RBlockingQueueReactive<V> getBlockingQueue(String name, Codec codec);

    /**
     * Returns deque instance by name.
     *
     * @param name of deque
     * @return
     */
    <V> RDequeReactive<V> getDeque(String name);

    /**
     * Returns deque instance by name
     * using provided codec for deque objects.
     *
     * @param name of deque
     * @param deque objects codec
     * @return
     */
    <V> RDequeReactive<V> getDeque(String name, Codec codec);

    /**
     * Returns "atomic long" instance by name.
     *
     * @param name of the "atomic long"
     * @return
     */
    RAtomicLongReactive getAtomicLong(String name);

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
     * Return batch object which executes group of
     * command in pipeline.
     *
     * See <a href="http://redis.io/topics/pipelining">http://redis.io/topics/pipelining</a>
     *
     * @return
     */
    RBatchReactive createBatch();

    /**
     * Returns keys operations.
     * Each of Redis/Redisson object associated with own key
     *
     * @return
     */
    RKeysReactive getKeys();

    /**
     * Shuts down Redisson instance <b>NOT</b> Redis server
     */
    void shutdown();

    /**
     * Allows to get configuration provided
     * during Redisson instance creation. Further changes on
     * this object not affect Redisson instance.
     *
     * @return Config object
     */
    Config getConfig();

    /**
     * Get Redis nodes group for server operations
     *
     * @return
     */
    NodesGroup<Node> getNodesGroup();

    /**
     * Get Redis cluster nodes group for server operations
     *
     * @return
     */
    NodesGroup<ClusterNode> getClusterNodesGroup();

    /**
     * Returns {@code true} if this Redisson instance has been shut down.
     *
     * @return
     */
    boolean isShutdown();

    /**
     * Returns {@code true} if this Redisson instance was started to be shutdown
     * or was shutdown {@link #isShutdown()} already.
     *
     * @return
     */
    boolean isShuttingDown();

}
