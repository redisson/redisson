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
package org.redisson.rx;

import java.util.Map;
import java.util.Map.Entry;

import org.reactivestreams.Publisher;
import org.redisson.RedissonMap;
import org.redisson.api.*;

/**
 * Distributed and concurrent implementation of {@link java.util.concurrent.ConcurrentMap}
 * and {@link java.util.Map}
 *
 * @author Nikita Koksharov
 *
 * @param <K> key
 * @param <V> value
 */
public class RedissonMapRx<K, V> {

    private final RMap<K, V> instance;
    private final CommandRxExecutor executor;

    public RedissonMapRx(RMap<K, V> instance, CommandRxExecutor executor) {
        this.instance = instance;
        this.executor = executor;
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
        return new RedissonMapRxIterator<K, V, Map.Entry<K, V>>((RedissonMap<K, V>) instance, pattern, count).create();
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
        return new RedissonMapRxIterator<K, V, V>((RedissonMap<K, V>) instance, pattern, count) {
            @Override
            V getValue(Entry<Object, Object> entry) {
                return (V) entry.getValue();
            }
        }.create();
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
        return new RedissonMapRxIterator<K, V, K>((RedissonMap<K, V>) instance, pattern, count) {
            @Override
            K getValue(Entry<Object, Object> entry) {
                return (K) entry.getKey();
            }
        }.create();
    }

    public RPermitExpirableSemaphoreRx getPermitExpirableSemaphore(K key) {
        RPermitExpirableSemaphore s = instance.getPermitExpirableSemaphore(key);
        return RxProxyBuilder.create(executor, s, RPermitExpirableSemaphoreRx.class);
    }

    public RSemaphoreRx getSemaphore(K key) {
        RSemaphore s = instance.getSemaphore(key);
        return RxProxyBuilder.create(executor, s, RSemaphoreRx.class);
    }
    
    public RLockRx getFairLock(K key) {
        RLock lock = instance.getFairLock(key);
        return RxProxyBuilder.create(executor, lock, RLockRx.class);
    }
    
    public RReadWriteLockRx getReadWriteLock(K key) {
        RReadWriteLock lock = instance.getReadWriteLock(key);
        return RxProxyBuilder.create(executor, lock, RReadWriteLockRx.class);
    }
    
    public RLockRx getLock(K key) {
        RLock lock = instance.getLock(key);
        return RxProxyBuilder.create(executor, lock, RLockRx.class);
    }

}
