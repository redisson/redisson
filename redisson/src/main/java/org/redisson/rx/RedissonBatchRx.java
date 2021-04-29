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
package org.redisson.rx;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import org.redisson.*;
import org.redisson.api.*;
import org.redisson.client.codec.Codec;
import org.redisson.connection.ConnectionManager;
import org.redisson.eviction.EvictionScheduler;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonBatchRx implements RBatchRx {

    private final EvictionScheduler evictionScheduler;
    private final CommandRxBatchService executorService;
    private final CommandRxExecutor commandExecutor;

    public RedissonBatchRx(EvictionScheduler evictionScheduler, ConnectionManager connectionManager, CommandRxExecutor commandExecutor, BatchOptions options) {
        this.evictionScheduler = evictionScheduler;
        this.executorService = new CommandRxBatchService(connectionManager, commandExecutor, options);
        this.commandExecutor = commandExecutor;
    }

    @Override
    public <K, V> RStreamRx<K, V> getStream(String name) {
        return RxProxyBuilder.create(executorService, new RedissonStream<K, V>(executorService, name), RStreamRx.class);
    }

    @Override
    public <K, V> RStreamRx<K, V> getStream(String name, Codec codec) {
        return RxProxyBuilder.create(executorService, new RedissonStream<K, V>(codec, executorService, name), RStreamRx.class);
    }
    
    @Override
    public <V> RBucketRx<V> getBucket(String name) {
        return RxProxyBuilder.create(executorService, new RedissonBucket<V>(executorService, name), RBucketRx.class);
    }

    @Override
    public <V> RBucketRx<V> getBucket(String name, Codec codec) {
        return RxProxyBuilder.create(executorService, new RedissonBucket<V>(codec, executorService, name), RBucketRx.class);
    }

    @Override
    public <V> RHyperLogLogRx<V> getHyperLogLog(String name) {
        return RxProxyBuilder.create(executorService, new RedissonHyperLogLog<V>(executorService, name), RHyperLogLogRx.class);
    }

    @Override
    public <V> RHyperLogLogRx<V> getHyperLogLog(String name, Codec codec) {
        return RxProxyBuilder.create(executorService, new RedissonHyperLogLog<V>(codec, executorService, name), RHyperLogLogRx.class);
    }

    @Override
    public <V> RListRx<V> getList(String name) {
        RedissonList<V> list = new RedissonList<V>(executorService, name, null);
        return RxProxyBuilder.create(executorService, list, 
                new RedissonListRx<V>(list), RListRx.class);
    }

    @Override
    public <V> RListRx<V> getList(String name, Codec codec) {
        RedissonList<V> list = new RedissonList<V>(codec, executorService, name, null);
        return RxProxyBuilder.create(executorService, list, 
                new RedissonListRx<V>(list), RListRx.class);
    }

    @Override
    public <K, V> RMapRx<K, V> getMap(String name) {
        RedissonMap<K, V> map = new RedissonMap<K, V>(executorService, name, null, null, null);
        return RxProxyBuilder.create(executorService, map, 
                new RedissonMapRx<K, V>(map, null), RMapRx.class);
    }

    @Override
    public <K, V> RMapRx<K, V> getMap(String name, Codec codec) {
        RedissonMap<K, V> map = new RedissonMap<K, V>(codec, executorService, name, null, null, null);
        return RxProxyBuilder.create(executorService, map, 
                new RedissonMapRx<K, V>(map, null), RMapRx.class);
    }

    @Override
    public <K, V> RMapCacheRx<K, V> getMapCache(String name, Codec codec) {
        RMapCache<K, V> map = new RedissonMapCache<K, V>(codec, evictionScheduler, executorService, name, null, null, null);
        return RxProxyBuilder.create(executorService, map,
                new RedissonMapCacheRx<>(map, commandExecutor), RMapCacheRx.class);
    }

    @Override
    public <K, V> RMapCacheRx<K, V> getMapCache(String name) {
        RMapCache<K, V> map = new RedissonMapCache<K, V>(evictionScheduler, executorService, name, null, null, null);
        return RxProxyBuilder.create(executorService, map,
                new RedissonMapCacheRx<>(map, commandExecutor), RMapCacheRx.class);
    }

    @Override
    public <V> RSetRx<V> getSet(String name) {
        RedissonSet<V> set = new RedissonSet<V>(executorService, name, null);
        return RxProxyBuilder.create(executorService, set, 
                new RedissonSetRx<V>(set, null), RSetRx.class);
    }

    @Override
    public <V> RSetRx<V> getSet(String name, Codec codec) {
        RedissonSet<V> set = new RedissonSet<V>(codec, executorService, name, null);
        return RxProxyBuilder.create(executorService, set, 
                new RedissonSetRx<V>(set, null), RSetRx.class);
    }

    @Override
    public RTopicRx getTopic(String name) {
        return RxProxyBuilder.create(executorService, new RedissonTopic(executorService, name), RTopicRx.class);
    }

    @Override
    public RTopicRx getTopic(String name, Codec codec) {
        return RxProxyBuilder.create(executorService, new RedissonTopic(codec, executorService, name), RTopicRx.class);
    }

    @Override
    public <V> RQueueRx<V> getQueue(String name) {
        RedissonQueue<V> queue = new RedissonQueue<V>(executorService, name, null);
        return RxProxyBuilder.create(executorService, queue, 
                new RedissonListRx<V>(queue), RQueueRx.class);
    }

    @Override
    public <V> RQueueRx<V> getQueue(String name, Codec codec) {
        RedissonQueue<V> queue = new RedissonQueue<V>(codec, executorService, name, null);
        return RxProxyBuilder.create(executorService, queue, 
                new RedissonListRx<V>(queue), RQueueRx.class);
    }

    @Override
    public <V> RBlockingQueueRx<V> getBlockingQueue(String name) {
        RedissonBlockingQueue<V> queue = new RedissonBlockingQueue<V>(executorService, name, null);
        return RxProxyBuilder.create(executorService, queue, 
                new RedissonListRx<V>(queue), RBlockingQueueRx.class);
    }

    @Override
    public <V> RBlockingQueueRx<V> getBlockingQueue(String name, Codec codec) {
        RedissonBlockingQueue<V> queue = new RedissonBlockingQueue<V>(codec, executorService, name, null);
        return RxProxyBuilder.create(executorService, queue, 
                new RedissonListRx<V>(queue), RBlockingQueueRx.class);
    }

    @Override
    public <V> RDequeRx<V> getDeque(String name) {
        RedissonDeque<V> deque = new RedissonDeque<V>(executorService, name, null);
        return RxProxyBuilder.create(executorService, deque, 
                new RedissonListRx<V>(deque), RDequeRx.class);
    }

    @Override
    public <V> RDequeRx<V> getDeque(String name, Codec codec) {
        RedissonDeque<V> deque = new RedissonDeque<V>(codec, executorService, name, null);
        return RxProxyBuilder.create(executorService, deque, 
                new RedissonListRx<V>(deque), RDequeRx.class);
    }

    @Override
    public RAtomicLongRx getAtomicLong(String name) {
        return RxProxyBuilder.create(executorService, new RedissonAtomicLong(executorService, name), RAtomicLongRx.class);
    }

    @Override
    public <V> RSetCacheRx<V> getSetCache(String name) {
        RSetCache<V> set = new RedissonSetCache<V>(evictionScheduler, executorService, name, null);
        return RxProxyBuilder.create(executorService, set, 
                new RedissonSetCacheRx<V>(set, null), RSetCacheRx.class);
    }

    @Override
    public <V> RSetCacheRx<V> getSetCache(String name, Codec codec) {
        RSetCache<V> set = new RedissonSetCache<V>(codec, evictionScheduler, executorService, name, null);
        return RxProxyBuilder.create(executorService, set, 
                new RedissonSetCacheRx<V>(set, null), RSetCacheRx.class);
    }

    @Override
    public <V> RScoredSortedSetRx<V> getScoredSortedSet(String name) {
        RedissonScoredSortedSet<V> set = new RedissonScoredSortedSet<V>(executorService, name, null);
        return RxProxyBuilder.create(executorService, set, 
                new RedissonScoredSortedSetRx<V>(set), RScoredSortedSetRx.class);
    }

    @Override
    public <V> RScoredSortedSetRx<V> getScoredSortedSet(String name, Codec codec) {
        RedissonScoredSortedSet<V> set = new RedissonScoredSortedSet<V>(codec, executorService, name, null);
        return RxProxyBuilder.create(executorService, set, 
                new RedissonScoredSortedSetRx<V>(set), RScoredSortedSetRx.class);
    }

    @Override
    public RLexSortedSetRx getLexSortedSet(String name) {
        RedissonLexSortedSet set = new RedissonLexSortedSet(executorService, name, null);
        return RxProxyBuilder.create(executorService, set, 
                new RedissonLexSortedSetRx(set), 
                RLexSortedSetRx.class);
    }

    @Override
    public RBitSetRx getBitSet(String name) {
        return RxProxyBuilder.create(executorService, new RedissonBitSet(executorService, name), RBitSetRx.class);
    }

    @Override
    public RScriptRx getScript() {
        return RxProxyBuilder.create(executorService, new RedissonScript(executorService), RScriptRx.class);
    }
    
    @Override
    public RScriptRx getScript(Codec codec) {
        return RxProxyBuilder.create(executorService, new RedissonScript(executorService, codec), RScriptRx.class);
    }

    @Override
    public RKeysRx getKeys() {
        return RxProxyBuilder.create(executorService, new RedissonKeys(executorService), new RedissonKeysRx(executorService), RKeysRx.class);
    }

    @Override
    public Maybe<BatchResult<?>> execute() {
        return commandExecutor.flowable(() -> executorService.executeAsync()).singleElement();
    }

    @Override
    public Completable discard() {
        return commandExecutor.flowable(() -> executorService.discardAsync()).ignoreElements();
    }

    @Override
    public <V> RGeoRx<V> getGeo(String name) {
        RedissonGeo<V> geo = new RedissonGeo<V>(executorService, name, null);
        return RxProxyBuilder.create(executorService, geo, 
                new RedissonScoredSortedSetRx<V>(geo), RGeoRx.class);
    }

    @Override
    public <V> RGeoRx<V> getGeo(String name, Codec codec) {
        RedissonGeo<V> geo = new RedissonGeo<V>(codec, executorService, name, null);
        return RxProxyBuilder.create(executorService, geo, 
                new RedissonScoredSortedSetRx<V>(geo), RGeoRx.class);
    }

    @Override
    public <K, V> RSetMultimapRx<K, V> getSetMultimap(String name) {
        RedissonSetMultimap<K, V> setMultimap = new RedissonSetMultimap<>(executorService, name);
        return RxProxyBuilder.create(executorService, setMultimap,
                new RedissonSetMultimapRx<K, V>(setMultimap, executorService, null), RSetMultimapRx.class);
    }

    @Override
    public <K, V> RSetMultimapRx<K, V> getSetMultimap(String name, Codec codec) {
        RedissonSetMultimap<K, V> setMultimap = new RedissonSetMultimap<>(codec, executorService, name);
        return RxProxyBuilder.create(executorService, setMultimap,
                new RedissonSetMultimapRx<K, V>(setMultimap, executorService, null), RSetMultimapRx.class);
    }

    @Override
    public <K, V> RSetMultimapCacheRx<K, V> getSetMultimapCache(String name) {
        RedissonSetMultimapCache<K, V> setMultimap = new RedissonSetMultimapCache<>(evictionScheduler, executorService, name);
        return RxProxyBuilder.create(executorService, setMultimap,
                new RedissonSetMultimapCacheRx<K, V>(setMultimap, executorService, null), RSetMultimapCacheRx.class);
    }

    @Override
    public <K, V> RSetMultimapCacheRx<K, V> getSetMultimapCache(String name, Codec codec) {
        RedissonSetMultimapCache<K, V> setMultimap = new RedissonSetMultimapCache<>(evictionScheduler, codec, executorService, name);
        return RxProxyBuilder.create(executorService, setMultimap,
                new RedissonSetMultimapCacheRx<K, V>(setMultimap, executorService, null), RSetMultimapCacheRx.class);
    }

    @Override
    public <K, V> RListMultimapRx<K, V> getListMultimap(String name) {
        RedissonListMultimap<K, V> listMultimap = new RedissonListMultimap<>(executorService, name);
        return RxProxyBuilder.create(executorService, listMultimap,
                new RedissonListMultimapRx<K, V>(listMultimap, executorService), RListMultimapRx.class);
    }

    @Override
    public <K, V> RListMultimapRx<K, V> getListMultimap(String name, Codec codec) {
        RedissonListMultimap<K, V> listMultimap = new RedissonListMultimap<>(codec, executorService, name);
        return RxProxyBuilder.create(executorService, listMultimap,
                new RedissonListMultimapRx<K, V>(listMultimap, executorService), RListMultimapRx.class);
    }

    @Override
    public <K, V> RListMultimapCacheRx<K, V> getListMultimapCache(String name) {
        RedissonListMultimapCache<K, V> listMultimap = new RedissonListMultimapCache<>(evictionScheduler, executorService, name);
        return RxProxyBuilder.create(executorService, listMultimap,
                new RedissonListMultimapCacheRx<K, V>(listMultimap, executorService), RListMultimapCacheRx.class);
    }

    @Override
    public <K, V> RListMultimapCacheRx<K, V> getListMultimapCache(String name, Codec codec) {
        RedissonListMultimapCache<K, V> listMultimap = new RedissonListMultimapCache<>(evictionScheduler, codec, executorService, name);
        return RxProxyBuilder.create(executorService, listMultimap,
                new RedissonListMultimapCacheRx<K, V>(listMultimap, executorService), RListMultimapCacheRx.class);
    }

    @Override
    public RAtomicDoubleRx getAtomicDouble(String name) {
        return RxProxyBuilder.create(executorService, new RedissonAtomicDouble(executorService, name), RAtomicDoubleRx.class);
    }

    @Override
    public <V> RBlockingDequeRx<V> getBlockingDeque(String name) {
        RedissonBlockingDeque<V> deque = new RedissonBlockingDeque<V>(executorService, name, null);
        return RxProxyBuilder.create(executorService, deque, 
                new RedissonListRx<V>(deque), RBlockingDequeRx.class);
    }

    @Override
    public <V> RBlockingDequeRx<V> getBlockingDeque(String name, Codec codec) {
        RedissonBlockingDeque<V> deque = new RedissonBlockingDeque<V>(codec, executorService, name, null);
        return RxProxyBuilder.create(executorService, deque, 
                new RedissonListRx<V>(deque), RBlockingDequeRx.class);
    }

}
