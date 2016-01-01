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

import org.redisson.core.RMap;
import org.springframework.cache.Cache;
import org.springframework.cache.support.SimpleValueWrapper;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonCache implements Cache {

    private final RMap<Object, Object> map;

    public RedissonCache(RMap<Object, Object> map) {
        this.map = map;
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
        map.fastPut(key, value);
    }

    public ValueWrapper putIfAbsent(Object key, Object value) {
        Object prevValue = map.putIfAbsent(key, value);
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

}
