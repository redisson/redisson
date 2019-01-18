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
package org.redisson.api.map.event;

import org.redisson.api.RMapCache;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <K> key type
 * @param <V> value type
 */
public class EntryEvent<K, V> {

    public enum Type {CREATED, UPDATED, REMOVED, EXPIRED}
    
    private RMapCache<K, V> source;
    private Type type;
    private K key;
    private V value;
    private V oldValue;
    
    public EntryEvent(RMapCache<K, V> source, Type type, K key, V value, V oldValue) {
        super();
        this.source = source;
        this.type = type;
        this.key = key;
        this.value = value;
        this.oldValue = oldValue;
    }
    
    public RMapCache<K, V> getSource() {
        return source;
    }

    public Type getType() {
        return type;
    }
    
    public K getKey() {
        return key;
    }
    
    public V getOldValue() {
        return oldValue;
    }
    
    public V getValue() {
        return value;
    }
    
}
