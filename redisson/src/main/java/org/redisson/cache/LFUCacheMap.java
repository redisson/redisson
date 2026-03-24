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
package org.redisson.cache;

import org.redisson.misc.WrappedLock;

import java.util.Map;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * LFU (least frequently used) cache.
 * 
 * @author Nikita Koksharov
 *
 * @param <K> key
 * @param <V> value
 */
public class LFUCacheMap<K, V> extends AbstractCacheMap<K, V> {

    public static class MapKey implements Comparable<MapKey> {
        
        private Long accessCount;
        private LFUCachedValue cachedValue;
        
        public MapKey(Long accessCount, LFUCachedValue cachedValue) {
            super();
            this.accessCount = accessCount;
            this.cachedValue = cachedValue;
        }

        @Override
        public int compareTo(MapKey o) {
            int compare = accessCount.compareTo(o.accessCount);
            if (compare == 0) {
                return cachedValue.id.compareTo(o.cachedValue.id);
            }
            return compare;
        }

        @Override
        public String toString() {
            return "MapKey [accessCount=" + accessCount + "]";
        }
        
    }
    
    public static class LFUCachedValue<K, V> extends StdCachedValue<K, V> {

        private final Long id;
        private long accessCount;
        
        public LFUCachedValue(long id, K key, V value, long ttl, long maxIdleTime) {
            super(key, value, ttl, maxIdleTime);
            this.id = id;
        }

        public void addAccessCount(long value) {
            accessCount += value;
        }
        
    }
    private final WrappedLock lock = new WrappedLock();
    private final AtomicLong idGenerator = new AtomicLong();
    private final ConcurrentNavigableMap<MapKey, LFUCachedValue<K, V>> accessMap = new ConcurrentSkipListMap<>();
    
    public LFUCacheMap(int size, long timeToLiveInMillis, long maxIdleInMillis) {
        super(size, timeToLiveInMillis, maxIdleInMillis);
    }
    
    @Override
    protected CachedValue<K, V> create(K key, V value, long ttl, long maxIdleTime) {
        return new LFUCachedValue<K, V>(idGenerator.incrementAndGet(), key, value, ttl, maxIdleTime);
    }

    @Override
    protected void onValueCreate(CachedValue<K, V> value) {
        MapKey key = toKey((LFUCachedValue<K, V>) value);
        accessMap.put(key, (LFUCachedValue<K, V>) value);
    }

    @Override
    protected void onValueRead(CachedValue<K, V> value) {
        addAccessCount((LFUCachedValue<K, V>) value, 1);
    }

    private MapKey toKey(LFUCachedValue<K, V> value) {
        return new MapKey(value.accessCount, value);
    }

    @Override
    protected void onValueRemove(CachedValue<K, V> value) {
        value.getLock().execute(() -> {
            MapKey key = toKey((LFUCachedValue<K, V>) value);
            accessMap.remove(key);
        });
        super.onValueRemove(value);
    }

    private void addAccessCount(LFUCachedValue<K, V> value, long c) {
        value.getLock().execute(() -> {
            long count = c;
            if (count < 0 && value.accessCount == 0) {
                return;
            }

            MapKey key = toKey(value);
            if (accessMap.remove(key) == null) {
                return;
            }

            if (count < 0) {
                count = -Math.min(value.accessCount, -count);
            }
            value.addAccessCount(count);

            key = toKey(value);
            accessMap.put(key, value);
        });
    }

    @Override
    protected void onMapFull() {
        lock.execute(() -> {
            Map.Entry<MapKey, LFUCachedValue<K, V>> entry = accessMap.pollFirstEntry();
            if (entry == null) {
                return;
            }
            if (map.remove(entry.getValue().getKey(), entry.getValue())) {
                super.onValueRemove(entry.getValue());
            }

            if (entry.getValue().accessCount == 0) {
                return;
            }

            // TODO optimize
            // decrease all values
            for (LFUCachedValue<K, V> value : accessMap.values()) {
                addAccessCount(value, -entry.getValue().accessCount);
            }
        });

    }

    @Override
    public void clear() {
        accessMap.clear();
        super.clear();
    }

}