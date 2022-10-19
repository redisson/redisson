/**
 * Copyright (c) 2013-2022 Nikita Koksharov
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

import org.redisson.RedissonList;
import org.redisson.RedissonListMultimapCache;
import org.redisson.api.RListReactive;

/**
 *
 * @author Nikita Koksharov
 *
 * @param <K> key type
 * @param <V> value type
 */
public class RedissonListMultimapCacheReactive<K, V> {

    private final RedissonListMultimapCache<K, V> instance;
    private final CommandReactiveExecutor commandExecutor;

    public RedissonListMultimapCacheReactive(RedissonListMultimapCache<K, V> instance, CommandReactiveExecutor commandExecutor) {
        this.instance = instance;
        this.commandExecutor = commandExecutor;
    }

    public RListReactive<V> get(K key) {
        RedissonList<V> list = (RedissonList<V>) instance.get(key);
        return ReactiveProxyBuilder.create(commandExecutor, list, new RedissonListReactive<>(list), RListReactive.class);
    }
}
