/**
 * Copyright (c) 2013-2021 Nikita Koksharov
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
package org.redisson.reactive;

import org.redisson.*;
import org.redisson.api.*;
import org.redisson.client.codec.Codec;
import org.redisson.connection.ConnectionManager;
import org.redisson.eviction.EvictionScheduler;
import reactor.core.publisher.Mono;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonBatchReactive implements RBatchReactive {

    private final EvictionScheduler evictionScheduler;
    private final CommandReactiveBatchService executorService;
    private final CommandReactiveExecutor commandExecutor;

    public RedissonBatchReactive(EvictionScheduler evictionScheduler, ConnectionManager connectionManager, CommandReactiveExecutor commandExecutor, BatchOptions options) {
        this.evictionScheduler = evictionScheduler;
        this.executorService = new CommandReactiveBatchService(connectionManager, commandExecutor, options);
        this.commandExecutor = commandExecutor;
    }

    @Override
    public <K, V> RStreamReactive<K, V> getStream(String name) {
        return ReactiveProxyBuilder.create(executorService, new RedissonStream<K, V>(executorService, name), RStreamReactive.class);
    }

    @Override
    public <K, V> RStreamReactive<K, V> getStream(String name, Codec codec) {
        return ReactiveProxyBuilder.create(executorService, new RedissonStream<K, V>(codec, executorService, name), RStreamReactive.class);
    }
    
    @Override
    public <V> RBucketReactive<V> getBucket(String name) {
        return ReactiveProxyBuilder.create(executorService, new RedissonBucket<V>(executorService, name), RBucketReactive.class);
    }

    @Override
    public <V> RBucketReactive<V> getBucket(String name, Codec codec) {
        return ReactiveProxyBuilder.create(executorService, new RedissonBucket<V>(codec, executorService, name), RBucketReactive.class);
    }

    @Override
    public <V> RHyperLogLogReactive<V> getHyperLogLog(String name) {
        return ReactiveProxyBuilder.create(executorService, new RedissonHyperLogLog<V>(executorService, name), RHyperLogLogReactive.class);
    }

    @Override
    public <V> RHyperLogLogReactive<V> getHyperLogLog(String name, Codec codec) {
        return ReactiveProxyBuilder.create(executorService, new RedissonHyperLogLog<V>(codec, executorService, name), RHyperLogLogReactive.class);
    }

    @Override
    public <V> RListReactive<V> getList(String name) {
        return ReactiveProxyBuilder.create(executorService, new RedissonList<V>(executorService, name, null), 
                new RedissonListReactive<V>(executorService, name), RListReactive.class);
    }

    @Override
    public <V> RListReactive<V> getList(String name, Codec codec) {
        return ReactiveProxyBuilder.create(executorService, new RedissonList<V>(codec, executorService, name, null), 
                new RedissonListReactive<V>(codec, executorService, name), RListReactive.class);
    }

    @Override
    public <K, V> RMapReactive<K, V> getMap(String name) {
        RMap<K, V> map = new RedissonMap<K, V>(executorService, name, null, null, null);
        return ReactiveProxyBuilder.create(executorService, map,
                new RedissonMapReactive<>(map, executorService), RMapReactive.class);
    }

    @Override
    public <K, V> RMapReactive<K, V> getMap(String name, Codec codec) {
        RedissonMap<K, V> map = new RedissonMap<>(codec, executorService, name, null, null, null);
        return ReactiveProxyBuilder.create(executorService, map,
                new RedissonMapReactive<>(map, executorService), RMapReactive.class);
    }

    @Override
    public <K, V> RMapCacheReactive<K, V> getMapCache(String name, Codec codec) {
        RMapCache<K, V> map = new RedissonMapCache<K, V>(codec, evictionScheduler, executorService, name, null, null, null);
        return ReactiveProxyBuilder.create(executorService, map, 
                new RedissonMapCacheReactive<K, V>(map, commandExecutor), RMapCacheReactive.class);
    }

    @Override
    public <K, V> RMapCacheReactive<K, V> getMapCache(String name) {
        RMapCache<K, V> map = new RedissonMapCache<K, V>(evictionScheduler, executorService, name, null, null, null);
        return ReactiveProxyBuilder.create(executorService, map, 
                new RedissonMapCacheReactive<K, V>(map, commandExecutor), RMapCacheReactive.class);
    }

    @Override
    public <V> RSetReactive<V> getSet(String name) {
        RedissonSet<V> set = new RedissonSet<V>(executorService, name, null);
        return ReactiveProxyBuilder.create(executorService, set, 
                new RedissonSetReactive<V>(set, null), RSetReactive.class);
    }

    @Override
    public <V> RSetReactive<V> getSet(String name, Codec codec) {
        RedissonSet<V> set = new RedissonSet<V>(codec, executorService, name, null);
        return ReactiveProxyBuilder.create(executorService, set, 
                new RedissonSetReactive<V>(set, null), RSetReactive.class);
    }

    @Override
    public RTopicReactive getTopic(String name) {
        return ReactiveProxyBuilder.create(executorService, new RedissonTopic(executorService, name), RTopicReactive.class);
    }

    @Override
    public RTopicReactive getTopic(String name, Codec codec) {
        return ReactiveProxyBuilder.create(executorService, new RedissonTopic(codec, executorService, name), RTopicReactive.class);
    }

    @Override
    public <V> RQueueReactive<V> getQueue(String name) {
        return ReactiveProxyBuilder.create(executorService, new RedissonQueue<V>(executorService, name, null), 
                new RedissonListReactive<V>(executorService, name), RQueueReactive.class);
    }

    @Override
    public <V> RQueueReactive<V> getQueue(String name, Codec codec) {
        return ReactiveProxyBuilder.create(executorService, new RedissonQueue<V>(codec, executorService, name, null), 
                new RedissonListReactive<V>(codec, executorService, name), RQueueReactive.class);
    }

    @Override
    public <V> RBlockingQueueReactive<V> getBlockingQueue(String name) {
        return ReactiveProxyBuilder.create(executorService, new RedissonBlockingQueue<V>(executorService, name, null), 
                new RedissonListReactive<V>(executorService, name), RBlockingQueueReactive.class);
    }

    @Override
    public <V> RBlockingQueueReactive<V> getBlockingQueue(String name, Codec codec) {
        return ReactiveProxyBuilder.create(executorService, new RedissonBlockingQueue<V>(codec, executorService, name, null), 
                new RedissonListReactive<V>(codec, executorService, name), RBlockingQueueReactive.class);
    }

    @Override
    public <V> RDequeReactive<V> getDeque(String name) {
        return ReactiveProxyBuilder.create(executorService, new RedissonDeque<V>(executorService, name, null), 
                new RedissonListReactive<V>(executorService, name), RDequeReactive.class);
    }

    @Override
    public <V> RDequeReactive<V> getDeque(String name, Codec codec) {
        return ReactiveProxyBuilder.create(executorService, new RedissonDeque<V>(codec, executorService, name, null), 
                new RedissonListReactive<V>(codec, executorService, name), RDequeReactive.class);
    }

    @Override
    public RAtomicLongReactive getAtomicLong(String name) {
        return ReactiveProxyBuilder.create(executorService, new RedissonAtomicLong(executorService, name), RAtomicLongReactive.class);
    }

    @Override
    public <V> RSetCacheReactive<V> getSetCache(String name) {
        RSetCache<V> set = new RedissonSetCache<V>(evictionScheduler, executorService, name, null);
        return ReactiveProxyBuilder.create(executorService, set, 
                new RedissonSetCacheReactive<V>(set, null), RSetCacheReactive.class);
    }

    @Override
    public <V> RSetCacheReactive<V> getSetCache(String name, Codec codec) {
        RSetCache<V> set = new RedissonSetCache<V>(codec, evictionScheduler, executorService, name, null);
        return ReactiveProxyBuilder.create(executorService, set, 
                new RedissonSetCacheReactive<V>(set, null), RSetCacheReactive.class);
    }

    @Override
    public <V> RScoredSortedSetReactive<V> getScoredSortedSet(String name) {
        return ReactiveProxyBuilder.create(executorService, new RedissonScoredSortedSet<V>(executorService, name, null), 
                new RedissonScoredSortedSetReactive<V>(executorService, name), RScoredSortedSetReactive.class);
    }

    @Override
    public <V> RScoredSortedSetReactive<V> getScoredSortedSet(String name, Codec codec) {
        return ReactiveProxyBuilder.create(executorService, new RedissonScoredSortedSet<V>(codec, executorService, name, null), 
                new RedissonScoredSortedSetReactive<V>(codec, executorService, name), RScoredSortedSetReactive.class);
    }

    @Override
    public RLexSortedSetReactive getLexSortedSet(String name) {
        RedissonLexSortedSet set = new RedissonLexSortedSet(executorService, name, null);
        return ReactiveProxyBuilder.create(executorService, set, 
                new RedissonLexSortedSetReactive(set), 
                RLexSortedSetReactive.class);
    }

    @Override
    public RBitSetReactive getBitSet(String name) {
        return ReactiveProxyBuilder.create(executorService, new RedissonBitSet(executorService, name), RBitSetReactive.class);
    }

    @Override
    public RScriptReactive getScript() {
        return ReactiveProxyBuilder.create(executorService, new RedissonScript(executorService), RScriptReactive.class);
    }
    
    @Override
    public RScriptReactive getScript(Codec codec) {
        return ReactiveProxyBuilder.create(executorService, new RedissonScript(executorService, codec), RScriptReactive.class);
    }

    @Override
    public RKeysReactive getKeys() {
        return ReactiveProxyBuilder.create(executorService, new RedissonKeys(executorService), new RedissonKeysReactive(executorService), RKeysReactive.class);
    }

    @Override
    public Mono<BatchResult<?>> execute() {
        return commandExecutor.reactive(() -> executorService.executeAsync());
    }

    @Override
    public Mono<Void> discard() {
        return commandExecutor.reactive(() -> executorService.discardAsync());
    }

    @Override
    public <V> RGeoReactive<V> getGeo(String name) {
        return ReactiveProxyBuilder.create(executorService, new RedissonGeo<V>(executorService, name, null), 
                new RedissonScoredSortedSetReactive<V>(executorService, name), RGeoReactive.class);
    }

    @Override
    public <V> RGeoReactive<V> getGeo(String name, Codec codec) {
        return ReactiveProxyBuilder.create(executorService, new RedissonGeo<V>(codec, executorService, name, null), 
                new RedissonScoredSortedSetReactive<V>(codec, executorService, name), RGeoReactive.class);
    }

    @Override
    public <K, V> RSetMultimapReactive<K, V> getSetMultimap(String name) {
        return ReactiveProxyBuilder.create(executorService, new RedissonSetMultimap<K, V>(executorService, name), 
                new RedissonSetMultimapReactive<K, V>(executorService, name, null), RSetMultimapReactive.class);
    }

    @Override
    public <K, V> RSetMultimapReactive<K, V> getSetMultimap(String name, Codec codec) {
        return ReactiveProxyBuilder.create(executorService, new RedissonSetMultimap<K, V>(codec, executorService, name), 
                new RedissonSetMultimapReactive<K, V>(codec, executorService, name, null), RSetMultimapReactive.class);
    }

    @Override
    public <K, V> RListMultimapReactive<K, V> getListMultimap(String name) {
        return ReactiveProxyBuilder.create(executorService, new RedissonListMultimap<K, V>(executorService, name), 
                new RedissonListMultimapReactive<K, V>(executorService, name), RListMultimapReactive.class);
    }

    @Override
    public <K, V> RListMultimapReactive<K, V> getListMultimap(String name, Codec codec) {
        return ReactiveProxyBuilder.create(executorService, new RedissonListMultimap<K, V>(codec, executorService, name), 
                new RedissonListMultimapReactive<K, V>(codec, executorService, name), RListMultimapReactive.class);
    }

    @Override
    public RAtomicDoubleReactive getAtomicDouble(String name) {
        return ReactiveProxyBuilder.create(executorService, new RedissonAtomicDouble(executorService, name), RAtomicDoubleReactive.class);
    }

    @Override
    public <V> RBlockingDequeReactive<V> getBlockingDeque(String name) {
        return ReactiveProxyBuilder.create(executorService, new RedissonBlockingDeque<V>(executorService, name, null), 
                new RedissonListReactive<V>(executorService, name), RBlockingDequeReactive.class);
    }

    @Override
    public <V> RBlockingDequeReactive<V> getBlockingDeque(String name, Codec codec) {
        return ReactiveProxyBuilder.create(executorService, new RedissonBlockingDeque<V>(codec, executorService, name, null), 
                new RedissonListReactive<V>(codec, executorService, name), RBlockingDequeReactive.class);
    }

}
