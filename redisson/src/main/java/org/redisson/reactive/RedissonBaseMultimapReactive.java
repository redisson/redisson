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

import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.reactivestreams.Publisher;
import org.redisson.api.RFuture;
import org.redisson.api.RMultimap;
import org.redisson.api.RMultimapReactive;
import org.redisson.client.codec.Codec;
import org.redisson.command.CommandReactiveExecutor;


/**
 * 
 * @author Nikita Koksharov
 *
 * @param <K> key type
 * @param <V> value type
 */
abstract class RedissonBaseMultimapReactive<K, V> extends RedissonExpirableReactive implements RMultimapReactive<K, V> {

    protected final RMultimap<K, V> instance;
    
    public RedissonBaseMultimapReactive(RMultimap<K, V> instance, CommandReactiveExecutor commandExecutor, String name) {
        super(commandExecutor, name, instance);
        this.instance = instance;
    }

    public RedissonBaseMultimapReactive(RMultimap<K, V> instance, Codec codec, CommandReactiveExecutor commandExecutor, String name) {
        super(codec, commandExecutor, name, instance);
        this.instance = instance;
    }

    @Override
    public Publisher<Integer> size() {
        return reactive(new Supplier<RFuture<Integer>>() {
            @Override
            public RFuture<Integer> get() {
                return instance.sizeAsync();
            }
        });
    }

    @Override
    public Publisher<Boolean> containsKey(final Object key) {
        return reactive(new Supplier<RFuture<Boolean>>() {
            @Override
            public RFuture<Boolean> get() {
                return instance.containsKeyAsync(key);
            }
        });
    }

    @Override
    public Publisher<Boolean> containsValue(final Object value) {
        return reactive(new Supplier<RFuture<Boolean>>() {
            @Override
            public RFuture<Boolean> get() {
                return instance.containsValueAsync(value);
            }
        });
    }

    @Override
    public Publisher<Boolean> containsEntry(final Object key, final Object value) {
        return reactive(new Supplier<RFuture<Boolean>>() {
            @Override
            public RFuture<Boolean> get() {
                return instance.containsEntryAsync(key, value);
            }
        });
    }

    @Override
    public Publisher<Boolean> put(final K key, final V value) {
        return reactive(new Supplier<RFuture<Boolean>>() {
            @Override
            public RFuture<Boolean> get() {
                return instance.putAsync(key, value);
            }
        });
    }

    @Override
    public Publisher<Boolean> remove(final Object key, final Object value) {
        return reactive(new Supplier<RFuture<Boolean>>() {
            @Override
            public RFuture<Boolean> get() {
                return instance.removeAsync(key, value);
            }
        });
    }

    @Override
    public Publisher<Boolean> putAll(final K key, final Iterable<? extends V> values) {
        return reactive(new Supplier<RFuture<Boolean>>() {
            @Override
            public RFuture<Boolean> get() {
                return instance.putAllAsync(key, values);
            }
        });
    }

    @Override
    public Publisher<Integer> keySize() {
        return reactive(new Supplier<RFuture<Integer>>() {
            @Override
            public RFuture<Integer> get() {
                return instance.keySizeAsync();
            }
        });
    }

    @Override
    public Publisher<Long> fastRemove(final K... keys) {
        return reactive(new Supplier<RFuture<Long>>() {
            @Override
            public RFuture<Long> get() {
                return instance.fastRemoveAsync(keys);
            }
        });
    }

    @Override
    public Publisher<Set<K>> readAllKeySet() {
        return reactive(new Supplier<RFuture<Set<K>>>() {
            @Override
            public RFuture<Set<K>> get() {
                return instance.readAllKeySetAsync();
            }
        });
    }

    @Override
    public Publisher<Boolean> delete() {
        return reactive(new Supplier<RFuture<Boolean>>() {
            @Override
            public RFuture<Boolean> get() {
                return instance.deleteAsync();
    }
        });
    }
    
    @Override
    public Publisher<Boolean> expire(final long timeToLive, final TimeUnit timeUnit) {
        return reactive(new Supplier<RFuture<Boolean>>() {
            @Override
            public RFuture<Boolean> get() {
                return instance.expireAsync(timeToLive, timeUnit);
        }
        });
    }

    @Override
    public Publisher<Boolean> expireAt(final long timestamp) {
        return reactive(new Supplier<RFuture<Boolean>>() {
            @Override
            public RFuture<Boolean> get() {
                return instance.expireAtAsync(timestamp);
    }
        });
    }
    
    @Override
    public Publisher<Boolean> clearExpire() {
        return reactive(new Supplier<RFuture<Boolean>>() {
            @Override
            public RFuture<Boolean> get() {
                return instance.clearExpireAsync();
    }
        });
    }
    
}
