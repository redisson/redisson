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

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.redisson.RedissonClient;
import org.redisson.core.RLock;
import org.redisson.core.RMap;
import org.redisson.core.RMapCache;
import org.redisson.misc.Hash;
import org.springframework.cache.Cache;
import org.springframework.cache.support.SimpleValueWrapper;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonCache implements Cache {

    private RMapCache<Object, Object> mapCache;

    private final RMap<Object, Object> map;

    private CacheConfig config;
    
    private final RedissonClient redisson;

    public RedissonCache(RedissonClient redisson, RMapCache<Object, Object> mapCache, CacheConfig config) {
        this.mapCache = mapCache;
        this.map = mapCache;
        this.config = config;
        this.redisson = redisson;
    }

    public RedissonCache(RedissonClient redisson, RMap<Object, Object> map) {
        this.map = map;
        this.redisson = redisson;
    }

    @Override
    public String getName() {
        return map.getName();
    }

    @Override
    public RMap<?, ?> getNativeCache() {
        return map;
    }

    @Override
    public ValueWrapper get(Object key) {
        Object value = map.get(key);
        return toValueWrapper(value);
    }

    public <T> T get(Object key, Class<T> type) {
        Object value = map.get(key);
        if (value != null) {
            if (value.getClass().getName().equals(NullValue.class.getName())) {
                return null;
            }
            if (type != null && !type.isInstance(value)) {
                throw new IllegalStateException("Cached value is not of required type [" + type.getName() + "]: " + value);
            }
        }
        return (T) value;
    }

    @Override
    public void put(Object key, Object value) {
        if (mapCache != null) {
            mapCache.fastPut(key, value, config.getTTL(), TimeUnit.MILLISECONDS, config.getMaxIdleTime(), TimeUnit.MILLISECONDS);
        } else {
            map.fastPut(key, value);
        }
    }

    public ValueWrapper putIfAbsent(Object key, Object value) {
        Object prevValue;
        if (mapCache != null) {
            prevValue = mapCache.putIfAbsent(key, value, config.getTTL(), TimeUnit.MILLISECONDS, config.getMaxIdleTime(), TimeUnit.MILLISECONDS);
        } else {
            prevValue = map.putIfAbsent(key, value);
        }
        return toValueWrapper(prevValue);
    }

    @Override
    public void evict(Object key) {
        map.fastRemove(key);
    }

    @Override
    public void clear() {
        map.clear();
    }

    private ValueWrapper toValueWrapper(Object value) {
        if (value == null) {
            return null;
        }
        if (value.getClass().getName().equals(NullValue.class.getName())) {
            return NullValue.INSTANCE;
        }
        return new SimpleValueWrapper(value);
    }

    final Map<Object, Lock> valueLoaderLocks = new ConcurrentHashMap<Object, Lock>();
    
    public Lock getLock(Object key) {
        Lock lock = valueLoaderLocks.get(key);
        if (lock == null) {
            Lock newlock = new ReentrantLock();
            lock = valueLoaderLocks.putIfAbsent(key, newlock);
            if (lock == null) {
                lock = newlock;
            }
        }
        return lock;
    }
    
    public <T> T get(Object key, Callable<T> valueLoader) {
        Object value = map.get(key);
        if (value == null) {
            String lockName = getLockName(key);
            RLock lock = redisson.getLock(lockName);
            lock.lock();
            try {
                value = map.get(key);
                if (value == null) {
                    try {
                        value = toStoreValue(valueLoader.call());
                    } catch (Exception ex) {
                        throw new ValueRetrievalException(key, valueLoader, ex.getCause());                
                    }
                    map.put(key, value);
                }
            } finally {
                lock.unlock();
            }
        }
        
        return (T) fromStoreValue(value);
    }

    private String getLockName(Object key) {
        try {
            byte[] keyState = redisson.getConfig().getCodec().getMapKeyEncoder().encode(key);
            return "{" + map.getName() + "}:" + Hash.hashToBase64(keyState) + ":key";
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    protected Object fromStoreValue(Object storeValue) {
        if (storeValue == NullValue.INSTANCE) {
            return null;
        }
        return storeValue;
    }

    protected Object toStoreValue(Object userValue) {
        if (userValue == null) {
            return NullValue.INSTANCE;
        }
        return userValue;
    }

}
