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

import java.util.AbstractMap;
import java.util.Map;
import java.util.Map.Entry;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <V> value type
 */
public abstract class RedissonBaseMapIterator<V> extends BaseIterator<V, Map.Entry<Object, Object>> {

    @SuppressWarnings("unchecked")
    protected V getValue(Map.Entry<Object, Object> entry) {
        return (V) new AbstractMap.SimpleEntry(entry.getKey(), entry.getValue()) {

            @Override
            public Object setValue(Object value) {
                return put(entry, value);
            }

        };
    }

    protected abstract Object put(Entry<Object, Object> entry, Object value);

}
