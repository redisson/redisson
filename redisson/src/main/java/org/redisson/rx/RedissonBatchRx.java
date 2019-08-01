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
package org.redisson.rx;

import java.util.concurrent.TimeUnit;

import org.redisson.RedissonAtomicDouble;
import org.redisson.RedissonAtomicLong;
import org.redisson.RedissonBitSet;
import org.redisson.RedissonBlockingDeque;
import org.redisson.RedissonBlockingQueue;
import org.redisson.RedissonBucket;
import org.redisson.RedissonDeque;
import org.redisson.RedissonGeo;
import org.redisson.RedissonHyperLogLog;
import org.redisson.RedissonKeys;
import org.redisson.RedissonLexSortedSet;
import org.redisson.RedissonList;
import org.redisson.RedissonListMultimap;
import org.redisson.RedissonMap;
import org.redisson.RedissonMapCache;
import org.redisson.RedissonQueue;
import org.redisson.RedissonScoredSortedSet;
import org.redisson.RedissonScript;
import org.redisson.RedissonSet;
import org.redisson.RedissonSetCache;
import org.redisson.RedissonSetMultimap;
import org.redisson.RedissonStream;
import org.redisson.RedissonTopic;
import org.redisson.api.BatchOptions;
import org.redisson.api.BatchResult;
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
import org.redisson.api.RMapCache;
import org.redisson.api.RMapCacheRx;
import org.redisson.api.RMapRx;
import org.redisson.api.RQueueRx;
import org.redisson.api.RScoredSortedSetRx;
import org.redisson.api.RScriptRx;
import org.redisson.api.RSetCache;
import org.redisson.api.RSetCacheRx;
import org.redisson.api.RSetMultimapRx;
import org.redisson.api.RSetRx;
import org.redisson.api.RStreamRx;
import org.redisson.api.RTopicRx;
import org.redisson.api.RedissonRxClient;
import org.redisson.client.codec.Codec;
import org.redisson.connection.ConnectionManager;
import org.redisson.eviction.EvictionScheduler;

import io.reactivex.Maybe;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonBatchRx implements RBatchRx {

    private final EvictionScheduler evictionScheduler;
    private final CommandRxBatchService executorService;
    private final CommandRxExecutor commandExecutor;
    private final BatchOptions options;
    
    public RedissonBatchRx(EvictionScheduler evictionScheduler, ConnectionManager connectionManager, CommandRxExecutor commandExecutor, BatchOptions options) {
        this.evictionScheduler = evictionScheduler;
        this.executorService = new CommandRxBatchService(connectionManager, options);
        this.commandExecutor = commandExecutor;
        this.options = options;
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
                new RedissonMapCacheRx<K, V>(map), RMapCacheRx.class);
    }

    @Override
    public <K, V> RMapCacheRx<K, V> getMapCache(String name) {
        RMapCache<K, V> map = new RedissonMapCache<K, V>(evictionScheduler, executorService, name, null, null, null);
        return RxProxyBuilder.create(executorService, map, 
                new RedissonMapCacheRx<K, V>(map), RMapCacheRx.class);
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
        return commandExecutor.flowable(() -> executorService.executeAsync(options)).singleElement();
    }
    
    public RBatchRx atomic() {
        options.atomic();
        return this;
    }
    
    @Override
    public RBatchRx syncSlaves(int slaves, long timeout, TimeUnit unit) {
        options.syncSlaves(slaves, timeout, unit);
        return this;
    }
    
    @Override
    public RBatchRx skipResult() {
        options.skipResult();
        return this;
    }
    
    @Override
    public RBatchRx retryAttempts(int retryAttempts) {
        options.retryAttempts(retryAttempts);
        return this;
    }
    
    @Override
    public RBatchRx retryInterval(long retryInterval, TimeUnit unit) {
        options.retryInterval(retryInterval, unit);
        return this;
    }
    
    @Override
    public RBatchRx timeout(long timeout, TimeUnit unit) {
        options.responseTimeout(timeout, unit);
        return this;
    }

    public void enableRedissonReferenceSupport(RedissonRxClient redissonRx) {
        this.executorService.enableRedissonReferenceSupport(redissonRx);
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
        return RxProxyBuilder.create(executorService, new RedissonSetMultimap<K, V>(executorService, name), 
                new RedissonSetMultimapRx<K, V>(executorService, name, null), RSetMultimapRx.class);
    }

    @Override
    public <K, V> RSetMultimapRx<K, V> getSetMultimap(String name, Codec codec) {
        return RxProxyBuilder.create(executorService, new RedissonSetMultimap<K, V>(codec, executorService, name), 
                new RedissonSetMultimapRx<K, V>(codec, executorService, name, null), RSetMultimapRx.class);
    }

    @Override
    public <K, V> RListMultimapRx<K, V> getListMultimap(String name) {
        return RxProxyBuilder.create(executorService, new RedissonListMultimap<K, V>(executorService, name), 
                new RedissonListMultimapRx<K, V>(executorService, name), RListMultimapRx.class);
    }

    @Override
    public <K, V> RListMultimapRx<K, V> getListMultimap(String name, Codec codec) {
        return RxProxyBuilder.create(executorService, new RedissonListMultimap<K, V>(codec, executorService, name), 
                new RedissonListMultimapRx<K, V>(codec, executorService, name), RListMultimapRx.class);
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
