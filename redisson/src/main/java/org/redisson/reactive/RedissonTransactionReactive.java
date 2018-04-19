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

import java.util.function.Supplier;

import org.reactivestreams.Publisher;
import org.redisson.api.RBucketReactive;
import org.redisson.api.RFuture;
import org.redisson.api.RMapCacheReactive;
import org.redisson.api.RMapReactive;
import org.redisson.api.RSetCacheReactive;
import org.redisson.api.RSetReactive;
import org.redisson.api.RTransaction;
import org.redisson.api.RTransactionReactive;
import org.redisson.api.TransactionOptions;
import org.redisson.client.codec.Codec;
import org.redisson.command.CommandReactiveExecutor;
import org.redisson.transaction.RedissonTransaction;

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
        return new RedissonBucketReactive<V>(executorService, name, transaction.<V>getBucket(name));
    }

    @Override
    public <V> RBucketReactive<V> getBucket(String name, Codec codec) {
        return new RedissonBucketReactive<V>(codec, executorService, name, transaction.<V>getBucket(name, codec));
    }

    @Override
    public <K, V> RMapReactive<K, V> getMap(String name) {
        return new RedissonMapReactive<K, V>(executorService, name, null, transaction.<K, V>getMap(name));
    }

    @Override
    public <K, V> RMapReactive<K, V> getMap(String name, Codec codec) {
        return new RedissonMapReactive<K, V>(codec, executorService, name, null, transaction.<K, V>getMap(name, codec));
    }

    @Override
    public <K, V> RMapCacheReactive<K, V> getMapCache(String name, Codec codec) {
        return new RedissonMapCacheReactive<K, V>(codec, executorService, name, null, transaction.<K, V>getMapCache(name, codec));
    }

    @Override
    public <K, V> RMapCacheReactive<K, V> getMapCache(String name) {
        return new RedissonMapCacheReactive<K, V>(executorService, name, null, transaction.<K, V>getMapCache(name));
    }

    @Override
    public <V> RSetReactive<V> getSet(String name) {
        return new RedissonSetReactive<V>(executorService, name, transaction.<V>getSet(name));
    }

    @Override
    public <V> RSetReactive<V> getSet(String name, Codec codec) {
        return new RedissonSetReactive<V>(codec, executorService, name, transaction.<V>getSet(name, codec));
    }

    @Override
    public <V> RSetCacheReactive<V> getSetCache(String name) {
        return new RedissonSetCacheReactive<V>(executorService, name, transaction.<V>getSetCache(name));
    }

    @Override
    public <V> RSetCacheReactive<V> getSetCache(String name, Codec codec) {
        return new RedissonSetCacheReactive<V>(codec, executorService, name, transaction.<V>getSetCache(name, codec));
    }

    @Override
    public Publisher<Void> commit() {
        return executorService.reactive(new Supplier<RFuture<Void>>() {
            @Override
            public RFuture<Void> get() {
                return transaction.commitAsync();
            }
        });
    }

    @Override
    public Publisher<Void> rollback() {
        return executorService.reactive(new Supplier<RFuture<Void>>() {
            @Override
            public RFuture<Void> get() {
                return transaction.rollbackAsync();
            }
        });
    }
    
}
