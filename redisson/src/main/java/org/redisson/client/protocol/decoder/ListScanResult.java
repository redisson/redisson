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

import java.util.List;

import org.redisson.ScanResult;
import org.redisson.client.RedisClient;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <V> value type
 */
public class ListScanResult<V> implements ScanResult<V> {

    private final long pos;
    private final List<V> values;
    private RedisClient client;

    public ListScanResult(long pos, List<V> values) {
        this.pos = pos;
        this.values = values;
    }

    @Override
    public long getPos() {
        return pos;
    }

    @Override
    public List<V> getValues() {
        return values;
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
