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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

import org.reactivestreams.Publisher;
import org.redisson.RedissonSetCache;
import org.redisson.ScanIterator;
import org.redisson.api.RFuture;
import org.redisson.api.RLockReactive;
import org.redisson.api.RPermitExpirableSemaphoreReactive;
import org.redisson.api.RReadWriteLockReactive;
import org.redisson.api.RSemaphoreReactive;
import org.redisson.api.RSetCache;
import org.redisson.api.RedissonReactiveClient;
import org.redisson.client.RedisClient;
import org.redisson.client.protocol.decoder.ListScanResult;

import io.netty.buffer.ByteBuf;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <V> value
 */
public class RedissonSetCacheReactive<V> {

    private final RSetCache<V> instance;
    private final RedissonReactiveClient redisson;
    
    public RedissonSetCacheReactive(RSetCache<V> instance, RedissonReactiveClient redisson) {
        this.instance = instance;
        this.redisson = redisson;
    }

    public Publisher<V> iterator() {
        return Flux.create(new SetReactiveIterator<V>() {
            @Override
            protected RFuture<ListScanResult<Object>> scanIterator(RedisClient client, long nextIterPos) {
                return ((ScanIterator)instance).scanIteratorAsync(instance.getName(), client, nextIterPos, null, 10);
            }
        });
    }

    public Publisher<Boolean> addAll(Publisher<? extends V> c) {
        return new PublisherAdder<V>() {
            @Override
            public RFuture<Boolean> add(Object o) {
                return instance.addAsync((V)o);
            }
        }.addAll(c);
    }
    
    public RPermitExpirableSemaphoreReactive getPermitExpirableSemaphore(V value) {
        String name = ((RedissonSetCache<V>)instance).getLockName(value, "permitexpirablesemaphore");
        return redisson.getPermitExpirableSemaphore(name);
    }

    public RSemaphoreReactive getSemaphore(V value) {
        String name = ((RedissonSetCache<V>)instance).getLockName(value, "semaphore");
        return redisson.getSemaphore(name);
    }
    
    public RLockReactive getFairLock(V value) {
        String name = ((RedissonSetCache<V>)instance).getLockName(value, "fairlock");
        return redisson.getFairLock(name);
    }
    
    public RReadWriteLockReactive getReadWriteLock(V value) {
        String name = ((RedissonSetCache<V>)instance).getLockName(value, "rw_lock");
        return redisson.getReadWriteLock(name);
    }
    
    public RLockReactive getLock(V value) {
        String name = ((RedissonSetCache<V>)instance).getLockName(value, "lock");
        return redisson.getLock(name);
    }

}
