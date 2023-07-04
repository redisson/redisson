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
import org.redisson.remote.ResponseEntry;
import org.redisson.rx.*;

import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Main infrastructure class allows to get access
 * to all Redisson objects on top of Redis server.
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonRx implements RedissonRxClient {

    protected final WriteBehindService writeBehindService;
    protected final EvictionScheduler evictionScheduler;
    protected final CommandRxExecutor commandExecutor;
    protected final ConnectionManager connectionManager;
    protected final ConcurrentMap<String, ResponseEntry> responses;
    
    protected RedissonRx(Config config) {
        Config configCopy = new Config(config);

        connectionManager = ConfigSupport.createConnectionManager(configCopy);
        RedissonObjectBuilder objectBuilder = null;
        if (connectionManager.getServiceManager().getCfg().isReferenceEnabled()) {
            objectBuilder = new RedissonObjectBuilder(this);
        }
        commandExecutor = new CommandRxService(connectionManager, objectBuilder);
        evictionScheduler = new EvictionScheduler(commandExecutor);
        writeBehindService = new WriteBehindService(commandExecutor);
        responses = new ConcurrentHashMap<>();
    }

    protected RedissonRx(ConnectionManager connectionManager, EvictionScheduler evictionScheduler,
                         WriteBehindService writeBehindService, ConcurrentMap<String, ResponseEntry> responses) {
        this.connectionManager = connectionManager;
        RedissonObjectBuilder objectBuilder = null;
        if (connectionManager.getServiceManager().getCfg().isReferenceEnabled()) {
            objectBuilder = new RedissonObjectBuilder(this);
        }
        commandExecutor = new CommandRxService(connectionManager, objectBuilder);
        this.evictionScheduler = evictionScheduler;
        this.writeBehindService = writeBehindService;
        this.responses = responses;
    }

    public CommandRxExecutor getCommandExecutor() {
        return commandExecutor;
    }

    @Override
    public <K, V> RStreamRx<K, V> getStream(String name) {
        return RxProxyBuilder.create(commandExecutor, new RedissonStream<K, V>(commandExecutor, name), RStreamRx.class);
    }

    @Override
    public <K, V> RStreamRx<K, V> getStream(String name, Codec codec) {
        return RxProxyBuilder.create(commandExecutor, new RedissonStream<K, V>(codec, commandExecutor, name), RStreamRx.class);
    }

    @Override
    public RSearchRx getSearch() {
        return getSearch(null);
    }

    @Override
    public RSearchRx getSearch(Codec codec) {
        return RxProxyBuilder.create(commandExecutor, new RedissonSearch(codec, commandExecutor), RSearchRx.class);
    }

    @Override
    public <V> RGeoRx<V> getGeo(String name) {
        RedissonScoredSortedSet<V> set = new RedissonScoredSortedSet<V>(commandExecutor, name, null);
        return RxProxyBuilder.create(commandExecutor, new RedissonGeo<V>(commandExecutor, name, null), 
                new RedissonScoredSortedSetRx<V>(set), RGeoRx.class);
    }
    
    @Override
    public <V> RGeoRx<V> getGeo(String name, Codec codec) {
        RedissonScoredSortedSet<V> set = new RedissonScoredSortedSet<V>(codec, commandExecutor, name, null);
        return RxProxyBuilder.create(commandExecutor, new RedissonGeo<V>(codec, commandExecutor, name, null), 
                new RedissonScoredSortedSetRx<V>(set), RGeoRx.class);
    }
    
    @Override
    public RLockRx getFairLock(String name) {
        return RxProxyBuilder.create(commandExecutor, new RedissonFairLock(commandExecutor, name), RLockRx.class);
    }
    
    @Override
    public RRateLimiterRx getRateLimiter(String name) {
        return RxProxyBuilder.create(commandExecutor, new RedissonRateLimiter(commandExecutor, name), RRateLimiterRx.class);
    }
    
    @Override
    public RBinaryStreamRx getBinaryStream(String name) {
        RedissonBinaryStream stream = new RedissonBinaryStream(commandExecutor, name);
        return RxProxyBuilder.create(commandExecutor, stream,
                new RedissonBinaryStreamRx(commandExecutor, stream), RBinaryStreamRx.class);
    }

    @Override
    public RSemaphoreRx getSemaphore(String name) {
        return RxProxyBuilder.create(commandExecutor, new RedissonSemaphore(commandExecutor, name), RSemaphoreRx.class);
    }

    @Override
    public RPermitExpirableSemaphoreRx getPermitExpirableSemaphore(String name) {
        return RxProxyBuilder.create(commandExecutor, new RedissonPermitExpirableSemaphore(commandExecutor, name), RPermitExpirableSemaphoreRx.class);
    }

    @Override
    public RReadWriteLockRx getReadWriteLock(String name) {
        return new RedissonReadWriteLockRx(commandExecutor, name);
    }

    @Override
    public RLockRx getLock(String name) {
        return RxProxyBuilder.create(commandExecutor, new RedissonLock(commandExecutor, name), RLockRx.class);
    }

    @Override
    public RLockRx getSpinLock(String name) {
        return getSpinLock(name, LockOptions.defaults());
    }

    @Override
    public RLockRx getSpinLock(String name, LockOptions.BackOff backOff) {
        RedissonSpinLock spinLock = new RedissonSpinLock(commandExecutor, name, backOff);
        return RxProxyBuilder.create(commandExecutor, spinLock, RLockRx.class);
    }

    @Override
    public RFencedLockRx getFencedLock(String name) {
        RedissonFencedLock lock = new RedissonFencedLock(commandExecutor, name);
        return RxProxyBuilder.create(commandExecutor, lock, RFencedLockRx.class);
    }

    @Override
    public RLockRx getMultiLock(RLockRx... locks) {
        RLock[] ls = Arrays.stream(locks)
                            .map(l -> new RedissonLock(commandExecutor, l.getName()))
                            .toArray(RLock[]::new);
        return RxProxyBuilder.create(commandExecutor, new RedissonMultiLock(ls), RLockRx.class);
    }

    @Override
    public RLockRx getMultiLock(RLock... locks) {
        return RxProxyBuilder.create(commandExecutor, new RedissonMultiLock(locks), RLockRx.class);
    }
    
    @Override
    public RLockRx getRedLock(RLock... locks) {
        return RxProxyBuilder.create(commandExecutor, new RedissonRedLock(locks), RLockRx.class);
    }

    @Override
    public RCountDownLatchRx getCountDownLatch(String name) {
        return RxProxyBuilder.create(commandExecutor, new RedissonCountDownLatch(commandExecutor, name), RCountDownLatchRx.class);
    }

    @Override
    public <K, V> RMapCacheRx<K, V> getMapCache(String name, Codec codec) {
        RMap<K, V> map = new RedissonMapCache<K, V>(codec, evictionScheduler, commandExecutor, name, null, null, null);
        return RxProxyBuilder.create(commandExecutor, map, 
                new RedissonMapCacheRx<K, V>(map, commandExecutor), RMapCacheRx.class);
    }

    @Override
    public <K, V> RMapCacheRx<K, V> getMapCache(String name) {
        RedissonMapCache<K, V> map = new RedissonMapCache<K, V>(evictionScheduler, commandExecutor, name, null, null, null);
        return RxProxyBuilder.create(commandExecutor, map, 
                new RedissonMapCacheRx<K, V>(map, commandExecutor), RMapCacheRx.class);
    }

    @Override
    public <V> RBucketRx<V> getBucket(String name) {
        return RxProxyBuilder.create(commandExecutor, new RedissonBucket<V>(commandExecutor, name), RBucketRx.class);
    }

    @Override
    public <V> RBucketRx<V> getBucket(String name, Codec codec) {
        return RxProxyBuilder.create(commandExecutor, new RedissonBucket<V>(codec, commandExecutor, name), RBucketRx.class);
    }

    @Override
    public RBucketsRx getBuckets() {
        return RxProxyBuilder.create(commandExecutor, new RedissonBuckets(commandExecutor), RBucketsRx.class);
    }

    @Override
    public RBucketsRx getBuckets(Codec codec) {
        return RxProxyBuilder.create(commandExecutor, new RedissonBuckets(codec, commandExecutor), RBucketsRx.class);
    }

    @Override
    public <V> RJsonBucketRx<V> getJsonBucket(String name, JsonCodec<V> codec) {
        return RxProxyBuilder.create(commandExecutor, new RedissonJsonBucket<>(codec, commandExecutor, name), RJsonBucketRx.class);
    }

    @Override
    public <V> RHyperLogLogRx<V> getHyperLogLog(String name) {
        return RxProxyBuilder.create(commandExecutor, new RedissonHyperLogLog<V>(commandExecutor, name), RHyperLogLogRx.class);
    }

    @Override
    public <V> RHyperLogLogRx<V> getHyperLogLog(String name, Codec codec) {
        return RxProxyBuilder.create(commandExecutor, new RedissonHyperLogLog<V>(codec, commandExecutor, name), RHyperLogLogRx.class);
    }

    @Override
    public RIdGeneratorRx getIdGenerator(String name) {
        return RxProxyBuilder.create(commandExecutor, new RedissonIdGenerator(commandExecutor, name), RIdGeneratorRx.class);
    }

    @Override
    public <V> RListRx<V> getList(String name) {
        RedissonList<V> list = new RedissonList<V>(commandExecutor, name, null);
        return RxProxyBuilder.create(commandExecutor, list, 
                new RedissonListRx<V>(list), RListRx.class);
    }

    @Override
    public <V> RListRx<V> getList(String name, Codec codec) {
        RedissonList<V> list = new RedissonList<V>(codec, commandExecutor, name, null);
        return RxProxyBuilder.create(commandExecutor, list, 
                new RedissonListRx<V>(list), RListRx.class);
    }

    @Override
    public <K, V> RListMultimapRx<K, V> getListMultimap(String name) {
        RedissonListMultimap<K, V> listMultimap = new RedissonListMultimap<>(commandExecutor, name);
        return RxProxyBuilder.create(commandExecutor, listMultimap,
                new RedissonListMultimapRx<K, V>(listMultimap, commandExecutor), RListMultimapRx.class);
    }

    @Override
    public <K, V> RListMultimapRx<K, V> getListMultimap(String name, Codec codec) {
        RedissonListMultimap<K, V> listMultimap = new RedissonListMultimap<>(codec, commandExecutor, name);
        return RxProxyBuilder.create(commandExecutor, listMultimap,
                new RedissonListMultimapRx<K, V>(listMultimap, commandExecutor), RListMultimapRx.class);
    }

    @Override
    public <K, V> RListMultimapCacheRx<K, V> getListMultimapCache(String name) {
        RedissonListMultimapCache<K, V> listMultimap = new RedissonListMultimapCache<>(evictionScheduler, commandExecutor, name);
        return RxProxyBuilder.create(commandExecutor, listMultimap,
                new RedissonListMultimapCacheRx<K, V>(listMultimap, commandExecutor), RListMultimapCacheRx.class);
    }

    @Override
    public <K, V> RListMultimapCacheRx<K, V> getListMultimapCache(String name, Codec codec) {
        RedissonListMultimapCache<K, V> listMultimap = new RedissonListMultimapCache<>(evictionScheduler, codec, commandExecutor, name);
        return RxProxyBuilder.create(commandExecutor, listMultimap,
                new RedissonListMultimapCacheRx<K, V>(listMultimap, commandExecutor), RListMultimapCacheRx.class);
    }

    @Override
    public <K, V> RSetMultimapRx<K, V> getSetMultimap(String name) {
        RedissonSetMultimap<K, V> setMultimap = new RedissonSetMultimap<>(commandExecutor, name);
        return RxProxyBuilder.create(commandExecutor, setMultimap,
                new RedissonSetMultimapRx<K, V>(setMultimap, commandExecutor, this), RSetMultimapRx.class);
    }

    @Override
    public <K, V> RSetMultimapRx<K, V> getSetMultimap(String name, Codec codec) {
        RedissonSetMultimap<K, V> setMultimap = new RedissonSetMultimap<>(codec, commandExecutor, name);
        return RxProxyBuilder.create(commandExecutor, setMultimap,
                new RedissonSetMultimapRx<K, V>(setMultimap, commandExecutor, this), RSetMultimapRx.class);
    }

    @Override
    public <K, V> RSetMultimapCacheRx<K, V> getSetMultimapCache(String name) {
        RedissonSetMultimapCache<K, V> setMultimap = new RedissonSetMultimapCache<>(evictionScheduler, commandExecutor, name);
        return RxProxyBuilder.create(commandExecutor, setMultimap,
                new RedissonSetMultimapCacheRx<K, V>(setMultimap, commandExecutor, this), RSetMultimapCacheRx.class);
    }

    @Override
    public <K, V> RSetMultimapCacheRx<K, V> getSetMultimapCache(String name, Codec codec) {
        RedissonSetMultimapCache<K, V> setMultimap = new RedissonSetMultimapCache<>(evictionScheduler, codec, commandExecutor, name);
        return RxProxyBuilder.create(commandExecutor, setMultimap,
                new RedissonSetMultimapCacheRx<K, V>(setMultimap, commandExecutor, this), RSetMultimapCacheRx.class);
    }

    @Override
    public <K, V> RMapRx<K, V> getMap(String name) {
        RedissonMap<K, V> map = new RedissonMap<K, V>(commandExecutor, name, null, null, null);
        return RxProxyBuilder.create(commandExecutor, map, 
                new RedissonMapRx<K, V>(map, commandExecutor), RMapRx.class);
    }

    @Override
    public <K, V> RMapRx<K, V> getMap(String name, Codec codec) {
        RedissonMap<K, V> map = new RedissonMap<K, V>(codec, commandExecutor, name, null, null, null);
        return RxProxyBuilder.create(commandExecutor, map, 
                new RedissonMapRx<K, V>(map, commandExecutor), RMapRx.class);
    }

    @Override
    public <V> RSetRx<V> getSet(String name) {
        RedissonSet<V> set = new RedissonSet<V>(commandExecutor, name, null);
        return RxProxyBuilder.create(commandExecutor, set, 
                new RedissonSetRx<V>(set, this), RSetRx.class);
    }

    @Override
    public <V> RSetRx<V> getSet(String name, Codec codec) {
        RedissonSet<V> set = new RedissonSet<V>(codec, commandExecutor, name, null);
        return RxProxyBuilder.create(commandExecutor, set, 
                new RedissonSetRx<V>(set, this), RSetRx.class);
    }

    @Override
    public <V> RScoredSortedSetRx<V> getScoredSortedSet(String name) {
        RedissonScoredSortedSet<V> set = new RedissonScoredSortedSet<V>(commandExecutor, name, null);
        return RxProxyBuilder.create(commandExecutor, set, 
                new RedissonScoredSortedSetRx<V>(set), RScoredSortedSetRx.class);
    }

    @Override
    public <V> RScoredSortedSetRx<V> getScoredSortedSet(String name, Codec codec) {
        RedissonScoredSortedSet<V> set = new RedissonScoredSortedSet<V>(codec, commandExecutor, name, null);
        return RxProxyBuilder.create(commandExecutor, set, 
                new RedissonScoredSortedSetRx<V>(set), RScoredSortedSetRx.class);
    }

    @Override
    public RLexSortedSetRx getLexSortedSet(String name) {
        RedissonLexSortedSet set = new RedissonLexSortedSet(commandExecutor, name, null);
        return RxProxyBuilder.create(commandExecutor, set, 
                new RedissonLexSortedSetRx(set), RLexSortedSetRx.class);
    }

    @Override
    public RShardedTopicRx getShardedTopic(String name) {
        RShardedTopic topic = new RedissonShardedTopic(commandExecutor, name);
        return RxProxyBuilder.create(commandExecutor, topic, new RedissonTopicRx(topic), RShardedTopicRx.class);
    }

    @Override
    public RShardedTopicRx getShardedTopic(String name, Codec codec) {
        RShardedTopic topic = new RedissonShardedTopic(codec, commandExecutor, name);
        return RxProxyBuilder.create(commandExecutor, topic, new RedissonTopicRx(topic), RShardedTopicRx.class);
    }

    @Override
    public RTopicRx getTopic(String name) {
        RTopic topic = new RedissonTopic(commandExecutor, name);
        return RxProxyBuilder.create(commandExecutor, topic, new RedissonTopicRx(topic), RTopicRx.class);
    }

    @Override
    public RTopicRx getTopic(String name, Codec codec) {
        RTopic topic = new RedissonTopic(codec, commandExecutor, name);
        return RxProxyBuilder.create(commandExecutor, topic, new RedissonTopicRx(topic), RTopicRx.class);
    }

    @Override
    public RReliableTopicRx getReliableTopic(String name) {
        return RxProxyBuilder.create(commandExecutor, new RedissonReliableTopic(commandExecutor, name, null), RReliableTopicRx.class);
    }

    @Override
    public RReliableTopicRx getReliableTopic(String name, Codec codec) {
        return RxProxyBuilder.create(commandExecutor, new RedissonReliableTopic(codec, commandExecutor, name, null), RReliableTopicRx.class);
    }

    @Override
    public RPatternTopicRx getPatternTopic(String pattern) {
        return RxProxyBuilder.create(commandExecutor, new RedissonPatternTopic(commandExecutor, pattern), RPatternTopicRx.class);
    }

    @Override
    public RPatternTopicRx getPatternTopic(String pattern, Codec codec) {
        return RxProxyBuilder.create(commandExecutor, new RedissonPatternTopic(codec, commandExecutor, pattern), RPatternTopicRx.class);
    }

    @Override
    public <V> RQueueRx<V> getQueue(String name) {
        return RxProxyBuilder.create(commandExecutor, new RedissonQueue<V>(commandExecutor, name, null), 
                new RedissonListRx<V>(new RedissonList<V>(commandExecutor, name, null)), RQueueRx.class);
    }

    @Override
    public <V> RQueueRx<V> getQueue(String name, Codec codec) {
        return RxProxyBuilder.create(commandExecutor, new RedissonQueue<V>(codec, commandExecutor, name, null), 
                new RedissonListRx<V>(new RedissonList<V>(codec, commandExecutor, name, null)), RQueueRx.class);
    }

    @Override
    public <V> RRingBufferRx<V> getRingBuffer(String name) {
        return RxProxyBuilder.create(commandExecutor, new RedissonRingBuffer<V>(commandExecutor, name, null), RRingBufferRx.class);
    }

    @Override
    public <V> RRingBufferRx<V> getRingBuffer(String name, Codec codec) {
        return RxProxyBuilder.create(commandExecutor, new RedissonRingBuffer<V>(codec, commandExecutor, name, null), RRingBufferRx.class);
    }

    @Override
    public <V> RBlockingQueueRx<V> getBlockingQueue(String name) {
        RedissonBlockingQueue<V> queue = new RedissonBlockingQueue<V>(commandExecutor, name, null);
        return RxProxyBuilder.create(commandExecutor, queue, 
                new RedissonBlockingQueueRx<V>(queue), RBlockingQueueRx.class);
    }

    @Override
    public <V> RBlockingQueueRx<V> getBlockingQueue(String name, Codec codec) {
        RedissonBlockingQueue<V> queue = new RedissonBlockingQueue<V>(codec, commandExecutor, name, null);
        return RxProxyBuilder.create(commandExecutor, queue, 
                new RedissonBlockingQueueRx<V>(queue), RBlockingQueueRx.class);
    }

    @Override
    public <V> RDequeRx<V> getDeque(String name) {
        RedissonDeque<V> queue = new RedissonDeque<V>(commandExecutor, name, null);
        return RxProxyBuilder.create(commandExecutor, queue, 
                new RedissonListRx<V>(queue), RDequeRx.class);
    }

    @Override
    public <V> RDequeRx<V> getDeque(String name, Codec codec) {
        RedissonDeque<V> queue = new RedissonDeque<V>(codec, commandExecutor, name, null);
        return RxProxyBuilder.create(commandExecutor, queue, 
                new RedissonListRx<V>(queue), RDequeRx.class);
    }

    @Override
    public <V, L> RTimeSeriesRx<V, L> getTimeSeries(String name) {
        RTimeSeries<V, L> timeSeries = new RedissonTimeSeries<V, L>(evictionScheduler, commandExecutor, name);
        return RxProxyBuilder.create(commandExecutor, timeSeries,
                new RedissonTimeSeriesRx<V, L>(timeSeries, this), RTimeSeriesRx.class);
    }

    @Override
    public <V, L> RTimeSeriesRx<V, L> getTimeSeries(String name, Codec codec) {
        RTimeSeries<V, L> timeSeries = new RedissonTimeSeries<V, L>(codec, evictionScheduler, commandExecutor, name);
        return RxProxyBuilder.create(commandExecutor, timeSeries,
                new RedissonTimeSeriesRx<V, L>(timeSeries, this), RTimeSeriesRx.class);
    }

    @Override
    public <V> RSetCacheRx<V> getSetCache(String name) {
        RSetCache<V> set = new RedissonSetCache<V>(evictionScheduler, commandExecutor, name, null);
        return RxProxyBuilder.create(commandExecutor, set, 
                new RedissonSetCacheRx<V>(set, this), RSetCacheRx.class);
    }

    @Override
    public <V> RSetCacheRx<V> getSetCache(String name, Codec codec) {
        RSetCache<V> set = new RedissonSetCache<V>(codec, evictionScheduler, commandExecutor, name, null);
        return RxProxyBuilder.create(commandExecutor, set, 
                new RedissonSetCacheRx<V>(set, this), RSetCacheRx.class);
    }

    @Override
    public RAtomicLongRx getAtomicLong(String name) {
        return RxProxyBuilder.create(commandExecutor, new RedissonAtomicLong(commandExecutor, name), RAtomicLongRx.class);
    }

    @Override
    public RAtomicDoubleRx getAtomicDouble(String name) {
        return RxProxyBuilder.create(commandExecutor, new RedissonAtomicDouble(commandExecutor, name), RAtomicDoubleRx.class);
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
    public RBitSetRx getBitSet(String name) {
        return RxProxyBuilder.create(commandExecutor, new RedissonBitSet(commandExecutor, name), RBitSetRx.class);
    }

    @Override
    public RFunctionRx getFunction() {
        return RxProxyBuilder.create(commandExecutor, new RedissonFuction(commandExecutor), RFunctionRx.class);
    }

    @Override
    public RFunctionRx getFunction(Codec codec) {
        return RxProxyBuilder.create(commandExecutor, new RedissonFuction(commandExecutor, codec), RFunctionRx.class);
    }

    @Override
    public RScriptRx getScript() {
        return RxProxyBuilder.create(commandExecutor, new RedissonScript(commandExecutor), RScriptRx.class);
    }
    
    @Override
    public RScriptRx getScript(Codec codec) {
        return RxProxyBuilder.create(commandExecutor, new RedissonScript(commandExecutor, codec), RScriptRx.class);
    }

    @Override
    public RBatchRx createBatch() {
        return createBatch(BatchOptions.defaults());
    }
    
    @Override
    public RBatchRx createBatch(BatchOptions options) {
        return new RedissonBatchRx(evictionScheduler, connectionManager, commandExecutor, options);
    }

    @Override
    public RKeysRx getKeys() {
        return RxProxyBuilder.create(commandExecutor, new RedissonKeys(commandExecutor), new RedissonKeysRx(commandExecutor), RKeysRx.class);
    }

    @Override
    public Config getConfig() {
        return connectionManager.getServiceManager().getCfg();
    }

    @Override
    public NodesGroup<Node> getNodesGroup() {
        return new RedisNodes<Node>(connectionManager, connectionManager.getServiceManager(), commandExecutor);
    }

    @Override
    public NodesGroup<ClusterNode> getClusterNodesGroup() {
        if (!connectionManager.isClusterMode()) {
            throw new IllegalStateException("Redisson not in cluster mode!");
        }
        return new RedisNodes<ClusterNode>(connectionManager, connectionManager.getServiceManager(), commandExecutor);
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
    public <K, V> RMapCacheRx<K, V> getMapCache(String name, Codec codec, MapOptions<K, V> options) {
        RedissonMapCache<K, V> map = new RedissonMapCache<K, V>(codec, evictionScheduler, commandExecutor, name, null, options, writeBehindService);
        return RxProxyBuilder.create(commandExecutor, map, 
                new RedissonMapCacheRx<K, V>(map, commandExecutor), RMapCacheRx.class);
    }


    @Override
    public <K, V> RMapCacheRx<K, V> getMapCache(String name, MapOptions<K, V> options) {
        RMap<K, V> map = new RedissonMapCache<K, V>(evictionScheduler, commandExecutor, name, null, options, writeBehindService);
        return RxProxyBuilder.create(commandExecutor, map, 
                new RedissonMapCacheRx<K, V>(map, commandExecutor), RMapCacheRx.class);
    }

    @Override
    public <K, V> RMapRx<K, V> getMap(String name, MapOptions<K, V> options) {
        RMap<K, V> map = new RedissonMap<K, V>(commandExecutor, name, null, options, writeBehindService);
        return RxProxyBuilder.create(commandExecutor, map, 
                new RedissonMapRx<K, V>(map, commandExecutor), RMapRx.class);
    }


    @Override
    public <K, V> RMapRx<K, V> getMap(String name, Codec codec, MapOptions<K, V> options) {
        RMap<K, V> map = new RedissonMap<K, V>(codec, commandExecutor, name, null, options, writeBehindService);
        return RxProxyBuilder.create(commandExecutor, map, 
                new RedissonMapRx<K, V>(map, commandExecutor), RMapRx.class);
    }

    @Override
    public <K, V> RLocalCachedMapRx<K, V> getLocalCachedMap(String name, LocalCachedMapOptions<K, V> options) {
        RMap<K, V> map = new RedissonLocalCachedMap<>(commandExecutor, name, options, evictionScheduler, null, writeBehindService);
        return RxProxyBuilder.create(commandExecutor, map,
                new RedissonMapRx<>(map, commandExecutor), RLocalCachedMapRx.class);
    }

    @Override
    public <K, V> RLocalCachedMapRx<K, V> getLocalCachedMap(String name, Codec codec, LocalCachedMapOptions<K, V> options) {
        RMap<K, V> map = new RedissonLocalCachedMap<>(codec, commandExecutor, name, options, evictionScheduler, null, writeBehindService);
        return RxProxyBuilder.create(commandExecutor, map,
                new RedissonMapRx<>(map, commandExecutor), RLocalCachedMapRx.class);
    }

    @Override
    public RTransactionRx createTransaction(TransactionOptions options) {
        return new RedissonTransactionRx(commandExecutor, options);
    }

    @Override
    public <V> RBlockingDequeRx<V> getBlockingDeque(String name) {
        RedissonBlockingDeque<V> deque = new RedissonBlockingDeque<V>(commandExecutor, name, null);
        return RxProxyBuilder.create(commandExecutor, deque, 
                new RedissonBlockingDequeRx<V>(deque), RBlockingDequeRx.class);
    }

    @Override
    public <V> RBlockingDequeRx<V> getBlockingDeque(String name, Codec codec) {
        RedissonBlockingDeque<V> deque = new RedissonBlockingDeque<V>(codec, commandExecutor, name, null);
        return RxProxyBuilder.create(commandExecutor, deque, 
                new RedissonBlockingDequeRx<V>(deque), RBlockingDequeRx.class);
    }

    @Override
    public <V> RTransferQueueRx<V> getTransferQueue(String name) {
        String remoteName = RedissonObject.suffixName(name, "remoteService");
        RRemoteService service = getRemoteService(remoteName);
        RedissonTransferQueue<V> queue = new RedissonTransferQueue<V>(commandExecutor, name, service);
        return RxProxyBuilder.create(commandExecutor, queue,
                new RedissonTransferQueueRx<V>(queue), RTransferQueueRx.class);
    }

    @Override
    public <V> RTransferQueueRx<V> getTransferQueue(String name, Codec codec) {
        String remoteName = RedissonObject.suffixName(name, "remoteService");
        RRemoteService service = getRemoteService(remoteName);
        RedissonTransferQueue<V> queue = new RedissonTransferQueue<V>(codec, commandExecutor, name, service);
        return RxProxyBuilder.create(commandExecutor, queue,
                new RedissonTransferQueueRx<V>(queue), RTransferQueueRx.class);
    }

    @Override
    public String getId() {
        return commandExecutor.getServiceManager().getId();
    }
    
}
