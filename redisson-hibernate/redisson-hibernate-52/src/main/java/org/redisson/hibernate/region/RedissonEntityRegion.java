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

import java.util.Properties;

import org.hibernate.cache.CacheException;
import org.hibernate.cache.spi.CacheDataDescription;
import org.hibernate.cache.spi.EntityRegion;
import org.hibernate.cache.spi.RegionFactory;
import org.hibernate.cache.spi.access.AccessType;
import org.hibernate.cache.spi.access.EntityRegionAccessStrategy;
import org.hibernate.cfg.Settings;
import org.redisson.api.RMapCache;
import org.redisson.hibernate.strategy.NonStrictReadWriteEntityRegionAccessStrategy;
import org.redisson.hibernate.strategy.ReadOnlyEntityRegionAccessStrategy;
import org.redisson.hibernate.strategy.ReadWriteEntityRegionAccessStrategy;
import org.redisson.hibernate.strategy.TransactionalEntityRegionAccessStrategy;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonEntityRegion extends BaseRegion implements EntityRegion {

    private final Settings settings;
    
    public RedissonEntityRegion(RMapCache<Object, Object> mapCache, RegionFactory regionFactory,
            CacheDataDescription metadata, Settings settings, Properties properties, String defaultKey) {
        super(mapCache, regionFactory, metadata, properties, defaultKey);
        this.settings = settings;
    }

    @Override
    public EntityRegionAccessStrategy buildAccessStrategy(AccessType accessType) throws CacheException {
        if (accessType == AccessType.READ_ONLY) {
            return new ReadOnlyEntityRegionAccessStrategy(settings, this);
        }
        if (accessType == AccessType.READ_WRITE) {
            return new ReadWriteEntityRegionAccessStrategy(settings, this, mapCache);
        }
        if (accessType == AccessType.NONSTRICT_READ_WRITE) {
            return new NonStrictReadWriteEntityRegionAccessStrategy(settings, this);
        }
        if (accessType == AccessType.TRANSACTIONAL) {
            return new TransactionalEntityRegionAccessStrategy(settings, this);
        }
        
        throw new CacheException("Unsupported access strategy: " + accessType);
    }

}
