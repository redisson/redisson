/**
 * Copyright 2014 Nikita Koksharov, Nickolay Borbit
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
package org.redisson.core;

import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * <p>Set-based cache with ability to set TTL for each entry via
 * {@link #put(Object, Object, long, TimeUnit)} method.
 * And therefore has an complex lua-scripts inside.
 * Uses map (value_hash, value) to tie with expiration sorted set under the hood.
 * </p>
 *
 * <p>Current Redis implementation doesn't have set entry eviction functionality.
 * Thus values are checked for TTL expiration during any value read operation.
 * If entry expired then it doesn't returns and clean task runs asynchronous.
 * Clean task deletes removes 100 expired entries at once.
 * In addition there is {@link org.redisson.RedissonEvictionScheduler}. This scheduler
 * deletes expired entries in time interval between 5 seconds to 2 hours.</p>
 *
 * <p>If eviction is not required then it's better to use {@link org.redisson.reactive.RedissonSet}.</p>
 *
 * @author Nikita Koksharov
 *
 * @param <K> key
 * @param <V> value
 */
public interface RSetCache<V> extends Set<V>, RExpirable, RSetCacheAsync<V> {

    boolean add(V value, long ttl, TimeUnit unit);

}
