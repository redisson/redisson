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
package org.redisson.hibernate;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.hibernate.cache.spi.support.DomainDataStorageAccess;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.redisson.api.RMapCache;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonStorage implements DomainDataStorageAccess {

    private final RMapCache<Object, Object> mapCache;
    
    int ttl;
    int maxIdle;
    
    public RedissonStorage(RMapCache<Object, Object> mapCache, Map<String, Object> properties, String defaultKey) {
        super();
        this.mapCache = mapCache;
        
        String maxEntries = getProperty(properties, mapCache.getName(), defaultKey, RedissonRegionFactory.MAX_ENTRIES_SUFFIX);
        if (maxEntries != null) {
            mapCache.setMaxSize(Integer.valueOf(maxEntries));
        }
        String timeToLive = getProperty(properties, mapCache.getName(), defaultKey, RedissonRegionFactory.TTL_SUFFIX);
        if (timeToLive != null) {
            ttl = Integer.valueOf(timeToLive);
        }
        String maxIdleTime = getProperty(properties, mapCache.getName(), defaultKey, RedissonRegionFactory.MAX_IDLE_SUFFIX);
        if (maxIdleTime != null) {
            maxIdle = Integer.valueOf(maxIdleTime);
        }
    }

    private String getProperty(Map<String, Object> properties, String name, String defaultKey, String suffix) {
        String maxEntries = (String) properties.get(RedissonRegionFactory.CONFIG_PREFIX + name + suffix);
        if (maxEntries != null) {
            return maxEntries;
        }
        String defValue = (String) properties.get(RedissonRegionFactory.CONFIG_PREFIX + defaultKey + suffix);
        if (defValue != null) {
            return defValue;
        }
        return null;
    }

    @Override
    public Object getFromCache(Object key, SharedSessionContractImplementor session) {
        return mapCache.get(key);
    }

    @Override
    public void putIntoCache(Object key, Object value, SharedSessionContractImplementor session) {
        mapCache.fastPut(key, value, ttl, TimeUnit.MILLISECONDS, maxIdle, TimeUnit.MILLISECONDS);
    }

    @Override
    public boolean contains(Object key) {
        return mapCache.containsKey(key);
    }

    @Override
    public void evictData() {
        mapCache.clear();
    }

    @Override
    public void evictData(Object key) {
        mapCache.fastRemove(key);
    }

    @Override
    public void release() {
        mapCache.destroy();
    }

}
