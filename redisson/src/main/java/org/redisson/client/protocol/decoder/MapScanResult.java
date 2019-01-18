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
package org.redisson.client.protocol.decoder;

import java.util.Collection;
import java.util.Map;

import org.redisson.ScanResult;
import org.redisson.client.RedisClient;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <K> key type
 * @param <V> value type
 */
public class MapScanResult<K, V> implements ScanResult<Map.Entry<K, V>> {

    private final long pos;
    private final Map<K, V> values;
    private RedisClient client;

    public MapScanResult(long pos, Map<K, V> values) {
        super();
        this.pos = pos;
        this.values = values;
    }

    @Override
    public Collection<Map.Entry<K, V>> getValues() {
        return values.entrySet();
    }
    
    public Map<K, V> getMap() {
        return values;
    }
    
    @Override
    public long getPos() {
        return pos;
    }

    @Override
    public void setRedisClient(RedisClient client) {
        this.client = client;
    }

    @Override
    public RedisClient getRedisClient() {
        return client;
    }

}
