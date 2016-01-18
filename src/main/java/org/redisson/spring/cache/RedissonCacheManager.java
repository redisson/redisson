/**
 * Copyright 2014 Nikita Koksharov, Nickolay Borbit
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

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.redisson.RedissonClient;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonCacheManager implements CacheManager {

    private final Set<String> cacheNames = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

    private RedissonClient redisson;

    public RedissonCacheManager() {
    }

    public RedissonCacheManager(RedissonClient redisson) {
        this.redisson = redisson;
    }

    public void setRedisson(RedissonClient redisson) {
        this.redisson = redisson;
    }

    @Override
    public Cache getCache(String name) {
        cacheNames.add(name);
        return new RedissonCache(redisson.getMap(name));
    }

    @Override
    public Collection<String> getCacheNames() {
        return Collections.unmodifiableSet(cacheNames);
    }

}
