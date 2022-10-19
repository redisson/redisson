/**
 * Copyright (c) 2013-2022 Nikita Koksharov
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

import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <V> value type
 */
public class CachedValueSoftReference<V> extends SoftReference<V> implements CachedValueReference {

    private final CachedValue<?, ?> owner;
    
    public CachedValueSoftReference(CachedValue<?, ?> owner, V referent, ReferenceQueue<? super V> q) {
        super(referent, q);
        this.owner = owner;
    }
    
    @Override
    public CachedValue<?, ?> getOwner() {
        return owner;
    }

}
