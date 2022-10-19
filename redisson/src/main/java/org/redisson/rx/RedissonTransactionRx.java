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
package org.redisson.rx;

import org.redisson.api.RBucketRx;
import org.redisson.api.RMap;
import org.redisson.api.RMapCache;
import org.redisson.api.RMapCacheRx;
import org.redisson.api.RMapRx;
import org.redisson.api.RSet;
import org.redisson.api.RSetCache;
import org.redisson.api.RSetCacheRx;
import org.redisson.api.RSetRx;
import org.redisson.api.RTransaction;
import org.redisson.api.RTransactionRx;
import org.redisson.api.TransactionOptions;
import org.redisson.client.codec.Codec;
import org.redisson.reactive.RedissonSetReactive;
import org.redisson.transaction.RedissonTransaction;

import io.reactivex.rxjava3.core.Completable;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonTransactionRx implements RTransactionRx {

    private final RTransaction transaction;
    private final CommandRxExecutor executorService;
    
    public RedissonTransactionRx(CommandRxExecutor executorService, TransactionOptions options) {
        this.transaction = new RedissonTransaction(executorService, options);
        this.executorService = executorService;
    }

    @Override
    public <V> RBucketRx<V> getBucket(String name) {
        return RxProxyBuilder.create(executorService, transaction.<V>getBucket(name), RBucketRx.class);
    }

    @Override
    public <V> RBucketRx<V> getBucket(String name, Codec codec) {
        return RxProxyBuilder.create(executorService, transaction.<V>getBucket(name, codec), RBucketRx.class);
    }

    @Override
    public <K, V> RMapRx<K, V> getMap(String name) {
        RMap<K, V> map = transaction.<K, V>getMap(name);
        return RxProxyBuilder.create(executorService, map, 
                new RedissonMapRx<K, V>(map, null), RMapRx.class);
    }

    @Override
    public <K, V> RMapRx<K, V> getMap(String name, Codec codec) {
        RMap<K, V> map = transaction.<K, V>getMap(name, codec);
        return RxProxyBuilder.create(executorService, map, 
                new RedissonMapRx<K, V>(map, null), RMapRx.class);
    }

    @Override
    public <K, V> RMapCacheRx<K, V> getMapCache(String name, Codec codec) {
        RMapCache<K, V> map = transaction.<K, V>getMapCache(name, codec);
        return RxProxyBuilder.create(executorService, map, 
                new RedissonMapCacheRx<K, V>(map, executorService), RMapCacheRx.class);
    }

    @Override
    public <K, V> RMapCacheRx<K, V> getMapCache(String name) {
        RMapCache<K, V> map = transaction.<K, V>getMapCache(name);
        return RxProxyBuilder.create(executorService, map, 
                new RedissonMapCacheRx<K, V>(map, executorService), RMapCacheRx.class);
    }

    @Override
    public <V> RSetRx<V> getSet(String name) {
        RSet<V> set = transaction.<V>getSet(name);
        return RxProxyBuilder.create(executorService, set, 
                new RedissonSetReactive<V>(set, null), RSetRx.class);
    }

    @Override
    public <V> RSetRx<V> getSet(String name, Codec codec) {
        RSet<V> set = transaction.<V>getSet(name, codec);
        return RxProxyBuilder.create(executorService, set, 
                new RedissonSetRx<V>(set, null), RSetRx.class);
    }

    @Override
    public <V> RSetCacheRx<V> getSetCache(String name) {
        RSetCache<V> set = transaction.<V>getSetCache(name);
        return RxProxyBuilder.create(executorService, set, 
                new RedissonSetCacheRx<V>(set, null), RSetCacheRx.class);
    }

    @Override
    public <V> RSetCacheRx<V> getSetCache(String name, Codec codec) {
        RSetCache<V> set = transaction.<V>getSetCache(name, codec);
        return RxProxyBuilder.create(executorService, set, 
                new RedissonSetCacheRx<V>(set, null), RSetCacheRx.class);
    }

    @Override
    public Completable commit() {
        return executorService.flowable(() -> transaction.commitAsync().toCompletableFuture()).ignoreElements();
    }

    @Override
    public Completable rollback() {
        return executorService.flowable(() -> transaction.rollbackAsync().toCompletableFuture()).ignoreElements();
    }
    
}
