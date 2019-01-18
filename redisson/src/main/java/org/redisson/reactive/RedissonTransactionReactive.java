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
package org.redisson.reactive;

import java.util.function.Supplier;

import org.redisson.api.RBucketReactive;
import org.redisson.api.RFuture;
import org.redisson.api.RMap;
import org.redisson.api.RMapCache;
import org.redisson.api.RMapCacheReactive;
import org.redisson.api.RMapReactive;
import org.redisson.api.RSet;
import org.redisson.api.RSetCache;
import org.redisson.api.RSetCacheReactive;
import org.redisson.api.RSetReactive;
import org.redisson.api.RTransaction;
import org.redisson.api.RTransactionReactive;
import org.redisson.api.TransactionOptions;
import org.redisson.client.codec.Codec;
import org.redisson.transaction.RedissonTransaction;

import reactor.core.publisher.Mono;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonTransactionReactive implements RTransactionReactive {

    private final RTransaction transaction;
    private final CommandReactiveExecutor executorService;
    
    public RedissonTransactionReactive(CommandReactiveExecutor executorService, TransactionOptions options) {
        this.transaction = new RedissonTransaction(executorService, options);
        this.executorService = executorService;
    }

    @Override
    public <V> RBucketReactive<V> getBucket(String name) {
        return ReactiveProxyBuilder.create(executorService, transaction.<V>getBucket(name), RBucketReactive.class);
    }

    @Override
    public <V> RBucketReactive<V> getBucket(String name, Codec codec) {
        return ReactiveProxyBuilder.create(executorService, transaction.<V>getBucket(name, codec), RBucketReactive.class);
    }

    @Override
    public <K, V> RMapReactive<K, V> getMap(String name) {
        RMap<K, V> map = transaction.<K, V>getMap(name);
        return ReactiveProxyBuilder.create(executorService, map, 
                new RedissonMapReactive<K, V>(map, null), RMapReactive.class);
    }

    @Override
    public <K, V> RMapReactive<K, V> getMap(String name, Codec codec) {
        RMap<K, V> map = transaction.<K, V>getMap(name, codec);
        return ReactiveProxyBuilder.create(executorService, map, 
                new RedissonMapReactive<K, V>(map, null), RMapReactive.class);
    }

    @Override
    public <K, V> RMapCacheReactive<K, V> getMapCache(String name, Codec codec) {
        RMapCache<K, V> map = transaction.<K, V>getMapCache(name, codec);
        return ReactiveProxyBuilder.create(executorService, map, 
                new RedissonMapCacheReactive<K, V>(map), RMapCacheReactive.class);
    }

    @Override
    public <K, V> RMapCacheReactive<K, V> getMapCache(String name) {
        RMapCache<K, V> map = transaction.<K, V>getMapCache(name);
        return ReactiveProxyBuilder.create(executorService, map, 
                new RedissonMapCacheReactive<K, V>(map), RMapCacheReactive.class);
    }

    @Override
    public <V> RSetReactive<V> getSet(String name) {
        RSet<V> set = transaction.<V>getSet(name);
        return ReactiveProxyBuilder.create(executorService, set, 
                new RedissonSetReactive<V>(set, null), RSetReactive.class);
    }

    @Override
    public <V> RSetReactive<V> getSet(String name, Codec codec) {
        RSet<V> set = transaction.<V>getSet(name, codec);
        return ReactiveProxyBuilder.create(executorService, set, 
                new RedissonSetReactive<V>(set, null), RSetReactive.class);
    }

    @Override
    public <V> RSetCacheReactive<V> getSetCache(String name) {
        RSetCache<V> set = transaction.<V>getSetCache(name);
        return ReactiveProxyBuilder.create(executorService, set, 
                new RedissonSetCacheReactive<V>(set, null), RSetCacheReactive.class);
    }

    @Override
    public <V> RSetCacheReactive<V> getSetCache(String name, Codec codec) {
        RSetCache<V> set = transaction.<V>getSetCache(name, codec);
        return ReactiveProxyBuilder.create(executorService, set, 
                new RedissonSetCacheReactive<V>(set, null), RSetCacheReactive.class);
    }

    @Override
    public Mono<Void> commit() {
        return executorService.reactive(new Supplier<RFuture<Void>>() {
            @Override
            public RFuture<Void> get() {
                return transaction.commitAsync();
            }
        });
    }

    @Override
    public Mono<Void> rollback() {
        return executorService.reactive(new Supplier<RFuture<Void>>() {
            @Override
            public RFuture<Void> get() {
                return transaction.rollbackAsync();
            }
        });
    }
    
}
