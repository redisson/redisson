/**
 * Copyright (c) 2013-2019 Nikita Koksharov
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

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.binder.cache.CacheMeterBinder;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonCacheMetrics extends CacheMeterBinder {

    private final RedissonCache cache;
    
    public RedissonCacheMetrics(RedissonCache cache, Iterable<Tag> tags) {
        super(cache, cache.getName(), tags);
        this.cache = cache;
    }
    
    /**
     * Record metrics on a Redisson cache.
     *
     * @param registry - registry to bind metrics to
     * @param cache - cache to instrument
     * @param tags - tags to apply to all recorded metrics
     * @return cache
     */
    public static RedissonCache monitor(MeterRegistry registry, RedissonCache cache, Iterable<Tag> tags) {
        new RedissonCacheMetrics(cache, tags).bindTo(registry);
        return cache;
    }

    @Override
    protected Long size() {
        return (long) cache.getNativeCache().size();
    }

    @Override
    protected long hitCount() {
        return cache.getCacheHits();
    }

    @Override
    protected Long missCount() {
        return cache.getCacheMisses();
    }

    @Override
    protected Long evictionCount() {
        return null;
    }

    @Override
    protected long putCount() {
        return cache.getCachePuts();
    }

    @Override
    protected void bindImplementationSpecificMetrics(MeterRegistry registry) {
    }

}
