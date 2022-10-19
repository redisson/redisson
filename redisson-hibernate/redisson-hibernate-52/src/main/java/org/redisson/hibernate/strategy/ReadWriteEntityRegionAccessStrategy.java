/**
 * Copyright (c) 2013-2022 Nikita Koksharov
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
import org.hibernate.cache.spi.EntityRegion;
import org.hibernate.cache.spi.GeneralDataRegion;
import org.hibernate.cache.spi.access.EntityRegionAccessStrategy;
import org.hibernate.cache.spi.access.SoftLock;
import org.hibernate.cfg.Settings;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.persister.entity.EntityPersister;
import org.redisson.api.RMapCache;
import org.redisson.hibernate.region.RedissonEntityRegion;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class ReadWriteEntityRegionAccessStrategy extends AbstractReadWriteAccessStrategy implements EntityRegionAccessStrategy {

    public ReadWriteEntityRegionAccessStrategy(Settings settings, GeneralDataRegion region, RMapCache<Object, Object> mapCache) {
        super(settings, region, mapCache);
    }

    @Override
    public EntityRegion getRegion() {
        return (EntityRegion) region;
    }

    @Override
    public boolean insert(SharedSessionContractImplementor session, Object key, Object value, Object version) throws CacheException {
        return false;
    }

    @Override
    public boolean afterInsert(SharedSessionContractImplementor session, Object key, Object value, Object version) throws CacheException {
        region.put(session, key, value);
        return true;
    }

    @Override
    public boolean update(SharedSessionContractImplementor session, Object key, Object value, Object currentVersion, Object previousVersion)
            throws CacheException {
        return false;
    }

    @Override
    public boolean afterUpdate(SharedSessionContractImplementor session, Object key, Object value, Object currentVersion, Object previousVersion, SoftLock lock)
            throws CacheException {
        region.put(session, key, value);
        return true;
    }

    @Override
    public Object generateCacheKey(Object id, EntityPersister persister, SessionFactoryImplementor factory, String tenantIdentifier) {
        return ((RedissonEntityRegion)region).getCacheKeysFactory().createEntityKey(id, persister, factory, tenantIdentifier);
    }

    @Override
    public Object getCacheKeyId(Object cacheKey) {
        return ((RedissonEntityRegion)region).getCacheKeysFactory().getEntityId(cacheKey);
    }

}
