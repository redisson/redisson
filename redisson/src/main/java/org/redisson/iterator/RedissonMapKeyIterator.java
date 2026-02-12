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
package org.redisson.iterator;

import org.redisson.RedissonMap;
import org.redisson.ScanResult;
import org.redisson.client.RedisClient;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <M> loaded value type
 */
public class RedissonMapKeyIterator<M> extends BaseIterator<M, M> {

    private final RedissonMap map;
    private final String pattern;
    private final int count;

    public RedissonMapKeyIterator(RedissonMap map, String pattern, int count) {
        this.map = map;
        this.pattern = pattern;
        this.count = count;
    }

    @Override
    protected ScanResult<M> iterator(RedisClient client, String nextIterPos) {
        return map.scanKeyIterator(map.getRawName(), client, nextIterPos, pattern, count);
    }

    @Override
    protected M getValue(Object entry) {
        return (M) entry;
    }

    @Override
    protected void remove(Object value) {
        map.fastRemove(value);
    }

}
