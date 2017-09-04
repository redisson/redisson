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
package org.redisson;

import java.util.Map.Entry;

import org.redisson.client.protocol.decoder.MapScanResult;
import org.redisson.client.protocol.decoder.ScanObjectEntry;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <K> key type
 * @param <V> value type
 * @param <M> loaded value type
 */
public class RedissonMapIterator<K, V, M> extends RedissonBaseMapIterator<K, V, M> {

    private final RedissonMap<K, V> map;
    private final String pattern;

    public RedissonMapIterator(RedissonMap<K, V> map, String pattern) {
        this.map = map;
        this.pattern = pattern;
    }

    protected MapScanResult<ScanObjectEntry, ScanObjectEntry> iterator() {
        return map.scanIterator(map.getName(), client, nextIterPos, pattern);
    }

    protected void removeKey() {
        map.fastRemove((K)entry.getKey().getObj());
    }

    protected V put(Entry<ScanObjectEntry, ScanObjectEntry> entry, V value) {
        return map.put((K) entry.getKey().getObj(), value);
    }

}
