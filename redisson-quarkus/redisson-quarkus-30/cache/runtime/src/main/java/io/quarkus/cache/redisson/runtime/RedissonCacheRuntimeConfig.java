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
package io.quarkus.cache.redisson.runtime;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.quarkus.runtime.annotations.ConfigItem;

import java.time.Duration;
import java.util.Optional;

/**
 *
 * @author Nikita Koksharov
 *
 */
@ConfigGroup
public class RedissonCacheRuntimeConfig {

    /**
     * Specifies that each entry should be automatically removed from the cache once a fixed duration has elapsed after
     * the entry's creation, or the most recent replacement of its value.
     */
    @ConfigItem
    Optional<Duration> expireAfterWrite;

    /**
     * Specifies that each entry should be automatically removed from the cache once a fixed duration has elapsed after
     * the last access of its value.
     */
    @ConfigItem
    Optional<Duration> expireAfterAccess;

    /**
     * Specifies the cache implementation.
     */
    @ConfigItem
    Optional<CacheImplementation> implementation;

}
