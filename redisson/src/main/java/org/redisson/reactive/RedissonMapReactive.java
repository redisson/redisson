/**
 * Copyright (c) 2013-2026 Nikita Koksharov
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

import org.reactivestreams.Publisher;
import org.redisson.RedissonMap;
import org.redisson.api.*;
import reactor.core.publisher.Flux;

import java.util.Map.Entry;

/**
 *
 * @author Nikita Koksharov
 *
 * @param <K> key
 * @param <V> value
 */
public class RedissonMapReactive<K, V> {

    private final RMap<K, V> map;
    private final CommandReactiveExecutor commandExecutor;

    public RedissonMapReactive(RMap<K, V> map, CommandReactiveExecutor commandExecutor) {
        this.map = map;
        this.commandExecutor = commandExecutor;
    }
    
    public Publisher<Entry<K, V>> entryIterator() {
        return entryIterator(null);
    }
    
    public Publisher<Entry<K, V>> entryIterator(int count) {
        return entryIterator(null, count);
    }
    
    public Publisher<Entry<K, V>> entryIterator(String pattern) {
        return entryIterator(pattern, 10);
    }
    
    public Publisher<Entry<K, V>> entryIterator(String pattern, int count) {
        return Flux.create(new MapReactiveIterator<>((RedissonMap<K, V>) map, pattern, count));
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
        return Flux.create(new MapReactiveIterator<K, V, V>((RedissonMap<K, V>) map, pattern, count) {
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
        return Flux.create(new MapReactiveIterator<K, V, K>((RedissonMap<K, V>) map, pattern, count) {
            @Override
            K getValue(Entry<Object, Object> entry) {
                return (K) entry.getKey();
            }
        });
    }

    public RPermitExpirableSemaphoreReactive getPermitExpirableSemaphore(K key) {
        RPermitExpirableSemaphore s = map.getPermitExpirableSemaphore(key);
        return ReactiveProxyBuilder.create(commandExecutor, s, RPermitExpirableSemaphoreReactive.class);
    }

    public RSemaphoreReactive getSemaphore(K key) {
        RSemaphore s = map.getSemaphore(key);
        return ReactiveProxyBuilder.create(commandExecutor, s, RSemaphoreReactive.class);
    }

    public RLockReactive getFairLock(K key) {
        RLock lock = map.getFairLock(key);
        return ReactiveProxyBuilder.create(commandExecutor, lock, RLockReactive.class);
    }

    public RReadWriteLockReactive getReadWriteLock(K key)  {
        RReadWriteLock lock = map.getReadWriteLock(key);
        return ReactiveProxyBuilder.create(commandExecutor, lock, RReadWriteLockReactive.class);
    }

    public RLockReactive getLock(K key) {
        RLock lock = map.getLock(key);
        return ReactiveProxyBuilder.create(commandExecutor, lock, RLockReactive.class);
    }

}
