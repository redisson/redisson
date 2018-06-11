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

import java.util.concurrent.TimeUnit;

import org.redisson.api.BatchOptions;
import org.redisson.api.BatchResult;
import org.redisson.api.RAtomicDoubleAsync;
import org.redisson.api.RAtomicLongAsync;
import org.redisson.api.RBatch;
import org.redisson.api.RBitSetAsync;
import org.redisson.api.RBlockingDequeAsync;
import org.redisson.api.RBlockingQueueAsync;
import org.redisson.api.RBucketAsync;
import org.redisson.api.RDequeAsync;
import org.redisson.api.RFuture;
import org.redisson.api.RGeoAsync;
import org.redisson.api.RHyperLogLogAsync;
import org.redisson.api.RKeysAsync;
import org.redisson.api.RLexSortedSetAsync;
import org.redisson.api.RListAsync;
import org.redisson.api.RMapAsync;
import org.redisson.api.RMapCacheAsync;
import org.redisson.api.RMultimapAsync;
import org.redisson.api.RMultimapCacheAsync;
import org.redisson.api.RQueueAsync;
import org.redisson.api.RScoredSortedSetAsync;
import org.redisson.api.RScriptAsync;
import org.redisson.api.RSetAsync;
import org.redisson.api.RSetCacheAsync;
import org.redisson.api.RTopicAsync;
import org.redisson.client.codec.Codec;
import org.redisson.command.CommandBatchService;
import org.redisson.connection.ConnectionManager;
import org.redisson.eviction.EvictionScheduler;

/**
 *
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonBatch implements RBatch {

    private final EvictionScheduler evictionScheduler;
    private final CommandBatchService executorService;
    private final BatchOptions options;

    public RedissonBatch(EvictionScheduler evictionScheduler, ConnectionManager connectionManager, BatchOptions options) {
        this.executorService = new CommandBatchService(connectionManager, options);
        this.evictionScheduler = evictionScheduler;
        this.options = options;
    }

    @Override
    public <V> RBucketAsync<V> getBucket(String name) {
        return new RedissonBucket<V>(executorService, name);
    }

    @Override
    public <V> RBucketAsync<V> getBucket(String name, Codec codec) {
        return new RedissonBucket<V>(codec, executorService, name);
    }

    @Override
    public <V> RHyperLogLogAsync<V> getHyperLogLog(String name) {
        return new RedissonHyperLogLog<V>(executorService, name);
    }

    @Override
    public <V> RHyperLogLogAsync<V> getHyperLogLog(String name, Codec codec) {
        return new RedissonHyperLogLog<V>(codec, executorService, name);
    }

    @Override
    public <V> RListAsync<V> getList(String name) {
        return new RedissonList<V>(executorService, name, null);
    }

    @Override
    public <V> RListAsync<V> getList(String name, Codec codec) {
        return new RedissonList<V>(codec, executorService, name, null);
    }

    @Override
    public <K, V> RMapAsync<K, V> getMap(String name) {
        return new RedissonMap<K, V>(executorService, name, null, null);
    }

    @Override
    public <K, V> RMapAsync<K, V> getMap(String name, Codec codec) {
        return new RedissonMap<K, V>(codec, executorService, name, null, null);
    }

    @Override
    public <V> RSetAsync<V> getSet(String name) {
        return new RedissonSet<V>(executorService, name, null);
    }

    @Override
    public <V> RSetAsync<V> getSet(String name, Codec codec) {
        return new RedissonSet<V>(codec, executorService, name, null);
    }

    @Override
    public <M> RTopicAsync<M> getTopic(String name) {
        return new RedissonTopic<M>(executorService, name);
    }

    @Override
    public <M> RTopicAsync<M> getTopic(String name, Codec codec) {
        return new RedissonTopic<M>(codec, executorService, name);
    }

    @Override
    public <V> RQueueAsync<V> getQueue(String name) {
        return new RedissonQueue<V>(executorService, name, null);
    }

    @Override
    public <V> RQueueAsync<V> getQueue(String name, Codec codec) {
        return new RedissonQueue<V>(codec, executorService, name, null);
    }

    @Override
    public <V> RBlockingQueueAsync<V> getBlockingQueue(String name) {
        return new RedissonBlockingQueue<V>(executorService, name, null);
    }

    @Override
    public <V> RBlockingQueueAsync<V> getBlockingQueue(String name, Codec codec) {
        return new RedissonBlockingQueue<V>(codec, executorService, name, null);
    }

    @Override
    public <V> RBlockingDequeAsync<V> getBlockingDeque(String name) {
        return new RedissonBlockingDeque<V>(executorService, name, null);
    }

    @Override
    public <V> RBlockingDequeAsync<V> getBlockingDeque(String name, Codec codec) {
        return new RedissonBlockingDeque<V>(codec, executorService, name, null);
    }

    @Override
    public <V> RDequeAsync<V> getDeque(String name) {
        return new RedissonDeque<V>(executorService, name, null);
    }

    @Override
    public <V> RDequeAsync<V> getDeque(String name, Codec codec) {
        return new RedissonDeque<V>(codec, executorService, name, null);
    }

    @Override
    public RAtomicLongAsync getAtomicLong(String name) {
        return new RedissonAtomicLong(executorService, name);
    }

    @Override
    public RAtomicDoubleAsync getAtomicDouble(String name) {
        return new RedissonAtomicDouble(executorService, name);
    }

    @Override
    public <V> RScoredSortedSetAsync<V> getScoredSortedSet(String name) {
        return new RedissonScoredSortedSet<V>(executorService, name, null);
    }

    @Override
    public <V> RScoredSortedSetAsync<V> getScoredSortedSet(String name, Codec codec) {
        return new RedissonScoredSortedSet<V>(codec, executorService, name, null);
    }

    @Override
    public RLexSortedSetAsync getLexSortedSet(String name) {
        return new RedissonLexSortedSet(executorService, name, null);
    }

    @Override
    public RBitSetAsync getBitSet(String name) {
        return new RedissonBitSet(executorService, name);
    }

    @Override
    public <K, V> RMapCacheAsync<K, V> getMapCache(String name, Codec codec) {
        return new RedissonMapCache<K, V>(codec, evictionScheduler, executorService, name, null, null);
    }

    @Override
    public <K, V> RMapCacheAsync<K, V> getMapCache(String name) {
        return new RedissonMapCache<K, V>(evictionScheduler, executorService, name, null, null);
    }

    @Override
    public RScriptAsync getScript() {
        return new RedissonScript(executorService);
    }

    @Override
    public RKeysAsync getKeys() {
        return new RedissonKeys(executorService);
    }

    @Override
    public <V> RSetCacheAsync<V> getSetCache(String name) {
        return new RedissonSetCache<V>(evictionScheduler, executorService, name, null);
    }

    @Override
    public <V> RSetCacheAsync<V> getSetCache(String name, Codec codec) {
        return new RedissonSetCache<V>(codec, evictionScheduler, executorService, name, null);
    }

    @Override
    public RBatch syncSlaves(int slaves, long timeout, TimeUnit unit) {
        options.syncSlaves(slaves, timeout, unit);
        return this;
    }
    
    @Override
    public RBatch atomic() {
        options.atomic();
        return this;
    }
    
    @Override
    public RBatch skipResult() {
        options.skipResult();
        return this;
    }
    
    @Override
    public RBatch retryAttempts(int retryAttempts) {
        options.retryAttempts(retryAttempts);
        return this;
    }
    
    @Override
    public RBatch retryInterval(long retryInterval, TimeUnit unit) {
        options.retryInterval(retryInterval, unit);
        return this;
    }
    
    @Override
    public RBatch timeout(long timeout, TimeUnit unit) {
        options.responseTimeout(timeout, unit);
        return this;
    }
    
    @Override
    public BatchResult<?> execute() {
        return executorService.execute(BatchOptions.defaults());
    }

    @Override
    public RFuture<BatchResult<?>> executeAsync() {
        return executorService.executeAsync(BatchOptions.defaults());
    }
    
    @Override
    public <K, V> RMultimapAsync<K, V> getSetMultimap(String name) {
        return new RedissonSetMultimap<K, V>(executorService, name);
    }

    @Override
    public <K, V> RMultimapAsync<K, V> getSetMultimap(String name, Codec codec) {
        return new RedissonSetMultimap<K, V>(codec, executorService, name);
    }

    @Override
    public <K, V> RMultimapAsync<K, V> getListMultimap(String name) {
        return new RedissonListMultimap<K, V>(executorService, name);
    }

    @Override
    public <K, V> RMultimapAsync<K, V> getListMultimap(String name, Codec codec) {
        return new RedissonListMultimap<K, V>(codec, executorService, name);
    }

    @Override
    public <V> RGeoAsync<V> getGeo(String name) {
        return new RedissonGeo<V>(executorService, name, null);
    }
    
    @Override
    public <V> RGeoAsync<V> getGeo(String name, Codec codec) {
        return new RedissonGeo<V>(codec, executorService, name, null);
    }
    
    @Override
    public <K, V> RMultimapCacheAsync<K, V> getSetMultimapCache(String name) {
        return new RedissonSetMultimapCache<K, V>(evictionScheduler, executorService, name);
    }
    
    @Override
    public <K, V> RMultimapCacheAsync<K, V> getSetMultimapCache(String name, Codec codec) {
        return new RedissonSetMultimapCache<K, V>(evictionScheduler, codec, executorService, name);
    }

    @Override
    public <K, V> RMultimapCacheAsync<K, V> getListMultimapCache(String name) {
        return new RedissonListMultimapCache<K, V>(evictionScheduler, executorService, name);
    }
    
    @Override
    public <K, V> RMultimapCacheAsync<K, V> getListMultimapCache(String name, Codec codec) {
        return new RedissonListMultimapCache<K, V>(evictionScheduler, codec, executorService, name);
    }

    protected void enableRedissonReferenceSupport(Redisson redisson) {
        this.executorService.enableRedissonReferenceSupport(redisson);
    }

}
