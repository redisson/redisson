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
package org.redisson.cache;

/**
 * Created by jribble on 2/20/17.
 */

public class StdCachedValue<K, V> implements CachedValue<K, V> {

    private final K key;
    private final V value;

    private long ttl;
    private long maxIdleTime;

    private long creationTime;
    private long lastAccess;

    public StdCachedValue(K key, V value, long ttl, long maxIdleTime) {
        this.value = value;
        this.ttl = ttl;
        this.key = key;
        this.maxIdleTime = maxIdleTime;
        
        if (ttl != 0 || maxIdleTime != 0) {
            creationTime = System.currentTimeMillis();
            lastAccess = creationTime;
        }
    }

    @Override
    public boolean isExpired() {
        if (maxIdleTime == 0 && ttl == 0) {
            return false;
        }
        long currentTime = System.currentTimeMillis();
        if (ttl != 0 && creationTime + ttl < currentTime) {
            return true;
        }
        if (maxIdleTime != 0 && lastAccess + maxIdleTime < currentTime) {
            return true;
        }
        return false;
    }

    @Override
    public K getKey() {
        return key;
    }

    @Override
    public V getValue() {
        lastAccess = System.currentTimeMillis();
        return value;
    }

    @Override
    public String toString() {
        return "CachedValue [key=" + key + ", value=" + value + "]";
    }

}
