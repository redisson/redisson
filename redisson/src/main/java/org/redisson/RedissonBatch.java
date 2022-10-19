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
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.command.CommandBatchService;
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

    public RedissonBatch(EvictionScheduler evictionScheduler, CommandAsyncExecutor executor, BatchOptions options) {
        this.executorService = new CommandBatchService(executor, options);
        this.evictionScheduler = evictionScheduler;
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
    public <V> RJsonBucketAsync<V> getJsonBucket(String name, JsonCodec<V> codec) {
        return new RedissonJsonBucket<>(codec, executorService, name);
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
        return new RedissonMap<K, V>(executorService, name, null, null, null);
    }

    @Override
    public <K, V> RMapAsync<K, V> getMap(String name, Codec codec) {
        return new RedissonMap<K, V>(codec, executorService, name, null, null, null);
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
    public RTopicAsync getTopic(String name) {
        return new RedissonTopic(executorService, name);
    }

    @Override
    public RTopicAsync getTopic(String name, Codec codec) {
        return new RedissonTopic(codec, executorService, name);
    }

    @Override
    public RShardedTopicAsync getShardedTopic(String name) {
        return new RedissonShardedTopic(executorService, name);
    }

    @Override
    public RShardedTopicAsync getShardedTopic(String name, Codec codec) {
        return new RedissonShardedTopic(codec, executorService, name);
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
        return new RedissonMapCache<K, V>(codec, evictionScheduler, executorService, name, null, null, null);
    }

    @Override
    public <K, V> RMapCacheAsync<K, V> getMapCache(String name) {
        return new RedissonMapCache<K, V>(evictionScheduler, executorService, name, null, null, null);
    }

    @Override
    public RScriptAsync getScript() {
        return new RedissonScript(executorService);
    }
    
    @Override
    public RScriptAsync getScript(Codec codec) {
        return new RedissonScript(executorService, codec);
    }

    @Override
    public RFunctionAsync getFunction() {
        return new RedissonFuction(executorService);
    }

    @Override
    public RFunctionAsync getFunction(Codec codec) {
        return new RedissonFuction(executorService, codec);
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
    public BatchResult<?> execute() {
        return executorService.execute();
    }

    @Override
    public RFuture<BatchResult<?>> executeAsync() {
        return executorService.executeAsync();
    }

    @Override
    public void discard() {
        executorService.discard();
    }

    @Override
    public RFuture<Void> discardAsync() {
        return executorService.discardAsync();
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

    @Override
    public <K, V> RStreamAsync<K, V> getStream(String name) {
        return new RedissonStream<K, V>(executorService, name);
    }

    @Override
    public <K, V> RStreamAsync<K, V> getStream(String name, Codec codec) {  
        return new RedissonStream<K, V>(codec, executorService, name);
    }

}
