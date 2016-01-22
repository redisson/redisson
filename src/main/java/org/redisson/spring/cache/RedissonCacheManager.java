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
import java.util.HashMap;
import java.util.Map;

import org.redisson.RedissonClient;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonCacheManager implements CacheManager {

    private RedissonClient redisson;

    private Map<String, CacheConfig> configMap = new HashMap<String, CacheConfig>();

    public RedissonCacheManager() {
    }

    public RedissonCacheManager(RedissonClient redisson, Map<String, CacheConfig> config) {
        this.redisson = redisson;
        this.configMap = config;
    }

    public void setConfig(Map<String, CacheConfig> config) {
        this.configMap = config;
    }

    public void setRedisson(RedissonClient redisson) {
        this.redisson = redisson;
    }

    @Override
    public Cache getCache(String name) {
        CacheConfig config = configMap.get(name);
        if (config == null) {
            config = new CacheConfig();
            configMap.put(name, config);
            return new RedissonCache(redisson.getMap(name));
        }
        if (config.getMaxIdleTime() == 0 && config.getTTL() == 0) {
            return new RedissonCache(redisson.getMap(name));
        }
        return new RedissonCache(redisson.getMapCache(name), config);
    }

    @Override
    public Collection<String> getCacheNames() {
        return Collections.unmodifiableSet(configMap.keySet());
    }

}
