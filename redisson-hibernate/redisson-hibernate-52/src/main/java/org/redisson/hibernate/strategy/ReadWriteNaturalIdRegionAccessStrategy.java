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
/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * Copyright (c) 2010-2011, Red Hat Inc. or third-party contributors as
 * indicated by the @author tags or express copyright attribution
 * statements applied by the authors.  All third-party contributions are
 * distributed under license by Red Hat Inc.
 *
 * This copyrighted material is made available to anyone wishing to use, modify,
 * copy, or redistribute it subject to the terms and conditions of the GNU
 * Lesser General Public License, as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution; if not, write to:
 * Free Software Foundation, Inc.
 * 51 Franklin Street, Fifth Floor
 * Boston, MA  02110-1301  USA
 */
package org.redisson.hibernate.strategy;

import org.hibernate.cache.CacheException;
import org.hibernate.cache.spi.GeneralDataRegion;
import org.hibernate.cache.spi.NaturalIdRegion;
import org.hibernate.cache.spi.access.NaturalIdRegionAccessStrategy;
import org.hibernate.cache.spi.access.SoftLock;
import org.hibernate.cfg.Settings;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.persister.entity.EntityPersister;
import org.redisson.api.RLock;
import org.redisson.api.RMapCache;
import org.redisson.hibernate.region.RedissonNaturalIdRegion;

/**
 * 
 * @author Nikita Koksharov
 * @author Eric Dalquist
 *
 */
public class ReadWriteNaturalIdRegionAccessStrategy extends AbstractReadWriteAccessStrategy implements NaturalIdRegionAccessStrategy {

    public ReadWriteNaturalIdRegionAccessStrategy(Settings settings, GeneralDataRegion region,
            RMapCache<Object, Object> mapCache) {
        super(settings, region, mapCache);
    }

    @Override
    public NaturalIdRegion getRegion() {
        return (NaturalIdRegion) region;
    }

    @Override
    public boolean insert(SharedSessionContractImplementor session, Object key, Object value) throws CacheException {
        return false;
    }

    @Override
    public boolean afterInsert(SharedSessionContractImplementor session, Object key, Object value) throws CacheException {
        RLock writeLock = mapCache.getReadWriteLock(key).writeLock();
        writeLock.lock();
        try {
            Lockable item = (Lockable) region.get(session, key);
            if (item == null) {
                region.put(session, key, new Item(value, null, region.nextTimestamp()));
                return true;
            } else {
                return false;
            }
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public boolean update(SharedSessionContractImplementor session, Object key, Object value) throws CacheException {
        return false;
    }

    @Override
    public boolean afterUpdate(SharedSessionContractImplementor session, Object key, Object value, SoftLock lock) throws CacheException {
        RLock writeLock = mapCache.getReadWriteLock(key).writeLock();
        writeLock.lock();
        try {
            Lockable item = (Lockable) region.get(session, key);

            if (item != null && item.isUnlockable(lock)) {
                Lock lockItem = (Lock) item;
                if (lockItem.wasLockedConcurrently()) {
                    decrementLock(session, key, lockItem);
                    return false;
                } else {
                    region.put(session, key, new Item(value, null, region.nextTimestamp()));
                    return true;
                }
            } else {
                handleLockExpiry(session, key, item);
                return false;
            }
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public Object generateCacheKey(Object[] naturalIdValues, EntityPersister persister, SharedSessionContractImplementor session) {
        return ((RedissonNaturalIdRegion)region).getCacheKeysFactory().createNaturalIdKey(naturalIdValues, persister, session);
    }

    @Override
    public Object[] getNaturalIdValues(Object cacheKey) {
        return ((RedissonNaturalIdRegion)region).getCacheKeysFactory().getNaturalIdValues(cacheKey);
    }

}
