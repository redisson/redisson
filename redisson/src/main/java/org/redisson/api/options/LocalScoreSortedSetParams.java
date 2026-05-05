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
 * Configuration for LocalCachedMap object.
 *
 * @param <V> value type
 * @author Nikita Koksharov
 */
public final class LocalScoreSortedSetParams<V> implements LocalScoreSortedSetOptions<V> {

    private final String name;
    private ReconnectionStrategy reconnectionStrategy = ReconnectionStrategy.NONE;
    private EvictionPolicy evictionPolicy = EvictionPolicy.NONE;
    private int cacheSize;
    private long timeToLiveInMillis;
    private long maxIdleInMillis;
    private CacheProvider cacheProvider = CacheProvider.REDISSON;
    private StoreMode storeMode = StoreMode.LOCALCACHE_REDIS;
    private ReadMode readMode = ReadMode.LOCALCACHE;
    private boolean preload = false;

    LocalScoreSortedSetParams(String name) {
        this.name = name;
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

    public boolean isPreload() {
        return preload;
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
    public LocalScoreSortedSetParams<V> cacheSize(int cacheSize) {
        this.cacheSize = cacheSize;
        return this;
    }

    public LocalScoreSortedSetParams<V> preload(boolean preload) {
        this.preload = preload;
        return this;
    }


    public ReconnectionStrategy getReconnectionStrategy() {
        return reconnectionStrategy;
    }

    /**
     * Defines strategy for load missed local cache updates after Redis connection failure.
     *
     * @param reconnectionStrategy <p><code>CLEAR</code> - clear local cache if map instance has been disconnected for a while.
     *                             <p><code>LOAD</code> - store invalidated entry hash in invalidation log for 10 minutes. Cache keys for stored invalidated entry hashes will be removed if LocalCachedMap instance has been disconnected less than 10 minutes or whole cache will be cleaned otherwise
     *                             <p><code>NONE</code> - Default. No reconnection handling
     * @return LocalCachedMapOptions instance
     */
    public LocalScoreSortedSetParams<V> reconnectionStrategy(ReconnectionStrategy reconnectionStrategy) {
        if (reconnectionStrategy == null) {
            throw new NullPointerException("reconnectionStrategy can't be null");
        }

        this.reconnectionStrategy = reconnectionStrategy;
        return this;
    }

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
    public LocalScoreSortedSetParams<V> evictionPolicy(EvictionPolicy evictionPolicy) {
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
     * @param ttl time to live in milliseconds
     * @return LocalCachedMapOptions instance
     */
    public LocalScoreSortedSetParams<V> timeToLive(Duration ttl) {
        this.timeToLiveInMillis = ttl.toMillis();
        return this;
    }

    /**
     * Defines max idle time in milliseconds of each map entry in local cache.
     * If value equals to <code>0</code> then timeout is not applied
     *
     * @param idleTime time to live in milliseconds
     * @return LocalCachedMapOptions instance
     */
    public LocalScoreSortedSetParams<V> maxIdle(Duration idleTime) {
        this.maxIdleInMillis = idleTime.toMillis();
        return this;
    }

    public StoreMode getStoreMode() {
        return storeMode;
    }

    public ReadMode getReadMode() {
        return readMode;
    }

    /**
     * Defines store mode of cache data.
     *
     * @param storeMode <p><code>LOCALCACHE</code> - store data in local cache only.
     *                  <p><code>LOCALCACHE_REDIS</code> - store data in both Redis and local cache.
     * @return LocalCachedMapOptions instance
     */
    public LocalScoreSortedSetParams<V> storeMode(StoreMode storeMode) {
        this.storeMode = storeMode;
        return this;
    }

    /**
     * Defines Cache provider used as local cache store.
     *
     * @param cacheProvider <p><code>REDISSON</code> - uses Redisson own implementation.
     *                      <p><code>CAFFEINE</code> - uses Caffeine implementation.
     * @return LocalCachedMapOptions instance
     */
    public LocalScoreSortedSetParams<V> cacheProvider(CacheProvider cacheProvider) {
        this.cacheProvider = cacheProvider;
        return this;
    }

    /**
     * Defines the read mode for this instance.
     *
     * @param readMode <p><code>LOCALCACHE</code> - serve reads from the local in-process cache (default).
     *                 <p><code>REDIS</code> - always serve reads directly from Redis.
     * @return LocalScoreSortedSetParams instance
     */
    public LocalScoreSortedSetParams<V> readMode(ReadMode readMode) {
        if (readMode == null) {
            throw new NullPointerException("readMode can't be null");
        }
        this.readMode = readMode;
        return this;
    }

    public String getName() {
        return name;
    }
}
