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

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;

/**
 * Created by jribble on 2/20/17.
 */

public class ReferenceCachedValue<K, V> extends StdCachedValue<K, V> implements CachedValue<K, V> {
    
    public enum Type {SOFT, WEAK}
    
    private final Reference<V> ref;

    public ReferenceCachedValue(K key, V value, long ttl, long maxIdleTime, ReferenceQueue<V> queue, Type type) {
        super(key, null, ttl, maxIdleTime);
        if (type == Type.SOFT) {
            this.ref = new CachedValueSoftReference<V>(this, value, queue);
        } else {
            this.ref = new CachedValueWeakReference<V>(this, value, queue);
        }
    }

    @Override
    public V getValue() {
        super.getValue();
        return ref.get();
    }

}
