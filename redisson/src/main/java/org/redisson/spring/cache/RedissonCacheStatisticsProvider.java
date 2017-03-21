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
package org.redisson.spring.cache;

import org.springframework.boot.actuate.cache.CacheStatistics;
import org.springframework.boot.actuate.cache.CacheStatisticsProvider;
import org.springframework.boot.actuate.cache.DefaultCacheStatistics;
import org.springframework.cache.CacheManager;

/**
 *
 * @author Craig Andrews
 *
 */
public class RedissonCacheStatisticsProvider implements CacheStatisticsProvider<RedissonCache> {

    @Override
    public CacheStatistics getCacheStatistics(final CacheManager cacheManager, final RedissonCache cache) {
        final DefaultCacheStatistics defaultCacheStatistics = new DefaultCacheStatistics();
        defaultCacheStatistics.setSize((long) cache.getNativeCache().size());
        defaultCacheStatistics.setGetCacheCounts(cache.getCacheHits(), cache.getCacheMisses());
        return defaultCacheStatistics;
    }

}
