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
package org.redisson.client.protocol.decoder;

import org.redisson.ScanResult;
import org.redisson.client.RedisClient;

import java.util.Collection;
import java.util.List;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <K> key type
 */
public class MapCacheKeyScanResult<K> implements ScanResult<K> {

    private final List<K> idleKeys;
    private final String pos;
    private final List<K> keys;
    private RedisClient client;

    public MapCacheKeyScanResult(String pos, List<K> keys, List<K> idleKeys) {
        super();
        this.pos = pos;
        this.keys = keys;
        this.idleKeys = idleKeys;
    }

    @Override
    public Collection<K> getValues() {
        return keys;
    }

    @Override
    public String getPos() {
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

    public List<K> getIdleKeys() {
        return idleKeys;
    }
}
