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
package org.redisson.api.options;

import java.time.Duration;

/**
 *
 * @author Nikita Koksharov
 *
 */
public interface ClientSideCachingOptions {

    enum EvictionPolicy {

        /**
         * Client cache without eviction.
         */
        NONE,

        /**
         * Least Recently Used client cache eviction policy.
         */
        LRU,

        /**
         * Least Frequently Used client cache eviction policy.
         */
        LFU,

        /**
         * Client cache  eviction policy with Soft Reference used for values.
         * All references will be collected by GC
         */
        SOFT,

        /**
         * Client cache eviction policy with Weak Reference used for values.
         * All references will be collected by GC
         */
        WEAK
    };

    /**
     * Creates the default options
     *
     * @return options instance
     */
    static ClientSideCachingOptions defaults() {
        return new ClientSideCachingParams();
    }

    /**
     * Defines client cache eviction policy.
     *
     * @param evictionPolicy
     *         <p><code>LRU</code> - uses client cache with LRU (least recently used) eviction policy.
     *         <p><code>LFU</code> - uses client cache with LFU (least frequently used) eviction policy.
     *         <p><code>SOFT</code> - uses client cache with soft references. The garbage collector will evict items from the client cache when the JVM is running out of memory.
     *         <p><code>WEAK</code> - uses client cache with weak references. The garbage collector will evict items from the client cache when it became weakly reachable.
     *         <p><code>NONE</code> - doesn't use eviction policy, but timeToLive and maxIdleTime params are still working.
     * @return options instance
     */
    ClientSideCachingOptions evictionPolicy(EvictionPolicy evictionPolicy);

    /**
     * Defines client cache size.
     * <p>
     * If size is <code>0</code> then client cache is unbounded.
     * <p>
     * If size is <code>-1</code> then client cache is always empty and doesn't store data.
     *
     * @param size size of client cache
     * @return options instance
     */
    ClientSideCachingOptions size(int size);

    /**
     * Defines time to live in milliseconds of each map entry in client cache.
     * If value equals to <code>0</code> then timeout is not applied
     *
     * @param ttl - time to live in milliseconds
     * @return LocalCachedMapOptions instance
     */
    ClientSideCachingOptions timeToLive(Duration ttl);

    /**
     * Defines max idle time in milliseconds of each map entry in client cache.
     * If value equals to <code>0</code> then timeout is not applied
     *
     * @param idleTime time to live in milliseconds
     * @return LocalCachedMapOptions instance
     */
    ClientSideCachingOptions maxIdle(Duration idleTime);

}
