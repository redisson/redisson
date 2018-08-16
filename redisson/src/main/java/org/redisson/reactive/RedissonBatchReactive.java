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
package org.redisson.reactive;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.reactivestreams.Publisher;
import org.redisson.api.BatchOptions;
import org.redisson.api.BatchResult;
import org.redisson.api.RAtomicDoubleReactive;
import org.redisson.api.RAtomicLongReactive;
import org.redisson.api.RBatchReactive;
import org.redisson.api.RBitSetReactive;
import org.redisson.api.RBlockingDequeReactive;
import org.redisson.api.RBlockingQueueReactive;
import org.redisson.api.RBucketReactive;
import org.redisson.api.RDequeReactive;
import org.redisson.api.RFuture;
import org.redisson.api.RGeoReactive;
import org.redisson.api.RHyperLogLogReactive;
import org.redisson.api.RKeysReactive;
import org.redisson.api.RLexSortedSetReactive;
import org.redisson.api.RListMultimapReactive;
import org.redisson.api.RListReactive;
import org.redisson.api.RMapCacheReactive;
import org.redisson.api.RMapReactive;
import org.redisson.api.RQueueReactive;
import org.redisson.api.RScoredSortedSetReactive;
import org.redisson.api.RScriptReactive;
import org.redisson.api.RSetCacheReactive;
import org.redisson.api.RSetMultimapReactive;
import org.redisson.api.RSetReactive;
import org.redisson.api.RTopicReactive;
import org.redisson.api.RedissonReactiveClient;
import org.redisson.client.codec.Codec;
import org.redisson.command.CommandReactiveBatchService;
import org.redisson.command.CommandReactiveService;
import org.redisson.connection.ConnectionManager;
import org.redisson.eviction.EvictionScheduler;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonBatchReactive implements RBatchReactive {

    private final EvictionScheduler evictionScheduler;
    private final CommandReactiveBatchService executorService;
    private final BatchOptions options;
    private final CommandReactiveService commandExecutor;

    public RedissonBatchReactive(EvictionScheduler evictionScheduler, ConnectionManager connectionManager, CommandReactiveService commandExecutor, BatchOptions options) {
        this.evictionScheduler = evictionScheduler;
        this.executorService = new CommandReactiveBatchService(connectionManager);
        this.commandExecutor = commandExecutor;
        this.options = options;
    }

    @Override
    public <V> RBucketReactive<V> getBucket(String name) {
        return new RedissonBucketReactive<V>(executorService, name);
    }

    @Override
    public <V> RBucketReactive<V> getBucket(String name, Codec codec) {
        return new RedissonBucketReactive<V>(codec, executorService, name);
    }

    @Override
    public <V> RHyperLogLogReactive<V> getHyperLogLog(String name) {
        return new RedissonHyperLogLogReactive<V>(executorService, name);
    }

    @Override
    public <V> RHyperLogLogReactive<V> getHyperLogLog(String name, Codec codec) {
        return new RedissonHyperLogLogReactive<V>(codec, executorService, name);
    }

    @Override
    public <V> RListReactive<V> getList(String name) {
        return new RedissonListReactive<V>(executorService, name);
    }

    @Override
    public <V> RListReactive<V> getList(String name, Codec codec) {
        return new RedissonListReactive<V>(codec, executorService, name);
    }

    @Override
    public <K, V> RMapReactive<K, V> getMap(String name) {
        return new RedissonMapReactive<K, V>(executorService, name, null);
    }

    @Override
    public <K, V> RMapReactive<K, V> getMap(String name, Codec codec) {
        return new RedissonMapReactive<K, V>(codec, executorService, name, null);
    }

    @Override
    public <K, V> RMapCacheReactive<K, V> getMapCache(String name, Codec codec) {
        return new RedissonMapCacheReactive<K, V>(evictionScheduler, codec, executorService, name, null);
    }

    @Override
    public <K, V> RMapCacheReactive<K, V> getMapCache(String name) {
        return new RedissonMapCacheReactive<K, V>(evictionScheduler, executorService, name, null);
    }

    @Override
    public <V> RSetReactive<V> getSet(String name) {
        return new RedissonSetReactive<V>(executorService, name);
    }

    @Override
    public <V> RSetReactive<V> getSet(String name, Codec codec) {
        return new RedissonSetReactive<V>(codec, executorService, name);
    }

    @Override
    public <M> RTopicReactive<M> getTopic(String name) {
        return new RedissonTopicReactive<M>(executorService, name);
    }

    @Override
    public <M> RTopicReactive<M> getTopic(String name, Codec codec) {
        return new RedissonTopicReactive<M>(codec, executorService, name);
    }

    @Override
    public <V> RQueueReactive<V> getQueue(String name) {
        return new RedissonQueueReactive<V>(executorService, name);
    }

    @Override
    public <V> RQueueReactive<V> getQueue(String name, Codec codec) {
        return new RedissonQueueReactive<V>(codec, executorService, name);
    }

    @Override
    public <V> RBlockingQueueReactive<V> getBlockingQueue(String name) {
        return new RedissonBlockingQueueReactive<V>(executorService, name);
    }

    @Override
    public <V> RBlockingQueueReactive<V> getBlockingQueue(String name, Codec codec) {
        return new RedissonBlockingQueueReactive<V>(codec, executorService, name);
    }

    @Override
    public <V> RDequeReactive<V> getDequeReactive(String name) {
        return new RedissonDequeReactive<V>(executorService, name);
    }

    @Override
    public <V> RDequeReactive<V> getDequeReactive(String name, Codec codec) {
        return new RedissonDequeReactive<V>(codec, executorService, name);
    }

    @Override
    public RAtomicLongReactive getAtomicLongReactive(String name) {
        return new RedissonAtomicLongReactive(executorService, name);
    }

    @Override
    public <V> RSetCacheReactive<V> getSetCache(String name) {
        return new RedissonSetCacheReactive<V>(evictionScheduler, executorService, name);
    }

    @Override
    public <V> RSetCacheReactive<V> getSetCache(String name, Codec codec) {
        return new RedissonSetCacheReactive<V>(codec, evictionScheduler, executorService, name);
    }

    @Override
    public <V> RScoredSortedSetReactive<V> getScoredSortedSet(String name) {
        return new RedissonScoredSortedSetReactive<V>(executorService, name);
    }

    @Override
    public <V> RScoredSortedSetReactive<V> getScoredSortedSet(String name, Codec codec) {
        return new RedissonScoredSortedSetReactive<V>(codec, executorService, name);
    }

    @Override
    public RLexSortedSetReactive getLexSortedSet(String name) {
        return new RedissonLexSortedSetReactive(executorService, name);
    }

    @Override
    public RBitSetReactive getBitSet(String name) {
        return new RedissonBitSetReactive(executorService, name);
    }

    @Override
    public RScriptReactive getScript() {
        return new RedissonScriptReactive(executorService);
    }

    @Override
    public RKeysReactive getKeys() {
        return new RedissonKeysReactive(executorService);
    }

    @Override
    public Publisher<BatchResult<?>> execute() {
        return commandExecutor.reactive(new Supplier<RFuture<BatchResult<?>>>() {
            @Override
            public RFuture<BatchResult<?>> get() {
                return executorService.executeAsync(options);
            }
        });
    }

    public RBatchReactive atomic() {
        options.atomic();
        return this;
    }
    
    @Override
    public RBatchReactive syncSlaves(int slaves, long timeout, TimeUnit unit) {
        options.syncSlaves(slaves, timeout, unit);
        return this;
    }
    
    @Override
    public RBatchReactive skipResult() {
        options.skipResult();
        return this;
    }
    
    @Override
    public RBatchReactive retryAttempts(int retryAttempts) {
        options.retryAttempts(retryAttempts);
        return this;
    }
    
    @Override
    public RBatchReactive retryInterval(long retryInterval, TimeUnit unit) {
        options.retryInterval(retryInterval, unit);
        return this;
    }
    
    @Override
    public RBatchReactive timeout(long timeout, TimeUnit unit) {
        options.responseTimeout(timeout, unit);
        return this;
    }

    public void enableRedissonReferenceSupport(RedissonReactiveClient redissonReactive) {
        this.executorService.enableRedissonReferenceSupport(redissonReactive);
    }

    @Override
    public <V> RGeoReactive<V> getGeo(String name) {
        return new RedissonGeoReactive<V>(executorService, name);
    }

    @Override
    public <V> RGeoReactive<V> getGeo(String name, Codec codec) {
        return new RedissonGeoReactive<V>(codec, executorService, name);
    }

    @Override
    public <K, V> RSetMultimapReactive<K, V> getSetMultimap(String name) {
        return new RedissonSetMultimapReactive<K, V>(executorService, name);
    }

    @Override
    public <K, V> RSetMultimapReactive<K, V> getSetMultimap(String name, Codec codec) {
        return new RedissonSetMultimapReactive<K, V>(codec, executorService, name);
    }

    @Override
    public <K, V> RListMultimapReactive<K, V> getListMultimap(String name) {
        return new RedissonListMultimapReactive<K, V>(executorService, name);
    }

    @Override
    public <K, V> RListMultimapReactive<K, V> getListMultimap(String name, Codec codec) {
        return new RedissonListMultimapReactive<K, V>(codec, executorService, name);
    }

    @Override
    public RAtomicDoubleReactive getAtomicDouble(String name) {
        return new RedissonAtomicDoubleReactive(executorService, name);
    }

    @Override
    public <V> RBlockingDequeReactive<V> getBlockingDeque(String name) {
        return new RedissonBlockingDequeReactive<V>(executorService, name);
    }

    @Override
    public <V> RBlockingDequeReactive<V> getBlockingDeque(String name, Codec codec) {
        return new RedissonBlockingDequeReactive<V>(codec, executorService, name);
    }

}
