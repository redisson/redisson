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
package org.redisson.hibernate;

import org.hibernate.cache.CacheException;
import org.hibernate.cache.spi.support.DomainDataStorageAccess;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.redisson.api.RFuture;
import org.redisson.api.RMapCache;
import org.redisson.connection.ConnectionManager;
import org.redisson.connection.ServiceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonStorage implements DomainDataStorageAccess {

    private static final Logger logger = LoggerFactory.getLogger(RedissonStorage.class);

    private final RMapCache<Object, Object> mapCache;

    private final ServiceManager serviceManager;

    int ttl;
    int maxIdle;
    int size;
    boolean fallback;
    volatile boolean fallbackMode;
    
    public RedissonStorage(String regionName, RMapCache<Object, Object> mapCache, ServiceManager serviceManager, Map<String, Object> properties, String defaultKey) {
        super();
        this.mapCache = mapCache;
        this.serviceManager = serviceManager;
        
        String maxEntries = getProperty(properties, mapCache.getName(), regionName, defaultKey, RedissonRegionFactory.MAX_ENTRIES_SUFFIX);
        if (maxEntries != null) {
            size = Integer.valueOf(maxEntries);
            mapCache.setMaxSize(size);
        }
        String timeToLive = getProperty(properties, mapCache.getName(), regionName, defaultKey, RedissonRegionFactory.TTL_SUFFIX);
        if (timeToLive != null) {
            ttl = Integer.valueOf(timeToLive);
        }
        String maxIdleTime = getProperty(properties, mapCache.getName(), regionName, defaultKey, RedissonRegionFactory.MAX_IDLE_SUFFIX);
        if (maxIdleTime != null) {
            maxIdle = Integer.valueOf(maxIdleTime);
        }

        String fallbackValue = (String) properties.getOrDefault(RedissonRegionFactory.FALLBACK, "false");
        fallback = Boolean.valueOf(fallbackValue);
    }

    private String getProperty(Map<String, Object> properties, String name, String regionName, String defaultKey, String suffix) {
        String maxEntries = (String) properties.get(RedissonRegionFactory.CONFIG_PREFIX + name + suffix);
        if (maxEntries != null) {
            return maxEntries;
        }
        maxEntries = (String) properties.get(RedissonRegionFactory.CONFIG_PREFIX + regionName + suffix);
        if (maxEntries != null) {
            return maxEntries;
        }
        return (String) properties.get(RedissonRegionFactory.CONFIG_PREFIX + defaultKey + suffix);
    }

    private void ping() {
        fallbackMode = true;
        serviceManager.newTimeout(t -> {
            RFuture<Boolean> future = mapCache.isExistsAsync();
            future.whenComplete((r, ex) -> {
                if (ex == null) {
                    fallbackMode = false;
                } else {
                    ping();
                }
            });
        }, 1, TimeUnit.SECONDS);
    }

    @Override
    public Object getFromCache(Object key, SharedSessionContractImplementor session) {
        if (fallbackMode) {
            return null;
        }
        try {
            if (maxIdle == 0 && size == 0) {
                return mapCache.getWithTTLOnly(key);
            }

            return mapCache.get(key);
        } catch (Exception e) {
            if (fallback) {
                ping();
                logger.error(e.getMessage(), e);
                return null;
            }
            throw new CacheException(e);
        }
    }

    @Override
    public void putIntoCache(Object key, Object value, SharedSessionContractImplementor session) {
        if (fallbackMode) {
            return;
        }
        try {
            mapCache.fastPut(key, value, ttl, TimeUnit.MILLISECONDS, maxIdle, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            if (fallback) {
                ping();
                logger.error(e.getMessage(), e);
                return;
            }
            throw new CacheException(e);
        }
    }

    @Override
    public boolean contains(Object key) {
        if (fallbackMode) {
            return false;
        }
        try {
            return mapCache.containsKey(key);
        } catch (Exception e) {
            if (fallback) {
                ping();
                logger.error(e.getMessage(), e);
                return false;
            }
            throw new CacheException(e);
        }
    }

    @Override
    public void evictData() {
        if (fallbackMode) {
            return;
        }
        try {
            mapCache.clear();
        } catch (Exception e) {
            if (fallback) {
                ping();
                logger.error(e.getMessage(), e);
                return;
            }
            throw new CacheException(e);
        }
    }

    @Override
    public void evictData(Object key) {
        if (fallbackMode) {
            return;
        }
        try {
            mapCache.fastRemove(key);
        } catch (Exception e) {
            if (fallback) {
                ping();
                logger.error(e.getMessage(), e);
                return;
            }
            throw new CacheException(e);
        }
    }

    @Override
    public void release() {
        try {
            mapCache.destroy();
        } catch (Exception e) {
            throw new CacheException(e);
        }
    }

}
