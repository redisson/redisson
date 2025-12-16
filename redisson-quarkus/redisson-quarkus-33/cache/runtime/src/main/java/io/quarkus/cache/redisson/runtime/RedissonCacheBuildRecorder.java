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

import io.quarkus.cache.Cache;
import io.quarkus.cache.CacheManager;
import io.quarkus.cache.CacheManagerInfo;
import io.quarkus.cache.runtime.CacheManagerImpl;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;
import org.jboss.logging.Logger;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 *
 * @author Nikita Koksharov
 *
 */
@Recorder
public class RedissonCacheBuildRecorder {

    private static final Logger LOGGER = Logger.getLogger(RedissonCacheBuildRecorder.class);

    private final RuntimeValue<RedissonCachesConfig> redisCacheConfigRV;

    public RedissonCacheBuildRecorder(RuntimeValue<RedissonCachesConfig> redisCacheConfigRV) {
        this.redisCacheConfigRV = redisCacheConfigRV;
    }

    public CacheManagerInfo getCacheManagerSupplier() {
        return new CacheManagerInfo() {
            @Override
            public boolean supports(Context context) {
                return context.cacheEnabled() && "redisson".equals(context.cacheType());
            }

            @Override
            @SuppressWarnings({ "rawtypes", "unchecked" })
            public Supplier<CacheManager> get(Context context) {
                return new Supplier<CacheManager>() {
                    @Override
                    public CacheManager get() {
                        Set<RedissonCacheInfo> cacheInfos = RedissonCacheInfoBuilder.build(context.cacheNames(), redisCacheConfigRV.getValue());
                        if (cacheInfos.isEmpty()) {
                            return new CacheManagerImpl(Collections.emptyMap());
                        } else {
                            // The number of caches is known at build time so we can use fixed initialCapacity and loadFactor for the caches map.
                            Map<String, Cache> caches = new HashMap<>(cacheInfos.size() + 1, 1.0F);
                            for (RedissonCacheInfo cacheInfo : cacheInfos) {
                                if (LOGGER.isDebugEnabled()) {
                                    LOGGER.debugf(
                                            "Building Redis cache [%s] with [expireAfterAccess=%s], [expireAfterWrite=%s], [maxSize=%s]",
                                            cacheInfo.name, cacheInfo.expireAfterAccess, cacheInfo.expireAfterWrite, cacheInfo.maxSize);
                                }

                                RedissonCacheImpl cache = new RedissonCacheImpl(cacheInfo);
                                caches.put(cacheInfo.name, cache);
                            }
                            return new CacheManagerImpl(caches);
                        }
                    }
                };
            }
        };
    }

}
