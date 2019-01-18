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
package org.redisson.cache;

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
    
    public static class LFUCachedValue extends StdCachedValue {

        Long id;
        long accessCount;
        
        public LFUCachedValue(long id, Object key, Object value, long ttl, long maxIdleTime) {
            super(key, value, ttl, maxIdleTime);
            this.id = id;
        }

        public void addAccessCount(long value) {
            accessCount += value;
        }
        
    }
    
    private final AtomicLong idGenerator = new AtomicLong();
    private final ConcurrentNavigableMap<MapKey, LFUCachedValue> accessMap = new ConcurrentSkipListMap<MapKey, LFUCachedValue>();
    
    public LFUCacheMap(int size, long timeToLiveInMillis, long maxIdleInMillis) {
        super(size, timeToLiveInMillis, maxIdleInMillis);
    }
    
    @Override
    protected CachedValue create(K key, V value, long ttl, long maxIdleTime) {
        return new LFUCachedValue(idGenerator.incrementAndGet(), key, value, ttl, maxIdleTime);
    }
    
    @Override
    protected void onValueCreate(CachedValue value) {
        MapKey key = toKey((LFUCachedValue)value);
        accessMap.put(key, (LFUCachedValue)value);
    }
    
    @Override
    protected void onValueRead(CachedValue value) {
        addAccessCount((LFUCachedValue)value, 1);
    }
    
    private MapKey toKey(LFUCachedValue value) {
        return new MapKey(value.accessCount, value);
    }
    
    @Override
    protected void onValueRemove(CachedValue value) {
        synchronized (value) {
            MapKey key = toKey((LFUCachedValue)value);
            accessMap.remove(key);
        }
    }

    private void addAccessCount(LFUCachedValue value, long count) {
        synchronized (value) {
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
        }
    }

    @Override
    protected void onMapFull() {
        Map.Entry<MapKey, LFUCachedValue> entry = accessMap.pollFirstEntry();
        if (entry == null) {
            return;
        }
        map.remove(entry.getValue().getKey(), entry.getValue());
        
        if (entry.getValue().accessCount == 0) {
            return;
        }

        // TODO optimize
        // decrease all values
        for (LFUCachedValue value : accessMap.values()) {
            addAccessCount(value, -entry.getValue().accessCount);
        }
    }

    @Override
    public void clear() {
        accessMap.clear();
        super.clear();
    }
    
}
