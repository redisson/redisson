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
package org.redisson.misc;

import java.util.Map;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * LFU (least frequently used) cache.
 * 
 * @author Nikita Koksharov
 *
 * @param <K>
 * @param <V>
 */
public class LFUCacheMap<K, V> extends AbstractCacheMap<K, V> {

    public static class MapKey implements Comparable<MapKey> {
        
        private Long accessCount;
        private CachedValue cachedValue;
        
        public MapKey(Long accessCount, CachedValue cachedValue) {
            super();
            this.accessCount = accessCount;
            this.cachedValue = cachedValue;
        }

        @Override
        public int compareTo(MapKey o) {
            int compare = accessCount.compareTo(o.accessCount);
            if (compare == 0) {
                if (!cachedValue.equals(o.cachedValue)) {
                    return ((Integer)cachedValue.hashCode()).compareTo(o.cachedValue.hashCode());
                }
                return 0;
            }
            return compare;
        }

        @Override
        public String toString() {
            return "MapKey [accessCount=" + accessCount + "]";
        }
        
    }
    
    private final ConcurrentNavigableMap<MapKey, CachedValue> accessMap = new ConcurrentSkipListMap<MapKey, CachedValue>();
    
    public LFUCacheMap(int size, long timeToLiveInMillis, long maxIdleInMillis) {
        super(size, timeToLiveInMillis, maxIdleInMillis);
    }
    
    @Override
    protected void onValueCreate(CachedValue value) {
        MapKey key = toKey(value);
        accessMap.put(key, value);
    }
    
    @Override
    protected void onValueRead(CachedValue value) {
        addAccessCount(value, 1);
    }
    
    private MapKey toKey(CachedValue value) {
        return new MapKey(value.getAccessCount(), value);
    }
    
    @Override
    protected void onValueRemove(CachedValue value) {
        MapKey key = toKey(value);
        if (accessMap.remove(key) == null) {
            throw new IllegalStateException();
        }
    }

    private void addAccessCount(CachedValue value, long count) {
        synchronized (value) {
            if (count < 0 && value.getAccessCount() == 0) {
                return;
            }
            
            MapKey key = toKey(value);
            if (accessMap.remove(key) == null) {
                throw new IllegalStateException();
            }

            if (count < 0) {
                count = -Math.min(value.getAccessCount(), -count);
            }
            value.addAccessCount(count);
            
            key = toKey(value);
            accessMap.put(key, value);
        }
    }

    @Override
    protected void onMapFull() {
        Map.Entry<MapKey, CachedValue> entry = accessMap.pollFirstEntry();
        map.remove(entry.getValue().getKey(), entry.getValue());
        
        if (entry.getValue().getAccessCount() == 0) {
            return;
        }

        // TODO optimize
        // decrease all values
        for (CachedValue value : accessMap.values()) {
            addAccessCount(value, -entry.getValue().getAccessCount());
        }
    }

    @Override
    public void clear() {
        accessMap.clear();
        super.clear();
    }
    
}
