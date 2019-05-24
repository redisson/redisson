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

import java.util.List;

import org.redisson.client.codec.Codec;
import org.redisson.config.Config;

/**
 * Main Redisson interface for access
 * to all redisson objects with Reactive interface.
 *
 * @author Nikita Koksharov
 *
 */
public interface RedissonReactiveClient {

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
     * Returns rate limiter instance by <code>name</code>
     * 
     * @param name of rate limiter
     * @return RateLimiter object
     */
    RRateLimiterReactive getRateLimiter(String name);
    
    /**
     * Returns semaphore instance by name
     *
     * @param name - name of object
     * @return Semaphore object
     */
    RSemaphoreReactive getSemaphore(String name);
    
    /**
     * Returns semaphore instance by name.
     * Supports lease time parameter for each acquired permit.
     * 
     * @param name - name of object
     * @return PermitExpirableSemaphore object
     */
    RPermitExpirableSemaphoreReactive getPermitExpirableSemaphore(String name);
    
    /**
     * Returns readWriteLock instance by name.
     *
     * @param name - name of object
     * @return Lock object
     */
    RReadWriteLockReactive getReadWriteLock(String name);
    
    /**
     * Returns lock instance by name.
     * <p>
     * Implements a <b>fair</b> locking so it guarantees an acquire order by threads.
     * 
     * @param name - name of object
     * @return Lock object
     */
    RLockReactive getFairLock(String name);
    
    /**
     * Returns lock instance by name.
     * <p>
     * Implements a <b>non-fair</b> locking so doesn't guarantee an acquire order by threads.
     *
     * @param name - name of object
     * @return Lock object
     */
    RLockReactive getLock(String name);
    
    /**
     * Returns MultiLock instance associated with specified <code>locks</code>
     * 
     * @param locks - collection of locks
     * @return MultiLock object
     */
    RLockReactive getMultiLock(RLock... locks);
    
    /**
     * Returns RedLock instance associated with specified <code>locks</code>
     * 
     * @param locks - collection of locks
     * @return RedLock object
     */
    RLockReactive getRedLock(RLock... locks);
    
    /**
     * Returns set-based cache instance by <code>name</code>.
     * Supports value eviction with a given TTL value.
     *
     * <p>If eviction is not required then it's better to use regular map {@link #getSet(String, Codec)}.</p>
     *
     * @param <V> type of values
     * @param name - name of object
     * @return SetCache object
     */
    <V> RSetCacheReactive<V> getSetCache(String name);

    /**
     * Returns set-based cache instance by <code>name</code>.
     * Supports value eviction with a given TTL value.
     *
     * <p>If eviction is not required then it's better to use regular map {@link #getSet(String, Codec)}.</p>
     *
     * @param <V> type of values
     * @param name - name of object
     * @param codec - codec for values
     * @return SetCache object
     */
    <V> RSetCacheReactive<V> getSetCache(String name, Codec codec);

    /**
     * Returns map-based cache instance by name
     * using provided codec for both cache keys and values.
     * Supports entry eviction with a given MaxIdleTime and TTL settings.
     * <p>
     * If eviction is not required then it's better to use regular map {@link #getMap(String, Codec)}.
     *
     * @param <K> type of keys
     * @param <V> type of values
     * @param name - name of object
     * @param codec - codec for values
     * @return MapCache object
     */
    <K, V> RMapCacheReactive<K, V> getMapCache(String name, Codec codec);

    /**
     * Returns map-based cache instance by <code>name</code>
     * using provided <code>codec</code> for both cache keys and values.
     * Supports entry eviction with a given MaxIdleTime and TTL settings.
     * <p>
     * If eviction is not required then it's better to use regular map {@link #getMap(String, Codec, MapOptions)}.
     * 
     * @param <K> type of key
     * @param <V> type of value
     * @param name - object name
     * @param codec - codec for keys and values
     * @param options - map options
     * @return MapCache object
     */
    <K, V> RMapCacheReactive<K, V> getMapCache(String name, Codec codec, MapOptions<K, V> options);

    /**
     * Returns map-based cache instance by name.
     * Supports entry eviction with a given MaxIdleTime and TTL settings.
     * <p>
     * If eviction is not required then it's better to use regular map {@link #getMap(String)}.
     *
     * @param <K> type of keys
     * @param <V> type of values
     * @param name - name of object
     * @return MapCache object
     */
    <K, V> RMapCacheReactive<K, V> getMapCache(String name);

    /**
     * Returns map-based cache instance by name.
     * Supports entry eviction with a given MaxIdleTime and TTL settings.
     * <p>
     * If eviction is not required then it's better to use regular map {@link #getMap(String, MapOptions)}.</p>
     *
     * @param <K> type of key
     * @param <V> type of value
     * @param name - name of object
     * @param options - map options
     * @return MapCache object
     */
    <K, V> RMapCacheReactive<K, V> getMapCache(String name, MapOptions<K, V> options);
    
    /**
     * Returns object holder instance by name
     * 
     * @param <V> type of value
     * @param name - name of object
     * @return Bucket object
     */
    <V> RBucketReactive<V> getBucket(String name);

    /**
     * Returns object holder instance by name
     * using provided codec for object.
     *
     * @param <V> type of value
     * @param name - name of object
     * @param codec - codec for value
     * @return Bucket object
     */
    <V> RBucketReactive<V> getBucket(String name, Codec codec);

    /**
     * Returns a list of object holder instances by a key pattern
     *
     * @param <V> type of value
     * @param pattern - pattern for name of buckets
     * @return list of buckets 
     */
    <V> List<RBucketReactive<V>> findBuckets(String pattern);

    /**
     * Returns HyperLogLog instance by name.
     * 
     * @param <V> type of values
     * @param name - name of object
     * @return HyperLogLog object
     */
    <V> RHyperLogLogReactive<V> getHyperLogLog(String name);

    /**
     * Returns HyperLogLog instance by name
     * using provided codec for hll objects.
     *
     * @param <V> type of values
     * @param name - name of object
     * @param codec - codec of values
     * @return HyperLogLog object
     */
    <V> RHyperLogLogReactive<V> getHyperLogLog(String name, Codec codec);

    /**
     * Returns list instance by name.
     *
     * @param <V> type of values
     * @param name - name of object
     * @return List object
     */
    <V> RListReactive<V> getList(String name);

    /**
     * Returns list instance by name
     * using provided codec for list objects.
     *
     * @param <V> type of values
     * @param name - name of object
     * @param codec - codec for values
     * @return List object
     */
    <V> RListReactive<V> getList(String name, Codec codec);

    /**
     * Returns List based Multimap instance by name.
     * 
     * @param <K> type of key
     * @param <V> type of value
     * @param name - name of object
     * @return ListMultimap object
     */
    <K, V> RListMultimapReactive<K, V> getListMultimap(String name);

    /**
     * Returns List based Multimap instance by name
     * using provided codec for both map keys and values.
     *
     * @param <K> type of key
     * @param <V> type of value
     * @param name - name of object
     * @param codec - codec for keys and values
     * @return RListMultimapReactive object
     */
    <K, V> RListMultimapReactive<K, V> getListMultimap(String name, Codec codec);

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
     * Returns map instance by name.
     *
     * @param <K> type of keys
     * @param <V> type of values
     * @param name - name of object
     * @return Map object
     */
    <K, V> RMapReactive<K, V> getMap(String name);

    /**
     * Returns map instance by name.
     *
     * @param <K> type of key
     * @param <V> type of value
     * @param name - name of object
     * @param options - map options
     * @return Map object
     */
    <K, V> RMapReactive<K, V> getMap(String name, MapOptions<K, V> options);

    /**
     * Returns map instance by name
     * using provided codec for both map keys and values.
     *
     * @param <K> type of keys
     * @param <V> type of values
     * @param name - name of object
     * @param codec - codec for keys and values
     * @return Map object
     */
    <K, V> RMapReactive<K, V> getMap(String name, Codec codec);

    /**
     * Returns map instance by name
     * using provided codec for both map keys and values.
     *
     * @param <K> type of key
     * @param <V> type of value
     * @param name - name of object
     * @param codec - codec for keys and values
     * @param options - map options
     * @return Map object
     */
    <K, V> RMapReactive<K, V> getMap(String name, Codec codec, MapOptions<K, V> options);

    /**
     * Returns set instance by name.
     *
     * @param <V> type of values
     * @param name - name of object
     * @return Set object
     */
    <V> RSetReactive<V> getSet(String name);

    /**
     * Returns set instance by name
     * using provided codec for set objects.
     *
     * @param <V> type of values
     * @param name - name of set
     * @param codec - codec for values
     * @return Set object
     */
    <V> RSetReactive<V> getSet(String name, Codec codec);

    /**
     * Returns Redis Sorted Set instance by name.
     * This sorted set sorts objects by object score.
     * 
     * @param <V> type of values
     * @param name of scored sorted set
     * @return ScoredSortedSet object
     */
    <V> RScoredSortedSetReactive<V> getScoredSortedSet(String name);

    /**
     * Returns Redis Sorted Set instance by name
     * using provided codec for sorted set objects.
     * This sorted set sorts objects by object score.
     * 
     * @param <V> type of values
     * @param name - name of scored sorted set
     * @param codec - codec for values
     * @return ScoredSortedSet object
     */
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
     * Returns topic instance by name.
     *
     * @param name - name of object
     * @return Topic object
     */
    RTopicReactive getTopic(String name);

    /**
     * Returns topic instance by name
     * using provided codec for messages.
     *
     * @param name - name of object
     * @param codec - codec for message
     * @return Topic object
     */
    RTopicReactive getTopic(String name, Codec codec);

    /**
     * Returns topic instance satisfies by pattern name.
     *
     *  Supported glob-style patterns:
     *    h?llo subscribes to hello, hallo and hxllo
     *    h*llo subscribes to hllo and heeeello
     *    h[ae]llo subscribes to hello and hallo, but not hillo
     *
     * @param pattern of the topic
     * @return PatternTopic object
     */
    RPatternTopicReactive getPatternTopic(String pattern);

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
     * @param codec - codec for message
     * @return PatternTopic object
     */
    RPatternTopicReactive getPatternTopic(String pattern, Codec codec);

    /**
     * Returns queue instance by name.
     *
     * @param <V> type of values
     * @param name - name of object
     * @return Queue object
     */
    <V> RQueueReactive<V> getQueue(String name);

    /**
     * Returns queue instance by name
     * using provided codec for queue objects.
     * 
     * @param <V> type of values
     * @param name - name of object
     * @param codec - codec for values
     * @return Queue object
     */
    <V> RQueueReactive<V> getQueue(String name, Codec codec);

    /**
     * Returns RingBuffer based queue.
     * 
     * @param <V> value type
     * @param name - name of object
     * @return RingBuffer object
     */
    <V> RRingBufferReactive<V> getRingBuffer(String name);
    
    /**
     * Returns RingBuffer based queue.
     * 
     * @param <V> value type
     * @param name - name of object
     * @param codec - codec for values
     * @return RingBuffer object
     */
    <V> RRingBufferReactive<V> getRingBuffer(String name, Codec codec);
    
    /**
     * Returns blocking queue instance by name.
     * 
     * @param <V> type of values
     * @param name - name of object
     * @return BlockingQueue object
     */
    <V> RBlockingQueueReactive<V> getBlockingQueue(String name);

    /**
     * Returns blocking queue instance by name
     * using provided codec for queue objects.
     * 
     * @param <V> type of values
     * @param name - name of object
     * @param codec - code for values
     * @return BlockingQueue object
     */
    <V> RBlockingQueueReactive<V> getBlockingQueue(String name, Codec codec);

    /**
     * Returns unbounded blocking deque instance by name.
     * 
     * @param <V> type of value
     * @param name - name of object
     * @return BlockingDeque object
     */
    <V> RBlockingDequeReactive<V> getBlockingDeque(String name);

    /**
     * Returns unbounded blocking deque instance by name
     * using provided codec for deque objects.
     * 
     * @param <V> type of value
     * @param name - name of object
     * @param codec - deque objects codec
     * @return BlockingDeque object
     */
    <V> RBlockingDequeReactive<V> getBlockingDeque(String name, Codec codec);
    
    /**
     * Returns deque instance by name.
     * 
     * @param <V> type of values
     * @param name - name of object
     * @return Deque object
     */
    <V> RDequeReactive<V> getDeque(String name);

    /**
     * Returns deque instance by name
     * using provided codec for deque objects.
     * 
     * @param <V> type of values
     * @param name - name of object
     * @param codec - coded for values
     * @return Deque object
     */
    <V> RDequeReactive<V> getDeque(String name, Codec codec);

    /**
     * Returns "atomic long" instance by name.
     *
     * @param name of the "atomic long"
     * @return AtomicLong object
     */
    RAtomicLongReactive getAtomicLong(String name);

    /**
     * Returns "atomic double" instance by name.
     *
     * @param name of the "atomic double"
     * @return AtomicLong object
     */
    RAtomicDoubleReactive getAtomicDouble(String name);

    /**
     * Returns object for remote operations prefixed with the default name (redisson_remote_service)
     * 
     * @return RemoteService object
     */
    RRemoteService getRemoteService();
    
    /**
     * Returns object for remote operations prefixed with the default name (redisson_remote_service)
     * and uses provided codec for method arguments and result.
     * 
     * @param codec - codec for response and request
     * @return RemoteService object
     */
    RRemoteService getRemoteService(Codec codec);

    /**
     * Returns object for remote operations prefixed with the specified name
     *
     * @param name - the name used as the Redis key prefix for the services
     * @return RemoteService object
     */
    RRemoteService getRemoteService(String name);
    
    /**
     * Returns object for remote operations prefixed with the specified name
     * and uses provided codec for method arguments and result.
     *
     * @param name - the name used as the Redis key prefix for the services
     * @param codec - codec for response and request
     * @return RemoteService object
     */
    RRemoteService getRemoteService(String name, Codec codec);
    
    /**
     * Returns bitSet instance by name.
     *
     * @param name - name of object
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
     * Creates transaction with <b>READ_COMMITTED</b> isolation level.
     * 
     * @param options - transaction configuration
     * @return Transaction object
     */
    RTransactionReactive createTransaction(TransactionOptions options);
    
    /**
     * Return batch object which executes group of
     * command in pipeline.
     *
     * See <a href="http://redis.io/topics/pipelining">http://redis.io/topics/pipelining</a>
     *
     * @param options - batch configuration
     * @return Batch object
     */
    RBatchReactive createBatch(BatchOptions options);

    /**
     * Return batch object which executes group of
     * command in pipeline.
     *
     * See <a href="http://redis.io/topics/pipelining">http://redis.io/topics/pipelining</a>
     *
     * @return Batch object
     */
    RBatchReactive createBatch();

    /**
     * Returns keys operations.
     * Each of Redis/Redisson object associated with own key
     *
     * @return Keys object
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
     * @return NodesGroup object
     */
    NodesGroup<Node> getNodesGroup();

    /**
     * Get Redis cluster nodes group for server operations
     *
     * @return NodesGroup object
     */
    NodesGroup<ClusterNode> getClusterNodesGroup();

    /**
     * Returns {@code true} if this Redisson instance has been shut down.
     *
     * @return <code>true</code> if this Redisson instance has been shut down otherwise <code>false</code>
     */
    boolean isShutdown();

    /**
     * Returns {@code true} if this Redisson instance was started to be shutdown
     * or was shutdown {@link #isShutdown()} already.
     *
     * @return <code>true</code> if this Redisson instance was started to be shutdown
     * or was shutdown {@link #isShutdown()} already otherwise <code>false</code>
     */
    boolean isShuttingDown();

}
