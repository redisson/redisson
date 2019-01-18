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
package org.redisson.hibernate.region;

import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.hibernate.cache.CacheException;
import org.hibernate.cache.spi.CacheDataDescription;
import org.hibernate.cache.spi.GeneralDataRegion;
import org.hibernate.cache.spi.RegionFactory;
import org.hibernate.cache.spi.TransactionalDataRegion;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.redisson.api.RMapCache;
import org.redisson.hibernate.RedissonRegionFactory;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class BaseRegion implements TransactionalDataRegion, GeneralDataRegion {

    final RMapCache<Object, Object> mapCache;
    final RegionFactory regionFactory;
    final CacheDataDescription metadata;
    
    int ttl;
    int maxIdle;
    
    public BaseRegion(RMapCache<Object, Object> mapCache, RegionFactory regionFactory, CacheDataDescription metadata, Properties properties, String defaultKey) {
        super();
        this.mapCache = mapCache;
        this.regionFactory = regionFactory;
        this.metadata = metadata;
        
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

    private String getProperty(Properties properties, String name, String defaultKey, String suffix) {
        String value = properties.getProperty(RedissonRegionFactory.CONFIG_PREFIX + name + suffix);
        if (value != null) {
            return value;
        }
        String defValue = properties.getProperty(RedissonRegionFactory.CONFIG_PREFIX + defaultKey + suffix);
        if (defValue != null) {
            return defValue;
        }
        return null;
    }

    @Override
    public boolean isTransactionAware() {
        // TODO Auto-generated method stub
        return false;
    }
    
    @Override
    public CacheDataDescription getCacheDataDescription() {
        return metadata;
    }

    @Override
    public String getName() {
        return mapCache.getName();
    }

    @Override
    public void destroy() throws CacheException {
        try {
            mapCache.destroy();
        } catch (Exception e) {
            throw new CacheException(e);
        }
    }

    @Override
    public boolean contains(Object key) {
        try {
            return mapCache.containsKey(key);
        } catch (Exception e) {
            throw new CacheException(e);
        }
    }

    @Override
    public long getSizeInMemory() {
        return mapCache.sizeInMemory();
    }

    @Override
    public long getElementCountInMemory() {
        return mapCache.size();
    }

    @Override
    public long getElementCountOnDisk() {
        return -1;
    }

    @Override
    public Map<?, ?> toMap() {
        return Collections.unmodifiableMap(mapCache);
    }

    @Override
    public long nextTimestamp() {
        return regionFactory.nextTimestamp();
    }

    @Override
    public int getTimeout() {
        // 60 seconds (normalized value)
        return (1 << 12) * 60000;
    }

    @Override
    public Object get(SharedSessionContractImplementor session, Object key) throws CacheException {
        try {
            return mapCache.get(key);
        } catch (Exception e) {
            throw new CacheException(e);
        }
    }

    @Override
    public void put(SharedSessionContractImplementor session, Object key, Object value) throws CacheException {
        try {
            mapCache.fastPut(key, value, ttl, TimeUnit.MILLISECONDS, maxIdle, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            throw new CacheException(e);
        }
    }

    @Override
    public void evict(Object key) throws CacheException {
        try {
            mapCache.fastRemove(key);
        } catch (Exception e) {
            throw new CacheException(e);
        }
    }

    @Override
    public void evictAll() throws CacheException {
        try {
            mapCache.clear();
        } catch (Exception e) {
            throw new CacheException(e);
        }
    }

}
