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

import org.hibernate.cache.spi.CollectionRegion;
import org.hibernate.cache.spi.GeneralDataRegion;
import org.hibernate.cache.spi.access.CollectionRegionAccessStrategy;
import org.hibernate.cfg.Settings;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.persister.collection.CollectionPersister;
import org.redisson.api.RMapCache;
import org.redisson.hibernate.region.RedissonCollectionRegion;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class ReadWriteCollectionRegionAccessStrategy extends AbstractReadWriteAccessStrategy implements CollectionRegionAccessStrategy {

    public ReadWriteCollectionRegionAccessStrategy(Settings settings, GeneralDataRegion region,
            RMapCache<Object, Object> mapCache) {
        super(settings, region, mapCache);
    }

    @Override
    public CollectionRegion getRegion() {
        return (CollectionRegion) region;
    }

    @Override
    public Object generateCacheKey(Object id, CollectionPersister persister, SessionFactoryImplementor factory, String tenantIdentifier) {
        return ((RedissonCollectionRegion)region).getCacheKeysFactory().createCollectionKey( id, persister, factory, tenantIdentifier );
    }

    @Override
    public Object getCacheKeyId(Object cacheKey) {
        return ((RedissonCollectionRegion)region).getCacheKeysFactory().getCollectionId(cacheKey);
    }

}
