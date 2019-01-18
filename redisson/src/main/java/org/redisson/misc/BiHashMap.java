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
package org.redisson.misc;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * This is not a concurrent map.
 * 
 * @author Rui Gu (https://github.com/jackygurui)
 * @param <K> key
 * @param <V> value
 */
public class BiHashMap<K, V> implements Map<K, V> {

    private Map<K, V> keyValueMap = new HashMap<K, V>();
    private Map<V, K> valueKeyMap = new HashMap<V, K>();
    
    @Override
    public int size() {
        return keyValueMap.size();
    }

    @Override
    public boolean isEmpty() {
        return keyValueMap.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return keyValueMap.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return valueKeyMap.containsKey(value);
    }

    @Override
    public V get(Object key) {
        return keyValueMap.get(key);
    }
    
    public K reverseGet(Object key) {
        return valueKeyMap.get(key);
    }

    @Override
    public V put(K key, V value) {
        if (keyValueMap.containsKey(key)) {
            valueKeyMap.remove(keyValueMap.get(key));
        }
        valueKeyMap.put(value, key);
        return keyValueMap.put(key, value);
    }

    @Override
    public V remove(Object key) {
        V removed = keyValueMap.remove(key);
        if (removed != null) {
            valueKeyMap.remove(removed);
        }
        return removed;
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        for (Entry<? extends K, ? extends V> entry : m.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public void clear() {
        keyValueMap.clear();
        valueKeyMap.clear();
    }

    @Override
    public Set<K> keySet() {
        return keyValueMap.keySet();
    }
    
    public Set<V> valueSet() {
        return valueKeyMap.keySet();
    }

    @Override
    public Collection<V> values() {
        return keyValueMap.values();
    }
    
    public Collection<K> keys() {
        return valueKeyMap.values();
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        return keyValueMap.entrySet();
    }
    
    public Set<Entry<V, K>> reverseEntrySet() {
        return valueKeyMap.entrySet();
    }
    
    public void makeImmutable() {
        keyValueMap = Collections.unmodifiableMap(keyValueMap);
        valueKeyMap = Collections.unmodifiableMap(valueKeyMap);
    }
}
