/**
 * Copyright (c) 2013-2022 Nikita Koksharov
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
import org.redisson.RedissonObject;
import org.redisson.ScanIterator;
import org.redisson.ScanResult;
import org.redisson.api.*;
import org.redisson.client.RedisClient;
import reactor.core.publisher.Flux;

/**
 * Distributed and concurrent implementation of {@link java.util.Set}
 *
 * @author Nikita Koksharov
 *
 * @param <V> value
 */
public class RedissonSetReactive<V> {

    private final RSet<V> instance;
    private final RedissonReactiveClient redisson;

    public RedissonSetReactive(RSet<V> instance, RedissonReactiveClient redisson) {
        this.instance = instance;
        this.redisson = redisson;
    }

    public Publisher<Boolean> addAll(Publisher<? extends V> c) {
        return new PublisherAdder<Object>() {
            @Override
            public RFuture<Boolean> add(Object e) {
                return instance.addAsync((V) e);
            }
        }.addAll(c);
    }

    public Publisher<V> iterator(int count) {
        return iterator(null, count);
    }
    
    public Publisher<V> iterator(String pattern) {
        return iterator(pattern, 10);
    }

    public Publisher<V> iterator(String pattern, int count) {
        return Flux.create(new SetReactiveIterator<V>() {
            @Override
            protected RFuture<ScanResult<Object>> scanIterator(RedisClient client, long nextIterPos) {
                return ((ScanIterator) instance).scanIteratorAsync(((RedissonObject) instance).getRawName(), client, nextIterPos, pattern, count);
            }
        });
    }

    public Publisher<V> iterator() {
        return iterator(null, 10);
}
    
    public RPermitExpirableSemaphoreReactive getPermitExpirableSemaphore(V value) {
        String name = ((RedissonObject) instance).getLockByValue(value, "permitexpirablesemaphore");
        return redisson.getPermitExpirableSemaphore(name);
    }

    public RSemaphoreReactive getSemaphore(V value) {
        String name = ((RedissonObject) instance).getLockByValue(value, "semaphore");
        return redisson.getSemaphore(name);
    }
    
    public RLockReactive getFairLock(V value) {
        String name = ((RedissonObject) instance).getLockByValue(value, "fairlock");
        return redisson.getFairLock(name);
    }
    
    public RReadWriteLockReactive getReadWriteLock(V value) {
        String name = ((RedissonObject) instance).getLockByValue(value, "rw_lock");
        return redisson.getReadWriteLock(name);
    }
    
    public RLockReactive getLock(V value) {
        String name = ((RedissonObject) instance).getLockByValue(value, "lock");
        return redisson.getLock(name);
    }
    
}
