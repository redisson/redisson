/**
 * Copyright (c) 2013-2024 Nikita Koksharov
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

import org.redisson.RedissonSet;
import org.redisson.api.RSetMultimap;
import org.redisson.api.RSetReactive;
import org.redisson.api.RedissonReactiveClient;

/**
 *
 * @author Nikita Koksharov
 *
 * @param <K> key type
 * @param <V> value type
 */
public class RedissonSetMultimapCacheReactive<K, V> {

    private final RSetMultimap<K, V> instance;
    private final CommandReactiveExecutor commandExecutor;
    private final RedissonReactiveClient redisson;

    public RedissonSetMultimapCacheReactive(RSetMultimap<K, V> instance, CommandReactiveExecutor commandExecutor,
                                            RedissonReactiveClient redisson) {
        this.instance = instance;
        this.redisson = redisson;
        this.commandExecutor = commandExecutor;
    }

    public RSetReactive<V> get(K key) {
        RedissonSet<V> set = (RedissonSet<V>) instance.get(key);
        return ReactiveProxyBuilder.create(commandExecutor, set, new RedissonSetReactive<>(set, redisson), RSetReactive.class);
    }
}
