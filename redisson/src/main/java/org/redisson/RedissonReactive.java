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
import org.redisson.api.RSetCacheReactive;
import org.redisson.api.RSetMultimapReactive;
import org.redisson.api.RSetReactive;
import org.redisson.api.RStreamReactive;
import org.redisson.api.RTopicReactive;
import org.redisson.api.RTransactionReactive;
import org.redisson.api.RedissonReactiveClient;
import org.redisson.api.TransactionOptions;
import org.redisson.client.codec.Codec;
import org.redisson.codec.ReferenceCodecProvider;
import org.redisson.command.CommandReactiveService;
import org.redisson.config.Config;
import org.redisson.config.ConfigSupport;
import org.redisson.connection.ConnectionManager;
import org.redisson.eviction.EvictionScheduler;
import org.redisson.pubsub.SemaphorePubSub;
import org.redisson.reactive.RedissonAtomicDoubleReactive;
import org.redisson.reactive.RedissonAtomicLongReactive;
import org.redisson.reactive.RedissonBatchReactive;
import org.redisson.reactive.RedissonBitSetReactive;
import org.redisson.reactive.RedissonBlockingDequeReactive;
import org.redisson.reactive.RedissonBlockingQueueReactive;
import org.redisson.reactive.RedissonBucketReactive;
import org.redisson.reactive.RedissonDequeReactive;
import org.redisson.reactive.RedissonGeoReactive;
import org.redisson.reactive.RedissonHyperLogLogReactive;
import org.redisson.reactive.RedissonKeysReactive;
import org.redisson.reactive.RedissonLexSortedSetReactive;
import org.redisson.reactive.RedissonListMultimapReactive;
import org.redisson.reactive.RedissonListReactive;
import org.redisson.reactive.RedissonLockReactive;
import org.redisson.reactive.RedissonMapCacheReactive;
import org.redisson.reactive.RedissonMapReactive;
import org.redisson.reactive.RedissonPatternTopicReactive;
import org.redisson.reactive.RedissonPermitExpirableSemaphoreReactive;
import org.redisson.reactive.RedissonQueueReactive;
import org.redisson.reactive.RedissonRateLimiterReactive;
import org.redisson.reactive.RedissonReadWriteLockReactive;
import org.redisson.reactive.RedissonScoredSortedSetReactive;
import org.redisson.reactive.RedissonScriptReactive;
import org.redisson.reactive.RedissonSemaphoreReactive;
import org.redisson.reactive.RedissonSetCacheReactive;
import org.redisson.reactive.RedissonSetMultimapReactive;
import org.redisson.reactive.RedissonSetReactive;
import org.redisson.reactive.RedissonStreamReactive;
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
    protected final ReferenceCodecProvider codecProvider;

    protected final SemaphorePubSub semaphorePubSub = new SemaphorePubSub();

    protected RedissonReactive(Config config) {
        this.config = config;
        Config configCopy = new Config(config);

        connectionManager = ConfigSupport.createConnectionManager(configCopy);
        commandExecutor = new CommandReactiveService(connectionManager);
        evictionScheduler = new EvictionScheduler(commandExecutor);
        codecProvider = config.getReferenceCodecProvider();
    }
    
    @Override
    public <K, V> RStreamReactive<K, V> getStream(String name) {
        return new RedissonStreamReactive<K, V>(commandExecutor, name);
    }

    @Override
    public <K, V> RStreamReactive<K, V> getStream(String name, Codec codec) {
        return new RedissonStreamReactive<K, V>(codec, commandExecutor, name);
    }

    @Override
    public <V> RGeoReactive<V> getGeo(String name) {
        return new RedissonGeoReactive<V>(commandExecutor, name);
    }
    
    @Override
    public <V> RGeoReactive<V> getGeo(String name, Codec codec) {
        return new RedissonGeoReactive<V>(codec, commandExecutor, name);
    }
    
    @Override
    public RLockReactive getFairLock(String name) {
        return new RedissonLockReactive(commandExecutor, name, new RedissonFairLock(commandExecutor, name));
    }
    
    @Override
    public RRateLimiterReactive getRateLimiter(String name) {
        return new RedissonRateLimiterReactive(commandExecutor, name);
    }
    
    @Override
    public RSemaphoreReactive getSemaphore(String name) {
        return new RedissonSemaphoreReactive(commandExecutor, name, semaphorePubSub);
    }

    @Override
    public RPermitExpirableSemaphoreReactive getPermitExpirableSemaphore(String name) {
        return new RedissonPermitExpirableSemaphoreReactive(commandExecutor, name, semaphorePubSub);
    }

    @Override
    public RReadWriteLockReactive getReadWriteLock(String name) {
        return new RedissonReadWriteLockReactive(commandExecutor, name);
    }

    @Override
    public RLockReactive getLock(String name) {
        return new RedissonLockReactive(commandExecutor, name);
    }

    @Override
    public <K, V> RMapCacheReactive<K, V> getMapCache(String name, Codec codec) {
        return new RedissonMapCacheReactive<K, V>(evictionScheduler, codec, commandExecutor, name, null);
    }

    @Override
    public <K, V> RMapCacheReactive<K, V> getMapCache(String name) {
        return new RedissonMapCacheReactive<K, V>(evictionScheduler, commandExecutor, name, null);
    }

    @Override
    public <V> RBucketReactive<V> getBucket(String name) {
        return new RedissonBucketReactive<V>(commandExecutor, name);
    }

    @Override
    public <V> RBucketReactive<V> getBucket(String name, Codec codec) {
        return new RedissonBucketReactive<V>(codec, commandExecutor, name);
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
        return new RedissonHyperLogLogReactive<V>(commandExecutor, name);
    }

    @Override
    public <V> RHyperLogLogReactive<V> getHyperLogLog(String name, Codec codec) {
        return new RedissonHyperLogLogReactive<V>(codec, commandExecutor, name);
    }

    @Override
    public <V> RListReactive<V> getList(String name) {
        return new RedissonListReactive<V>(commandExecutor, name);
    }

    @Override
    public <V> RListReactive<V> getList(String name, Codec codec) {
        return new RedissonListReactive<V>(codec, commandExecutor, name);
    }

    @Override
    public <K, V> RListMultimapReactive<K, V> getListMultimap(String name) {
        return new RedissonListMultimapReactive<K, V>(commandExecutor, name);
    }

    @Override
    public <K, V> RListMultimapReactive<K, V> getListMultimap(String name, Codec codec) {
        return new RedissonListMultimapReactive<K, V>(codec, commandExecutor, name);
    }

    @Override
    public <K, V> RSetMultimapReactive<K, V> getSetMultimap(String name) {
        return new RedissonSetMultimapReactive<K, V>(commandExecutor, name);
    }

    @Override
    public <K, V> RSetMultimapReactive<K, V> getSetMultimap(String name, Codec codec) {
        return new RedissonSetMultimapReactive<K, V>(codec, commandExecutor, name);
    }

    @Override
    public <K, V> RMapReactive<K, V> getMap(String name) {
        return new RedissonMapReactive<K, V>(commandExecutor, name, null);
    }

    @Override
    public <K, V> RMapReactive<K, V> getMap(String name, Codec codec) {
        return new RedissonMapReactive<K, V>(codec, commandExecutor, name, null);
    }

    @Override
    public <V> RSetReactive<V> getSet(String name) {
        return new RedissonSetReactive<V>(commandExecutor, name);
    }

    @Override
    public <V> RSetReactive<V> getSet(String name, Codec codec) {
        return new RedissonSetReactive<V>(codec, commandExecutor, name);
    }

    @Override
    public <V> RScoredSortedSetReactive<V> getScoredSortedSet(String name) {
        return new RedissonScoredSortedSetReactive<V>(commandExecutor, name);
    }

    @Override
    public <V> RScoredSortedSetReactive<V> getScoredSortedSet(String name, Codec codec) {
        return new RedissonScoredSortedSetReactive<V>(codec, commandExecutor, name);
    }

    @Override
    public RLexSortedSetReactive getLexSortedSet(String name) {
        return new RedissonLexSortedSetReactive(commandExecutor, name);
    }

    @Override
    public <M> RTopicReactive<M> getTopic(String name) {
        return new RedissonTopicReactive<M>(commandExecutor, name);
    }

    @Override
    public <M> RTopicReactive<M> getTopic(String name, Codec codec) {
        return new RedissonTopicReactive<M>(codec, commandExecutor, name);
    }

    @Override
    public <M> RPatternTopicReactive<M> getPatternTopic(String pattern) {
        return new RedissonPatternTopicReactive<M>(commandExecutor, pattern);
    }

    @Override
    public <M> RPatternTopicReactive<M> getPatternTopic(String pattern, Codec codec) {
        return new RedissonPatternTopicReactive<M>(codec, commandExecutor, pattern);
    }

    @Override
    public <V> RQueueReactive<V> getQueue(String name) {
        return new RedissonQueueReactive<V>(commandExecutor, name);
    }

    @Override
    public <V> RQueueReactive<V> getQueue(String name, Codec codec) {
        return new RedissonQueueReactive<V>(codec, commandExecutor, name);
    }

    @Override
    public <V> RBlockingQueueReactive<V> getBlockingQueue(String name) {
        return new RedissonBlockingQueueReactive<V>(commandExecutor, name);
    }

    @Override
    public <V> RBlockingQueueReactive<V> getBlockingQueue(String name, Codec codec) {
        return new RedissonBlockingQueueReactive<V>(codec, commandExecutor, name);
    }

    @Override
    public <V> RDequeReactive<V> getDeque(String name) {
        return new RedissonDequeReactive<V>(commandExecutor, name);
    }

    @Override
    public <V> RDequeReactive<V> getDeque(String name, Codec codec) {
        return new RedissonDequeReactive<V>(codec, commandExecutor, name);
    }

    @Override
    public <V> RSetCacheReactive<V> getSetCache(String name) {
        return new RedissonSetCacheReactive<V>(evictionScheduler, commandExecutor, name);
    }

    @Override
    public <V> RSetCacheReactive<V> getSetCache(String name, Codec codec) {
        return new RedissonSetCacheReactive<V>(codec, evictionScheduler, commandExecutor, name);
    }

    @Override
    public RAtomicLongReactive getAtomicLong(String name) {
        return new RedissonAtomicLongReactive(commandExecutor, name);
    }

    @Override
    public RAtomicDoubleReactive getAtomicDouble(String name) {
        return new RedissonAtomicDoubleReactive(commandExecutor, name);
    }

    @Override
    public RBitSetReactive getBitSet(String name) {
        return new RedissonBitSetReactive(commandExecutor, name);
    }

    @Override
    public RScriptReactive getScript() {
        return new RedissonScriptReactive(commandExecutor);
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
        return new RedissonKeysReactive(commandExecutor);
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

    protected void enableRedissonReferenceSupport() {
        this.commandExecutor.enableRedissonReferenceSupport(this);
    }

    @Override
    public <K, V> RMapCacheReactive<K, V> getMapCache(String name, Codec codec, MapOptions<K, V> options) {
        return new RedissonMapCacheReactive<K, V>(evictionScheduler, codec, commandExecutor, name, options);
    }


    @Override
    public <K, V> RMapCacheReactive<K, V> getMapCache(String name, MapOptions<K, V> options) {
        return new RedissonMapCacheReactive<K, V>(evictionScheduler, commandExecutor, name, options);
    }


    @Override
    public <K, V> RMapReactive<K, V> getMap(String name, MapOptions<K, V> options) {
        return new RedissonMapReactive<K, V>(commandExecutor, name, options);
    }


    @Override
    public <K, V> RMapReactive<K, V> getMap(String name, Codec codec, MapOptions<K, V> options) {
        return new RedissonMapReactive<K, V>(codec, commandExecutor, name, options);
    }

    @Override
    public RTransactionReactive createTransaction(TransactionOptions options) {
        return new RedissonTransactionReactive(commandExecutor, options);
    }

    @Override
    public <V> RBlockingDequeReactive<V> getBlockingDeque(String name) {
        return new RedissonBlockingDequeReactive<V>(commandExecutor, name);
    }

    @Override
    public <V> RBlockingDequeReactive<V> getBlockingDeque(String name, Codec codec) {
        return new RedissonBlockingDequeReactive<V>(codec, commandExecutor, name);
    }
}
