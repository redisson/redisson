/**
 * Copyright (c) 2013-2026 Nikita Koksharov
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
package org.redisson.api.cuckoofilter;

import java.util.Collection;

/**
 * @param <V> element type
 *
 * @author Nikita Koksharov
 *
 */
public final class CuckooFilterAddArgsImpl<V> implements CuckooFilterAddArgs<V> {

    final Collection<V> items;
    Long capacity;
    boolean noCreate;

    CuckooFilterAddArgsImpl(Collection<V> items) {
        this.items = items;
    }

    @Override
    public CuckooFilterAddArgs<V> capacity(long capacity) {
        this.capacity = capacity;
        return this;
    }

    @Override
    public CuckooFilterAddArgs<V> noCreate() {
        this.noCreate = true;
        return this;
    }

    public Collection<V> getItems() {
        return items;
    }

    public Long getCapacity() {
        return capacity;
    }

    public boolean isNoCreate() {
        return noCreate;
    }
}
