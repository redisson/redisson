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

import org.reactivestreams.Publisher;
import org.redisson.RedissonSet;
import org.redisson.api.RFuture;
import org.redisson.api.RLockRx;
import org.redisson.api.RPermitExpirableSemaphoreRx;
import org.redisson.api.RReadWriteLockRx;
import org.redisson.api.RSemaphoreRx;
import org.redisson.api.RSet;
import org.redisson.api.RedissonRxClient;
import org.redisson.client.RedisClient;
import org.redisson.client.protocol.decoder.ListScanResult;

import io.reactivex.Flowable;

/**
 * Distributed and concurrent implementation of {@link java.util.Set}
 *
 * @author Nikita Koksharov
 *
 * @param <V> value
 */
public class RedissonSetRx<V> {

    private final RSet<V> instance;
    private final RedissonRxClient redisson;

    public RedissonSetRx(RSet<V> instance, RedissonRxClient redisson) {
        this.instance = instance;
        this.redisson = redisson;
    }
    
    public Flowable<Boolean> addAll(Publisher<? extends V> c) {
        return new PublisherAdder<Object>() {
            @Override
            public RFuture<Boolean> add(Object e) {
                return instance.addAsync((V)e);
            }
        }.addAll(c);
    }

    public Flowable<V> iterator(int count) {
        return iterator(null, count);
    }
    
    public Flowable<V> iterator(String pattern) {
        return iterator(pattern, 10);
    }

    public Flowable<V> iterator(final String pattern, final int count) {
        return new SetRxIterator<V>() {
            @Override
            protected RFuture<ListScanResult<Object>> scanIterator(RedisClient client, long nextIterPos) {
                return ((RedissonSet<V>)instance).scanIteratorAsync(instance.getName(), client, nextIterPos, pattern, count);
            }
        }.create();
    }

    public Publisher<V> iterator() {
        return iterator(null, 10);
    }
    
    public RPermitExpirableSemaphoreRx getPermitExpirableSemaphore(V value) {
        String name = ((RedissonSet<V>)instance).getLockName(value, "permitexpirablesemaphore");
        return redisson.getPermitExpirableSemaphore(name);
    }

    public RSemaphoreRx getSemaphore(V value) {
        String name = ((RedissonSet<V>)instance).getLockName(value, "semaphore");
        return redisson.getSemaphore(name);
    }
    
    public RLockRx getFairLock(V value) {
        String name = ((RedissonSet<V>)instance).getLockName(value, "fairlock");
        return redisson.getFairLock(name);
    }
    
    public RReadWriteLockRx getReadWriteLock(V value) {
        String name = ((RedissonSet<V>)instance).getLockName(value, "rw_lock");
        return redisson.getReadWriteLock(name);
    }
    
    public RLockRx getLock(V value) {
        String name = ((RedissonSet<V>)instance).getLockName(value, "lock");
        return redisson.getLock(name);
    }
    
}
