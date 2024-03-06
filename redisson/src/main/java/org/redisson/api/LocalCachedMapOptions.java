/**
 * Copyright (c) 2013-2024 Nikita Koksharov
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

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.redisson.api.map.MapLoader;
import org.redisson.api.map.MapLoaderAsync;
import org.redisson.api.map.MapWriter;
 import org.redisson.api.map.MapWriterAsync;

/**
 * Use org.redisson.api.options.LocalCachedMapOptions instead
 * 
 * @author Nikita Koksharov
 *
 * @param <K> key type
 * @param <V> value type
 */
@Deprecated
public class LocalCachedMapOptions<K, V> extends MapOptions<K, V> {
    
    /**
     * Various strategies to avoid stale objects in local cache.
     * Handle cases when map instance has been disconnected for a while.
     *
     */
    public enum ReconnectionStrategy {
        
        /**
         * No reconnect handling.
         */
        NONE,
        
        /**
         * Clear local cache if map instance disconnected.
         */
        CLEAR,
        
        /**
         * Store invalidated entry hash in invalidation log for 10 minutes.
         * Cache keys for stored invalidated entry hashes will be removed 
         * if LocalCachedMap instance has been disconnected less than 10 minutes 
         * or whole local cache will be cleaned otherwise.
         */
        LOAD
        
    }
    
    public enum SyncStrategy {
        
        /**
         * No synchronizations on map changes.
         */
        NONE,
        
        /**
         * Invalidate local cache entry across all LocalCachedMap instances on map entry change. Broadcasts map entry hash (16 bytes) to all instances.
         */
        INVALIDATE,
        
        /**
         * Update local cache entry across all LocalCachedMap instances on map entry change. Broadcasts full map entry state (Key and Value objects) to all instances.
         */
        UPDATE
        
    }
    
    public enum EvictionPolicy {
        
        /**
         * Local cache without eviction. 
         */
        NONE, 
        
        /**
         * Least Recently Used local cache eviction policy.
         */
        LRU, 
        
        /**
         * Least Frequently Used local cache eviction policy.
         */
        LFU, 
        
        /**
         * Local cache  eviction policy with Soft Reference used for values.
         * All references will be collected by GC
         */
        SOFT, 

        /**
         * Local cache eviction policy with Weak Reference used for values.
         * All references will be collected by GC
         */
        WEAK
    };

    public enum CacheProvider {

        REDISSON,

        CAFFEINE

    }

    public enum StoreMode {

        /**
         * Store data only in local cache.
         */
        LOCALCACHE,

        /**
         * Store data only in both Redis and local cache.
         */
        LOCALCACHE_REDIS

    }

    public enum ExpirationEventPolicy {

        /**
         * Don't subscribe on expire event.
         */
        DONT_SUBSCRIBE,

        /**
         * Subscribe on expire event using __keyevent@*:expired pattern
         */
        SUBSCRIBE_WITH_KEYEVENT_PATTERN,

        /**
         * Subscribe on expire event using __keyspace@N__:name channel
         */
        SUBSCRIBE_WITH_KEYSPACE_CHANNEL

    }

    private ReconnectionStrategy reconnectionStrategy;
    private SyncStrategy syncStrategy;
    private EvictionPolicy evictionPolicy;
    private int cacheSize;
    private long timeToLiveInMillis;
    private long maxIdleInMillis;
    private CacheProvider cacheProvider;
    private StoreMode storeMode;
    private boolean storeCacheMiss;
    private ExpirationEventPolicy expirationEventPolicy;

    protected LocalCachedMapOptions() {
    }
    
    protected LocalCachedMapOptions(LocalCachedMapOptions<K, V> copy) {
        this.reconnectionStrategy = copy.reconnectionStrategy;
        this.syncStrategy = copy.syncStrategy;
        this.evictionPolicy = copy.evictionPolicy;
        this.cacheSize = copy.cacheSize;
        this.timeToLiveInMillis = copy.timeToLiveInMillis;
        this.maxIdleInMillis = copy.maxIdleInMillis;
        this.cacheProvider = copy.cacheProvider;
        this.storeMode = copy.storeMode;
        this.storeCacheMiss = copy.storeCacheMiss;
    }
    
    /**
     * Creates a new instance of LocalCachedMapOptions with default options.
     * <p>
     * This is equivalent to:
     * <pre>
     *     new LocalCachedMapOptions()
     *      .cacheSize(0).timeToLive(0).maxIdle(0)
     *      .evictionPolicy(EvictionPolicy.NONE)
     *      .reconnectionStrategy(ReconnectionStrategy.NONE)
     *      .cacheProvider(CacheProvider.REDISSON)
     *      .syncStrategy(SyncStrategy.INVALIDATE)
     *      .storeCacheMiss(false);
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
                    .cacheProvider(CacheProvider.REDISSON)
                    .storeMode(StoreMode.LOCALCACHE_REDIS)
                    .syncStrategy(SyncStrategy.INVALIDATE)
                    .storeCacheMiss(false)
                    .expirationEventPolicy(ExpirationEventPolicy.SUBSCRIBE_WITH_KEYEVENT_PATTERN);
    }

    public CacheProvider getCacheProvider() {
        return cacheProvider;
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
     * Defines local cache size.
     * <p>
     * If size is <code>0</code> then local cache is unbounded.
     * <p>
     * If size is <code>-1</code> then local cache is always empty and doesn't store data.
     * 
     * @param cacheSize size of cache
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

    /**
     * Defines strategy for load missed local cache updates after Redis connection failure.
     *
     * @param reconnectionStrategy
     *          <p><code>CLEAR</code> - clear local cache if map instance has been disconnected for a while.
     *          <p><code>LOAD</code> - store invalidated entry hash in invalidation log for 10 minutes. Cache keys for stored invalidated entry hashes will be removed if LocalCachedMap instance has been disconnected less than 10 minutes or whole cache will be cleaned otherwise
     *          <p><code>NONE</code> - Default. No reconnection handling
     * @return LocalCachedMapOptions instance
     */
    public LocalCachedMapOptions<K, V> reconnectionStrategy(ReconnectionStrategy reconnectionStrategy) {
        if (reconnectionStrategy == null) {
            throw new NullPointerException("reconnectionStrategy can't be null");
        }

        this.reconnectionStrategy = reconnectionStrategy;
        return this;
    }

    /**
     * Defines local cache synchronization strategy.
     *
     * @param syncStrategy
     *          <p><code>INVALIDATE</code> - Default. Invalidate cache entry across all LocalCachedMap instances on map entry change
     *          <p><code>UPDATE</code> - Insert/update cache entry across all LocalCachedMap instances on map entry change
     *          <p><code>NONE</code> - No synchronizations on map changes
     * @return LocalCachedMapOptions instance
     */
    public LocalCachedMapOptions<K, V> syncStrategy(SyncStrategy syncStrategy) {
        if (syncStrategy == null) {
            throw new NullPointerException("syncStrategy can't be null");
        }

        this.syncStrategy = syncStrategy;
        return this;
    }
    
    /**
     * Defines local cache eviction policy.
     * 
     * @param evictionPolicy
     *         <p><code>LRU</code> - uses local cache with LRU (least recently used) eviction policy.
     *         <p><code>LFU</code> - uses local cache with LFU (least frequently used) eviction policy.
     *         <p><code>SOFT</code> - uses local cache with soft references. The garbage collector will evict items from the local cache when the JVM is running out of memory.
     *         <p><code>WEAK</code> - uses local cache with weak references. The garbage collector will evict items from the local cache when it became weakly reachable.
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
     * Defines time to live in milliseconds of each map entry in local cache.
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
     * Defines time to live of each map entry in local cache.
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
     * Defines max idle time in milliseconds of each map entry in local cache.
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
     * Defines max idle time of each map entry in local cache.
     * If value equals to <code>0</code> then timeout is not applied
     * 
     * @param maxIdle - max idle time
     * @param timeUnit - time unit
     * @return LocalCachedMapOptions instance
     */
    public LocalCachedMapOptions<K, V> maxIdle(long maxIdle, TimeUnit timeUnit) {
        return maxIdle(timeUnit.toMillis(maxIdle));
    }

    public StoreMode getStoreMode() {
        return storeMode;
    }

    /**
     * Defines store mode of cache data.
     *
     * @param storeMode
     *         <p><code>LOCALCACHE</code> - store data in local cache only.
     *         <p><code>LOCALCACHE_REDIS</code> - store data in both Redis and local cache.
     * @return LocalCachedMapOptions instance
     */
    public LocalCachedMapOptions<K, V> storeMode(StoreMode storeMode) {
        this.storeMode = storeMode;
        return this;
    }

    /**
     * Defines Cache provider used as local cache store.
     *
     * @param cacheProvider
     *         <p><code>REDISSON</code> - uses Redisson own implementation.
     *         <p><code>CAFFEINE</code> - uses Caffeine implementation.
     * @return LocalCachedMapOptions instance
     */
    public LocalCachedMapOptions<K, V> cacheProvider(CacheProvider cacheProvider) {
        this.cacheProvider = cacheProvider;
        return this;
    }

    public boolean isStoreCacheMiss() {
        return this.storeCacheMiss;
    }

    /**
     * Defines whether to store a cache miss into the local cache.
     *
     * @param storeCacheMiss - whether to store a cache miss into the local cache
     * @return LocalCachedMapOptions instance
     */
    public LocalCachedMapOptions<K, V> storeCacheMiss(boolean storeCacheMiss) {
        this.storeCacheMiss = storeCacheMiss;
        return this;
    }

    /**
     * Use {@link #expirationEventPolicy(ExpirationEventPolicy)} instead
     *
     * @param useKeyEventsPattern - whether to use __keyevent pattern topic
     * @return LocalCachedMapOptions instance
     */
    @Deprecated
    public LocalCachedMapOptions<K, V> useKeyEventsPattern(boolean useKeyEventsPattern) {
        if (useKeyEventsPattern) {
            this.expirationEventPolicy = ExpirationEventPolicy.SUBSCRIBE_WITH_KEYEVENT_PATTERN;
        } else {
            this.expirationEventPolicy = ExpirationEventPolicy.SUBSCRIBE_WITH_KEYSPACE_CHANNEL;
        }
        return this;
    }

    /**
     * Defines how to listen expired event sent by Redis upon this instance deletion.
     *
     * @param expirationEventPolicy expiration policy value
     * @return LocalCachedMapOptions instance
     */
    public LocalCachedMapOptions<K, V> expirationEventPolicy(ExpirationEventPolicy expirationEventPolicy) {
        this.expirationEventPolicy = expirationEventPolicy;
        return this;
    }

    public ExpirationEventPolicy getExpirationEventPolicy() {
        return expirationEventPolicy;
    }

    @Override
    public LocalCachedMapOptions<K, V> writeBehindBatchSize(int writeBehindBatchSize) {
        return (LocalCachedMapOptions<K, V>) super.writeBehindBatchSize(writeBehindBatchSize);
    }
    
    @Override
    public LocalCachedMapOptions<K, V> writeBehindDelay(int writeBehindDelay) {
        return (LocalCachedMapOptions<K, V>) super.writeBehindDelay(writeBehindDelay);
    }
    
    @Override
    public LocalCachedMapOptions<K, V> writer(MapWriter<K, V> writer) {
        return (LocalCachedMapOptions<K, V>) super.writer(writer);
    }

    @Override
    public LocalCachedMapOptions<K, V> writerAsync(MapWriterAsync<K, V> writer) {
        return (LocalCachedMapOptions<K, V>) super.writerAsync(writer);
    }

    @Override
    public LocalCachedMapOptions<K, V> writeMode(org.redisson.api.MapOptions.WriteMode writeMode) {
        return (LocalCachedMapOptions<K, V>) super.writeMode(writeMode);
    }
    
    @Override
    public LocalCachedMapOptions<K, V> loader(MapLoader<K, V> loader) {
        return (LocalCachedMapOptions<K, V>) super.loader(loader);
    }

    @Override
    public LocalCachedMapOptions<K, V> loaderAsync(MapLoaderAsync<K, V> loaderAsync) {
        return (LocalCachedMapOptions<K, V>) super.loaderAsync(loaderAsync);
    }

    @Override
    public LocalCachedMapOptions<K, V> writerRetryAttempts(int writerRetryAttempts) {
        return (LocalCachedMapOptions<K, V>) super.writerRetryAttempts(writerRetryAttempts);
    }

    @Override
    public LocalCachedMapOptions<K, V> writerRetryInterval(Duration writerRetryInterval) {
        return (LocalCachedMapOptions<K, V>) super.writerRetryInterval(writerRetryInterval);
    }
}
