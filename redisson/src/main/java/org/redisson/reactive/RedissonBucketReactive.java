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
import org.redisson.RedissonBucket;
import org.redisson.api.RBucketAsync;
import org.redisson.api.RBucketReactive;
import org.redisson.api.RFuture;
import org.redisson.client.codec.Codec;
import org.redisson.command.CommandReactiveExecutor;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <V> value type
 */
public class RedissonBucketReactive<V> extends RedissonExpirableReactive implements RBucketReactive<V> {

    private final RBucketAsync<V> instance;
    
    public RedissonBucketReactive(CommandReactiveExecutor connectionManager, String name) {
        this(connectionManager, name, new RedissonBucket<V>(connectionManager, name));
    }
    
    public RedissonBucketReactive(CommandReactiveExecutor connectionManager, String name, RBucketAsync<V> instance) {
        super(connectionManager, name, instance);
        this.instance = instance;
    }

    public RedissonBucketReactive(Codec codec, CommandReactiveExecutor connectionManager, String name) {
        this(codec, connectionManager, name, new RedissonBucket<V>(codec, connectionManager, name));
    }
    
    public RedissonBucketReactive(Codec codec, CommandReactiveExecutor connectionManager, String name, RBucketAsync<V> instance) {
        super(codec, connectionManager, name, instance);
        this.instance = instance;
    }

    @Override
    public Publisher<V> get() {
        return reactive(new Supplier<RFuture<V>>() {
            @Override
            public RFuture<V> get() {
                return instance.getAsync();
            }
        });
    }
    
    @Override
    public Publisher<V> getAndDelete() {
        return reactive(new Supplier<RFuture<V>>() {
            @Override
            public RFuture<V> get() {
                return instance.getAndDeleteAsync();
            }
        });
    }

    @Override
    public Publisher<Void> set(final V value) {
        return reactive(new Supplier<RFuture<Void>>() {
            @Override
            public RFuture<Void> get() {
                return instance.setAsync(value);
            }
        });
    }

    @Override
    public Publisher<Void> set(final V value, final long timeToLive, final TimeUnit timeUnit) {
        return reactive(new Supplier<RFuture<Void>>() {
            @Override
            public RFuture<Void> get() {
                return instance.setAsync(value, timeToLive, timeUnit);
            }
        });
    }

    @Override
    public Publisher<Long> size() {
        return reactive(new Supplier<RFuture<Long>>() {
            @Override
            public RFuture<Long> get() {
                return instance.sizeAsync();
            }
        });
    }

    @Override
    public Publisher<Boolean> trySet(final V value) {
        return reactive(new Supplier<RFuture<Boolean>>() {
            @Override
            public RFuture<Boolean> get() {
                return instance.trySetAsync(value);
            }
        });
    }

    @Override
    public Publisher<Boolean> trySet(final V value, final long timeToLive, final TimeUnit timeUnit) {
        return reactive(new Supplier<RFuture<Boolean>>() {
            @Override
            public RFuture<Boolean> get() {
                return instance.trySetAsync(value, timeToLive, timeUnit);
            }
        });
    }

    @Override
    public Publisher<Boolean> compareAndSet(final V expect, final V update) {
        return reactive(new Supplier<RFuture<Boolean>>() {
            @Override
            public RFuture<Boolean> get() {
                return instance.compareAndSetAsync(expect, update);
            }
        });
    }

    @Override
    public Publisher<V> getAndSet(final V newValue) {
        return reactive(new Supplier<RFuture<V>>() {
            @Override
            public RFuture<V> get() {
                return instance.getAndSetAsync(newValue);
            }
        });
    }

}
