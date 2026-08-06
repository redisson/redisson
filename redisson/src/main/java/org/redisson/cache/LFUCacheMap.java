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

        private final long accessCount;
        private final long id;

        public MapKey(long accessCount, long id) {
            this.accessCount = accessCount;
            this.id = id;
        }

        @Override
        public int compareTo(MapKey o) {
            // Wraparound-safe: counters are absolute and monotonic, so they eventually
            // exceed Long.MAX_VALUE. Comparing the difference rather than the values keeps
            // the order correct across that wrap. Live counters are held within
            // MAX_SPREAD (< 2^63) of each other, so the subtraction cannot overflow and
            // this stays a consistent total order.
            long d = accessCount - o.accessCount;
            if (d != 0) {
                return d < 0 ? -1 : 1;
            }
            return Long.compare(id, o.id);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof MapKey)) {
                return false;
            }
            MapKey other = (MapKey) o;
            return accessCount == other.accessCount && id == other.id;
        }

        @Override
        public int hashCode() {
            return 31 * Long.hashCode(accessCount) + Long.hashCode(id);
        }

        @Override
        public String toString() {
            return "MapKey [accessCount=" + accessCount + "]";
        }
        
    }
    
    public static class LFUCachedValue<K, V> extends StdCachedValue<K, V> {

        private final long id;

        /**
         * Absolute counter. The effective frequency is {@code accessCount - baseline}.
         */
        private long accessCount;

        /**
         * Current position in {@code accessMap}, cached so that a read allocates
         * one key instead of two. Guarded by {@link #getLock()}.
         */
        private MapKey mapKey;

        public LFUCachedValue(long id, K key, V value, long ttl, long maxIdleTime) {
            super(key, value, ttl, maxIdleTime);
            this.id = id;
        }

    }

    private final WrappedLock lock = new WrappedLock();
    private final AtomicLong idGenerator = new AtomicLong();
    private final ConcurrentNavigableMap<MapKey, LFUCachedValue<K, V>> accessMap = new ConcurrentSkipListMap<>();

    /**
     * How far a single entry's counter may run ahead of the floor. Keeping every live
     * counter inside a window narrower than 2^63 is what makes {@link MapKey#compareTo}'s
     * wraparound arithmetic a consistent total order. Reaching the cap would take
     * ~4.6e18 reads of one key, and the effect if it ever happened is benign: the hottest
     * keys tie at the top instead of one of them being ordered above the others.
     */
    static final long MAX_SPREAD = Long.MAX_VALUE / 2;

    /**
     * Current eviction floor: the counter value a new entry enters at. Increases on
     * eviction and is allowed to wrap. Written only under {@link #lock}.
     */
    private volatile long baseline;

    public LFUCacheMap(int size, long timeToLiveInMillis, long maxIdleInMillis) {
        super(size, timeToLiveInMillis, maxIdleInMillis);
    }

    @Override
    protected CachedValue<K, V> create(K key, V value, long ttl, long maxIdleTime) {
        return new LFUCachedValue<>(idGenerator.incrementAndGet(), key, value, ttl, maxIdleTime);
    }

    @Override
    protected void onValueCreate(CachedValue<K, V> value) {
        LFUCachedValue<K, V> v = (LFUCachedValue<K, V>) value;
        v.getLock().execute(() -> {
            while (true) {
                long floor = baseline;
                MapKey key = new MapKey(floor, v.id);
                v.accessCount = floor;
                v.mapKey = key;
                accessMap.put(key, v);

                if (baseline == floor) {
                    return;
                }
                if (accessMap.remove(key) == null) {
                    // already polled by an eviction - don't resurrect it
                    v.mapKey = null;
                    return;
                }
            }
        });
    }

    @Override
    protected void onValueRead(CachedValue<K, V> value) {
        LFUCachedValue<K, V> v = (LFUCachedValue<K, V>) value;
        v.getLock().execute(() -> {
            if (v.accessCount - baseline >= MAX_SPREAD) {
                // already maximally frequent; counting further would widen the window past
                // the point where wraparound comparison stays consistent
                return;
            }
            MapKey current = v.mapKey;
            if (current == null || accessMap.remove(current) == null) {
                // concurrently evicted or removed - don't resurrect it
                return;
            }
            v.accessCount++;
            v.mapKey = new MapKey(v.accessCount, v.id);
            accessMap.put(v.mapKey, v);
        });
    }

    @Override
    protected void onValueRemove(CachedValue<K, V> value) {
        LFUCachedValue<K, V> v = (LFUCachedValue<K, V>) value;
        v.getLock().execute(() -> {
            MapKey current = v.mapKey;
            if (current != null) {
                accessMap.remove(current);
                v.mapKey = null;
            }
        });
        super.onValueRemove(value);
    }

    @Override
    protected void onMapFull() {
        lock.execute(() -> {
            Map.Entry<MapKey, LFUCachedValue<K, V>> entry = accessMap.pollFirstEntry();
            if (entry == null) {
                return;
            }

            LFUCachedValue<K, V> evicted = entry.getValue();
            evicted.getLock().execute(() -> evicted.mapKey = null);

            if (map.remove(evicted.getKey(), evicted)) {
                super.onValueRemove(evicted);
            }

            // raise the floor instead of decrementing every surviving entry
            long floor = entry.getKey().accessCount;
            if (floor - baseline > 0) {
                baseline = floor;
            }
        });
    }

    @Override
    public void clear() {
        accessMap.clear();
        super.clear();
    }

}