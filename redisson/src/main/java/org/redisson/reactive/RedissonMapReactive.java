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

import org.reactivestreams.Publisher;
import org.redisson.RedissonMap;
import org.redisson.api.RMapReactive;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.ScanCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.decoder.MapScanResult;
import org.redisson.client.protocol.decoder.ScanObjectEntry;
import org.redisson.command.CommandReactiveExecutor;

import reactor.fn.BiFunction;
import reactor.fn.Function;
import reactor.rx.Streams;


/**
 * Distributed and concurrent implementation of {@link java.util.concurrent.ConcurrentMap}
 * and {@link java.util.Map}
 *
 * @author Nikita Koksharov
 *
 * @param <K> key
 * @param <V> value
 */
public class RedissonMapReactive<K, V> extends RedissonExpirableReactive implements RMapReactive<K, V> {

    private final RedissonMap<K, V> instance;

    public RedissonMapReactive(CommandReactiveExecutor commandExecutor, String name) {
        super(commandExecutor, name);
        instance = new RedissonMap<K, V>(codec, commandExecutor, name);
    }

    public RedissonMapReactive(Codec codec, CommandReactiveExecutor commandExecutor, String name) {
        super(codec, commandExecutor, name);
        instance = new RedissonMap<K, V>(codec, commandExecutor, name);
    }

    @Override
    public Publisher<Integer> size() {
        return reactive(instance.sizeAsync());
    }

    @Override
    public Publisher<Boolean> containsKey(Object key) {
        return reactive(instance.containsKeyAsync(key));
    }

    @Override
    public Publisher<Boolean> containsValue(Object value) {
        return reactive(instance.containsValueAsync(value));
    }

    @Override
    public Publisher<Map<K, V>> getAll(Set<K> keys) {
        return reactive(instance.getAllAsync(keys));
    }

    public Publisher<Void> putAll(Map<? extends K, ? extends V> map) {
        return reactive(instance.putAllAsync(map));
    }

    @Override
    public Publisher<V> putIfAbsent(K key, V value) {
        return reactive(instance.putIfAbsentAsync(key, value));
    }

    @Override
    public Publisher<Long> remove(Object key, Object value) {
        return reactive(instance.removeAsync(key, value));
    }

    @Override
    public Publisher<Boolean> replace(K key, V oldValue, V newValue) {
        return reactive(instance.replaceAsync(key, oldValue, newValue));
    }

    @Override
    public Publisher<V> replace(K key, V value) {
        return reactive(instance.replaceAsync(key, value));
    }

    @Override
    public Publisher<V> get(K key) {
        return reactive(instance.getAsync(key));
    }

    @Override
    public Publisher<V> put(K key, V value) {
        return reactive(instance.putAsync(key, value));
    }


    @Override
    public Publisher<V> remove(K key) {
        return reactive(instance.removeAsync(key));
    }

    @Override
    public Publisher<Boolean> fastPut(K key, V value) {
        return reactive(instance.fastPutAsync(key, value));
    }

    @Override
    public Publisher<Long> fastRemove(K ... keys) {
        return reactive(instance.fastRemoveAsync(keys));
    }

    Publisher<MapScanResult<ScanObjectEntry, ScanObjectEntry>> scanIteratorReactive(InetSocketAddress client, long startPos) {
        return commandExecutor.readReactive(client, getName(), new ScanCodec(codec), RedisCommands.HSCAN, getName(), startPos);
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
    public Publisher<V> addAndGet(K key, Number value) {
        return reactive(instance.addAndGetAsync(key, value));
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
