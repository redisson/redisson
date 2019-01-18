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

import java.util.Map.Entry;

import org.redisson.client.RedisClient;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <V> value type
 */
public class RedissonMultiMapKeysIterator<V> extends RedissonBaseMapIterator<V> {

    private final RedissonMultimap map;

    public RedissonMultiMapKeysIterator(RedissonMultimap map) {
        this.map = map;
    }
    @Override
    protected Object put(Entry<Object, Object> entry, Object value) {
        return map.put(entry.getKey(), value);
    }

    @Override
    protected ScanResult<Entry<Object, Object>> iterator(RedisClient client, long nextIterPos) {
        return map.scanIterator(client, nextIterPos);
    }

    @Override
    protected void remove(Entry<Object, Object> value) {
        map.fastRemove(value.getKey());
    }

}
