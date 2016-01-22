/**
 * Copyright 2014 Nikita Koksharov, Nickolay Borbit
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
package org.redisson.spring.cache;

import java.util.concurrent.TimeUnit;

public class CacheConfig {

    private long ttl;

    private TimeUnit ttlUnit;

    private long maxIdleTime;

    private TimeUnit maxIdleUnit;

    public CacheConfig() {
    }

    public CacheConfig(long ttl, TimeUnit ttlUnit, long maxIdleTime, TimeUnit maxIdleUnit) {
        super();
        this.ttl = ttl;
        this.ttlUnit = ttlUnit;
        this.maxIdleTime = maxIdleTime;
        this.maxIdleUnit = maxIdleUnit;
    }

    public long getTTL() {
        return ttl;
    }

    public void setTTL(long ttl) {
        this.ttl = ttl;
    }

    public TimeUnit getTTLUnit() {
        return ttlUnit;
    }

    public void setTTLUnit(TimeUnit ttlUnit) {
        this.ttlUnit = ttlUnit;
    }

    public long getMaxIdleTime() {
        return maxIdleTime;
    }

    public void setMaxIdleTime(long maxIdleTime) {
        this.maxIdleTime = maxIdleTime;
    }

    public TimeUnit getMaxIdleUnit() {
        return maxIdleUnit;
    }

    public void setMaxIdleUnit(TimeUnit maxIdleUnit) {
        this.maxIdleUnit = maxIdleUnit;
    }

}
