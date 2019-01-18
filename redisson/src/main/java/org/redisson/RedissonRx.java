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
package org.redisson;

import org.redisson.api.BatchOptions;
import org.redisson.api.ClusterNode;
import org.redisson.api.MapOptions;
import org.redisson.api.Node;
import org.redisson.api.NodesGroup;
import org.redisson.api.RAtomicDoubleRx;
import org.redisson.api.RAtomicLongRx;
import org.redisson.api.RBatchRx;
import org.redisson.api.RBitSetRx;
import org.redisson.api.RBlockingDequeRx;
import org.redisson.api.RBlockingQueueRx;
import org.redisson.api.RBucketRx;
import org.redisson.api.RDequeRx;
import org.redisson.api.RGeoRx;
import org.redisson.api.RHyperLogLogRx;
import org.redisson.api.RKeysRx;
import org.redisson.api.RLexSortedSetRx;
import org.redisson.api.RListMultimapRx;
import org.redisson.api.RListRx;
import org.redisson.api.RLockRx;
import org.redisson.api.RMapCacheRx;
import org.redisson.api.RMapRx;
import org.redisson.api.RPatternTopicRx;
import org.redisson.api.RPermitExpirableSemaphoreRx;
import org.redisson.api.RQueueRx;
import org.redisson.api.RRateLimiterRx;
import org.redisson.api.RReadWriteLockRx;
import org.redisson.api.RScoredSortedSetRx;
import org.redisson.api.RScriptRx;
import org.redisson.api.RSemaphoreRx;
import org.redisson.api.RSetCache;
import org.redisson.api.RSetCacheRx;
import org.redisson.api.RSetMultimapRx;
import org.redisson.api.RSetRx;
import org.redisson.api.RStreamRx;
import org.redisson.api.RTopic;
import org.redisson.api.RTopicRx;
import org.redisson.api.RTransactionRx;
import org.redisson.api.RedissonRxClient;
import org.redisson.api.TransactionOptions;
import org.redisson.client.codec.Codec;
import org.redisson.config.Config;
import org.redisson.config.ConfigSupport;
import org.redisson.connection.ConnectionManager;
import org.redisson.eviction.EvictionScheduler;
import org.redisson.pubsub.SemaphorePubSub;
import org.redisson.rx.CommandRxExecutor;
import org.redisson.rx.CommandRxService;
import org.redisson.rx.RedissonBatchRx;
import org.redisson.rx.RedissonBlockingDequeRx;
import org.redisson.rx.RedissonBlockingQueueRx;
import org.redisson.rx.RedissonKeysRx;
import org.redisson.rx.RedissonLexSortedSetRx;
import org.redisson.rx.RedissonListMultimapRx;
import org.redisson.rx.RedissonListRx;
import org.redisson.rx.RedissonMapCacheRx;
import org.redisson.rx.RedissonMapRx;
import org.redisson.rx.RedissonReadWriteLockRx;
import org.redisson.rx.RedissonScoredSortedSetRx;
import org.redisson.rx.RedissonSetCacheRx;
import org.redisson.rx.RedissonSetMultimapRx;
import org.redisson.rx.RedissonSetRx;
import org.redisson.rx.RedissonTopicRx;
import org.redisson.rx.RedissonTransactionRx;
import org.redisson.rx.RxProxyBuilder;

/**
 * Main infrastructure class allows to get access
 * to all Redisson objects on top of Redis server.
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonRx implements RedissonRxClient {

    protected final EvictionScheduler evictionScheduler;
    protected final CommandRxExecutor commandExecutor;
    protected final ConnectionManager connectionManager;
    protected final Config config;

    protected final SemaphorePubSub semaphorePubSub = new SemaphorePubSub();

    protected RedissonRx(Config config) {
        this.config = config;
        Config configCopy = new Config(config);

        connectionManager = ConfigSupport.createConnectionManager(configCopy);
        commandExecutor = new CommandRxService(connectionManager);
        evictionScheduler = new EvictionScheduler(commandExecutor);
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
    public RSemaphoreRx getSemaphore(String name) {
        return RxProxyBuilder.create(commandExecutor, new RedissonSemaphore(commandExecutor, name, semaphorePubSub), RSemaphoreRx.class);
    }

    @Override
    public RPermitExpirableSemaphoreRx getPermitExpirableSemaphore(String name) {
        return RxProxyBuilder.create(commandExecutor, new RedissonPermitExpirableSemaphore(commandExecutor, name, semaphorePubSub), RPermitExpirableSemaphoreRx.class);
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
    public <K, V> RMapCacheRx<K, V> getMapCache(String name, Codec codec) {
        RedissonMapCache<K, V> map = new RedissonMapCache<K, V>(codec, evictionScheduler, commandExecutor, name, null, null);
        return RxProxyBuilder.create(commandExecutor, map, 
                new RedissonMapCacheRx<K, V>(map), RMapCacheRx.class);
    }

    @Override
    public <K, V> RMapCacheRx<K, V> getMapCache(String name) {
        RedissonMapCache<K, V> map = new RedissonMapCache<K, V>(evictionScheduler, commandExecutor, name, null, null);
        return RxProxyBuilder.create(commandExecutor, map, 
                new RedissonMapCacheRx<K, V>(map), RMapCacheRx.class);
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
    public <V> RHyperLogLogRx<V> getHyperLogLog(String name) {
        return RxProxyBuilder.create(commandExecutor, new RedissonHyperLogLog<V>(commandExecutor, name), RHyperLogLogRx.class);
    }

    @Override
    public <V> RHyperLogLogRx<V> getHyperLogLog(String name, Codec codec) {
        return RxProxyBuilder.create(commandExecutor, new RedissonHyperLogLog<V>(codec, commandExecutor, name), RHyperLogLogRx.class);
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
        return RxProxyBuilder.create(commandExecutor, new RedissonListMultimap<K, V>(commandExecutor, name), 
                new RedissonListMultimapRx<K, V>(commandExecutor, name), RListMultimapRx.class);
    }

    @Override
    public <K, V> RListMultimapRx<K, V> getListMultimap(String name, Codec codec) {
        return RxProxyBuilder.create(commandExecutor, new RedissonListMultimap<K, V>(codec, commandExecutor, name), 
                new RedissonListMultimapRx<K, V>(codec, commandExecutor, name), RListMultimapRx.class);
    }

    @Override
    public <K, V> RSetMultimapRx<K, V> getSetMultimap(String name) {
        return RxProxyBuilder.create(commandExecutor, new RedissonSetMultimap<K, V>(commandExecutor, name), 
                new RedissonSetMultimapRx<K, V>(commandExecutor, name, this), RSetMultimapRx.class);
    }

    @Override
    public <K, V> RSetMultimapRx<K, V> getSetMultimap(String name, Codec codec) {
        return RxProxyBuilder.create(commandExecutor, new RedissonSetMultimap<K, V>(codec, commandExecutor, name), 
                new RedissonSetMultimapRx<K, V>(codec, commandExecutor, name, this), RSetMultimapRx.class);
    }

    @Override
    public <K, V> RMapRx<K, V> getMap(String name) {
        RedissonMap<K, V> map = new RedissonMap<K, V>(commandExecutor, name, null, null);
        return RxProxyBuilder.create(commandExecutor, map, 
                new RedissonMapRx<K, V>(map, this), RMapRx.class);
    }

    @Override
    public <K, V> RMapRx<K, V> getMap(String name, Codec codec) {
        RedissonMap<K, V> map = new RedissonMap<K, V>(codec, commandExecutor, name, null, null);
        return RxProxyBuilder.create(commandExecutor, map, 
                new RedissonMapRx<K, V>(map, this), RMapRx.class);
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
                new RedissonListRx<V>(new RedissonList<V>(codec,commandExecutor, name, null)), RQueueRx.class);
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
    public RBitSetRx getBitSet(String name) {
        return RxProxyBuilder.create(commandExecutor, new RedissonBitSet(commandExecutor, name), RBitSetRx.class);
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
    public RBatchRx createBatch(BatchOptions options) {
        RedissonBatchRx batch = new RedissonBatchRx(evictionScheduler, connectionManager, options);
        if (config.isReferenceEnabled()) {
            batch.enableRedissonReferenceSupport(this);
        }
        return batch;
    }

    @Override
    public RKeysRx getKeys() {
        return RxProxyBuilder.create(commandExecutor, new RedissonKeys(commandExecutor), new RedissonKeysRx(commandExecutor), RKeysRx.class);
    }

    @Override
    public Config getConfig() {
        return config;
    }

    @Override
    public NodesGroup<Node> getNodesGroup() {
        return new RedisNodes<Node>(connectionManager);
    }

    @Override
    public NodesGroup<ClusterNode> getClusterNodesGroup() {
        if (!connectionManager.isClusterMode()) {
            throw new IllegalStateException("Redisson not in cluster mode!");
        }
        return new RedisNodes<ClusterNode>(connectionManager);
    }

    @Override
    public void shutdown() {
        connectionManager.shutdown();
    }

    @Override
    public boolean isShutdown() {
        return connectionManager.isShutdown();
    }

    @Override
    public boolean isShuttingDown() {
        return connectionManager.isShuttingDown();
    }

    protected void enableRedissonReferenceSupport() {
        this.commandExecutor.enableRedissonReferenceSupport(this);
    }

    @Override
    public <K, V> RMapCacheRx<K, V> getMapCache(String name, Codec codec, MapOptions<K, V> options) {
        RedissonMapCache<K, V> map = new RedissonMapCache<K, V>(codec, evictionScheduler, commandExecutor, name, null, options);
        return RxProxyBuilder.create(commandExecutor, map, 
                new RedissonMapCacheRx<K, V>(map), RMapCacheRx.class);
    }


    @Override
    public <K, V> RMapCacheRx<K, V> getMapCache(String name, MapOptions<K, V> options) {
        RedissonMapCache<K, V> map = new RedissonMapCache<K, V>(evictionScheduler, commandExecutor, name, null, options);
        return RxProxyBuilder.create(commandExecutor, map, 
                new RedissonMapCacheRx<K, V>(map), RMapCacheRx.class);
    }

    @Override
    public <K, V> RMapRx<K, V> getMap(String name, MapOptions<K, V> options) {
        RedissonMap<K, V> map = new RedissonMap<K, V>(commandExecutor, name, null, options);
        return RxProxyBuilder.create(commandExecutor, map, 
                new RedissonMapRx<K, V>(map, this), RMapRx.class);
    }


    @Override
    public <K, V> RMapRx<K, V> getMap(String name, Codec codec, MapOptions<K, V> options) {
        RedissonMap<K, V> map = new RedissonMap<K, V>(codec, commandExecutor, name, null, options);
        return RxProxyBuilder.create(commandExecutor, map, 
                new RedissonMapRx<K, V>(map, this), RMapRx.class);
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
    
}
