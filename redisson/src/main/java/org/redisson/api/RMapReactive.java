/**
 * Copyright 2016 Nikita Koksharov
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
package org.redisson.api;

import java.util.Map;
import java.util.Set;

import org.reactivestreams.Publisher;

/**
 *  map functions
 *
 * @author Nikita Koksharov
 *
 * @param <K> key
 * @param <V> value
 */
public interface RMapReactive<K, V> extends RExpirableReactive {

    Publisher<Map<K, V>> getAll(Set<K> keys);

    Publisher<Void> putAll(Map<? extends K, ? extends V> map);

    Publisher<V> addAndGet(K key, Number value);

    Publisher<Boolean> containsValue(Object value);

    Publisher<Boolean> containsKey(Object key);

    Publisher<Integer> size();

    /**
     * Removes <code>keys</code> from map by one operation in  manner
     *
     * Works faster than <code>RMap.remove</code> but not returning
     * the value associated with <code>key</code>
     *
     * @param keys
     * @return the number of keys that were removed from the hash, not including specified but non existing keys
     */
    Publisher<Long> fastRemove(K ... keys);

    /**
     * Associates the specified <code>value</code> with the specified <code>key</code>
     * in  manner.
     *
     * Works faster than <code>RMap.put</code> but not returning
     * the previous value associated with <code>key</code>
     *
     * @param key
     * @param value
     * @return <code>true</code> if key is a new key in the hash and value was set.
     *         <code>false</code> if key already exists in the hash and the value was updated.
     */
    Publisher<Boolean> fastPut(K key, V value);

    Publisher<V> get(K key);

    Publisher<V> put(K key, V value);

    Publisher<V> remove(K key);

    Publisher<V> replace(K key, V value);

    Publisher<Boolean> replace(K key, V oldValue, V newValue);

    Publisher<Long> remove(Object key, Object value);

    Publisher<V> putIfAbsent(K key, V value);

    Publisher<Map.Entry<K, V>> entryIterator();

    Publisher<V> valueIterator();

    Publisher<K> keyIterator();

}
