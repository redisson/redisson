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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.reactivestreams.Publisher;
import org.redisson.RedissonSetCache;
import org.redisson.ScanIterator;
import org.redisson.api.RFuture;
import org.redisson.api.RSetCacheAsync;
import org.redisson.api.RSetCacheReactive;
import org.redisson.client.RedisClient;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.decoder.ListScanResult;
import org.redisson.command.CommandReactiveExecutor;
import org.redisson.eviction.EvictionScheduler;

import io.netty.buffer.ByteBuf;
import reactor.core.publisher.Flux;

/**
 * <p>Set-based cache with ability to set TTL for each entry via
 * {@link #add(Object, long, TimeUnit)} method.
 * And therefore has an complex lua-scripts inside.
 * Uses map(value_hash, value) to tie with sorted set which contains expiration record for every value with TTL.
 * </p>
 *
 * <p>Current Redis implementation doesn't have set entry eviction functionality.
 * Thus values are checked for TTL expiration during any value read operation.
 * If entry expired then it doesn't returns and clean task runs hronous.
 * Clean task deletes removes 100 expired entries at once.
 * In addition there is {@link org.redisson.eviction.EvictionScheduler}. This scheduler
 * deletes expired entries in time interval between 5 seconds to 2 hours.</p>
 *
 * <p>If eviction is not required then it's better to use {@link org.redisson.api.RSet}.</p>
 *
 * @author Nikita Koksharov
 *
 * @param <V> value
 */
public class RedissonSetCacheReactive<V> extends RedissonExpirableReactive implements RSetCacheReactive<V> {

    private final RSetCacheAsync<V> instance;
    
    public RedissonSetCacheReactive(EvictionScheduler evictionScheduler, CommandReactiveExecutor commandExecutor, String name) {
        this(commandExecutor, name, new RedissonSetCache<V>(evictionScheduler, commandExecutor, name, null));
    }
    
    public RedissonSetCacheReactive(CommandReactiveExecutor commandExecutor, String name, RSetCacheAsync<V> instance) {
        super(commandExecutor, name, instance);
        this.instance = instance;
    }

    public RedissonSetCacheReactive(Codec codec, EvictionScheduler evictionScheduler, CommandReactiveExecutor commandExecutor, String name) {
        this(codec, commandExecutor, name, new RedissonSetCache<V>(codec, evictionScheduler, commandExecutor, name, null));
    }

    public RedissonSetCacheReactive(Codec codec, CommandReactiveExecutor commandExecutor, String name, RSetCacheAsync<V> instance) {
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
    public Publisher<Boolean> contains(final Object o) {
        return reactive(new Supplier<RFuture<Boolean>>() {
            @Override
            public RFuture<Boolean> get() {
                return instance.containsAsync(o);
            }
        });
    }

    Publisher<ListScanResult<Object>> scanIterator(final RedisClient client, final long startPos) {
        return reactive(new Supplier<RFuture<ListScanResult<Object>>>() {
            @Override
            public RFuture<ListScanResult<Object>> get() {
                return ((ScanIterator)instance).scanIteratorAsync(getName(), client, startPos, null, 10);
            }
        });
    }

    @Override
    public Publisher<V> iterator() {
        return Flux.create(new SetReactiveIterator<V>() {
            @Override
            protected Publisher<ListScanResult<Object>> scanIteratorReactive(RedisClient client, long nextIterPos) {
                return RedissonSetCacheReactive.this.scanIterator(client, nextIterPos);
            }
        });
    }

    @Override
    public Publisher<Boolean> add(final V value, final long ttl, final TimeUnit unit) {
        return reactive(new Supplier<RFuture<Boolean>>() {
            @Override
            public RFuture<Boolean> get() {
                return instance.addAsync(value, ttl, unit);
            }
        });
    }

    @Override
    public Publisher<Integer> add(V value) {
        long timeoutDate = 92233720368547758L;
        return commandExecutor.evalWriteReactive(getName(), codec, RedisCommands.EVAL_INTEGER,
                "local expireDateScore = redis.call('zscore', KEYS[1], ARGV[3]); "
                + "if expireDateScore ~= false and tonumber(expireDateScore) > tonumber(ARGV[1]) then "
                    + "return 0;"
                + "end; " +
                "redis.call('zadd', KEYS[1], ARGV[2], ARGV[3]); " +
                "return 1; ",
                Arrays.<Object>asList(getName()), System.currentTimeMillis(), timeoutDate, encode(value));
    }

    @Override
    public Publisher<Set<V>> readAll() {
        return reactive(new Supplier<RFuture<Set<V>>>() {
            @Override
            public RFuture<Set<V>> get() {
                return instance.readAllAsync();
            }
        });
    }
    
    @Override
    public Publisher<Boolean> remove(final Object o) {
        return reactive(new Supplier<RFuture<Boolean>>() {
            @Override
            public RFuture<Boolean> get() {
                return instance.removeAsync(o);
            }
        });
    }

    @Override
    public Publisher<Boolean> containsAll(final Collection<?> c) {
        return reactive(new Supplier<RFuture<Boolean>>() {
            @Override
            public RFuture<Boolean> get() {
                return instance.containsAllAsync(c);
            }
        });
    }

    @Override
    public Publisher<Integer> addAll(Collection<? extends V> c) {
        if (c.isEmpty()) {
            return newSucceeded(0);
        }

        long score = 92233720368547758L - System.currentTimeMillis();
        List<Object> params = new ArrayList<Object>(c.size()*2 + 1);
        params.add(getName());
        for (V value : c) {
            ByteBuf objectState = encode(value);
            params.add(score);
            params.add(objectState);
        }

        return commandExecutor.writeReactive(getName(), codec, RedisCommands.ZADD_RAW, params.toArray());
    }

    @Override
    public Publisher<Boolean> retainAll(final Collection<?> c) {
        return reactive(new Supplier<RFuture<Boolean>>() {
            @Override
            public RFuture<Boolean> get() {
                return instance.retainAllAsync(c);
            }
        });
    }

    @Override
    public Publisher<Boolean> removeAll(final Collection<?> c) {
        return reactive(new Supplier<RFuture<Boolean>>() {
            @Override
            public RFuture<Boolean> get() {
                return instance.removeAllAsync(c);
            }
        });
    }

    @Override
    public Publisher<Integer> addAll(Publisher<? extends V> c) {
        return new PublisherAdder<V>(this).addAll(c);
    }

}
