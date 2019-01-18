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
package org.redisson.rx;

import java.util.Map;
import java.util.Map.Entry;

import org.reactivestreams.Publisher;
import org.redisson.RedissonMap;
import org.redisson.RedissonRx;
import org.redisson.api.RLockRx;
import org.redisson.api.RMap;
import org.redisson.api.RPermitExpirableSemaphoreRx;
import org.redisson.api.RReadWriteLockRx;
import org.redisson.api.RSemaphoreRx;
import org.redisson.api.RedissonRxClient;

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

    private final RedissonMap<K, V> instance;
    private RedissonRxClient redisson;

    public RedissonMapRx(RMap<K, V> instance, RedissonRx redisson) {
        this.instance = (RedissonMap<K, V>) instance;
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
        return new RedissonMapRxIterator<K, V, Map.Entry<K, V>>(instance, pattern, count).create();
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
        return new RedissonMapRxIterator<K, V, V>(instance, pattern, count) {
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
        return new RedissonMapRxIterator<K, V, K>(instance, pattern, count) {
            @Override
            K getValue(Entry<Object, Object> entry) {
                return (K) entry.getKey();
            }
        }.create();
    }

    public RPermitExpirableSemaphoreRx getPermitExpirableSemaphore(K key) {
        String name = ((RedissonMap<K, V>)instance).getLockName(key, "permitexpirablesemaphore");
        return redisson.getPermitExpirableSemaphore(name);
    }

    public RSemaphoreRx getSemaphore(K key) {
        String name = ((RedissonMap<K, V>)instance).getLockName(key, "semaphore");
        return redisson.getSemaphore(name);
    }
    
    public RLockRx getFairLock(K key) {
        String name = ((RedissonMap<K, V>)instance).getLockName(key, "fairlock");
        return redisson.getFairLock(name);
    }
    
    public RReadWriteLockRx getReadWriteLock(K key) {
        String name = ((RedissonMap<K, V>)instance).getLockName(key, "rw_lock");
        return redisson.getReadWriteLock(name);
    }
    
    public RLockRx getLock(K key) {
        String name = ((RedissonMap<K, V>)instance).getLockName(key, "lock");
        return redisson.getLock(name);
    }

}
