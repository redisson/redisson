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

import java.util.ArrayList;
import java.util.List;

import org.redisson.api.BatchOptions;
import org.redisson.api.ClusterNode;
import org.redisson.api.MapOptions;
import org.redisson.api.Node;
import org.redisson.api.NodesGroup;
import org.redisson.api.RAtomicDoubleReactive;
import org.redisson.api.RAtomicLongReactive;
import org.redisson.api.RBatchReactive;
import org.redisson.api.RBitSetReactive;
import org.redisson.api.RBlockingDequeReactive;
import org.redisson.api.RBlockingQueueReactive;
import org.redisson.api.RBucketReactive;
import org.redisson.api.RDequeReactive;
import org.redisson.api.RGeoReactive;
import org.redisson.api.RHyperLogLogReactive;
import org.redisson.api.RKeys;
import org.redisson.api.RKeysReactive;
import org.redisson.api.RLexSortedSetReactive;
import org.redisson.api.RListMultimapReactive;
import org.redisson.api.RListReactive;
import org.redisson.api.RLockReactive;
import org.redisson.api.RMapCache;
import org.redisson.api.RMapCacheReactive;
import org.redisson.api.RMapReactive;
import org.redisson.api.RPatternTopicReactive;
import org.redisson.api.RPermitExpirableSemaphoreReactive;
import org.redisson.api.RQueueReactive;
import org.redisson.api.RRateLimiterReactive;
import org.redisson.api.RReadWriteLockReactive;
import org.redisson.api.RScoredSortedSetReactive;
import org.redisson.api.RScriptReactive;
import org.redisson.api.RSemaphoreReactive;
import org.redisson.api.RSetCache;
import org.redisson.api.RSetCacheReactive;
import org.redisson.api.RSetMultimapReactive;
import org.redisson.api.RSetReactive;
import org.redisson.api.RStreamReactive;
import org.redisson.api.RTopicReactive;
import org.redisson.api.RTransactionReactive;
import org.redisson.api.RedissonReactiveClient;
import org.redisson.api.TransactionOptions;
import org.redisson.client.codec.Codec;
import org.redisson.config.Config;
import org.redisson.config.ConfigSupport;
import org.redisson.connection.ConnectionManager;
import org.redisson.eviction.EvictionScheduler;
import org.redisson.pubsub.SemaphorePubSub;
import org.redisson.reactive.CommandReactiveService;
import org.redisson.reactive.ReactiveProxyBuilder;
import org.redisson.reactive.RedissonBatchReactive;
import org.redisson.reactive.RedissonBlockingDequeReactive;
import org.redisson.reactive.RedissonBlockingQueueReactive;
import org.redisson.reactive.RedissonKeysReactive;
import org.redisson.reactive.RedissonLexSortedSetReactive;
import org.redisson.reactive.RedissonListMultimapReactive;
import org.redisson.reactive.RedissonListReactive;
import org.redisson.reactive.RedissonMapCacheReactive;
import org.redisson.reactive.RedissonMapReactive;
import org.redisson.reactive.RedissonReadWriteLockReactive;
import org.redisson.reactive.RedissonScoredSortedSetReactive;
import org.redisson.reactive.RedissonSetCacheReactive;
import org.redisson.reactive.RedissonSetMultimapReactive;
import org.redisson.reactive.RedissonSetReactive;
import org.redisson.reactive.RedissonTopicReactive;
import org.redisson.reactive.RedissonTransactionReactive;

/**
 * Main infrastructure class allows to get access
 * to all Redisson objects on top of Redis server.
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonReactive implements RedissonReactiveClient {

    protected final EvictionScheduler evictionScheduler;
    protected final CommandReactiveService commandExecutor;
    protected final ConnectionManager connectionManager;
    protected final Config config;

    protected final SemaphorePubSub semaphorePubSub = new SemaphorePubSub();

    protected RedissonReactive(Config config) {
        this.config = config;
        Config configCopy = new Config(config);

        connectionManager = ConfigSupport.createConnectionManager(configCopy);
        commandExecutor = new CommandReactiveService(connectionManager);
        evictionScheduler = new EvictionScheduler(commandExecutor);
    }
    
    public EvictionScheduler getEvictionScheduler() {
        return evictionScheduler;
    }
    
    public ConnectionManager getConnectionManager() {
        return connectionManager;
    }
    
    public CommandReactiveService getCommandExecutor() {
        return commandExecutor;
    }
    
    public SemaphorePubSub getSemaphorePubSub() {
        return semaphorePubSub;
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
    public RSemaphoreReactive getSemaphore(String name) {
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonSemaphore(commandExecutor, name, semaphorePubSub), RSemaphoreReactive.class);
    }

    @Override
    public RPermitExpirableSemaphoreReactive getPermitExpirableSemaphore(String name) {
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonPermitExpirableSemaphore(commandExecutor, name, semaphorePubSub), RPermitExpirableSemaphoreReactive.class);
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
    public <K, V> RMapCacheReactive<K, V> getMapCache(String name, Codec codec) {
        RMapCache<K, V> map = new RedissonMapCache<K, V>(codec, evictionScheduler, commandExecutor, name, null, null);
        return ReactiveProxyBuilder.create(commandExecutor, map, 
                new RedissonMapCacheReactive<K, V>(map), RMapCacheReactive.class);
    }

    @Override
    public <K, V> RMapCacheReactive<K, V> getMapCache(String name) {
        RMapCache<K, V> map = new RedissonMapCache<K, V>(evictionScheduler, commandExecutor, name, null, null);
        return ReactiveProxyBuilder.create(commandExecutor, map, 
                new RedissonMapCacheReactive<K, V>(map), RMapCacheReactive.class);
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
    public <V> List<RBucketReactive<V>> findBuckets(String pattern) {
        RKeys redissonKeys = new RedissonKeys(commandExecutor);
        Iterable<String> keys = redissonKeys.getKeysByPattern(pattern);

        List<RBucketReactive<V>> buckets = new ArrayList<RBucketReactive<V>>();
        for (Object key : keys) {
            if(key != null) {
                buckets.add(this.<V>getBucket(key.toString()));
            }
        }
        return buckets;
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
    public <K, V> RMapReactive<K, V> getMap(String name) {
        RedissonMap<K, V> map = new RedissonMap<K, V>(commandExecutor, name, null, null);
        return ReactiveProxyBuilder.create(commandExecutor, map, 
                new RedissonMapReactive<K, V>(map, this), RMapReactive.class);
    }

    @Override
    public <K, V> RMapReactive<K, V> getMap(String name, Codec codec) {
        RedissonMap<K, V> map = new RedissonMap<K, V>(codec, commandExecutor, name, null, null);
        return ReactiveProxyBuilder.create(commandExecutor, map, 
                new RedissonMapReactive<K, V>(map, this), RMapReactive.class);
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
                new RedissonListReactive<V>(codec,commandExecutor, name), RQueueReactive.class);
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
    public RBitSetReactive getBitSet(String name) {
        return ReactiveProxyBuilder.create(commandExecutor, new RedissonBitSet(commandExecutor, name), RBitSetReactive.class);
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
        RedissonBatchReactive batch = new RedissonBatchReactive(evictionScheduler, connectionManager, commandExecutor, options);
        if (config.isReferenceEnabled()) {
            batch.enableRedissonReferenceSupport(this);
        }
        return batch;
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
    public <K, V> RMapCacheReactive<K, V> getMapCache(String name, Codec codec, MapOptions<K, V> options) {
        RMapCache<K, V> map = new RedissonMapCache<K, V>(codec, evictionScheduler, commandExecutor, name, null, options);
        return ReactiveProxyBuilder.create(commandExecutor, map, 
                new RedissonMapCacheReactive<K, V>(map), RMapCacheReactive.class);
    }


    @Override
    public <K, V> RMapCacheReactive<K, V> getMapCache(String name, MapOptions<K, V> options) {
        RMapCache<K, V> map = new RedissonMapCache<K, V>(evictionScheduler, commandExecutor, name, null, options);
        return ReactiveProxyBuilder.create(commandExecutor, map, 
                new RedissonMapCacheReactive<K, V>(map), RMapCacheReactive.class);
    }

    @Override
    public <K, V> RMapReactive<K, V> getMap(String name, MapOptions<K, V> options) {
        RedissonMap<K, V> map = new RedissonMap<K, V>(commandExecutor, name, null, options);
        return ReactiveProxyBuilder.create(commandExecutor, map, 
                new RedissonMapReactive<K, V>(map, this), RMapReactive.class);
    }

    @Override
    public <K, V> RMapReactive<K, V> getMap(String name, Codec codec, MapOptions<K, V> options) {
        RedissonMap<K, V> map = new RedissonMap<K, V>(codec, commandExecutor, name, null, options);
        return ReactiveProxyBuilder.create(commandExecutor, map, 
                new RedissonMapReactive<K, V>(map, this), RMapReactive.class);
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
}
