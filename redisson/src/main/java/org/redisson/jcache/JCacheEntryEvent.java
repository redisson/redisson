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
package org.redisson.jcache;

import javax.cache.Cache;
import javax.cache.event.CacheEntryEvent;
import javax.cache.event.EventType;

/**
 * Entry event element passed to EventListener of JCache object
 * 
 * @author Nikita Koksharov
 *
 * @param <K> key
 * @param <V> value
 */
public class JCacheEntryEvent<K, V> extends CacheEntryEvent<K, V> {

    private static final long serialVersionUID = -4601376694286796662L;

    private final Object key;
    private final Object value;
    
    public JCacheEntryEvent(Cache<K, V> source, EventType eventType, Object key, Object value) {
        super(source, eventType);
        this.key = key;
        this.value = value;
    }

    @Override
    public K getKey() {
        return (K) key;
    }

    @Override
    public V getValue() {
        return (V) value;
    }

    @Override
    public <T> T unwrap(Class<T> clazz) {
        if (clazz.isAssignableFrom(getClass())) {
            return clazz.cast(this);
        }

        return null;
    }

    @Override
    public V getOldValue() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean isOldValueAvailable() {
        // TODO Auto-generated method stub
        return false;
    }

}
