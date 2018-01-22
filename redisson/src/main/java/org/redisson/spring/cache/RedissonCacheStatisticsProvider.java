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

/**
 *
 * @author Craig Andrews
 * TODO needs reimplementation for spring boot 2 to {@code io.micrometer.core.instrument.binder.MeterBinder} from {@code io .micrometer:micrometer-core}
 * TODO see https://docs.spring.io/spring-boot/docs/2.0.0.M6/reference/htmlsingle/#production-ready-metrics
 */
public class RedissonCacheStatisticsProvider /*implements CacheStatisticsProvider<RedissonCache>*/ {

//    @Override
//    public CacheStatistics getCacheStatistics(final CacheManager cacheManager, final RedissonCache cache) {
//        final DefaultCacheStatistics defaultCacheStatistics = new DefaultCacheStatistics();
//        defaultCacheStatistics.setSize((long) cache.getNativeCache().size());
//        defaultCacheStatistics.setGetCacheCounts(cache.getCacheHits(), cache.getCacheMisses());
//        return defaultCacheStatistics;
//    }

}
