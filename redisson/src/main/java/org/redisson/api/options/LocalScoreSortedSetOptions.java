/**
 * Copyright (c) 2013-2026 Nikita Koksharov
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
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
 * @param <V> value type
 * @author Nikita Koksharov
 */
public interface LocalScoreSortedSetOptions<V> {

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
         * Preload local cache if set instance has reconnected
         */
        PRE_LOAD

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
    }

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
         * Store data in both Redis and local cache.
         */
        LOCALCACHE_REDIS

    }

    /**
     * Defines where read operations are served from.
     * Can be combined with {@link StoreMode} independently:
     * {@code StoreMode} governs where writes are persisted,
     * while {@code ReadMode} governs where reads are resolved.
     */
    enum ReadMode {

        /**
         * Serve reads from the local in-process cache.
         * Fastest option; may return stale data if the cache is not fully up-to-date.
         */
        LOCALCACHE,

        /**
         * Always serve reads directly from Redis.
         * Guarantees up-to-date data at the cost of a network round-trip per read.
         */
        REDIS

    }

    /**
     * Creates options with the name of object instance
     *
     * @param name of object instance
     * @return options instance
     */
    static <V> LocalScoreSortedSetOptions<V> name(String name) {
        return new LocalScoreSortedSetParams<>(name);
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
    LocalScoreSortedSetOptions<V> cacheSize(int cacheSize);

    /**
     * Defines strategy for load missed local cache updates after Redis connection failure.
     *
     * @param reconnectionStrategy <p><code>CLEAR</code> - clear local cache if map instance has been disconnected for a while.
     *                             <p><code>LOAD</code> - store invalidated entry hash in invalidation log for 10 minutes. Cache keys for stored invalidated entry hashes will be removed if LocalCachedMap instance has been disconnected less than 10 minutes or whole cache will be cleaned otherwise
     *                             <p><code>NONE</code> - Default. No reconnection handling
     * @return LocalCachedMapOptions instance
     */
    LocalScoreSortedSetOptions<V> reconnectionStrategy(ReconnectionStrategy reconnectionStrategy);

    /**
     * Defines local cache eviction policy.
     *
     * @param evictionPolicy <p><code>LRU</code> - uses local cache with LRU (least recently used) eviction policy.
     *                       <p><code>LFU</code> - uses local cache with LFU (least frequently used) eviction policy.
     *                       <p><code>SOFT</code> - uses local cache with soft references. The garbage collector will evict items from the local cache when the JVM is running out of memory.
     *                       <p><code>WEAK</code> - uses local cache with weak references. The garbage collector will evict items from the local cache when it became weakly reachable.
     *                       <p><code>NONE</code> - doesn't use eviction policy, but timeToLive and maxIdleTime params are still working.
     * @return LocalCachedMapOptions instance
     */
    LocalScoreSortedSetOptions<V> evictionPolicy(EvictionPolicy evictionPolicy);

    /**
     * Defines time to live in milliseconds of each map entry in local cache.
     * If value equals to <code>0</code> then timeout is not applied
     *
     * @param ttl - time to live in milliseconds
     * @return LocalCachedMapOptions instance
     */
    LocalScoreSortedSetOptions<V> timeToLive(Duration ttl);

    /**
     * Defines max idle time in milliseconds of each map entry in local cache.
     * If value equals to <code>0</code> then timeout is not applied
     *
     * @param idleTime time to live in milliseconds
     * @return LocalCachedMapOptions instance
     */
    LocalScoreSortedSetOptions<V> maxIdle(Duration idleTime);

    /**
     * Defines store mode of cache data.
     *
     * @param storeMode <p><code>LOCALCACHE</code> - store data in local cache only.
     *                  <p><code>LOCALCACHE_REDIS</code> - store data in both Redis and local cache.
     * @return LocalCachedMapOptions instance
     */
    LocalScoreSortedSetOptions<V> storeMode(StoreMode storeMode);

    /**
     * Defines Cache provider used as local cache store.
     *
     * @param cacheProvider <p><code>REDISSON</code> - uses Redisson own implementation.
     *                      <p><code>CAFFEINE</code> - uses Caffeine implementation.
     * @return LocalCachedMapOptions instance
     */
    LocalScoreSortedSetOptions<V> cacheProvider(CacheProvider cacheProvider);

    /**
     * Defines the read mode for this instance.
     *
     * @param readMode <p><code>LOCALCACHE</code> - serve reads from the local in-process cache (default).
     *                 <p><code>REDIS</code> - always serve reads directly from Redis.
     * @return LocalScoreSortedSetOptions instance
     */
    LocalScoreSortedSetOptions<V> readMode(ReadMode readMode);

}
