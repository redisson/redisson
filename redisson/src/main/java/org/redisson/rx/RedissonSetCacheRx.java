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
package org.redisson.rx;

import io.reactivex.rxjava3.core.Single;
import org.reactivestreams.Publisher;
import org.redisson.RedissonObject;
import org.redisson.ScanIterator;
import org.redisson.ScanResult;
import org.redisson.api.*;
import org.redisson.client.RedisClient;

/**
 *
 * @author Nikita Koksharov
 *
 * @param <V> value
 */
public class RedissonSetCacheRx<V> {

    private final RSetCache<V> instance;
    private final RedissonRxClient redisson;
    
    public RedissonSetCacheRx(RSetCache<V> instance, RedissonRxClient redisson) {
        this.instance = instance;
        this.redisson = redisson;
    }

    public Publisher<V> iterator() {
        return new SetRxIterator<V>() {
            @Override
            protected RFuture<ScanResult<Object>> scanIterator(RedisClient client, long nextIterPos) {
                return ((ScanIterator) instance).scanIteratorAsync(((RedissonObject) instance).getRawName(), client, nextIterPos, null, 10);
            }
        }.create();
    }

    public Single<Boolean> addAll(Publisher<? extends V> c) {
        return new PublisherAdder<V>() {
            @Override
            public RFuture<Boolean> add(Object o) {
                return instance.addAsync((V) o);
            }
        }.addAll(c);
    }

    public RPermitExpirableSemaphoreRx getPermitExpirableSemaphore(V value) {
        String name = ((RedissonObject) instance).getLockByValue(value, "permitexpirablesemaphore");
        return redisson.getPermitExpirableSemaphore(name);
    }

    public RSemaphoreRx getSemaphore(V value) {
        String name = ((RedissonObject) instance).getLockByValue(value, "semaphore");
        return redisson.getSemaphore(name);
    }
    
    public RLockRx getFairLock(V value) {
        String name = ((RedissonObject) instance).getLockByValue(value, "fairlock");
        return redisson.getFairLock(name);
    }
    
    public RReadWriteLockRx getReadWriteLock(V value) {
        String name = ((RedissonObject) instance).getLockByValue(value, "rw_lock");
        return redisson.getReadWriteLock(name);
    }
    
    public RLockRx getLock(V value) {
        String name = ((RedissonObject) instance).getLockByValue(value, "lock");
        return redisson.getLock(name);
    }
    
}
