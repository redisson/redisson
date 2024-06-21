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

import java.time.Duration;
import java.util.Optional;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonCacheInfo {

    /**
     * The cache name
     */
    public String name;

    /**
     * The default time to live of the item stored in the cache
     */
    public Optional<Duration> expireAfterAccess = Optional.empty();

    /**
     * The default time to live to add to the item once read
     */
    public Optional<Duration> expireAfterWrite = Optional.empty();

}
