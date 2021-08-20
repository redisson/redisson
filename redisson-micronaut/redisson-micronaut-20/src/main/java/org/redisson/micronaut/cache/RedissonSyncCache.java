/**
 * Copyright (c) 2013-2021 Nikita Koksharov
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
package org.redisson.micronaut.cache;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.cache.AbstractMapBasedSyncCache;
import io.micronaut.cache.AsyncCache;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.util.ArgumentUtils;
import org.redisson.api.RMap;
import org.redisson.api.RMapCache;

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonSyncCache extends AbstractMapBasedSyncCache<RMap<Object, Object>> {

    private final ConversionService<?> conversionService;
    private final ExecutorService executorService;
    private final RedissonCacheConfiguration configuration;
    private final RMapCache<Object, Object> mapCache;
    private final RMap<Object, Object> map;

    public RedissonSyncCache(ConversionService<?> conversionService,
                             RMapCache<Object, Object> mapCache,
                             RMap<Object, Object> map,
                             ExecutorService executorService,
                             RedissonCacheConfiguration configuration) {
        super(conversionService, map);
        this.executorService = executorService;
        this.configuration = configuration;
        this.mapCache = mapCache;
        this.map = map;
        this.conversionService = conversionService;
        if (configuration.getMaxSize() != 0) {
            mapCache.setMaxSize(configuration.getMaxSize());
        }
    }

    @Override
    public String getName() {
        return getNativeCache().getName();
    }

    @NonNull
    @Override
    public <T> Optional<T> putIfAbsent(@NonNull Object key, @NonNull T value) {
        ArgumentUtils.requireNonNull("key", key);
        ArgumentUtils.requireNonNull("value", value);
        T res;
        if (mapCache != null) {
            res = (T) mapCache.putIfAbsent(key, value, configuration.getExpireAfterWrite().toMillis(), TimeUnit.MILLISECONDS,
                    configuration.getExpireAfterAccess().toMillis(), TimeUnit.MILLISECONDS);
        } else {
            res = (T) mapCache.putIfAbsent(key, value);
        }
        return Optional.ofNullable(res);
    }

    @NonNull
    @Override
    public <T> T putIfAbsent(@NonNull Object key, @NonNull Supplier<T> value) {
        ArgumentUtils.requireNonNull("key", key);
        ArgumentUtils.requireNonNull("value", value);
        T val = value.get();
        T res;
        if (mapCache != null) {
            res = (T) mapCache.putIfAbsent(key, val, configuration.getExpireAfterWrite().toMillis(), TimeUnit.MILLISECONDS,
                    configuration.getExpireAfterAccess().toMillis(), TimeUnit.MILLISECONDS);
        } else {
            res = (T) mapCache.putIfAbsent(key, value);
        }
        return Optional.ofNullable(res).orElse(val);
    }

    @Override
    public void put(@NonNull Object key, @NonNull Object value) {
        ArgumentUtils.requireNonNull("key", key);
        ArgumentUtils.requireNonNull("value", value);
        if (mapCache != null) {
            mapCache.fastPut(key, value, configuration.getExpireAfterWrite().toMillis(), TimeUnit.MILLISECONDS,
                    configuration.getExpireAfterAccess().toMillis(), TimeUnit.MILLISECONDS);
        } else {
            mapCache.fastPut(key, value);
        }
    }

    @NonNull
    @Override
    public AsyncCache<RMap<Object, Object>> async() {
        return new RedissonAsyncCache(mapCache, map, executorService, conversionService);
    }
}
