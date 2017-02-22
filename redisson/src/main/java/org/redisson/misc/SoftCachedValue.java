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
package org.redisson.misc;

import java.lang.ref.SoftReference;

/**
 * Created by jribble on 2/20/17.
 */

public class SoftCachedValue<K, V> implements CachedValue<K, V> {
    StdCachedValue<K, SoftReference<V>> value;

    public SoftCachedValue(K key, V value, long ttl, long maxIdleTime) {
        this.value = new StdCachedValue<K, SoftReference<V>>(key, new SoftReference<V>(value), ttl, maxIdleTime);
    }

    @Override
    public boolean isExpired() {
        return value.isExpired();
    }

    @Override
    public K getKey() {
        return value.getKey();
    }

    @Override
    public V getValue() {
        return value.getValue().get();
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
