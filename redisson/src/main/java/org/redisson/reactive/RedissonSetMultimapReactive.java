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
import org.redisson.api.RSet;
import org.redisson.api.RSetMultimap;
import org.redisson.api.RSetReactive;
import org.redisson.api.RedissonReactiveClient;
import org.redisson.client.codec.Codec;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <K> key type
 * @param <V> value type
 */
public class RedissonSetMultimapReactive<K, V> {

    private final RedissonReactiveClient redisson;
    private final CommandReactiveExecutor commandExecutor;
    private final RedissonListMultimap<K, V> instance;
    
    public RedissonSetMultimapReactive(CommandReactiveExecutor commandExecutor, String name, RedissonReactiveClient redisson) {
        this.instance = new RedissonListMultimap<K, V>(commandExecutor, name);
        this.redisson = redisson;
        this.commandExecutor = commandExecutor;
    }

    public RedissonSetMultimapReactive(Codec codec, CommandReactiveExecutor commandExecutor, String name, RedissonReactiveClient redisson) {
        this.instance = new RedissonListMultimap<K, V>(codec, commandExecutor, name);
        this.redisson = redisson;
        this.commandExecutor = commandExecutor;
    }

    public RSetReactive<V> get(K key) {
        RSet<V> set = ((RSetMultimap<K, V>)instance).get(key);
        return ReactiveProxyBuilder.create(commandExecutor, set, 
                new RedissonSetReactive<V>(set, redisson), RSetReactive.class);
    }

            }
