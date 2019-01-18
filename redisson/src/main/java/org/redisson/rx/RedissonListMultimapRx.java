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
package org.redisson.rx;

import org.redisson.RedissonList;
import org.redisson.RedissonListMultimap;
import org.redisson.api.RListMultimap;
import org.redisson.api.RListRx;
import org.redisson.client.codec.Codec;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <K> key type
 * @param <V> value type
 */
public class RedissonListMultimapRx<K, V> {

    private CommandRxExecutor commandExecutor;
    private RedissonListMultimap<K, V> instance;
    
    public RedissonListMultimapRx(CommandRxExecutor commandExecutor, String name) {
        this.instance = new RedissonListMultimap<K, V>(commandExecutor, name);
    }

    public RedissonListMultimapRx(Codec codec, CommandRxExecutor commandExecutor, String name) {
        this.instance = new RedissonListMultimap<K, V>(codec, commandExecutor, name);
    }

    public RListRx<V> get(K key) {
        RedissonList<V> list = (RedissonList<V>) ((RListMultimap<K, V>)instance).get(key);
        return RxProxyBuilder.create(commandExecutor, instance, 
                new RedissonListRx<V>(list), RListRx.class);
    }

}
