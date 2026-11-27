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
package org.redisson.api.options;

import java.time.Duration;

/**
 * {@link org.redisson.api.RLocalCachedMap} instance options.
 * 
 * @author Nikita Koksharov
 *
 * @param <K> key type
 * @param <V> value type
 */
public interface LocalCachedMapOptions<K, V> extends ExMapOptions<LocalCachedMapOptions<K, V>, K, V> {
    
    /**
     * Various strategies to avoid stale objects in local cache.
     * Handle cases when map instance has been disconnected for a while.
     *
     */
    enum ReconnectionStrategy {
        
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
        LOAD,

        /**
         * Reload local cache if map instance connect/disconnected.
         */
        RELOAD
        
    }
    
    enum SyncStrategy {
        
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
    
    enum EvictionPolicy {
        
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

    enum CacheProvider {

        REDISSON,

        CAFFEINE

    }

    enum StoreMode {

        /**
         * Store data only in local cache.
         */
        LOCALCACHE,

        /**
         * Store data only in both Redis and local cache.
         */
        LOCALCACHE_REDIS

    }

    enum ReadMode {

        /**
         * Read data only in local cache.
         */
        LOCALCACHE,

        /**
         * Read data in Redis if not found in local cache.
         */
        LOCALCACHE_REDIS

    }

    enum ExpirationEventPolicy {

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

    /**
     * Creates options with the name of object instance
     *
     * @param name of object instance
     * @return options instance
     */
    static <K, V> LocalCachedMapOptions<K, V> name(String name) {
        return new LocalCachedMapParams<>(name);
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
    LocalCachedMapOptions<K, V> cacheSize(int cacheSize);
    
    /**
     * Defines strategy for load missed local cache updates after Redis connection failure.
     *
     * @param reconnectionStrategy
     *          <p><code>CLEAR</code> - clear local cache if map instance has been disconnected for a while.
     *          <p><code>LOAD</code> - store invalidated entry hash in invalidation log for 10 minutes. Cache keys for stored invalidated entry hashes will be removed if LocalCachedMap instance has been disconnected less than 10 minutes or whole cache will be cleaned otherwise
     *          <p><code>RELOAD</code> - Reload local cache if map instance connect/disconnected.
     *          <p><code>NONE</code> - Default. No reconnection handling
     * @return LocalCachedMapOptions instance
     */
    LocalCachedMapOptions<K, V> reconnectionStrategy(ReconnectionStrategy reconnectionStrategy);

    /**
     * Defines local cache synchronization strategy.
     *
     * @param syncStrategy
     *          <p><code>INVALIDATE</code> - Default. Invalidate cache entry across all LocalCachedMap instances on map entry change
     *          <p><code>UPDATE</code> - Insert/update cache entry across all LocalCachedMap instances on map entry change
     *          <p><code>NONE</code> - No synchronizations on map changes
     * @return LocalCachedMapOptions instance
     */
    LocalCachedMapOptions<K, V> syncStrategy(SyncStrategy syncStrategy);
    
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
    LocalCachedMapOptions<K, V> evictionPolicy(EvictionPolicy evictionPolicy);
    
    /**
     * Defines time to live in milliseconds of each map entry in local cache.
     * If value equals to <code>0</code> then timeout is not applied
     * 
     * @param ttl - time to live in milliseconds
     * @return LocalCachedMapOptions instance
     */
    LocalCachedMapOptions<K, V> timeToLive(Duration ttl);

    /**
     * Defines max idle time in milliseconds of each map entry in local cache.
     * If value equals to <code>0</code> then timeout is not applied
     * 
     * @param idleTime time to live in milliseconds
     * @return LocalCachedMapOptions instance
     */
    LocalCachedMapOptions<K, V> maxIdle(Duration idleTime);

    /**
     * Defines store mode of cache data.
     *
     * @param storeMode <p><code>LOCALCACHE</code> - store data in local cache only.
     *                  <p><code>LOCALCACHE_REDIS</code> - store data in both Redis and local cache.
     * @return LocalCachedMapOptions instance
     */
    LocalCachedMapOptions<K, V> storeMode(StoreMode storeMode);

    /**
     * Defines read mode of cache data.
     *
     * @param readMode
     *         <p><code>LOCALCACHE</code> - read data in local cache only.
     *         <p><code>LOCALCACHE_REDIS</code> - read data in Redis if not found in local cache.
     * @return LocalCachedMapOptions instance
     */
    LocalCachedMapOptions<K, V> readMode(LocalCachedMapOptions.ReadMode readMode);

    /**
     * Defines Cache provider used as local cache store.
     *
     * @param cacheProvider <p><code>REDISSON</code> - uses Redisson own implementation.
     *                      <p><code>CAFFEINE</code> - uses Caffeine implementation.
     * @return LocalCachedMapOptions instance
     */
    LocalCachedMapOptions<K, V> cacheProvider(CacheProvider cacheProvider);

    /**
     * Defines whether to store a cache miss into the local cache.
     *
     * @param storeCacheMiss - whether to store a cache miss into the local cache
     * @return LocalCachedMapOptions instance
     */
    LocalCachedMapOptions<K, V> storeCacheMiss(boolean storeCacheMiss);

    /**
     * Use {@link #expirationEventPolicy(ExpirationEventPolicy)} instead
     *
     * @param useKeyEventsPattern - whether to use __keyevent pattern topic
     * @return LocalCachedMapOptions instance
     */
    @Deprecated
    LocalCachedMapOptions<K, V> useKeyEventsPattern(boolean useKeyEventsPattern);

    /**
     * Defines how to listen expired event sent by Redis upon this instance deletion.
     *
     * @param expirationEventPolicy expiration policy value
     * @return LocalCachedMapOptions instance
     */
    LocalCachedMapOptions<K, V> expirationEventPolicy(ExpirationEventPolicy expirationEventPolicy);

    /**
     * Defines whether to store CacheKey of an object key into the local cache. <br>
     * This indicator only affects when {@link #cacheProvider(CacheProvider)} != CAFFEINE
     *
     * @param useObjectAsCacheKey - whether to store CacheKey of an object key into the local cache
     * @return LocalCachedMapOptions instance
     */
    LocalCachedMapOptions<K, V> useObjectAsCacheKey(boolean useObjectAsCacheKey);

    /**
     * Defines whether to use a global topic pattern listener
     * that applies to all local cache instances belonging to the same Redisson instance.
     *
     * @param value whether to use a global topic pattern listener
     * @return LocalCachedMapOptions instance
     */
    LocalCachedMapOptions<K, V> useTopicPattern(boolean value);

}
