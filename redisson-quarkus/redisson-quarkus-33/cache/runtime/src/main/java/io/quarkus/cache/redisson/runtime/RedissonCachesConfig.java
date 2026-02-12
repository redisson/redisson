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
package io.quarkus.cache.redisson.runtime;

import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithParentName;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import static io.quarkus.runtime.annotations.ConfigPhase.RUN_TIME;

/**
 *
 * @author Nikita Koksharov
 *
 */
@ConfigRoot(phase = RUN_TIME)
@ConfigMapping(prefix = "cache.redisson")
public interface RedissonCachesConfig {

    /**
     * Default configuration applied to all Redis caches (lowest precedence)
     */
    @WithParentName
    RedissonCacheRuntimeConfig defaultConfig();

    /**
     * Additional configuration applied to a specific Redis cache (highest precedence)
     */
    @WithParentName
    Map<String, RedissonCacheRuntimeConfig> cachesConfig();

    interface RedissonCacheRuntimeConfig {

        /**
         * Specifies maximum size of this cache.
         * Superfluous elements are evicted using LRU algorithm.
         * If <code>0</code> the cache is unbounded (default).
         */
        Optional<Integer> maxSize();

        /**
         * Specifies that each entry should be automatically removed from the cache once a fixed duration has elapsed after
         * the entry's creation, or the most recent replacement of its value.
         */
        Optional<Duration> expireAfterWrite();

        /**
         * Specifies that each entry should be automatically removed from the cache once a fixed duration has elapsed after
         * the last access of its value.
         */
        Optional<Duration> expireAfterAccess();

        /**
         * Specifies the cache implementation.
         */
        Optional<CacheImplementation> implementation();

    }


}
