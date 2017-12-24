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
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.reactivestreams.Publisher;
import org.redisson.RedissonMap;
import org.redisson.api.MapOptions;
import org.redisson.api.RFuture;
import org.redisson.api.RMapAsync;
import org.redisson.api.RMapReactive;
import org.redisson.client.RedisClient;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.MapScanCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.decoder.MapScanResult;
import org.redisson.client.protocol.decoder.ScanObjectEntry;
import org.redisson.command.CommandReactiveExecutor;

import reactor.fn.BiFunction;
import reactor.fn.Function;
import reactor.fn.Supplier;
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
public class RedissonMapReactive<K, V> extends RedissonExpirableReactive implements RMapReactive<K, V>, MapReactive<K, V> {

    private final RMapAsync<K, V> instance;

    public RedissonMapReactive(CommandReactiveExecutor commandExecutor, String name, MapOptions<K, V> options) {
        super(commandExecutor, name);
        instance = new RedissonMap<K, V>(codec, commandExecutor, name, null, options);
    }

    public RedissonMapReactive(Codec codec, CommandReactiveExecutor commandExecutor, String name, MapOptions<K, V> options) {
        super(codec, commandExecutor, name);
        instance = new RedissonMap<K, V>(codec, commandExecutor, name, null, options);
    }

    @Override
    public Publisher<Void> loadAll(final boolean replaceExistingValues, final int parallelism) {
        return reactive(new Supplier<RFuture<Void>>() {
            @Override
            public RFuture<Void> get() {
                return instance.loadAllAsync(replaceExistingValues, parallelism);
            }
        });
    }

    @Override
    public Publisher<Void> loadAll(final Set<? extends K> keys, final boolean replaceExistingValues, final int parallelism) {
        return reactive(new Supplier<RFuture<Void>>() {
            @Override
            public RFuture<Void> get() {
                return instance.loadAllAsync(keys, replaceExistingValues, parallelism);
            }
        });
    }
    
    @Override
    public Publisher<Boolean> fastPutIfAbsent(final K key, final V value) {
        return reactive(new Supplier<RFuture<Boolean>>() {
            @Override
            public RFuture<Boolean> get() {
                return instance.fastPutIfAbsentAsync(key, value);
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
    public Publisher<Collection<V>> readAllValues() {
        return reactive(new Supplier<RFuture<Collection<V>>>() {
            @Override
            public RFuture<Collection<V>> get() {
                return instance.readAllValuesAsync();
            }
        });
    }

    @Override
    public Publisher<Set<Entry<K, V>>> readAllEntrySet() {
        return reactive(new Supplier<RFuture<Set<Entry<K, V>>>>() {
            @Override
            public RFuture<Set<Entry<K, V>>> get() {
                return instance.readAllEntrySetAsync();
            }
        });
    }

    @Override
    public Publisher<Map<K, V>> readAllMap() {
        return reactive(new Supplier<RFuture<Map<K, V>>>() {
            @Override
            public RFuture<Map<K, V>> get() {
                return instance.readAllMapAsync();
            }
        });
    }
    
    @Override
    public Publisher<Integer> valueSize(final K key) {
        return reactive(new Supplier<RFuture<Integer>>() {
            @Override
            public RFuture<Integer> get() {
                return instance.valueSizeAsync(key);
            }
        });
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
    public Publisher<Map<K, V>> getAll(final Set<K> keys) {
        return reactive(new Supplier<RFuture<Map<K, V>>>() {
            @Override
            public RFuture<Map<K, V>> get() {
                return instance.getAllAsync(keys);
            }
        });
    }

    @Override
    public Publisher<Void> putAll(final Map<? extends K, ? extends V> map) {
        return reactive(new Supplier<RFuture<Void>>() {
            @Override
            public RFuture<Void> get() {
                return instance.putAllAsync(map);
            }
        });
    }

    @Override
    public Publisher<V> putIfAbsent(final K key, final V value) {
        return reactive(new Supplier<RFuture<V>>() {
            @Override
            public RFuture<V> get() {
                return instance.putIfAbsentAsync(key, value);
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
    public Publisher<Boolean> replace(final K key, final V oldValue, final V newValue) {
        return reactive(new Supplier<RFuture<Boolean>>() {
            @Override
            public RFuture<Boolean> get() {
                return instance.replaceAsync(key, oldValue, newValue);
            }
        });
    }

    @Override
    public Publisher<V> replace(final K key, final V value) {
        return reactive(new Supplier<RFuture<V>>() {
            @Override
            public RFuture<V> get() {
                return instance.replaceAsync(key, value);
            }
        });
    }

    @Override
    public Publisher<V> get(final K key) {
        return reactive(new Supplier<RFuture<V>>() {
            @Override
            public RFuture<V> get() {
                return instance.getAsync(key);
            }
        });
    }

    @Override
    public Publisher<V> put(final K key, final V value) {
        return reactive(new Supplier<RFuture<V>>() {
            @Override
            public RFuture<V> get() {
                return instance.putAsync(key, value);
            }
        });
    }


    @Override
    public Publisher<V> remove(final K key) {
        return reactive(new Supplier<RFuture<V>>() {
            @Override
            public RFuture<V> get() {
                return instance.removeAsync(key);
            }
        });
    }

    @Override
    public Publisher<Boolean> fastPut(final K key, final V value) {
        return reactive(new Supplier<RFuture<Boolean>>() {
            @Override
            public RFuture<Boolean> get() {
                return instance.fastPutAsync(key, value);
            }
        });
    }

    @Override
    public Publisher<Long> fastRemove(final K ... keys) {
        return reactive(new Supplier<RFuture<Long>>() {
            @Override
            public RFuture<Long> get() {
                return instance.fastRemoveAsync(keys);
            }
        });
    }

    public Publisher<MapScanResult<ScanObjectEntry, ScanObjectEntry>> scanIteratorReactive(RedisClient client, long startPos) {
        return commandExecutor.readReactive(client, getName(), new MapScanCodec(codec), RedisCommands.HSCAN, getName(), startPos);
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
    public Publisher<V> addAndGet(final K key, final Number value) {
        return reactive(new Supplier<RFuture<V>>() {
            @Override
            public RFuture<V> get() {
                return instance.addAndGetAsync(key, value);
            }
        });
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
