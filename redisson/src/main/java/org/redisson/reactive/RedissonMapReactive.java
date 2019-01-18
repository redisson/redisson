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
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import org.reactivestreams.Publisher;
import org.redisson.RedissonMap;
import org.redisson.api.RLockReactive;
import org.redisson.api.RMap;
import org.redisson.api.RPermitExpirableSemaphoreReactive;
import org.redisson.api.RReadWriteLockReactive;
import org.redisson.api.RSemaphoreReactive;
import org.redisson.api.RedissonReactiveClient;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Distributed and concurrent implementation of {@link java.util.concurrent.ConcurrentMap}
 * and {@link java.util.Map}
 *
 * @author Nikita Koksharov
 *
 * @param <K> key
 * @param <V> value
 */
public class RedissonMapReactive<K, V> {

    private final RMap<K, V> instance;
    private final RedissonReactiveClient redisson;

    public RedissonMapReactive(RMap<K, V> instance, RedissonReactiveClient redisson) {
        this.instance = instance;
        this.redisson = redisson;
    }

    public Publisher<Map.Entry<K, V>> entryIterator() {
        return entryIterator(null);
    }
    
    public Publisher<Entry<K, V>> entryIterator(int count) {
        return entryIterator(null, count);
    }
    
    public Publisher<Entry<K, V>> entryIterator(String pattern) {
        return entryIterator(pattern, 10);
    }
    
    public Publisher<Map.Entry<K, V>> entryIterator(String pattern, int count) {
        return Flux.create(new MapReactiveIterator<K, V, Map.Entry<K, V>>((RedissonMap<K, V>) instance, pattern, count));
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
        return Flux.create(new MapReactiveIterator<K, V, V>((RedissonMap<K, V>) instance, pattern, count) {
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
        return Flux.create(new MapReactiveIterator<K, V, K>((RedissonMap<K, V>) instance, pattern, count) {
            @Override
            K getValue(Entry<Object, Object> entry) {
                return (K) entry.getKey();
            }
        });
    }
    
    public RPermitExpirableSemaphoreReactive getPermitExpirableSemaphore(K key) {
        String name = ((RedissonMap<K, V>)instance).getLockName(key, "permitexpirablesemaphore");
        return redisson.getPermitExpirableSemaphore(name);
    }

    public RSemaphoreReactive getSemaphore(K key) {
        String name = ((RedissonMap<K, V>)instance).getLockName(key, "semaphore");
        return redisson.getSemaphore(name);
    }
    
    public RLockReactive getFairLock(K key) {
        String name = ((RedissonMap<K, V>)instance).getLockName(key, "fairlock");
        return redisson.getFairLock(name);
    }
    
    public RReadWriteLockReactive getReadWriteLock(K key) {
        String name = ((RedissonMap<K, V>)instance).getLockName(key, "rw_lock");
        return redisson.getReadWriteLock(name);
    }
    
    public RLockReactive getLock(K key) {
        String name = ((RedissonMap<K, V>)instance).getLockName(key, "lock");
        return redisson.getLock(name);
    }

            }
