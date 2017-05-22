/**
 * Copyright 2016 Nikita Koksharov
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

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.reactivestreams.Publisher;
import org.redisson.RedissonMapCache;
import org.redisson.api.RMapCache;
import org.redisson.api.RMapCacheReactive;
import org.redisson.api.RMapReactive;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommand.ValueType;
import org.redisson.client.protocol.decoder.MapScanResult;
import org.redisson.client.protocol.decoder.MapScanResultReplayDecoder;
import org.redisson.client.protocol.decoder.NestedMultiDecoder;
import org.redisson.client.protocol.decoder.ObjectMapReplayDecoder;
import org.redisson.client.protocol.decoder.ScanObjectEntry;
import org.redisson.command.CommandReactiveExecutor;
import org.redisson.eviction.EvictionScheduler;

import reactor.fn.BiFunction;
import reactor.fn.Function;
import reactor.rx.Streams;

/**
 * <p>Map-based cache with ability to set TTL for each entry via
 * {@link #put(Object, Object, long, TimeUnit)} or {@link #putIfAbsent(Object, Object, long, TimeUnit)} method.
 * And therefore has an complex lua-scripts inside.</p>
 *
 * <p>Current redis implementation doesnt have map entry eviction functionality.
 * Thus entries are checked for TTL expiration during any key/value/entry read operation.
 * If key/value/entry expired then it doesn't returns and clean task runs asynchronous.
 * Clean task deletes removes 100 expired entries at once.
 * In addition there is {@link org.redisson.eviction.EvictionScheduler}. This scheduler
 * deletes expired entries in time interval between 5 seconds to 2 hours.</p>
 *
 * <p>If eviction is not required then it's better to use {@link org.redisson.reactive.RedissonMapReactive}.</p>
 *
 * @author Nikita Koksharov
 *
 * @param <K> key
 * @param <V> value
 */
public class RedissonMapCacheReactive<K, V> extends RedissonExpirableReactive implements RMapCacheReactive<K, V>, MapReactive<K, V> {

    private static final RedisCommand<MapScanResult<Object, Object>> EVAL_HSCAN = 
            new RedisCommand<MapScanResult<Object, Object>>("EVAL", new NestedMultiDecoder(new ObjectMapReplayDecoder(), new MapScanResultReplayDecoder()), ValueType.MAP);

    private final RMapCache<K, V> mapCache;

    public RedissonMapCacheReactive(UUID id, EvictionScheduler evictionScheduler, CommandReactiveExecutor commandExecutor, String name) {
        super(commandExecutor, name);
        this.mapCache = new RedissonMapCache<K, V>(id, evictionScheduler, commandExecutor, name, null);
    }

    public RedissonMapCacheReactive(UUID id, EvictionScheduler evictionScheduler, Codec codec, CommandReactiveExecutor commandExecutor, String name) {
        super(codec, commandExecutor, name);
        this.mapCache = new RedissonMapCache<K, V>(id, codec, evictionScheduler, commandExecutor, name, null);
    }

    @Override
    public Publisher<Boolean> containsKey(Object key) {
        return reactive(mapCache.containsKeyAsync(key));
    }

    @Override
    public Publisher<Boolean> containsValue(Object value) {
        return reactive(mapCache.containsValueAsync(value));
    }

    @Override
    public Publisher<Map<K, V>> getAll(Set<K> keys) {
        return reactive(mapCache.getAllAsync(keys));
    }

    @Override
    public Publisher<V> putIfAbsent(K key, V value, long ttl, TimeUnit unit) {
        return reactive(mapCache.putIfAbsentAsync(key, value, ttl, unit));
    }

    @Override
    public Publisher<Boolean> remove(Object key, Object value) {
        return reactive(mapCache.removeAsync(key, value));
    }

    @Override
    public Publisher<V> get(K key) {
        return reactive(mapCache.getAsync(key));
    }

    @Override
    public Publisher<V> put(K key, V value, long ttl, TimeUnit unit) {
        return reactive(mapCache.putAsync(key, value, ttl, unit));
    }

    String getTimeoutSetName() {
        return "redisson__timeout__set__{" + getName() + "}";
    }

    @Override
    public Publisher<V> remove(K key) {
        return reactive(mapCache.removeAsync(key));
    }

    @Override
    public Publisher<Long> fastRemove(K ... keys) {
        return reactive(mapCache.fastRemoveAsync(keys));
    }

    @Override
    public Publisher<MapScanResult<ScanObjectEntry, ScanObjectEntry>> scanIteratorReactive(InetSocketAddress client, long startPos) {
        return reactive(((RedissonMapCache<K, V>)mapCache).scanIteratorAsync(getName(), client, startPos));
    }

    @Override
    public Publisher<Boolean> delete() {
        return reactive(mapCache.deleteAsync());
    }

    @Override
    public Publisher<Boolean> expire(long timeToLive, TimeUnit timeUnit) {
        return reactive(mapCache.expireAsync(timeToLive, timeUnit));
    }

    @Override
    public Publisher<Boolean> expireAt(long timestamp) {
        return reactive(mapCache.expireAtAsync(timestamp));
    }

    @Override
    public Publisher<Boolean> clearExpire() {
        return reactive(mapCache.clearExpireAsync());
    }

    @Override
    public Publisher<Void> putAll(Map<? extends K, ? extends V> map) {
        return reactive(mapCache.putAllAsync(map));
    }

    @Override
    public Publisher<V> addAndGet(K key, Number delta) {
        return reactive(mapCache.addAndGetAsync(key, delta));
    }

    @Override
    public Publisher<Boolean> fastPut(K key, V value) {
        return reactive(mapCache.fastPutAsync(key, value));
    }

    @Override
    public Publisher<V> put(K key, V value) {
        return reactive(mapCache.putAsync(key, value));
    }

    @Override
    public Publisher<V> replace(K key, V value) {
        return reactive(mapCache.replaceAsync(key, value));
    }

    @Override
    public Publisher<Boolean> replace(K key, V oldValue, V newValue) {
        return reactive(mapCache.replaceAsync(key, oldValue, newValue));
    }

    @Override
    public Publisher<V> putIfAbsent(K key, V value) {
        return reactive(mapCache.putIfAbsentAsync(key, value));
    }

    @Override
    public Publisher<Map.Entry<K, V>> entryIterator() {
        return new RedissonMapReactiveIterator<K, V, Map.Entry<K, V>>(this).stream();
    }

    @Override
    public Publisher<V> valueIterator() {
        return new RedissonMapReactiveIterator<K, V, V>(this) {
            @Override
            V getValue(Entry<ScanObjectEntry, ScanObjectEntry> entry) {
                return (V) entry.getValue().getObj();
            }
        }.stream();
    }

    @Override
    public Publisher<K> keyIterator() {
        return new RedissonMapReactiveIterator<K, V, K>(this) {
            @Override
            K getValue(Entry<ScanObjectEntry, ScanObjectEntry> entry) {
                return (K) entry.getKey().getObj();
            }
        }.stream();
    }

    @Override
    public Publisher<Integer> size() {
        return reactive(mapCache.sizeAsync());
    }

    @Override
    public boolean equals(Object o) {
        if (o == this)
            return true;

        if (o instanceof Map) {
            final Map<?,?> m = (Map<?,?>) o;
            if (m.size() != Streams.create(size()).next().poll()) {
                return false;
            }

            return Streams.create(entryIterator()).map(mapFunction(m)).reduce(true, booleanAnd()).next().poll();
        } else if (o instanceof RMapReactive) {
            final RMapReactive<Object, Object> m = (RMapReactive<Object, Object>) o;
            if (Streams.create(m.size()).next().poll() != Streams.create(size()).next().poll()) {
                return false;
            }

            return Streams.create(entryIterator()).map(mapFunction(m)).reduce(true, booleanAnd()).next().poll();
        }

        return true;
    }

    private BiFunction<Boolean, Boolean, Boolean> booleanAnd() {
        return new BiFunction<Boolean, Boolean, Boolean>() {

            @Override
            public Boolean apply(Boolean t, Boolean u) {
                return t & u;
            }
        };
    }

    private Function<Entry<K, V>, Boolean> mapFunction(final Map<?, ?> m) {
        return new Function<Map.Entry<K, V>, Boolean>() {
            @Override
            public Boolean apply(Entry<K, V> e) {
                K key = e.getKey();
                V value = e.getValue();
                if (value == null) {
                    if (!(m.get(key)==null && m.containsKey(key)))
                        return false;
                } else {
                    if (!value.equals(m.get(key)))
                        return false;
                }
                return true;
            }
        };
    }

    private Function<Entry<K, V>, Boolean> mapFunction(final RMapReactive<Object, Object> m) {
        return new Function<Map.Entry<K, V>, Boolean>() {
            @Override
            public Boolean apply(Entry<K, V> e) {
                Object key = e.getKey();
                Object value = e.getValue();
                if (value == null) {
                    if (!(Streams.create(m.get(key)).next().poll() ==null && Streams.create(m.containsKey(key)).next().poll()))
                        return false;
                } else {
                    if (!value.equals(Streams.create(m.get(key)).next().poll()))
                        return false;
                }
                return true;
            }
        };
    }

    @Override
    public int hashCode() {
        return Streams.create(entryIterator()).map(new Function<Map.Entry<K, V>, Integer>() {
            @Override
            public Integer apply(Entry<K, V> t) {
                return t.hashCode();
            }
        }).reduce(0, new BiFunction<Integer, Integer, Integer>() {

            @Override
            public Integer apply(Integer t, Integer u) {
                return t + u;
            }
        }).next().poll();
    }

}
