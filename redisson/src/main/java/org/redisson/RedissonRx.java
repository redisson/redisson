/**
 * Copyright 2018 Nikita Koksharov
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

import org.redisson.api.ClusterNode;
import org.redisson.api.MapOptions;
import org.redisson.api.Node;
import org.redisson.api.NodesGroup;
import org.redisson.api.RBucketRx;
import org.redisson.api.RKeysRx;
import org.redisson.api.RMapCacheRx;
import org.redisson.api.RMapRx;
import org.redisson.api.RSetRx;
import org.redisson.api.RedissonRxClient;
import org.redisson.client.codec.Codec;
import org.redisson.codec.ReferenceCodecProvider;
import org.redisson.config.Config;
import org.redisson.config.ConfigSupport;
import org.redisson.connection.ConnectionManager;
import org.redisson.eviction.EvictionScheduler;
import org.redisson.pubsub.SemaphorePubSub;
import org.redisson.rx.CommandRxExecutor;
import org.redisson.rx.CommandRxService;
import org.redisson.rx.RedissonKeysRx;
import org.redisson.rx.RedissonMapCacheRx;
import org.redisson.rx.RedissonMapRx;
import org.redisson.rx.RedissonSetRx;
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
    protected final ReferenceCodecProvider codecProvider;

    protected final SemaphorePubSub semaphorePubSub = new SemaphorePubSub();

    protected RedissonRx(Config config) {
        this.config = config;
        Config configCopy = new Config(config);

        connectionManager = ConfigSupport.createConnectionManager(configCopy);
        commandExecutor = new CommandRxService(connectionManager);
        evictionScheduler = new EvictionScheduler(commandExecutor);
        codecProvider = config.getReferenceCodecProvider();
    }
    
//    @Override
//    public <K, V> RStreamReactive<K, V> getStream(String name) {
//        return ReactiveProxyBuilder.create(commandExecutor, new RedissonStream<K, V>(commandExecutor, name), RStreamReactive.class);
//    }
//
//    @Override
//    public <K, V> RStreamReactive<K, V> getStream(String name, Codec codec) {
//        return ReactiveProxyBuilder.create(commandExecutor, new RedissonStream<K, V>(codec, commandExecutor, name), RStreamReactive.class);
//    }
//
//    @Override
//    public <V> RGeoReactive<V> getGeo(String name) {
//        return ReactiveProxyBuilder.create(commandExecutor, new RedissonGeo<V>(commandExecutor, name, null), 
//                new RedissonScoredSortedSetReactive<V>(commandExecutor, name), RGeoReactive.class);
//    }
//    
//    @Override
//    public <V> RGeoReactive<V> getGeo(String name, Codec codec) {
//        return ReactiveProxyBuilder.create(commandExecutor, new RedissonGeo<V>(codec, commandExecutor, name, null), 
//                new RedissonScoredSortedSetReactive<V>(codec, commandExecutor, name), RGeoReactive.class);
//    }
//    
//    @Override
//    public RLockReactive getFairLock(String name) {
//        return ReactiveProxyBuilder.create(commandExecutor, new RedissonFairLock(commandExecutor, name), RLockReactive.class);
//    }
//    
//    @Override
//    public RRateLimiterReactive getRateLimiter(String name) {
//        return ReactiveProxyBuilder.create(commandExecutor, new RedissonRateLimiter(commandExecutor, name), RRateLimiterReactive.class);
//    }
//    
//    @Override
//    public RSemaphoreReactive getSemaphore(String name) {
//        return ReactiveProxyBuilder.create(commandExecutor, new RedissonSemaphore(commandExecutor, name, semaphorePubSub), RSemaphoreReactive.class);
//    }
//
//    @Override
//    public RPermitExpirableSemaphoreReactive getPermitExpirableSemaphore(String name) {
//        return ReactiveProxyBuilder.create(commandExecutor, new RedissonPermitExpirableSemaphore(commandExecutor, name, semaphorePubSub), RPermitExpirableSemaphoreReactive.class);
//    }
//
//    @Override
//    public RReadWriteLockReactive getReadWriteLock(String name) {
//        return new RedissonReadWriteLockReactive(commandExecutor, name);
//    }
//
//    @Override
//    public RLockReactive getLock(String name) {
//        return ReactiveProxyBuilder.create(commandExecutor, new RedissonLock(commandExecutor, name), RLockReactive.class);
//    }
//
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

//    @Override
//    public <V> List<RBucketReactive<V>> findBuckets(String pattern) {
//        RKeys redissonKeys = new RedissonKeys(commandExecutor);
//        Iterable<String> keys = redissonKeys.getKeysByPattern(pattern);
//
//        List<RBucketReactive<V>> buckets = new ArrayList<RBucketReactive<V>>();
//        for (Object key : keys) {
//            if(key != null) {
//                buckets.add(this.<V>getBucket(key.toString()));
//            }
//        }
//        return buckets;
//    }
//
//    @Override
//    public <V> RHyperLogLogReactive<V> getHyperLogLog(String name) {
//        return ReactiveProxyBuilder.create(commandExecutor, new RedissonHyperLogLog<V>(commandExecutor, name), RHyperLogLogReactive.class);
//    }
//
//    @Override
//    public <V> RHyperLogLogReactive<V> getHyperLogLog(String name, Codec codec) {
//        return ReactiveProxyBuilder.create(commandExecutor, new RedissonHyperLogLog<V>(codec, commandExecutor, name), RHyperLogLogReactive.class);
//    }
//
//    @Override
//    public <V> RListReactive<V> getList(String name) {
//        return ReactiveProxyBuilder.create(commandExecutor, new RedissonList<V>(commandExecutor, name, null), 
//                new RedissonListReactive<V>(commandExecutor, name), RListReactive.class);
//    }
//
//    @Override
//    public <V> RListReactive<V> getList(String name, Codec codec) {
//        return ReactiveProxyBuilder.create(commandExecutor, new RedissonList<V>(codec, commandExecutor, name, null), 
//                new RedissonListReactive<V>(codec, commandExecutor, name), RListReactive.class);
//    }
//
//    @Override
//    public <K, V> RListMultimapReactive<K, V> getListMultimap(String name) {
//        return ReactiveProxyBuilder.create(commandExecutor, new RedissonListMultimap<K, V>(commandExecutor, name), 
//                new RedissonListMultimapReactive<K, V>(commandExecutor, name), RListMultimapReactive.class);
//    }
//
//    @Override
//    public <K, V> RListMultimapReactive<K, V> getListMultimap(String name, Codec codec) {
//        return ReactiveProxyBuilder.create(commandExecutor, new RedissonListMultimap<K, V>(codec, commandExecutor, name), 
//                new RedissonListMultimapReactive<K, V>(codec, commandExecutor, name), RListMultimapReactive.class);
//    }
//
//    @Override
//    public <K, V> RSetMultimapReactive<K, V> getSetMultimap(String name) {
//        return ReactiveProxyBuilder.create(commandExecutor, new RedissonSetMultimap<K, V>(commandExecutor, name), 
//                new RedissonSetMultimapReactive<K, V>(commandExecutor, name), RSetMultimapReactive.class);
//    }
//
//    @Override
//    public <K, V> RSetMultimapReactive<K, V> getSetMultimap(String name, Codec codec) {
//        return ReactiveProxyBuilder.create(commandExecutor, new RedissonSetMultimap<K, V>(codec, commandExecutor, name), 
//                new RedissonSetMultimapReactive<K, V>(codec, commandExecutor, name), RSetMultimapReactive.class);
//    }

    @Override
    public <K, V> RMapRx<K, V> getMap(String name) {
        RedissonMap<K, V> map = new RedissonMap<K, V>(commandExecutor, name, null, null);
        return RxProxyBuilder.create(commandExecutor, map, 
                new RedissonMapRx<K, V>(map), RMapRx.class);
    }

    @Override
    public <K, V> RMapRx<K, V> getMap(String name, Codec codec) {
        RedissonMap<K, V> map = new RedissonMap<K, V>(codec, commandExecutor, name, null, null);
        return RxProxyBuilder.create(commandExecutor, map, 
                new RedissonMapRx<K, V>(map), RMapRx.class);
    }

    @Override
    public <V> RSetRx<V> getSet(String name) {
        RedissonSet<V> set = new RedissonSet<V>(commandExecutor, name, null);
        return RxProxyBuilder.create(commandExecutor, set, 
                new RedissonSetRx<V>(set), RSetRx.class);
    }

    @Override
    public <V> RSetRx<V> getSet(String name, Codec codec) {
        RedissonSet<V> set = new RedissonSet<V>(codec, commandExecutor, name, null);
        return RxProxyBuilder.create(commandExecutor, set, 
                new RedissonSetRx<V>(set), RSetRx.class);
    }

//    @Override
//    public <V> RScoredSortedSetReactive<V> getScoredSortedSet(String name) {
//        return ReactiveProxyBuilder.create(commandExecutor, new RedissonScoredSortedSet<V>(commandExecutor, name, null), 
//                new RedissonScoredSortedSetReactive<V>(commandExecutor, name), RScoredSortedSetReactive.class);
//    }
//
//    @Override
//    public <V> RScoredSortedSetReactive<V> getScoredSortedSet(String name, Codec codec) {
//        return ReactiveProxyBuilder.create(commandExecutor, new RedissonScoredSortedSet<V>(codec, commandExecutor, name, null), 
//                new RedissonScoredSortedSetReactive<V>(codec, commandExecutor, name), RScoredSortedSetReactive.class);
//    }
//
//    @Override
//    public RLexSortedSetReactive getLexSortedSet(String name) {
//        RedissonLexSortedSet set = new RedissonLexSortedSet(commandExecutor, name, null);
//        return ReactiveProxyBuilder.create(commandExecutor, set, 
//                new RedissonLexSortedSetReactive(set), 
//                RLexSortedSetReactive.class);
//    }
//
//    @Override
//    public <M> RTopicReactive<M> getTopic(String name) {
//        return new RedissonTopicReactive<M>(commandExecutor, name);
//    }
//
//    @Override
//    public <M> RTopicReactive<M> getTopic(String name, Codec codec) {
//        return new RedissonTopicReactive<M>(codec, commandExecutor, name);
//    }
//
//    @Override
//    public <M> RPatternTopicReactive<M> getPatternTopic(String pattern) {
//        return new RedissonPatternTopicReactive<M>(commandExecutor, pattern);
//    }
//
//    @Override
//    public <M> RPatternTopicReactive<M> getPatternTopic(String pattern, Codec codec) {
//        return new RedissonPatternTopicReactive<M>(codec, commandExecutor, pattern);
//    }
//
//    @Override
//    public <V> RQueueReactive<V> getQueue(String name) {
//        return ReactiveProxyBuilder.create(commandExecutor, new RedissonQueue<V>(commandExecutor, name, null), 
//                new RedissonListReactive<V>(commandExecutor, name), RQueueReactive.class);
//    }
//
//    @Override
//    public <V> RQueueReactive<V> getQueue(String name, Codec codec) {
//        return ReactiveProxyBuilder.create(commandExecutor, new RedissonQueue<V>(codec, commandExecutor, name, null), 
//                new RedissonListReactive<V>(codec,commandExecutor, name), RQueueReactive.class);
//    }
//
//    @Override
//    public <V> RBlockingQueueReactive<V> getBlockingQueue(String name) {
//        return ReactiveProxyBuilder.create(commandExecutor, new RedissonBlockingQueue<V>(commandExecutor, name, null), 
//                new RedissonListReactive<V>(commandExecutor, name), RBlockingQueueReactive.class);
//    }
//
//    @Override
//    public <V> RBlockingQueueReactive<V> getBlockingQueue(String name, Codec codec) {
//        return ReactiveProxyBuilder.create(commandExecutor, new RedissonBlockingQueue<V>(codec, commandExecutor, name, null), 
//                new RedissonListReactive<V>(codec, commandExecutor, name), RBlockingQueueReactive.class);
//    }
//
//    @Override
//    public <V> RDequeReactive<V> getDeque(String name) {
//        return ReactiveProxyBuilder.create(commandExecutor, new RedissonDeque<V>(commandExecutor, name, null), 
//                new RedissonListReactive<V>(commandExecutor, name), RDequeReactive.class);
//    }
//
//    @Override
//    public <V> RDequeReactive<V> getDeque(String name, Codec codec) {
//        return ReactiveProxyBuilder.create(commandExecutor, new RedissonDeque<V>(codec, commandExecutor, name, null), 
//                new RedissonListReactive<V>(codec, commandExecutor, name), RDequeReactive.class);
//    }
//
//    @Override
//    public <V> RSetCacheReactive<V> getSetCache(String name) {
//        RSetCache<V> set = new RedissonSetCache<V>(evictionScheduler, commandExecutor, name, null);
//        return ReactiveProxyBuilder.create(commandExecutor, set, 
//                new RedissonSetCacheReactive<V>(set), RSetCacheReactive.class);
//    }
//
//    @Override
//    public <V> RSetCacheReactive<V> getSetCache(String name, Codec codec) {
//        RSetCache<V> set = new RedissonSetCache<V>(codec, evictionScheduler, commandExecutor, name, null);
//        return ReactiveProxyBuilder.create(commandExecutor, set, 
//                new RedissonSetCacheReactive<V>(set), RSetCacheReactive.class);
//    }
//
//    @Override
//    public RAtomicLongReactive getAtomicLong(String name) {
//        return ReactiveProxyBuilder.create(commandExecutor, new RedissonAtomicLong(commandExecutor, name), RAtomicLongReactive.class);
//    }
//
//    @Override
//    public RAtomicDoubleReactive getAtomicDouble(String name) {
//        return ReactiveProxyBuilder.create(commandExecutor, new RedissonAtomicDouble(commandExecutor, name), RAtomicDoubleReactive.class);
//    }
//
//    @Override
//    public RBitSetReactive getBitSet(String name) {
//        return ReactiveProxyBuilder.create(commandExecutor, new RedissonBitSet(commandExecutor, name), RBitSetReactive.class);
//    }
//
//    @Override
//    public RScriptReactive getScript() {
//        return ReactiveProxyBuilder.create(commandExecutor, new RedissonScript(commandExecutor), RScriptReactive.class);
//    }
//
//    @Override
//    public RBatchReactive createBatch(BatchOptions options) {
//        RedissonBatchReactive batch = new RedissonBatchReactive(evictionScheduler, connectionManager, options);
//        if (config.isReferenceEnabled()) {
//            batch.enableRedissonReferenceSupport(this);
//        }
//        return batch;
//    }
//
//    @Override
//    public RBatchReactive createBatch() {
//        return createBatch(BatchOptions.defaults());
//    }

    @Override
    public RKeysRx getKeys() {
        return RxProxyBuilder.create(commandExecutor, new RedissonKeys(commandExecutor), new RedissonKeysRx(commandExecutor), RKeysRx.class);
    }

    @Override
    public Config getConfig() {
        return config;
    }

    @Override
    public ReferenceCodecProvider getCodecProvider() {
        return codecProvider;
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

//    protected void enableRedissonReferenceSupport() {
//        this.commandExecutor.enableRedissonReferenceSupport(this);
//    }

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
                new RedissonMapRx<K, V>(map), RMapRx.class);
    }


    @Override
    public <K, V> RMapRx<K, V> getMap(String name, Codec codec, MapOptions<K, V> options) {
        RedissonMap<K, V> map = new RedissonMap<K, V>(codec, commandExecutor, name, null, options);
        return RxProxyBuilder.create(commandExecutor, map, 
                new RedissonMapRx<K, V>(map), RMapRx.class);
    }

//    @Override
//    public RTransactionReactive createTransaction(TransactionOptions options) {
//        return new RedissonTransactionReactive(commandExecutor, options);
//    }
//
//    @Override
//    public <V> RBlockingDequeReactive<V> getBlockingDeque(String name) {
//        return ReactiveProxyBuilder.create(commandExecutor, new RedissonBlockingDeque<V>(commandExecutor, name, null), 
//                new RedissonListReactive<V>(commandExecutor, name), RBlockingDequeReactive.class);
//    }
//
//    @Override
//    public <V> RBlockingDequeReactive<V> getBlockingDeque(String name, Codec codec) {
//        return ReactiveProxyBuilder.create(commandExecutor, new RedissonBlockingDeque<V>(codec, commandExecutor, name, null), 
//                new RedissonListReactive<V>(codec, commandExecutor, name), RBlockingDequeReactive.class);
//    }
}
