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
package org.redisson.client.protocol.decoder;

import java.net.InetSocketAddress;
import java.util.Map;

import org.redisson.RedisClientResult;

public class MapScanResult<K, V> implements RedisClientResult {

    private final Long pos;
    private final Map<K, V> values;
    private InetSocketAddress client;

    public MapScanResult(Long pos, Map<K, V> values) {
        super();
        this.pos = pos;
        this.values = values;
    }

    public Long getPos() {
        return pos;
    }

    public Map<K, V> getMap() {
        return values;
    }

    @Override
    public void setRedisClient(InetSocketAddress client) {
        this.client = client;
    }

    public InetSocketAddress getRedisClient() {
        return client;
    }

}
