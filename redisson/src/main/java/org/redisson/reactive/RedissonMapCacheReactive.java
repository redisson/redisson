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

import java.util.Map;
import java.util.Map.Entry;

import org.reactivestreams.Publisher;
import org.redisson.RedissonMap;
import org.redisson.api.RMapCache;

import reactor.core.publisher.Flux;

/**
 *
 * @author Nikita Koksharov
 *
 * @param <K> key
 * @param <V> value
 */
public class RedissonMapCacheReactive<K, V> {

    private final RMapCache<K, V> mapCache;

    public RedissonMapCacheReactive(RMapCache<K, V> mapCache) {
        this.mapCache = mapCache;
    }
    
    public Publisher<Map.Entry<K, V>> entryIterator() {
        return entryIterator(null);
    }
    
    public Publisher<Map.Entry<K, V>> entryIterator(int count) {
        return entryIterator(null, count);
    }
    
    public Publisher<Map.Entry<K, V>> entryIterator(String pattern) {
        return entryIterator(pattern, 10);
    }
    
    public Publisher<Map.Entry<K, V>> entryIterator(String pattern, int count) {
        return Flux.create(new MapReactiveIterator<K, V, Map.Entry<K, V>>((RedissonMap<K, V>) mapCache, pattern, count));
    }

    public Publisher<V> valueIterator() {
        return valueIterator(null);
    }
    
    public Publisher<V> valueIterator(String pattern) {
        return valueIterator(pattern, 10);
    }
    
    public Publisher<V> valueIterator(int count) {
        return valueIterator(null, count);
    }
    
    public Publisher<V> valueIterator(String pattern, int count) {
        return Flux.create(new MapReactiveIterator<K, V, V>((RedissonMap<K, V>) mapCache, pattern, count) {
            @Override
            V getValue(Entry<Object, Object> entry) {
                return (V) entry.getValue();
            }
        });
    }

    public Publisher<K> keyIterator() {
        return keyIterator(null);
    }
    
    public Publisher<K> keyIterator(String pattern) {
        return keyIterator(pattern, 10);
    }

    public Publisher<K> keyIterator(int count) {
        return keyIterator(null, count);
    }
    
    public Publisher<K> keyIterator(String pattern, int count) {
        return Flux.create(new MapReactiveIterator<K, V, K>((RedissonMap<K, V>) mapCache, pattern, count) {
            @Override
            K getValue(Entry<Object, Object> entry) {
                return (K) entry.getKey();
            }
        });
    }

            }
