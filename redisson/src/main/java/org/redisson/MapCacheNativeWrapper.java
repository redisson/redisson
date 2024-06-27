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
package org.redisson;

import org.redisson.api.RMapCacheNative;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

public class MapCacheNativeWrapper<K, V> extends RedissonMapCache<K, V> {

    private final RMapCacheNative<K, V> cache;

    public MapCacheNativeWrapper(RMapCacheNative<K, V> cache, Redisson redisson) {
        super(null, redisson.getCommandExecutor(), "", null, null, null);
        this.cache = cache;
    }

    @Override
    public V getWithTTLOnly(K key) {
        return cache.get(key);
    }

    @Override
    public boolean fastPut(K key, V value, long ttl, TimeUnit ttlUnit, long maxIdleTime, TimeUnit maxIdleUnit) {
        return cache.fastPut(key, value, Duration.ofMillis(ttlUnit.toMillis(ttl)));
    }

    @Override
    public V putIfAbsent(K key, V value, long ttl, TimeUnit ttlUnit, long maxIdleTime, TimeUnit maxIdleUnit) {
        return cache.putIfAbsent(key, value, Duration.ofMillis(ttlUnit.toMillis(ttl)));
    }

    @Override
    public boolean containsKey(Object key) {
        return cache.containsKey(key);
    }

    @Override
    public void clear() {
        cache.clear();
    }

    @Override
    public long fastRemove(K... keys) {
        return cache.fastRemove(keys);
    }

    @Override
    public void destroy() {
        cache.destroy();
    }
}
