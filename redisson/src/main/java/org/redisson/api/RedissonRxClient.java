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

import org.redisson.client.codec.Codec;
import org.redisson.codec.JsonCodec;
import org.redisson.config.Config;

/**
 * Main Redisson interface for access
 * to all redisson objects with RxJava2 interface.
 *
 * @see RedissonReactiveClient
 * @see RedissonClient
 *
 *
 * @author Nikita Koksharov
 *
 */
public interface RedissonRxClient {

    /**
     * Returns time-series instance by <code>name</code>
     *
     * @param <V> value type
     * @param <L> label type
     * @param name - name of instance
     * @return RTimeSeries object
     */
    <V, L> RTimeSeriesRx<V, L> getTimeSeries(String name);

    /**
     * Returns time-series instance by <code>name</code>
     * using provided <code>codec</code> for values.
     *
     * @param <V> value type
     * @param <L> label type
     * @param name - name of instance
     * @param codec - codec for values
     * @return RTimeSeries object
     */
    <V, L> RTimeSeriesRx<V, L> getTimeSeries(String name, Codec codec);

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
    <K, V> RStreamRx<K, V> getStream(String name);
    
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
    <K, V> RStreamRx<K, V> getStream(String name, Codec codec);
    
    /**
     * Returns geospatial items holder instance by <code>name</code>.
     * 
     * @param <V> type of value
     * @param name - name of object
     * @return Geo object
     */
    <V> RGeoRx<V> getGeo(String name);
    
    /**
     * Returns geospatial items holder instance by <code>name</code>
     * using provided codec for geospatial members.
     * 
     * @param <V> type of value
     * @param name - name of object
     * @param codec - codec for value
     * @return Geo object
     */
    <V> RGeoRx<V> getGeo(String name, Codec codec);
    
    /**
     * Returns rate limiter instance by <code>name</code>
     * 
     * @param name of rate limiter
     * @return RateLimiter object
     */
    RRateLimiterRx getRateLimiter(String name);

    /**
     * Returns binary stream holder instance by <code>name</code>
     *
     * @param name of binary stream
     * @return BinaryStream object
     */
    RBinaryStreamRx getBinaryStream(String name);

    /**
     * Returns semaphore instance by name
     *
     * @param name - name of object
     * @return Semaphore object
     */
    RSemaphoreRx getSemaphore(String name);
    
    /**
     * Returns semaphore instance by name.
     * Supports lease time parameter for each acquired permit.
     * 
     * @param name - name of object
     * @return PermitExpirableSemaphore object
     */
    RPermitExpirableSemaphoreRx getPermitExpirableSemaphore(String name);
    
    /**
     * Returns ReadWriteLock instance by name.
     * <p>
     * To increase reliability during failover, all operations wait for propagation to all Redis slaves.
     *
     * @param name - name of object
     * @return Lock object
     */
    RReadWriteLockRx getReadWriteLock(String name);
    
    /**
     * Returns Lock instance by name.
     * <p>
     * Implements a <b>fair</b> locking so it guarantees an acquire order by threads.
     * <p>
     * To increase reliability during failover, all operations wait for propagation to all Redis slaves.
     * 
     * @param name - name of object
     * @return Lock object
     */
    RLockRx getFairLock(String name);
    
    /**
     * Returns Lock instance by name.
     * <p>
     * Implements a <b>non-fair</b> locking so doesn't guarantees an acquire order by threads.
     * <p>
     * To increase reliability during failover, all operations wait for propagation to all Redis slaves.
     *
     * @param name - name of object
     * @return Lock object
     */
    RLockRx getLock(String name);

    /**
     * Returns Spin lock instance by name.
     * <p>
     * Implements a <b>non-fair</b> locking so doesn't guarantees an acquire order by threads.
     * <p>
     * Lock doesn't use a pub/sub mechanism
     *
     * @param name - name of object
     * @return Lock object
     */
    RLockRx getSpinLock(String name);

    /**
     * Returns Spin lock instance by name with specified back off options.
     * <p>
     * Implements a <b>non-fair</b> locking so doesn't guarantees an acquire order by threads.
     * <p>
     * Lock doesn't use a pub/sub mechanism
     *
     * @param name - name of object
     * @return Lock object
     */
    RLockRx getSpinLock(String name, LockOptions.BackOff backOff);

    /**
     * Returns MultiLock instance associated with specified <code>locks</code>
     *
     * @param locks - collection of locks
     * @return MultiLock object
     */
    RLockRx getMultiLock(RLockRx... locks);

    /*
     * Use getMultiLock(RLockReactive) method instead
     */
    @Deprecated
    RLockRx getMultiLock(RLock... locks);

    /*
     * Use getMultiLock method instead. Returned instance uses Redis Slave synchronization
     */
    @Deprecated
    RLockRx getRedLock(RLock... locks);

    /**
     * Returns CountDownLatch instance by name.
     *
     * @param name - name of object
     * @return CountDownLatch object
     */
    RCountDownLatchRx getCountDownLatch(String name);

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
    <V> RSetCacheRx<V> getSetCache(String name);

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
    <V> RSetCacheRx<V> getSetCache(String name, Codec codec);

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
    <K, V> RMapCacheRx<K, V> getMapCache(String name, Codec codec);

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
    <K, V> RMapCacheRx<K, V> getMapCache(String name, Codec codec, MapOptions<K, V> options);

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
    <K, V> RMapCacheRx<K, V> getMapCache(String name);

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
    <K, V> RMapCacheRx<K, V> getMapCache(String name, MapOptions<K, V> options);
    
    /**
     * Returns object holder instance by name
     * 
     * @param <V> type of value
     * @param name - name of object
     * @return Bucket object
     */
    <V> RBucketRx<V> getBucket(String name);

    /**
     * Returns object holder instance by name
     * using provided codec for object.
     *
     * @param <V> type of value
     * @param name - name of object
     * @param codec - codec for value
     * @return Bucket object
     */
    <V> RBucketRx<V> getBucket(String name, Codec codec);

    /**
     * Returns interface for mass operations with Bucket objects.
     *
     * @return Buckets
     */
    RBucketsRx getBuckets();

    /**
     * Returns interface for mass operations with Bucket objects
     * using provided codec for object.
     *
     * @param codec - codec for bucket objects
     * @return Buckets
     */
    RBucketsRx getBuckets(Codec codec);

    /**
     * Returns JSON data holder instance by name using provided codec.
     *
     * @param <V> type of value
     * @param name name of object
     * @param codec codec for values
     * @return JsonBucket object
     */
    <V> RJsonBucketRx<V> getJsonBucket(String name, JsonCodec<V> codec);

    /**
     * Returns HyperLogLog instance by name.
     * 
     * @param <V> type of values
     * @param name - name of object
     * @return HyperLogLog object
     */
    <V> RHyperLogLogRx<V> getHyperLogLog(String name);

    /**
     * Returns HyperLogLog instance by name
     * using provided codec for hll objects.
     *
     * @param <V> type of values
     * @param name - name of object
     * @param codec - codec of values
     * @return HyperLogLog object
     */
    <V> RHyperLogLogRx<V> getHyperLogLog(String name, Codec codec);

    /**
     * Returns id generator by name.
     *
     * @param name - name of object
     * @return IdGenerator object
     */
    RIdGeneratorRx getIdGenerator(String name);

    /**
     * Returns list instance by name.
     *
     * @param <V> type of values
     * @param name - name of object
     * @return List object
     */
    <V> RListRx<V> getList(String name);

    /**
     * Returns list instance by name
     * using provided codec for list objects.
     *
     * @param <V> type of values
     * @param name - name of object
     * @param codec - codec for values
     * @return List object
     */
    <V> RListRx<V> getList(String name, Codec codec);

    /**
     * Returns List based Multimap instance by name.
     * 
     * @param <K> type of key
     * @param <V> type of value
     * @param name - name of object
     * @return ListMultimap object
     */
    <K, V> RListMultimapRx<K, V> getListMultimap(String name);

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
    <K, V> RListMultimapRx<K, V> getListMultimap(String name, Codec codec);

    /**
     * Returns List based Multimap cache instance by name.
     * Supports key eviction by specifying a time to live.
     * If eviction is not required then it's better to use regular list multimap {@link #getListMultimap(String)}.
     *
     * @param <K> type of key
     * @param <V> type of value
     * @param name - name of object
     * @return RListMultimapCacheRx object
     */
    <K, V> RListMultimapCacheRx<K, V> getListMultimapCache(String name);

    /**
     * Returns List based Multimap cache instance by name using provided codec for both map keys and values.
     * Supports key eviction by specifying a time to live.
     * If eviction is not required then it's better to use regular list multimap {@link #getListMultimap(String, Codec)}.
     *
     * @param <K> type of key
     * @param <V> type of value
     * @param name - name of object
     * @param codec - codec for keys and values
     * @return RListMultimapCacheRx object
     */
    <K, V> RListMultimapCacheRx<K, V> getListMultimapCache(String name, Codec codec);

    /**
     * Returns Set based Multimap instance by name.
     * 
     * @param <K> type of key
     * @param <V> type of value
     * @param name - name of object
     * @return SetMultimap object
     */
    <K, V> RSetMultimapRx<K, V> getSetMultimap(String name);

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
    <K, V> RSetMultimapRx<K, V> getSetMultimap(String name, Codec codec);

    /**
     * Returns Set based Multimap cache instance by name.
     * Supports key eviction by specifying a time to live.
     * If eviction is not required then it's better to use regular set multimap {@link #getSetMultimap(String)}.
     *
     * @param <K> type of key
     * @param <V> type of value
     * @param name - name of object
     * @return RSetMultimapCacheRx object
     */
    <K, V> RSetMultimapCacheRx<K, V> getSetMultimapCache(String name);

    /**
     * Returns Set based Multimap cache instance by name using provided codec for both map keys and values.
     * Supports key eviction by specifying a time to live.
     * If eviction is not required then it's better to use regular set multimap {@link #getSetMultimap(String, Codec)}.
     *
     * @param <K> type of key
     * @param <V> type of value
     * @param name - name of object
     * @param codec - codec for keys and values
     * @return RSetMultimapCacheRx object
     */
    <K, V> RSetMultimapCacheRx<K, V> getSetMultimapCache(String name, Codec codec);

    /**
     * Returns map instance by name.
     *
     * @param <K> type of keys
     * @param <V> type of values
     * @param name - name of object
     * @return Map object
     */
    <K, V> RMapRx<K, V> getMap(String name);

    /**
     * Returns map instance by name.
     *
     * @param <K> type of key
     * @param <V> type of value
     * @param name - name of object
     * @param options - map options
     * @return Map object
     */
    <K, V> RMapRx<K, V> getMap(String name, MapOptions<K, V> options);

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
    <K, V> RMapRx<K, V> getMap(String name, Codec codec);

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
    <K, V> RMapRx<K, V> getMap(String name, Codec codec, MapOptions<K, V> options);

    /**
     * Returns local cached map instance by name.
     * Configured by parameters of options-object.
     *
     * @param <K> type of key
     * @param <V> type of value
     * @param name - name of object
     * @param options - local map options
     * @return LocalCachedMap object
     */
    <K, V> RLocalCachedMapRx<K, V> getLocalCachedMap(String name, LocalCachedMapOptions<K, V> options);

    /**
     * Returns local cached map instance by name
     * using provided codec. Configured by parameters of options-object.
     *
     * @param <K> type of key
     * @param <V> type of value
     * @param name - name of object
     * @param codec - codec for keys and values
     * @param options - local map options
     * @return LocalCachedMap object
     */
    <K, V> RLocalCachedMapRx<K, V> getLocalCachedMap(String name, Codec codec, LocalCachedMapOptions<K, V> options);

    /**
     * Returns set instance by name.
     *
     * @param <V> type of values
     * @param name - name of object
     * @return Set object
     */
    <V> RSetRx<V> getSet(String name);

    /**
     * Returns set instance by name
     * using provided codec for set objects.
     *
     * @param <V> type of values
     * @param name - name of set
     * @param codec - codec for values
     * @return Set object
     */
    <V> RSetRx<V> getSet(String name, Codec codec);

    /**
     * Returns Redis Sorted Set instance by name.
     * This sorted set sorts objects by object score.
     * 
     * @param <V> type of values
     * @param name of scored sorted set
     * @return ScoredSortedSet object
     */
    <V> RScoredSortedSetRx<V> getScoredSortedSet(String name);

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
    <V> RScoredSortedSetRx<V> getScoredSortedSet(String name, Codec codec);

    /**
     * Returns String based Redis Sorted Set instance by name
     * All elements are inserted with the same score during addition,
     * in order to force lexicographical ordering
     *
     * @param name - name of object
     * @return LexSortedSet object
     */
    RLexSortedSetRx getLexSortedSet(String name);

    /**
     * Returns Sharded Topic instance by name.
     * <p>
     * Messages are delivered to message listeners connected to the same Topic.
     * <p>
     *
     * @param name - name of object
     * @return Topic object
     */
    RShardedTopicRx getShardedTopic(String name);

    /**
     * Returns Sharded Topic instance by name using provided codec for messages.
     * <p>
     * Messages are delivered to message listeners connected to the same Topic.
     * <p>
     *
     * @param name - name of object
     * @param codec - codec for message
     * @return Topic object
     */
    RShardedTopicRx getShardedTopic(String name, Codec codec);

    /**
     * Returns topic instance by name.
     *
     * @param name - name of object
     * @return Topic object
     */
    RTopicRx getTopic(String name);

    /**
     * Returns topic instance by name
     * using provided codec for messages.
     *
     * @param name - name of object
     * @param codec - codec for message
     * @return Topic object
     */
    RTopicRx getTopic(String name, Codec codec);

    /**
     * Returns reliable topic instance by name.
     * <p>
     * Dedicated Redis connection is allocated per instance (subscriber) of this object.
     * Messages are delivered to all listeners attached to the same Redis setup.
     * <p>
     * Requires <b>Redis 5.0.0 and higher.</b>
     *
     * @param name - name of object
     * @return ReliableTopic object
     */
    RReliableTopicRx getReliableTopic(String name);

    /**
     * Returns reliable topic instance by name
     * using provided codec for messages.
     * <p>
     * Dedicated Redis connection is allocated per instance (subscriber) of this object.
     * Messages are delivered to all listeners attached to the same Redis setup.
     * <p>
     * Requires <b>Redis 5.0.0 and higher.</b>
     *
     * @param name - name of object
     * @param codec - codec for message
     * @return ReliableTopic object
     */
    RReliableTopicRx getReliableTopic(String name, Codec codec);

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
    RPatternTopicRx getPatternTopic(String pattern);

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
    RPatternTopicRx getPatternTopic(String pattern, Codec codec);

    /**
     * Returns queue instance by name.
     *
     * @param <V> type of values
     * @param name - name of object
     * @return Queue object
     */
    <V> RQueueRx<V> getQueue(String name);

    /**
     * Returns queue instance by name
     * using provided codec for queue objects.
     * 
     * @param <V> type of values
     * @param name - name of object
     * @param codec - codec for values
     * @return Queue object
     */
    <V> RQueueRx<V> getQueue(String name, Codec codec);

    /**
     * Returns RingBuffer based queue.
     * 
     * @param <V> value type
     * @param name - name of object
     * @return RingBuffer object
     */
    <V> RRingBufferRx<V> getRingBuffer(String name);
    
    /**
     * Returns RingBuffer based queue.
     * 
     * @param <V> value type
     * @param name - name of object
     * @param codec - codec for values
     * @return RingBuffer object
     */
    <V> RRingBufferRx<V> getRingBuffer(String name, Codec codec);
    
    /**
     * Returns blocking queue instance by name.
     * 
     * @param <V> type of values
     * @param name - name of object
     * @return BlockingQueue object
     */
    <V> RBlockingQueueRx<V> getBlockingQueue(String name);

    /**
     * Returns blocking queue instance by name
     * using provided codec for queue objects.
     * 
     * @param <V> type of values
     * @param name - name of object
     * @param codec - code for values
     * @return BlockingQueue object
     */
    <V> RBlockingQueueRx<V> getBlockingQueue(String name, Codec codec);

    /**
     * Returns unbounded blocking deque instance by name.
     * 
     * @param <V> type of value
     * @param name - name of object
     * @return BlockingDeque object
     */
    <V> RBlockingDequeRx<V> getBlockingDeque(String name);

    /**
     * Returns unbounded blocking deque instance by name
     * using provided codec for deque objects.
     * 
     * @param <V> type of value
     * @param name - name of object
     * @param codec - deque objects codec
     * @return BlockingDeque object
     */
    <V> RBlockingDequeRx<V> getBlockingDeque(String name, Codec codec);

    /**
     * Returns transfer queue instance by name.
     *
     * @param <V> type of values
     * @param name - name of object
     * @return TransferQueue object
     */
    <V> RTransferQueueRx<V> getTransferQueue(String name);

    /**
     * Returns transfer queue instance by name
     * using provided codec for queue objects.
     *
     * @param <V> type of values
     * @param name - name of object
     * @param codec - code for values
     * @return TransferQueue object
     */
    <V> RTransferQueueRx<V> getTransferQueue(String name, Codec codec);

    /**
     * Returns deque instance by name.
     * 
     * @param <V> type of values
     * @param name - name of object
     * @return Deque object
     */
    <V> RDequeRx<V> getDeque(String name);

    /**
     * Returns deque instance by name
     * using provided codec for deque objects.
     * 
     * @param <V> type of values
     * @param name - name of object
     * @param codec - coded for values
     * @return Deque object
     */
    <V> RDequeRx<V> getDeque(String name, Codec codec);

    /**
     * Returns "atomic long" instance by name.
     *
     * @param name of the "atomic long"
     * @return AtomicLong object
     */
    RAtomicLongRx getAtomicLong(String name);

    /**
     * Returns "atomic double" instance by name.
     *
     * @param name of the "atomic double"
     * @return AtomicLong object
     */
    RAtomicDoubleRx getAtomicDouble(String name);

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
    RBitSetRx getBitSet(String name);

    /**
     * Returns interface for Redis Function feature
     *
     * @return function object
     */
    RFunctionRx getFunction();

    /**
     * Returns interface for Redis Function feature using provided codec
     *
     * @param codec - codec for params and result
     * @return function interface
     */
    RFunctionRx getFunction(Codec codec);

    /**
     * Returns script operations object
     *
     * @return Script object
     */
    RScriptRx getScript();

    /**
     * Returns script operations object using provided codec.
     * 
     * @param codec - codec for params and result
     * @return Script object
     */
    RScriptRx getScript(Codec codec);
    
    /**
     * Creates transaction with <b>READ_COMMITTED</b> isolation level.
     * 
     * @param options - transaction configuration
     * @return Transaction object
     */
    RTransactionRx createTransaction(TransactionOptions options);
    
    /**
     * Return batch object which executes group of
     * command in pipeline.
     *
     * See <a href="http://redis.io/topics/pipelining">http://redis.io/topics/pipelining</a>
     *
     * @param options - batch configuration
     * @return Batch object
     */
    RBatchRx createBatch(BatchOptions options);

    /**
     * Return batch object which executes group of
     * command in pipeline.
     *
     * See <a href="http://redis.io/topics/pipelining">http://redis.io/topics/pipelining</a>
     *
     * @return Batch object
     */
    RBatchRx createBatch();

    /**
     * Returns keys operations.
     * Each of Redis/Redisson object associated with own key
     *
     * @return Keys object
     */
    RKeysRx getKeys();

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

    /**
     * Returns id of this Redisson instance
     * 
     * @return id
     */
    String getId();
    
}
