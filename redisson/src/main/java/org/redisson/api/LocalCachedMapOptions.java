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

/**
 * RLocalCachedMap options object. Used to specify RLocalCachedMap settings.
 * 
 * @author Nikita Koksharov
 *
 */
public class LocalCachedMapOptions {
    
    public enum EvictionPolicy {NONE, LRU, LFU};
    
    private boolean invalidateEntryOnChange;
    private EvictionPolicy evictionPolicy;
    private int cacheSize;
    private long timeToLiveInMillis;
    private long maxIdleInMillis;
    
    private LocalCachedMapOptions() {
    }
    
    protected LocalCachedMapOptions(LocalCachedMapOptions copy) {
        this.invalidateEntryOnChange = copy.invalidateEntryOnChange;
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
     * @return LocalCachedMapOptions instance
     * 
     */
    public static LocalCachedMapOptions defaults() {
        return new LocalCachedMapOptions()
                    .cacheSize(0).timeToLive(0).maxIdle(0)
                    .evictionPolicy(EvictionPolicy.NONE)
                    .invalidateEntryOnChange(true);
    }
    
    public boolean isInvalidateEntryOnChange() {
        return invalidateEntryOnChange;
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
     * Sets cache size. If size is <code>0</code> then cache is unbounded.
     * 
     * @param cacheSize - size of cache
     * @return LocalCachedMapOptions instance
     */
    public LocalCachedMapOptions cacheSize(int cacheSize) {
        this.cacheSize = cacheSize;
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
    public LocalCachedMapOptions invalidateEntryOnChange(boolean value) {
        this.invalidateEntryOnChange = value;
        return this;
    }

    /**
     * Sets eviction policy. 
     * 
     * @param evictionPolicy
     *         <p><code>LRU</code> - uses cache with LRU (least recently used) eviction policy.
     *         <p><code>LFU</code> - uses cache with LFU (least frequently used) eviction policy.
     *         <p><code>NONE</code> - doesn't use eviction policy, but timeToLive and maxIdleTime params are still working.
     * @return LocalCachedMapOptions instance
     */
    public LocalCachedMapOptions evictionPolicy(EvictionPolicy evictionPolicy) {
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
    public LocalCachedMapOptions timeToLive(long timeToLiveInMillis) {
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
    public LocalCachedMapOptions timeToLive(long timeToLive, TimeUnit timeUnit) {
        return timeToLive(timeUnit.toMillis(timeToLive));
    }

    /**
     * Sets max idle time in milliseconds for each map entry in cache.
     * If value equals to <code>0</code> then timeout is not applied
     * 
     * @param maxIdleInMillis - time to live in milliseconds
     * @return LocalCachedMapOptions instance
     */
    public LocalCachedMapOptions maxIdle(long maxIdleInMillis) {
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
    public LocalCachedMapOptions maxIdle(long maxIdle, TimeUnit timeUnit) {
        return timeToLive(timeUnit.toMillis(maxIdle));
    }

    
}
