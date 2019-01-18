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

import java.util.Iterator;

import org.redisson.client.codec.Codec;
import org.redisson.command.CommandAsyncExecutor;

public class RedissonListMultimapIterator<K, V, M> extends RedissonMultiMapIterator<K, V, M> {

    public RedissonListMultimapIterator(RedissonMultimap<K, V> map, CommandAsyncExecutor commandExecutor, Codec codec) {
        super(map, commandExecutor, codec);
    }

    @Override
    protected Iterator<V> getIterator(String name) {
        RedissonList<V> set = new RedissonList<V>(codec, commandExecutor, map.getValuesName(name), null);
        return set.iterator();
    }

}
