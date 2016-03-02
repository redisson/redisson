/**
 * Copyright 2014 Nikita Koksharov, Nickolay Borbit
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
package org.redisson;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.redisson.client.codec.Codec;
import org.redisson.core.ClusterNode;
import org.redisson.core.Node;
import org.redisson.core.NodesGroup;
import org.redisson.core.RAtomicDouble;
import org.redisson.core.RAtomicLong;
import org.redisson.core.RBatch;
import org.redisson.core.RBitSet;
import org.redisson.core.RBlockingDeque;
import org.redisson.core.RBlockingQueue;
import org.redisson.core.RBloomFilter;
import org.redisson.core.RBucket;
import org.redisson.core.RMapCache;
import org.redisson.core.RCountDownLatch;
import org.redisson.core.RDeque;
import org.redisson.core.RHyperLogLog;
import org.redisson.core.RKeys;
import org.redisson.core.RLexSortedSet;
import org.redisson.core.RList;
import org.redisson.core.RLock;
import org.redisson.core.RMap;
import org.redisson.core.RPatternTopic;
import org.redisson.core.RQueue;
import org.redisson.core.RReadWriteLock;
import org.redisson.core.RScoredSortedSet;
import org.redisson.core.RScript;
import org.redisson.core.RSemaphore;
import org.redisson.core.RSet;
import org.redisson.core.RSetCache;
import org.redisson.core.RSetMultiMap;
import org.redisson.core.RSortedSet;
import org.redisson.core.RTopic;

/**
 * Main Redisson interface for access
 * to all redisson objects with sync/async interface.
 *
 * @author Nikita Koksharov
 *
 */
public interface RedissonClient {

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
    <V> RSetCache<V> getSetCache(String name);

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
    <V> RSetCache<V> getSetCache(String name, Codec codec);

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
    <K, V> RMapCache<K, V> getMapCache(String name, Codec codec);

    /**
     * Returns map-based cache instance by name.
     * Supports entry eviction with a given TTL value.
     *
     * <p>If eviction is not required then it's better to use regular map {@link #getMap(String)}.</p>
     *
     * @param name
     * @return
     */
    <K, V> RMapCache<K, V> getMapCache(String name);

    /**
     * Returns object holder instance by name.
     *
     * @param name of object
     * @return
     */
    <V> RBucket<V> getBucket(String name);

    /**
     * Returns object holder instance by name
     * using provided codec for object.
     *
     * @param name of object
     * @param object codec
     * @return
     */
    <V> RBucket<V> getBucket(String name, Codec codec);

    /**
     * <p>Returns a list of object holder instances by a key pattern.
     *
     * <pre>Supported glob-style patterns:
     *    h?llo subscribes to hello, hallo and hxllo
     *    h*llo subscribes to hllo and heeeello
     *    h[ae]llo subscribes to hello and hallo, but not hillo
     *    h[^e]llo matches hallo, hbllo, ... but not hello
     *    h[a-b]llo matches hallo and hbllo</pre>
     * <p>Use \ to escape special characters if you want to match them verbatim.
     *
     * <p>Uses <code>KEYS</code> Redis command.
     *
     * @param pattern
     * @return
     */
    <V> List<RBucket<V>> findBuckets(String pattern);

    /**
     * <p>Returns Redis object mapped by key. Result Map is not contains
     * key-value entry for null values.
     *
     * <p>Uses <code>MGET</code> Redis command.
     *
     * @param keys
     * @return
     */
    <V> Map<String, V> loadBucketValues(Collection<String> keys);

    /**
     * <p>Returns Redis object mapped by key. Result Map is not contains
     * key-value entry for null values.
     *
     * <p>Uses <code>MGET</code> Redis command.
     *
     * @param keys
     * @return
     */
    <V> Map<String, V> loadBucketValues(String ... keys);

    /**
     * Saves Redis object mapped by key.
     *
     * @param buckets
     */
    void saveBuckets(Map<String, ?> buckets);

    /**
     * Use {@link #findBuckets(String)}
     */
    @Deprecated
    <V> List<RBucket<V>> getBuckets(String pattern);

    /**
     * Returns HyperLogLog instance by name.
     *
     * @param name of object
     * @return
     */
    <V> RHyperLogLog<V> getHyperLogLog(String name);

    /**
     * Returns HyperLogLog instance by name
     * using provided codec for hll objects.
     *
     * @param name of object
     * @param object codec
     * @return
     */
    <V> RHyperLogLog<V> getHyperLogLog(String name, Codec codec);

    /**
     * Returns list instance by name.
     *
     * @param name of object
     * @return
     */
    <V> RList<V> getList(String name);

    /**
     * Returns list instance by name
     * using provided codec for list objects.
     *
     * @param name of object
     * @param list object codec
     * @return
     */
    <V> RList<V> getList(String name, Codec codec);

    /**
     * Returns map instance by name.
     *
     * @param name of map
     * @return
     */
    <K, V> RMap<K, V> getMap(String name);

    /**
     * Returns map instance by name
     * using provided codec for both map keys and values.
     *
     * @param name of map
     * @param map key and value codec
     * @return
     */
    <K, V> RMap<K, V> getMap(String name, Codec codec);

    /**
     * Returns Set based MultiMap instance by name.
     *
     * @param name
     * @return
     */
    <K, V> RSetMultiMap<K, V> getSetMultiMap(String name);

    /**
     * Returns Set based MultiMap instance by name
     * using provided codec for both map keys and values.
     *
     * @param name
     * @param codec
     * @return
     */
    <K, V> RSetMultiMap<K, V> getSetMultiMap(String name, Codec codec);

    /**
     * Returns semaphore instance by name
     *
     * @param name of semaphore
     * @return
     */
    RSemaphore getSemaphore(String name);

    /**
     * Returns lock instance by name.
     *
     * @param name of lock
     * @return
     */
    RLock getLock(String name);

    /**
     * Returns readWriteLock instance by name.
     *
     * @param name
     * @return
     */
    RReadWriteLock getReadWriteLock(String name);

    /**
     * Returns set instance by name.
     *
     * @param name of set
     * @return
     */
    <V> RSet<V> getSet(String name);

    /**
     * Returns set instance by name
     * using provided codec for set objects.
     *
     * @param name of set
     * @param set object codec
     * @return
     */
    <V> RSet<V> getSet(String name, Codec codec);

    /**
     * Returns sorted set instance by name.
     * This sorted set uses comparator to sort objects.
     *
     * @param name of sorted set
     * @return
     */
    <V> RSortedSet<V> getSortedSet(String name);

    /**
     * Returns sorted set instance by name
     * using provided codec for sorted set objects.
     * This sorted set sorts objects using comparator.
     *
     * @param name of sorted set
     * @param sorted set object codec
     * @return
     */
    <V> RSortedSet<V> getSortedSet(String name, Codec codec);

    /**
     * Returns Redis Sorted Set instance by name.
     * This sorted set sorts objects by object score.
     *
     * @param name of scored sorted set
     * @return
     */
    <V> RScoredSortedSet<V> getScoredSortedSet(String name);

    /**
     * Returns Redis Sorted Set instance by name
     * using provided codec for sorted set objects.
     * This sorted set sorts objects by object score.
     *
     * @param name of scored sorted set
     * @param scored sorted set object codec
     * @return
     */
    <V> RScoredSortedSet<V> getScoredSortedSet(String name, Codec codec);

    /**
     * Returns String based Redis Sorted Set instance by name
     * All elements are inserted with the same score during addition,
     * in order to force lexicographical ordering
     *
     * @param name
     * @return
     */
    RLexSortedSet getLexSortedSet(String name);

    /**
     * Returns topic instance by name.
     *
     * @param name of topic
     * @return
     */
    <M> RTopic<M> getTopic(String name);

    /**
     * Returns topic instance by name
     * using provided codec for messages.
     *
     * @param name of topic
     * @param message codec
     * @return
     */
    <M> RTopic<M> getTopic(String name, Codec codec);

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
    <M> RPatternTopic<M> getPatternTopic(String pattern);

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
    <M> RPatternTopic<M> getPatternTopic(String pattern, Codec codec);

    /**
     * Returns queue instance by name.
     *
     * @param name of queue
     * @return
     */
    <V> RQueue<V> getQueue(String name);

    /**
     * Returns queue instance by name
     * using provided codec for queue objects.
     *
     * @param name of queue
     * @param queue objects codec
     * @return
     */
    <V> RQueue<V> getQueue(String name, Codec codec);

    /**
     * Returns blocking queue instance by name.
     *
     * @param name of queue
     * @return
     */
    <V> RBlockingQueue<V> getBlockingQueue(String name);

    /**
     * Returns blocking queue instance by name
     * using provided codec for queue objects.
     *
     * @param name of queue
     * @param queue objects codec
     * @return
     */
    <V> RBlockingQueue<V> getBlockingQueue(String name, Codec codec);

    /**
     * Returns deque instance by name.
     *
     * @param name of deque
     * @return
     */
    <V> RDeque<V> getDeque(String name);

    /**
     * Returns deque instance by name
     * using provided codec for deque objects.
     *
     * @param name of deque
     * @param deque objects codec
     * @return
     */
    <V> RDeque<V> getDeque(String name, Codec codec);

    /**
     * Returns blocking deque instance by name.
     *
     * @param name of deque
     * @return
     */
    <V> RBlockingDeque<V> getBlockingDeque(String name);

    /**
     * Returns blocking deque instance by name
     * using provided codec for deque objects.
     *
     * @param name of deque
     * @param deque objects codec
     * @return
     */
    <V> RBlockingDeque<V> getBlockingDeque(String name, Codec codec);

    /**
     * Returns atomicLong instance by name.
     *
     * @param name of atomicLong
     * @return
     */
    RAtomicLong getAtomicLong(String name);

    /**
     * Returns atomicDouble instance by name.
     *
     * @param name of atomicLong
     * @return
     */
    RAtomicDouble getAtomicDouble(String name);

    /**
     * Returns countDownLatch instance by name.
     *
     * @param name of countDownLatch
     * @return
     */
    RCountDownLatch getCountDownLatch(String name);

    /**
     * Returns bitSet instance by name.
     *
     * @param name of bitSet
     * @return
     */
    RBitSet getBitSet(String name);

    /**
     * Returns bloom filter instance by name.
     *
     * @param name of bloom filter
     * @return
     */
    <V> RBloomFilter<V> getBloomFilter(String name);

    /**
     * Returns bloom filter instance by name
     * using provided codec for objects.
     *
     * @param name of bloom filter
     * @return
     */
    <V> RBloomFilter<V> getBloomFilter(String name, Codec codec);

    /**
     * Returns script operations object
     *
     * @return
     */
    RScript getScript();

    /**
     * Return batch object which executes group of
     * command in pipeline.
     *
     * See <a href="http://redis.io/topics/pipelining">http://redis.io/topics/pipelining</a>
     *
     * @return
     */
    RBatch createBatch();

    /**
     * Returns interface with methods for Redis keys.
     * Each of Redis/Redisson object associated with own key
     *
     * @return
     */
    RKeys getKeys();

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
     * Use {@link RKeys#flushdb()}
     */
    @Deprecated
    void flushdb();

    /**
     * Use {@link RKeys#flushall()}
     */
    @Deprecated
    void flushall();

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
