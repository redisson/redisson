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
package org.redisson.hibernate.strategy;

import org.hibernate.cache.CacheException;
import org.hibernate.cache.spi.GeneralDataRegion;
import org.hibernate.cache.spi.access.RegionAccessStrategy;
import org.hibernate.cache.spi.access.SoftLock;
import org.hibernate.cfg.Settings;

/**
 * 
 * @author Nikita Koksharov
 *
 */
abstract class BaseRegionAccessStrategy implements RegionAccessStrategy {

    final GeneralDataRegion region;
    final Settings settings;
    
    BaseRegionAccessStrategy(Settings settings, GeneralDataRegion region) {
        this.settings = settings;
        this.region = region;
    }

    @Override
    public boolean putFromLoad(Object key, Object value, long txTimestamp, Object version) throws CacheException {
        return putFromLoad( key, value, txTimestamp, version, settings.isMinimalPutsEnabled() );
    }

    @Override
    public SoftLock lockRegion() throws CacheException {
        return null;
    }

    @Override
    public void unlockRegion(SoftLock lock) throws CacheException {
        region.evictAll();
    }

    @Override
    public void remove(Object key) throws CacheException {
    }

    @Override
    public void removeAll() throws CacheException {
        region.evictAll();
    }

    @Override
    public void evict(Object key) throws CacheException {
        region.evict(key);
    }

    @Override
    public void evictAll() throws CacheException {
        region.evictAll();
    }

}
