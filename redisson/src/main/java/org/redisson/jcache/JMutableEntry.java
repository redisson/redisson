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

import javax.cache.processor.MutableEntry;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <K> key
 * @param <V> value
 */
public class JMutableEntry<K, V> implements MutableEntry<K, V> {

    public enum Action {CREATED, READ, UPDATED, DELETED, LOADED, SKIPPED}
    
    private final JCache<K, V> jCache;
    private final K key;
    private boolean isReadThrough;

    private Action action = Action.SKIPPED;
    private V value;
    private boolean isValueRead;
    
    public JMutableEntry(JCache<K, V> jCache, K key, V value, boolean isReadThrough) {
        super();
        this.jCache = jCache;
        this.key = key;
        this.value = value;
        this.isReadThrough = isReadThrough;
    }

    @Override
    public K getKey() {
        return key;
    }

    public V value() {
        return value;
    }
    
    @Override
    public V getValue() {
        if (action != Action.SKIPPED) {
            return value;
        }
        
        if (!isValueRead) {
            value = jCache.getValueLocked(key);
            isValueRead = true;
        }
        
        if (value != null) {
            action = Action.READ;
        } else if (isReadThrough) {
            value = jCache.load(key);
            if (value != null) {
                action = Action.LOADED;
            }
            isReadThrough = false;
        }
        return value;
    }
    
    @Override
    public <T> T unwrap(Class<T> clazz) {
        return (T) this;
    }

    @Override
    public boolean exists() {
        return getValue() != null;
    }

    @Override
    public void remove() {
        if (action == Action.CREATED) {
            action = Action.SKIPPED;
        } else {
            action = Action.DELETED;
        }
        value = null;
    }

    @Override
    public void setValue(V value) {
        if (value == null) {
            throw new NullPointerException();
        }
        
        if (action != Action.CREATED) {
            if (jCache.containsKey(key)) {
                action = Action.UPDATED;
            } else {
                action = Action.CREATED;
            }
        }
        this.value = value;
    }
    
    public Action getAction() {
        return action;
    }

}
