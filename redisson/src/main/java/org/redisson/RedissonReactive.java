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
package org.redisson;

import org.redisson.api.*;
import org.redisson.client.codec.Codec;
import org.redisson.codec.JsonCodec;
import org.redisson.config.Config;
import org.redisson.config.ConfigSupport;
import org.redisson.connection.ConnectionManager;
import org.redisson.eviction.EvictionScheduler;
import org.redisson.liveobject.core.RedissonObjectBuilder;
import org.redisson.reactive.*;
import org.redisson.remote.ResponseEntry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Main infrastructure class allows to get access
 * to all Redisson objects on top of Redis server.
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonReactive implements RedissonReactiveClient {

    protected final WriteBehindService writeBehindService;
    protected final EvictionScheduler evictionScheduler;
    protected final CommandReactiveExecutor commandExecutor;
    protected final ConnectionManager connectionManager;
    protected final ConcurrentMap<String, ResponseEntry> responses;

    protected RedissonReactive(Config config) {
        Config configCopy = new Config(config);

        connectionManager = ConfigSupport.createConnectionManager(configCopy);
        RedissonObjectBuilder objectBuilder = null;
        if (config.isReferenceEnabled()) {
            objectBuilder = new RedissonObjectBuilder(this);
        }
        commandExecutor = new CommandReactiveService(connectionManager, objectBuilder);
        evictionScheduler = new EvictionScheduler(commandExecutor);
        writeBehindService = new WriteBehindService(commandExecutor);
        responses = new ConcurrentHashMap<>();
    }

    protected RedissonReactive(ConnectionManager connectionManager, EvictionScheduler evictionScheduler,
                               WriteBehindService writeBehindService, ConcurrentMap<String, ResponseEntry> responses) {
        this.connectionManager = connectionManager;
        RedissonObjectBuilder objectBuilder = null;
        if (connectionManager.getServiceManager().getCfg().isReferenceEnabled()) {
            objectBuilder = new RedissonObjectBuilder(this);
        }
        commandExecutor = new CommandReactiveService(connectionManager, objectBuilder);
        this.evictionScheduler = evictionScheduler;
        this.writeBehindService = writeBehindService;
        this.responses = responses;
    }

    public EvictionScheduler getEvictionScheduler() {
        return evictionScheduler;
    }
    
    public ConnectionManager getConnectionManager() {
        return connectionManager;
    }
    
    public CommandReactiveExecutor getCommandExecutor() {
        return commandExecutor;
    }
    
    @Override
    public <K, V> RStreamReactive<K, V> getStream(String name) {
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonStream<K, V>(commandExecutor, name), RStreamReactive.class);
    }

    @Override
    public <K, V> RStreamReactive<K, V> getStream(String name, Codec codec) {
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonStream<K, V>(codec, commandExecutor, name), RStreamReactive.class);
    }

    @Override
    public RSearchReactive getSearch() {
        return getSearch(null);
    }

    @Override
    public RSearchReactive getSearch(Codec codec) {
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonSearch(codec, commandExecutor), RSearchReactive.class);
    }

    @Override
    public <V> RGeoReactive<V> getGeo(String name) {
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonGeo<V>(commandExecutor, name, null), 
                new RedissonScoredSortedSetReactive<V>(commandExecutor, name), RGeoReactive.class);
    }
    
    @Override
    public <V> RGeoReactive<V> getGeo(String name, Codec codec) {
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonGeo<V>(codec, commandExecutor, name, null), 
                new RedissonScoredSortedSetReactive<V>(codec, commandExecutor, name), RGeoReactive.class);
    }
    
    @Override
    public RLockReactive getFairLock(String name) {
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonFairLock(commandExecutor, name), RLockReactive.class);
    }
    
    @Override
    public RRateLimiterReactive getRateLimiter(String name) {
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonRateLimiter(commandExecutor, name), RRateLimiterReactive.class);
    }
    
    @Override
    public RBinaryStreamReactive getBinaryStream(String name) {
        RedissonBinaryStream stream = new RedissonBinaryStream(commandExecutor, name);
        return ReactiveProxyBuilder.create(commandExecutor, stream,
                new RedissonBinaryStreamReactive(commandExecutor, stream), RBinaryStreamReactive.class);
    }

    @Override
    public RSemaphoreReactive getSemaphore(String name) {
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonSemaphore(commandExecutor, name), RSemaphoreReactive.class);
    }

    @Override
    public RPermitExpirableSemaphoreReactive getPermitExpirableSemaphore(String name) {
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonPermitExpirableSemaphore(commandExecutor, name), RPermitExpirableSemaphoreReactive.class);
    }

    @Override
    public RReadWriteLockReactive getReadWriteLock(String name) {
        return new RedissonReadWriteLockReactive(commandExecutor, name);
    }

    @Override
    public RLockReactive getLock(String name) {
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonLock(commandExecutor, name), RLockReactive.class);
    }

    @Override
    public RLockReactive getSpinLock(String name) {
        return getSpinLock(name, LockOptions.defaults());
    }

    @Override
    public RLockReactive getSpinLock(String name, LockOptions.BackOff backOff) {
        RedissonSpinLock spinLock = new RedissonSpinLock(commandExecutor, name, backOff);
        return ReactiveProxyBuilder.create(commandExecutor, spinLock, RLockReactive.class);
    }

    @Override
    public RFencedLockReactive getFencedLock(String name) {
        RedissonFencedLock lock = new RedissonFencedLock(commandExecutor, name);
        return ReactiveProxyBuilder.create(commandExecutor, lock, RFencedLockReactive.class);
    }

    @Override
    public RLockReactive getMultiLock(RLockReactive... locks) {
        RLock[] ls = Arrays.stream(locks)
                            .map(l -> new RedissonLock(commandExecutor, l.getName()))
                            .toArray(RLock[]::new);
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonMultiLock(ls), RLockReactive.class);
    }

    @Override
    public RLockReactive getMultiLock(RLock... locks) {
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonMultiLock(locks), RLockReactive.class);
    }
    
    @Override
    public RLockReactive getRedLock(RLock... locks) {
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonRedLock(locks), RLockReactive.class);
    }

    @Override
    public RCountDownLatchReactive getCountDownLatch(String name) {
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonCountDownLatch(commandExecutor, name), RCountDownLatchReactive.class);
    }

    @Override
    public <K, V> RMapCacheReactive<K, V> getMapCache(String name, Codec codec) {
        RMapCache<K, V> map = new RedissonMapCache<K, V>(codec, evictionScheduler, commandExecutor, name, null, null, null);
        return ReactiveProxyBuilder.create(commandExecutor, map, 
                new RedissonMapCacheReactive<K, V>(map, commandExecutor), RMapCacheReactive.class);
    }

    @Override
    public <K, V> RMapCacheReactive<K, V> getMapCache(String name) {
        RMapCache<K, V> map = new RedissonMapCache<K, V>(evictionScheduler, commandExecutor, name, null, null, null);
        return ReactiveProxyBuilder.create(commandExecutor, map, 
                new RedissonMapCacheReactive<K, V>(map, commandExecutor), RMapCacheReactive.class);
    }

    @Override
    public <V> RBucketReactive<V> getBucket(String name) {
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonBucket<V>(commandExecutor, name), RBucketReactive.class);
    }

    @Override
    public <V> RBucketReactive<V> getBucket(String name, Codec codec) {
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonBucket<V>(codec, commandExecutor, name), RBucketReactive.class);
    }

    @Override
    public RBucketsReactive getBuckets() {
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonBuckets(commandExecutor), RBucketsReactive.class);
    }

    @Override
    public RBucketsReactive getBuckets(Codec codec) {
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonBuckets(codec, commandExecutor), RBucketsReactive.class);
    }

    @Override
    public <V> List<RBucketReactive<V>> findBuckets(String pattern) {
        RKeys redissonKeys = new RedissonKeys(commandExecutor);
        Iterable<String> keys = redissonKeys.getKeysByPattern(pattern);

        List<RBucketReactive<V>> buckets = new ArrayList<RBucketReactive<V>>();
        for (Object key : keys) {
            if (key != null) {
                buckets.add(this.<V>getBucket(key.toString()));
            }
        }
        return buckets;
    }

    @Override
    public <V> RJsonBucketReactive<V> getJsonBucket(String name, JsonCodec<V> codec) {
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonJsonBucket<V>(codec, commandExecutor, name), RJsonBucketReactive.class);
    }

    @Override
    public <V> RHyperLogLogReactive<V> getHyperLogLog(String name) {
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonHyperLogLog<V>(commandExecutor, name), RHyperLogLogReactive.class);
    }

    @Override
    public <V> RHyperLogLogReactive<V> getHyperLogLog(String name, Codec codec) {
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonHyperLogLog<V>(codec, commandExecutor, name), RHyperLogLogReactive.class);
    }

    @Override
    public RIdGeneratorReactive getIdGenerator(String name) {
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonIdGenerator(commandExecutor, name), RIdGeneratorReactive.class);
    }

    @Override
    public <V> RListReactive<V> getList(String name) {
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonList<V>(commandExecutor, name, null), 
                new RedissonListReactive<V>(commandExecutor, name), RListReactive.class);
    }

    @Override
    public <V> RListReactive<V> getList(String name, Codec codec) {
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonList<V>(codec, commandExecutor, name, null), 
                new RedissonListReactive<V>(codec, commandExecutor, name), RListReactive.class);
    }

    @Override
    public <K, V> RListMultimapReactive<K, V> getListMultimap(String name) {
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonListMultimap<K, V>(commandExecutor, name), 
                new RedissonListMultimapReactive<K, V>(commandExecutor, name), RListMultimapReactive.class);
    }

    @Override
    public <K, V> RListMultimapReactive<K, V> getListMultimap(String name, Codec codec) {
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonListMultimap<K, V>(codec, commandExecutor, name), 
                new RedissonListMultimapReactive<K, V>(codec, commandExecutor, name), RListMultimapReactive.class);
    }

    @Override
    public <K, V> RSetMultimapReactive<K, V> getSetMultimap(String name) {
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonSetMultimap<K, V>(commandExecutor, name), 
                new RedissonSetMultimapReactive<K, V>(commandExecutor, name, this), RSetMultimapReactive.class);
    }

    @Override
    public <K, V> RSetMultimapReactive<K, V> getSetMultimap(String name, Codec codec) {
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonSetMultimap<K, V>(codec, commandExecutor, name), 
                new RedissonSetMultimapReactive<K, V>(codec, commandExecutor, name, this), RSetMultimapReactive.class);
    }

    @Override
    public <K, V> RListMultimapCacheReactive<K, V> getListMultimapCache(String name) {
        RedissonListMultimapCache<K, V> listMultimap = new RedissonListMultimapCache<>(evictionScheduler, commandExecutor, name);
        return ReactiveProxyBuilder.create(commandExecutor, listMultimap,
                new RedissonListMultimapCacheReactive<K, V>(listMultimap, commandExecutor), RListMultimapCacheReactive.class);
    }

    @Override
    public <K, V> RListMultimapCacheReactive<K, V> getListMultimapCache(String name, Codec codec) {
        RedissonListMultimapCache<K, V> listMultimap = new RedissonListMultimapCache<>(evictionScheduler, codec, commandExecutor, name);
        return ReactiveProxyBuilder.create(commandExecutor, listMultimap,
                new RedissonListMultimapCacheReactive<>(listMultimap, commandExecutor), RListMultimapCacheReactive.class);
    }

    @Override
    public <K, V> RSetMultimapCacheReactive<K, V> getSetMultimapCache(String name) {
        RedissonSetMultimapCache<K, V> setMultimap = new RedissonSetMultimapCache<>(evictionScheduler, commandExecutor, name);
        return ReactiveProxyBuilder.create(commandExecutor, setMultimap,
                new RedissonSetMultimapCacheReactive<K, V>(setMultimap, commandExecutor, this), RSetMultimapCacheReactive.class);
    }

    @Override
    public <K, V> RSetMultimapCacheReactive<K, V> getSetMultimapCache(String name, Codec codec) {
        RedissonSetMultimapCache<K, V> setMultimap = new RedissonSetMultimapCache<>(evictionScheduler, codec, commandExecutor, name);
        return ReactiveProxyBuilder.create(commandExecutor, setMultimap,
                new RedissonSetMultimapCacheReactive<K, V>(setMultimap, commandExecutor, this), RSetMultimapCacheReactive.class);
    }

    @Override
    public <K, V> RMapReactive<K, V> getMap(String name) {
        RedissonMap<K, V> map = new RedissonMap<K, V>(commandExecutor, name, null, null, null);
        return ReactiveProxyBuilder.create(commandExecutor, map, 
                new RedissonMapReactive<K, V>(map, commandExecutor), RMapReactive.class);
    }

    @Override
    public <K, V> RMapReactive<K, V> getMap(String name, Codec codec) {
        RedissonMap<K, V> map = new RedissonMap<K, V>(codec, commandExecutor, name, null, null, null);
        return ReactiveProxyBuilder.create(commandExecutor, map, 
                new RedissonMapReactive<K, V>(map, commandExecutor), RMapReactive.class);
    }

    @Override
    public <V> RSetReactive<V> getSet(String name) {
        RedissonSet<V> set = new RedissonSet<V>(commandExecutor, name, null);
        return ReactiveProxyBuilder.create(commandExecutor, set, 
                new RedissonSetReactive<V>(set, this), RSetReactive.class);
    }

    @Override
    public <V> RSetReactive<V> getSet(String name, Codec codec) {
        RedissonSet<V> set = new RedissonSet<V>(codec, commandExecutor, name, null);
        return ReactiveProxyBuilder.create(commandExecutor, set, 
                new RedissonSetReactive<V>(set, this), RSetReactive.class);
    }

    @Override
    public <V> RScoredSortedSetReactive<V> getScoredSortedSet(String name) {
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonScoredSortedSet<V>(commandExecutor, name, null), 
                new RedissonScoredSortedSetReactive<V>(commandExecutor, name), RScoredSortedSetReactive.class);
    }

    @Override
    public <V> RScoredSortedSetReactive<V> getScoredSortedSet(String name, Codec codec) {
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonScoredSortedSet<V>(codec, commandExecutor, name, null), 
                new RedissonScoredSortedSetReactive<V>(codec, commandExecutor, name), RScoredSortedSetReactive.class);
    }

    @Override
    public RLexSortedSetReactive getLexSortedSet(String name) {
        RedissonLexSortedSet set = new RedissonLexSortedSet(commandExecutor, name, null);
        return ReactiveProxyBuilder.create(commandExecutor, set, 
                new RedissonLexSortedSetReactive(set), 
                RLexSortedSetReactive.class);
    }

    @Override
    public RShardedTopicReactive getShardedTopic(String name) {
        RedissonShardedTopic topic = new RedissonShardedTopic(commandExecutor, name);
        return ReactiveProxyBuilder.create(commandExecutor, topic,
                new RedissonTopicReactive(topic), RShardedTopicReactive.class);
    }

    @Override
    public RShardedTopicReactive getShardedTopic(String name, Codec codec) {
        RedissonShardedTopic topic = new RedissonShardedTopic(codec, commandExecutor, name);
        return ReactiveProxyBuilder.create(commandExecutor, topic,
                new RedissonTopicReactive(topic), RShardedTopicReactive.class);
    }

    @Override
    public RTopicReactive getTopic(String name) {
        RedissonTopic topic = new RedissonTopic(commandExecutor, name);
        return ReactiveProxyBuilder.create(commandExecutor, topic,
                new RedissonTopicReactive(topic), RTopicReactive.class);
    }

    @Override
    public RTopicReactive getTopic(String name, Codec codec) {
        RedissonTopic topic = new RedissonTopic(codec, commandExecutor, name);
        return ReactiveProxyBuilder.create(commandExecutor, topic, 
                new RedissonTopicReactive(topic), RTopicReactive.class);
    }

    @Override
    public RReliableTopicReactive getReliableTopic(String name) {
        RedissonReliableTopic topic = new RedissonReliableTopic(commandExecutor, name, null);
        return ReactiveProxyBuilder.create(commandExecutor, topic,
                new RedissonReliableTopicReactive(topic), RReliableTopicReactive.class);
    }

    @Override
    public RReliableTopicReactive getReliableTopic(String name, Codec codec) {
        RedissonReliableTopic topic = new RedissonReliableTopic(codec, commandExecutor, name, null);
        return ReactiveProxyBuilder.create(commandExecutor, topic,
                new RedissonReliableTopicReactive(topic), RReliableTopicReactive.class);
    }

    @Override
    public RPatternTopicReactive getPatternTopic(String pattern) {
         return ReactiveProxyBuilder.create(commandExecutor, new RedissonPatternTopic(commandExecutor, pattern), RPatternTopicReactive.class);
    }

    @Override
    public RPatternTopicReactive getPatternTopic(String pattern, Codec codec) {
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonPatternTopic(codec, commandExecutor, pattern), RPatternTopicReactive.class);
    }

    @Override
    public <V> RQueueReactive<V> getQueue(String name) {
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonQueue<V>(commandExecutor, name, null), 
                new RedissonListReactive<V>(commandExecutor, name), RQueueReactive.class);
    }

    @Override
    public <V> RQueueReactive<V> getQueue(String name, Codec codec) {
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonQueue<V>(codec, commandExecutor, name, null), 
                new RedissonListReactive<V>(codec, commandExecutor, name), RQueueReactive.class);
    }
    
    @Override
    public <V> RRingBufferReactive<V> getRingBuffer(String name) {
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonRingBuffer<V>(commandExecutor, name, null), RRingBufferReactive.class);
    }

    @Override
    public <V> RRingBufferReactive<V> getRingBuffer(String name, Codec codec) {
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonRingBuffer<V>(codec, commandExecutor, name, null), RRingBufferReactive.class);
    }

    @Override
    public <V> RBlockingQueueReactive<V> getBlockingQueue(String name) {
        RedissonBlockingQueue<V> queue = new RedissonBlockingQueue<V>(commandExecutor, name, null);
        return ReactiveProxyBuilder.create(commandExecutor, queue, 
                new RedissonBlockingQueueReactive<V>(queue), RBlockingQueueReactive.class);
    }

    @Override
    public <V> RBlockingQueueReactive<V> getBlockingQueue(String name, Codec codec) {
        RedissonBlockingQueue<V> queue = new RedissonBlockingQueue<V>(codec, commandExecutor, name, null);
        return ReactiveProxyBuilder.create(commandExecutor, queue, 
                new RedissonBlockingQueueReactive<V>(queue), RBlockingQueueReactive.class);
    }

    @Override
    public <V> RDequeReactive<V> getDeque(String name) {
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonDeque<V>(commandExecutor, name, null), 
                new RedissonListReactive<V>(commandExecutor, name), RDequeReactive.class);
    }

    @Override
    public <V> RDequeReactive<V> getDeque(String name, Codec codec) {
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonDeque<V>(codec, commandExecutor, name, null), 
                new RedissonListReactive<V>(codec, commandExecutor, name), RDequeReactive.class);
    }

    @Override
    public <V, L> RTimeSeriesReactive<V, L> getTimeSeries(String name) {
        RTimeSeries<V, L> timeSeries = new RedissonTimeSeries<V, L>(evictionScheduler, commandExecutor, name);
        return ReactiveProxyBuilder.create(commandExecutor, timeSeries,
                new RedissonTimeSeriesReactive<V, L>(timeSeries, this), RTimeSeriesReactive.class);
    }

    @Override
    public <V, L> RTimeSeriesReactive<V, L> getTimeSeries(String name, Codec codec) {
        RTimeSeries<V, L> timeSeries = new RedissonTimeSeries<V, L>(codec, evictionScheduler, commandExecutor, name);
        return ReactiveProxyBuilder.create(commandExecutor, timeSeries,
                new RedissonTimeSeriesReactive<V, L>(timeSeries, this), RTimeSeriesReactive.class);
    }

    @Override
    public <V> RSetCacheReactive<V> getSetCache(String name) {
        RSetCache<V> set = new RedissonSetCache<V>(evictionScheduler, commandExecutor, name, null);
        return ReactiveProxyBuilder.create(commandExecutor, set, 
                new RedissonSetCacheReactive<V>(set, this), RSetCacheReactive.class);
    }

    @Override
    public <V> RSetCacheReactive<V> getSetCache(String name, Codec codec) {
        RSetCache<V> set = new RedissonSetCache<V>(codec, evictionScheduler, commandExecutor, name, null);
        return ReactiveProxyBuilder.create(commandExecutor, set, 
                new RedissonSetCacheReactive<V>(set, this), RSetCacheReactive.class);
    }

    @Override
    public RAtomicLongReactive getAtomicLong(String name) {
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonAtomicLong(commandExecutor, name), RAtomicLongReactive.class);
    }

    @Override
    public RAtomicDoubleReactive getAtomicDouble(String name) {
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonAtomicDouble(commandExecutor, name), RAtomicDoubleReactive.class);
    }
    
    @Override
    public RRemoteService getRemoteService() {
        return getRemoteService("redisson_rs", connectionManager.getServiceManager().getCfg().getCodec());
    }

    @Override
    public RRemoteService getRemoteService(String name) {
        return getRemoteService(name, connectionManager.getServiceManager().getCfg().getCodec());
    }

    @Override
    public RRemoteService getRemoteService(Codec codec) {
        return getRemoteService("redisson_rs", codec);
    }

    @Override
    public RRemoteService getRemoteService(String name, Codec codec) {
        String executorId = connectionManager.getServiceManager().getId();
        if (codec != connectionManager.getServiceManager().getCfg().getCodec()) {
            executorId = executorId + ":" + name;
        }
        return new RedissonRemoteService(codec, name, commandExecutor, executorId, responses);
    }

    @Override
    public RBitSetReactive getBitSet(String name) {
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonBitSet(commandExecutor, name), RBitSetReactive.class);
    }

    @Override
    public RFunctionReactive getFunction() {
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonFuction(commandExecutor), RFunctionReactive.class);
    }

    @Override
    public RFunctionReactive getFunction(Codec codec) {
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonFuction(commandExecutor, codec), RFunctionReactive.class);
    }

    @Override
    public RScriptReactive getScript() {
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonScript(commandExecutor), RScriptReactive.class);
    }
    
    @Override
    public RScriptReactive getScript(Codec codec) {
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonScript(commandExecutor, codec), RScriptReactive.class);
    }

    @Override
    public RBatchReactive createBatch(BatchOptions options) {
        return new RedissonBatchReactive(evictionScheduler, connectionManager, commandExecutor, options);
    }

    @Override
    public RBatchReactive createBatch() {
        return createBatch(BatchOptions.defaults());
    }

    @Override
    public RKeysReactive getKeys() {
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonKeys(commandExecutor), new RedissonKeysReactive(commandExecutor), RKeysReactive.class);
    }

    @Override
    public Config getConfig() {
        return connectionManager.getServiceManager().getCfg();
    }

    @Override
    public NodesGroup<Node> getNodesGroup() {
        return new RedisNodes<>(connectionManager, connectionManager.getServiceManager(), commandExecutor);
    }

    @Override
    public NodesGroup<ClusterNode> getClusterNodesGroup() {
        if (!connectionManager.isClusterMode()) {
            throw new IllegalStateException("Redisson not in cluster mode!");
        }
        return new RedisNodes<>(connectionManager, connectionManager.getServiceManager(), commandExecutor);
    }

    @Override
    public void shutdown() {
        writeBehindService.stop();
        connectionManager.shutdown();
    }

    @Override
    public boolean isShutdown() {
        return connectionManager.getServiceManager().isShutdown();
    }

    @Override
    public boolean isShuttingDown() {
        return connectionManager.getServiceManager().isShuttingDown();
    }

    @Override
    public <K, V> RMapCacheReactive<K, V> getMapCache(String name, Codec codec, MapOptions<K, V> options) {
        RMapCache<K, V> map = new RedissonMapCache<>(codec, evictionScheduler, commandExecutor, name, null, options, writeBehindService);
        return ReactiveProxyBuilder.create(commandExecutor, map,
                new RedissonMapCacheReactive<>(map, commandExecutor), RMapCacheReactive.class);
    }


    @Override
    public <K, V> RMapCacheReactive<K, V> getMapCache(String name, MapOptions<K, V> options) {
        RMapCache<K, V> map = new RedissonMapCache<K, V>(evictionScheduler, commandExecutor, name, null, options, writeBehindService);
        return ReactiveProxyBuilder.create(commandExecutor, map,
                new RedissonMapCacheReactive<>(map, commandExecutor), RMapCacheReactive.class);
    }

    @Override
    public <K, V> RMapReactive<K, V> getMap(String name, MapOptions<K, V> options) {
        RMap<K, V> map = new RedissonMap<K, V>(commandExecutor, name, null, options, writeBehindService);
        return ReactiveProxyBuilder.create(commandExecutor, map, 
                new RedissonMapReactive<K, V>(map, commandExecutor), RMapReactive.class);
    }

    @Override
    public <K, V> RMapReactive<K, V> getMap(String name, Codec codec, MapOptions<K, V> options) {
        RMap<K, V> map = new RedissonMap<>(codec, commandExecutor, name, null, options, writeBehindService);
        return ReactiveProxyBuilder.create(commandExecutor, map,
                new RedissonMapReactive<>(map, commandExecutor), RMapReactive.class);
    }

    @Override
    public <K, V> RLocalCachedMapReactive<K, V> getLocalCachedMap(String name, LocalCachedMapOptions<K, V> options) {
        RMap<K, V> map = new RedissonLocalCachedMap<>(commandExecutor, name,
                                                        options, evictionScheduler, null, writeBehindService);
        return ReactiveProxyBuilder.create(commandExecutor, map,
                new RedissonMapReactive<>(map, commandExecutor), RLocalCachedMapReactive.class);
    }

    @Override
    public <K, V> RLocalCachedMapReactive<K, V> getLocalCachedMap(String name, Codec codec, LocalCachedMapOptions<K, V> options) {
        RMap<K, V> map = new RedissonLocalCachedMap<>(codec, commandExecutor, name,
                                                        options, evictionScheduler, null, writeBehindService);
        return ReactiveProxyBuilder.create(commandExecutor, map,
                new RedissonMapReactive<>(map, commandExecutor), RLocalCachedMapReactive.class);
    }

    @Override
    public RTransactionReactive createTransaction(TransactionOptions options) {
        return new RedissonTransactionReactive(commandExecutor, options);
    }

    @Override
    public <V> RBlockingDequeReactive<V> getBlockingDeque(String name) {
        RedissonBlockingDeque<V> deque = new RedissonBlockingDeque<V>(commandExecutor, name, null);
        return ReactiveProxyBuilder.create(commandExecutor, deque, 
                new RedissonBlockingDequeReactive<V>(deque), RBlockingDequeReactive.class);
    }

    @Override
    public <V> RBlockingDequeReactive<V> getBlockingDeque(String name, Codec codec) {
        RedissonBlockingDeque<V> deque = new RedissonBlockingDeque<V>(codec, commandExecutor, name, null);
        return ReactiveProxyBuilder.create(commandExecutor, deque, 
                new RedissonBlockingDequeReactive<V>(deque), RBlockingDequeReactive.class);
    }

    @Override
    public <V> RTransferQueueReactive<V> getTransferQueue(String name) {
        String remoteName = RedissonObject.suffixName(name, "remoteService");
        RRemoteService service = getRemoteService(remoteName);
        RedissonTransferQueue<V> queue = new RedissonTransferQueue<V>(commandExecutor, name, service);
        return ReactiveProxyBuilder.create(commandExecutor, queue,
                new RedissonTransferQueueReactive<V>(queue), RTransferQueueReactive.class);
    }

    @Override
    public <V> RTransferQueueReactive<V> getTransferQueue(String name, Codec codec) {
        String remoteName = RedissonObject.suffixName(name, "remoteService");
        RRemoteService service = getRemoteService(remoteName);
        RedissonTransferQueue<V> queue = new RedissonTransferQueue<V>(codec, commandExecutor, name, service);
        return ReactiveProxyBuilder.create(commandExecutor, queue,
                new RedissonTransferQueueReactive<V>(queue), RTransferQueueReactive.class);
    }

    @Override
    public String getId() {
        return commandExecutor.getServiceManager().getId();
    }

}
