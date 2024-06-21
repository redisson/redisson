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

import static io.quarkus.runtime.annotations.ConfigPhase.RUN_TIME;

import java.util.Map;

import io.quarkus.runtime.annotations.ConfigDocMapKey;
import io.quarkus.runtime.annotations.ConfigDocSection;
import io.quarkus.runtime.annotations.ConfigItem;
import io.quarkus.runtime.annotations.ConfigRoot;

/**
 *
 * @author Nikita Koksharov
 *
 */
@ConfigRoot(phase = RUN_TIME, name = "cache.redisson")
public class RedissonCachesConfig {

    /**
     * Default configuration applied to all Redis caches (lowest precedence)
     */
    @ConfigItem(name = ConfigItem.PARENT)
    public RedissonCacheRuntimeConfig defaultConfig;

    /**
     * Additional configuration applied to a specific Redis cache (highest precedence)
     */
    @ConfigItem(name = ConfigItem.PARENT)
    @ConfigDocMapKey("cache-name")
    @ConfigDocSection
    Map<String, RedissonCacheRuntimeConfig> cachesConfig;

}
