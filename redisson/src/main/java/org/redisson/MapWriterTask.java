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
package org.redisson;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class MapWriterTask {

    public static class Remove extends MapWriterTask {

        public Remove(Collection<?> keys) {
            super(keys);
        }

        public Remove(Object key) {
            super(key);
        }
        
    }

    public static class Add extends MapWriterTask {

        public Add() {
        }
        
        public Add(Map<?, ?> map) {
            super(map);
        }

        public Add(Object key, Object value) {
            super(key, value);
        }
        
    }
    
    private Collection<?> keys = Collections.emptyList();
    private Map<?, ?> map = Collections.emptyMap();
    
    public MapWriterTask() {
    }
    
    public MapWriterTask(Object key) {
        this.keys = Collections.singletonList(key);
    }
    
    public MapWriterTask(Object key, Object value) {
        this.map = Collections.singletonMap(key, value);
    }
    
    public MapWriterTask(Map<?, ?> map) {
        this.map = map;
    }
    
    public MapWriterTask(Collection<?> keys) {
        this.keys = keys;
    }

    public <V> Collection<V> getKeys() {
        return (Collection<V>) keys;
    }
    
    public <K, V> Map<K, V> getMap() {
        return (Map<K, V>) map;
    }
    
}
