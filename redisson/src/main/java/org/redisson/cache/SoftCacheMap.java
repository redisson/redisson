/**
 * Copyright 2016 Nikita Koksharov
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

import java.lang.ref.ReferenceQueue;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <K> key
 * @param <V> value
 */
public class SoftCacheMap<K, V> extends AbstractCacheMap<K, V> {

    private final ReferenceQueue<V> queue = new ReferenceQueue<V>();
    
    public SoftCacheMap(long timeToLiveInMillis, long maxIdleInMillis) {
        super(0, timeToLiveInMillis, maxIdleInMillis);
    }

    protected CachedValue<K, V> create(K key, V value, long ttl, long maxIdleTime) {
        return new SoftCachedValue<K, V>(key, value, ttl, maxIdleTime, queue);
    }

    @Override
    protected boolean removeExpiredEntries() {
        while (true) {
            CachedValueReference<V> value = (CachedValueReference<V>) queue.poll();
            if (value == null) {
                break;
            }
            map.remove(value.getOwner().getKey(), value.getOwner());
        }
        return super.removeExpiredEntries();
    }
    
    @Override
    protected void onMapFull() {
    }
    
}
