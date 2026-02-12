/**
 * Copyright (c) 2013-2026 Nikita Koksharov
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

import org.hibernate.cache.spi.QueryResultsRegion;
import org.hibernate.cache.spi.RegionFactory;
import org.redisson.api.RMapCache;
import org.redisson.connection.ServiceManager;

import java.util.Properties;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonQueryRegion extends BaseRegion implements QueryResultsRegion {

    public RedissonQueryRegion(RMapCache<Object, Object> mapCache, ServiceManager serviceManager,
            RegionFactory regionFactory, Properties properties, String defaultKey) {
        super(mapCache, serviceManager, regionFactory, null, properties, defaultKey);
    }

}
