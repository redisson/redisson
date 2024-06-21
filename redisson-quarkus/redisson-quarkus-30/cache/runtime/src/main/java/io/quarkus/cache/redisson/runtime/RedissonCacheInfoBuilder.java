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

import io.quarkus.runtime.configuration.HashSetFactory;

import java.util.Collections;
import java.util.Set;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonCacheInfoBuilder {

    public static Set<RedissonCacheInfo> build(Set<String> cacheNames,
                                               RedissonCachesConfig runtimeConfig) {
        if (cacheNames.isEmpty()) {
            return Collections.emptySet();
        } else {
            Set<RedissonCacheInfo> result = HashSetFactory.<RedissonCacheInfo> getInstance().apply(cacheNames.size());
            ;
            for (String cacheName : cacheNames) {

                RedissonCacheInfo cacheInfo = new RedissonCacheInfo();
                cacheInfo.name = cacheName;

                RedissonCacheRuntimeConfig defaultRuntimeConfig = runtimeConfig.defaultConfig;
                RedissonCacheRuntimeConfig namedRuntimeConfig = runtimeConfig.cachesConfig.get(cacheInfo.name);

                if (namedRuntimeConfig != null && namedRuntimeConfig.expireAfterAccess.isPresent()) {
                    cacheInfo.expireAfterAccess = namedRuntimeConfig.expireAfterAccess;
                } else if (defaultRuntimeConfig.expireAfterAccess.isPresent()) {
                    cacheInfo.expireAfterAccess = defaultRuntimeConfig.expireAfterAccess;
                }

                if (namedRuntimeConfig != null && namedRuntimeConfig.expireAfterWrite.isPresent()) {
                    cacheInfo.expireAfterWrite = namedRuntimeConfig.expireAfterWrite;
                } else if (defaultRuntimeConfig.expireAfterWrite.isPresent()) {
                    cacheInfo.expireAfterWrite = defaultRuntimeConfig.expireAfterWrite;
                }

                result.add(cacheInfo);
            }
            return result;
        }
    }
}
