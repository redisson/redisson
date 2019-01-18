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
package org.redisson.transaction.operation.map;

import java.util.concurrent.TimeUnit;

import org.redisson.api.RMap;
import org.redisson.api.RMapCache;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class MapCachePutIfAbsentOperation extends MapOperation {

    private long ttl; 
    private TimeUnit unit;
    private long maxIdleTime;
    private TimeUnit maxIdleUnit;

    public MapCachePutIfAbsentOperation() {
    }
    
    public MapCachePutIfAbsentOperation(RMap<?, ?> map, Object key, Object value, 
            long ttl, TimeUnit unit, long maxIdleTime, TimeUnit maxIdleUnit, String transactionId) {
        this(map, key, value, transactionId);
        this.ttl = ttl;
        this.unit = unit;
        this.maxIdleTime = maxIdleTime;
        this.maxIdleUnit = maxIdleUnit;
    }
    
    public MapCachePutIfAbsentOperation(RMap<?, ?> map, Object key, Object value, String transactionId) {
        super(map, key, value, transactionId);
    }

    @Override
    public void commit(RMap<Object, Object> map) {
        ((RMapCache<Object, Object>)map).putIfAbsentAsync(key, value, ttl, unit, maxIdleTime, maxIdleUnit);
    }
    
    public long getTTL() {
        return ttl;
    }
    
    public TimeUnit getTTLUnit() {
        return unit;
    }
    
    public long getMaxIdleTime() {
        return maxIdleTime;
    }
    
    public TimeUnit getMaxIdleUnit() {
        return maxIdleUnit;
    }
    
}
