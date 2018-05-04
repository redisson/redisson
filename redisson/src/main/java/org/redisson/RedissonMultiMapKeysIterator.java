/**
 * Copyright 2018 Nikita Koksharov
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
import org.redisson.client.protocol.decoder.ScanObjectEntry;

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
    protected Object put(Entry<ScanObjectEntry, ScanObjectEntry> entry, Object value) {
        return map.put(entry.getKey().getObj(), value);
    }

    @Override
    protected ScanResult<Entry<ScanObjectEntry, ScanObjectEntry>> iterator(RedisClient client, long nextIterPos) {
        return map.scanIterator(client, nextIterPos);
    }

    @Override
    protected void remove(Entry<ScanObjectEntry, ScanObjectEntry> value) {
        map.fastRemove(value.getKey().getObj());
    }

}
