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
package org.redisson.api;

import java.util.concurrent.TimeUnit;

import org.redisson.api.map.MapLoader;
import org.redisson.api.map.MapWriter;

/**
 * RLocalCachedMap options object. Used to specify RLocalCachedMap settings.
 * 
 * @author Nikita Koksharov
 *
 * @param <K> key type
 * @param <V> value type
 */
public class LocalCachedMapOptions<K, V> {
    
    public enum InvalidationPolicy {
        
        /**
         * No invalidation on map changes
         */
        NONE, 

        /**
         * Invalidate cache entry across all LocalCachedMap instances on map entry change.
         */
        ON_CHANGE, 
        
        /**
         * Invalidate cache entry across all LocalCachedMap instances on map entry change.
         * <p>
         * Clear cache if LocalCachedMap instance has been disconnected for a while.
         * It's applied to avoid stale objects in cache.
         */
        ON_CHANGE_WITH_CLEAR_ON_RECONNECT, 

        /**
         * Invalidate cache entry across all LocalCachedMap instances on map entry change.
         * <p>
         * Store invalidated entry hash in invalidation log for 10 minutes.
         * Cache keys for stored invalidated entry hashes will be removed 
         * if LocalCachedMap instance has been disconnected less than 10 minutes 
         * or whole cache will be cleaned otherwise.
         * It's applied to avoid stale objects in cache.
         */
        ON_CHANGE_WITH_LOAD_ON_RECONNECT
    }
    
    public enum EvictionPolicy {
        
        /**
         * Cache without eviction. 
         */
        NONE, 
        
        /**
         * Least Recently Used cache.
         */
        LRU, 
        
        /**
         * Least Frequently Used cache.
         */
        LFU, 
        
        /**
         * Cache with Soft Reference used for values.
         * All references will be collected by GC
         */
        SOFT, 

        /**
         * Cache with Weak Reference used for values. 
         * All references will be collected by GC
         */
        WEAK
    };
    
    private InvalidationPolicy invalidationPolicy;
    private EvictionPolicy evictionPolicy;
    private int cacheSize;
    private long timeToLiveInMillis;
    private long maxIdleInMillis;
    private MapLoader<K, V> mapLoader;
    private MapWriter<K, V> mapWriter;
    
    private LocalCachedMapOptions() {
    }
    
    protected LocalCachedMapOptions(LocalCachedMapOptions<K, V> copy) {
        this.invalidationPolicy = copy.invalidationPolicy;
        this.evictionPolicy = copy.evictionPolicy;
        this.cacheSize = copy.cacheSize;
        this.timeToLiveInMillis = copy.timeToLiveInMillis;
        this.maxIdleInMillis = copy.maxIdleInMillis;
    }
    
    /**
     * Creates a new instance of LocalCachedMapOptions with default options.
     * <p>
     * This is equivalent to:
     * <pre>
     *     new LocalCachedMapOptions()
     *      .cacheSize(0).timeToLive(0).maxIdle(0)
     *      .evictionPolicy(EvictionPolicy.NONE)
     *      .invalidateEntryOnChange(true);
     * </pre>
     * 
     * @param <K> key type
     * @param <V> value type
     * 
     * @return LocalCachedMapOptions instance
     * 
     */
    public static <K, V> LocalCachedMapOptions<K, V> defaults() {
        return new LocalCachedMapOptions<K, V>()
                    .cacheSize(0).timeToLive(0).maxIdle(0)
                    .evictionPolicy(EvictionPolicy.NONE)
                    .invalidationPolicy(InvalidationPolicy.ON_CHANGE);
    }
    
    public EvictionPolicy getEvictionPolicy() {
        return evictionPolicy;
    }

    public int getCacheSize() {
        return cacheSize;
    }

    public long getTimeToLiveInMillis() {
        return timeToLiveInMillis;
    }

    public long getMaxIdleInMillis() {
        return maxIdleInMillis;
    }

    /**
     * Sets cache size. If size is <code>0</code> then local cache is unbounded.
     * 
     * @param cacheSize - size of cache
     * @return LocalCachedMapOptions instance
     */
    public LocalCachedMapOptions<K, V> cacheSize(int cacheSize) {
        this.cacheSize = cacheSize;
        return this;
    }
    
    public InvalidationPolicy getInvalidationPolicy() {
        return invalidationPolicy;
    }

    /**
     * Sets entry invalidation policy. 
     *
     * @param invalidationPolicy
     *         <p><code>NONE</code> - no invalidation applied.
     *         <p><code>ON_CHANGE</code> - invalidation message which removes corresponding entry from cache
     *                                     will be sent to all other RLocalCachedMap instances on each entry update/remove operation.
     *         <p><code>ON_CHANGE_WITH_CLEAR_ON_RECONNECT</code> - includes <code>ON_CHANGE</code> policy 
     *                                     and clears local cache of current instance in case of reconnection to Redis. 
     * 
     * @return LocalCachedMapOptions instance
     */
    public LocalCachedMapOptions<K, V> invalidationPolicy(InvalidationPolicy invalidationPolicy) {
        this.invalidationPolicy = invalidationPolicy;
        return this;
    }

    /**
     * Sets entry invalidation behavior. 
     * 
     * @param value - if <code>true</code> then invalidation message which removes corresponding entry from cache
     *         will be sent to all other RLocalCachedMap instances on each entry update/remove operation.
     *         if <code>false</code> then invalidation message won't be sent
     * @return LocalCachedMapOptions instance
     */
    @Deprecated
    public LocalCachedMapOptions<K, V> invalidateEntryOnChange(boolean value) {
        if (value) {
            return invalidationPolicy(InvalidationPolicy.ON_CHANGE);
        }
        return invalidationPolicy(InvalidationPolicy.NONE);
    }

    /**
     * Sets eviction policy. 
     * 
     * @param evictionPolicy
     *         <p><code>LRU</code> - uses cache with LRU (least recently used) eviction policy.
     *         <p><code>LFU</code> - uses cache with LFU (least frequently used) eviction policy.
     *         <p><code>SOFT</code> - uses cache with soft references. The garbage collector will evict items from the cache when the JVM is running out of memory.
     *         <p><code>NONE</code> - doesn't use eviction policy, but timeToLive and maxIdleTime params are still working.
     * @return LocalCachedMapOptions instance
     */
    public LocalCachedMapOptions<K, V> evictionPolicy(EvictionPolicy evictionPolicy) {
        if (evictionPolicy == null) {
            throw new NullPointerException("evictionPolicy can't be null");
        }
        this.evictionPolicy = evictionPolicy;
        return this;
    }
    
    /**
     * Sets time to live in milliseconds for each map entry in cache.
     * If value equals to <code>0</code> then timeout is not applied
     * 
     * @param timeToLiveInMillis - time to live in milliseconds
     * @return LocalCachedMapOptions instance
     */
    public LocalCachedMapOptions<K, V> timeToLive(long timeToLiveInMillis) {
        this.timeToLiveInMillis = timeToLiveInMillis;
        return this;
    }

    /**
     * Sets time to live for each map entry in cache.
     * If value equals to <code>0</code> then timeout is not applied
     * 
     * @param timeToLive - time to live
     * @param timeUnit - time unit
     * @return LocalCachedMapOptions instance
     */
    public LocalCachedMapOptions<K, V> timeToLive(long timeToLive, TimeUnit timeUnit) {
        return timeToLive(timeUnit.toMillis(timeToLive));
    }

    /**
     * Sets max idle time in milliseconds for each map entry in cache.
     * If value equals to <code>0</code> then timeout is not applied
     * 
     * @param maxIdleInMillis - time to live in milliseconds
     * @return LocalCachedMapOptions instance
     */
    public LocalCachedMapOptions<K, V> maxIdle(long maxIdleInMillis) {
        this.maxIdleInMillis = maxIdleInMillis;
        return this;
    }

    /**
     * Sets max idle time for each map entry in cache.
     * If value equals to <code>0</code> then timeout is not applied
     * 
     * @param maxIdle - max idle time
     * @param timeUnit - time unit
     * @return LocalCachedMapOptions instance
     */
    public LocalCachedMapOptions<K, V> maxIdle(long maxIdle, TimeUnit timeUnit) {
        return maxIdle(timeUnit.toMillis(maxIdle));
    }

    /**
     * Sets map writer object used for write-through operations.
     * 
     * @param writer object
     * @return LocalCachedMapOptions instance
     */
    public LocalCachedMapOptions<K, V> mapWriter(MapWriter<K, V> writer) {
        this.mapWriter = writer;
        return this;
    }
    public MapWriter<K, V> getMapWriter() {
        return mapWriter;
    }
    
    /**
     * Sets map reader object used for write-through operations.
     * 
     * @param loader object
     * @return LocalCachedMapOptions instance
     */
    public LocalCachedMapOptions<K, V> mapLoader(MapLoader<K, V> loader) {
        this.mapLoader = loader;
        return this;
    }
    public MapLoader<K, V> getMapLoader() {
        return mapLoader;
    }
    
}
