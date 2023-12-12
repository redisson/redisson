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
package org.redisson.cache;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * LRU (least recently used) cache.
 * 
 * @author Nikita Koksharov
 *
 * @param <K> key
 * @param <V> value
 */
public class LRUCacheMap<K, V> extends AbstractCacheMap<K, V> {

    private final AtomicLong index = new AtomicLong();
    private final List<Collection<CachedValue<K, V>>> queues = new ArrayList<>();
    private final Map<Collection<CachedValue<K, V>>, Lock> queueLocks = new IdentityHashMap<>();

    public LRUCacheMap(int size, long timeToLiveInMillis, long maxIdleInMillis) {
        super(size, timeToLiveInMillis, maxIdleInMillis);
        
        for (int i = 0; i < Runtime.getRuntime().availableProcessors()*2; i++) {
            Set<CachedValue<K, V>> instance = Collections.synchronizedSet(new LinkedHashSet<>());
            queues.add(instance);
            queueLocks.put(instance, new ReentrantLock());
        }
    }

    @Override
    protected void onValueCreate(CachedValue<K, V> value) {
        Collection<CachedValue<K, V>> queue = getQueue(value);
        queue.add(value);
    }

    private Collection<CachedValue<K, V>> getQueue(CachedValue<K, V> value) {
        return queues.get(Math.abs(value.hashCode() % queues.size()));
    }
    
    @Override
    protected void onValueRemove(CachedValue<K, V> value) {
        Collection<CachedValue<K, V>> queue = getQueue(value);
        queue.remove(value);
    }
    
    @Override
    protected void onValueRead(CachedValue<K, V> value) {
        Collection<CachedValue<K, V>> queue = getQueue(value);
        // move value to the tail of the queue
        if (queue.remove(value)) {
            queue.add(value);
        }
    }

    @Override
    protected void onMapFull() {
        int startIndex = -1;
        while (true) {
            int queueIndex = (int) Math.abs(index.incrementAndGet() % queues.size());
            if (queueIndex == startIndex) {
                return;
            }
            if (startIndex == -1) {
                startIndex = queueIndex;
            }

            Collection<CachedValue<K, V>> queue = queues.get(queueIndex);
            CachedValue<K, V> removedValue = null;
            Lock lock = queueLocks.get(queue);
            lock.lock();
            try {
                Iterator<CachedValue<K, V>> iter = queue.iterator();
                if (iter.hasNext()) {
                    removedValue = iter.next();
                    iter.remove();
                }
            } finally {
                lock.unlock();
            }

            if (removedValue != null) {
                map.remove(removedValue.getKey(), removedValue);
                return;
            }
        }
    }
    
    @Override
    public void clear() {
        for (Collection<CachedValue<K, V>> collection : queues) {
            collection.clear();
        }
        super.clear();
    }

}
