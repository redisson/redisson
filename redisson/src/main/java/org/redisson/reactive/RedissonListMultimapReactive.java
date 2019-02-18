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
package org.redisson.reactive;

import org.redisson.RedissonListMultimap;
import org.redisson.api.RList;
import org.redisson.api.RListMultimap;
import org.redisson.api.RListReactive;
import org.redisson.client.codec.Codec;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <K> key type
 * @param <V> value type
 */
public class RedissonListMultimapReactive<K, V> {

    private final CommandReactiveExecutor commandExecutor;
    private final RedissonListMultimap<K, V> instance;
    
    public RedissonListMultimapReactive(CommandReactiveExecutor commandExecutor, String name) {
        this.instance = new RedissonListMultimap<K, V>(commandExecutor, name);
        this.commandExecutor = commandExecutor;
    }

    public RedissonListMultimapReactive(Codec codec, CommandReactiveExecutor commandExecutor, String name) {
        this.instance = new RedissonListMultimap<K, V>(codec, commandExecutor, name);
        this.commandExecutor = commandExecutor;
    }

    public RListReactive<V> get(K key) {
        RList<V> list = ((RListMultimap<K, V>) instance).get(key);
        return ReactiveProxyBuilder.create(commandExecutor, instance, 
                new RedissonListReactive<V>(instance.getCodec(), commandExecutor, list.getName()), RListReactive.class);
    }

}
