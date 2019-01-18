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
package org.redisson.api;

import java.util.concurrent.TimeUnit;

import org.redisson.api.map.MapLoader;
import org.redisson.api.map.MapWriter;

/**
 * Configuration for LocalCachedMap object.
 * 
 * @author Nikita Koksharov
 *
 * @param <K> key type
 * @param <V> value type
 */
public class LocalCachedMapOptions<K, V> extends MapOptions<K, V> {
    
    /**
     * Various strategies to avoid stale objects in cache.
     * Handle cases when map instance has been disconnected for a while.
     *
     */
    public enum ReconnectionStrategy {
        
        /**
         * No reconnect handling.
         */
        NONE,
        
        /**
         * Clear local cache if map instance has been disconnected for a while.
         */
        CLEAR,
        
        /**
         * Store invalidated entry hash in invalidation log for 10 minutes.
         * Cache keys for stored invalidated entry hashes will be removed 
         * if LocalCachedMap instance has been disconnected less than 10 minutes 
         * or whole cache will be cleaned otherwise.
         */
        LOAD
        
    }
    
    public enum SyncStrategy {
        
        /**
         * No synchronizations on map changes.
         */
        NONE,
        
        /**
         * Invalidate cache entry across all LocalCachedMap instances on map entry change. Broadcasts map entry hash (16 bytes) to all instances.
         */
        INVALIDATE,
        
        /**
         * Update cache entry across all LocalCachedMap instances on map entry change. Broadcasts full map entry state (Key and Value objects) to all instances.
         */
        UPDATE
        
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
    
    private ReconnectionStrategy reconnectionStrategy;
    private SyncStrategy syncStrategy;
    private EvictionPolicy evictionPolicy;
    private int cacheSize;
    private long timeToLiveInMillis;
    private long maxIdleInMillis;
    
    protected LocalCachedMapOptions() {
    }
    
    protected LocalCachedMapOptions(LocalCachedMapOptions<K, V> copy) {
        this.reconnectionStrategy = copy.reconnectionStrategy;
        this.syncStrategy = copy.syncStrategy;
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
                    .reconnectionStrategy(ReconnectionStrategy.NONE)
                    .syncStrategy(SyncStrategy.INVALIDATE);
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
    
    public ReconnectionStrategy getReconnectionStrategy() {
        return reconnectionStrategy;
    }
    
    public SyncStrategy getSyncStrategy() {
        return syncStrategy;
    }
    
    public LocalCachedMapOptions<K, V> reconnectionStrategy(ReconnectionStrategy reconnectionStrategy) {
        if (reconnectionStrategy == null) {
            throw new NullPointerException("reconnectionStrategy can't be null");
        }

        this.reconnectionStrategy = reconnectionStrategy;
        return this;
    }

    public LocalCachedMapOptions<K, V> syncStrategy(SyncStrategy syncStrategy) {
        if (syncStrategy == null) {
            throw new NullPointerException("syncStrategy can't be null");
        }

        this.syncStrategy = syncStrategy;
        return this;
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
    
    @Override
    public LocalCachedMapOptions<K, V> writer(MapWriter<K, V> writer) {
        return (LocalCachedMapOptions<K, V>) super.writer(writer);
    }
    
    @Override
    public LocalCachedMapOptions<K, V> writeBehindThreads(int writeBehindThreads) {
        return (LocalCachedMapOptions<K, V>) super.writeBehindThreads(writeBehindThreads);
    }
    
    @Override
    public LocalCachedMapOptions<K, V> writeMode(org.redisson.api.MapOptions.WriteMode writeMode) {
        return (LocalCachedMapOptions<K, V>) super.writeMode(writeMode);
    }
    
    @Override
    public LocalCachedMapOptions<K, V> loader(MapLoader<K, V> loader) {
        return (LocalCachedMapOptions<K, V>) super.loader(loader);
    }

}
