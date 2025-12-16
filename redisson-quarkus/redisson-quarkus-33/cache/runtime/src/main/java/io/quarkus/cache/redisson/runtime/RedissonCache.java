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
package io.quarkus.cache.redisson.runtime;

import io.quarkus.cache.Cache;
import io.smallrye.mutiny.Uni;

/**
 *
 * @author Nikita Koksharov
 *
 */
public interface RedissonCache extends Cache {

    <K, V> Uni<V> put(K key, V value);

    <K, V> Uni<V> putIfAbsent(K key, V value);

    <K, V> Uni<V> putIfExists(K key, V value);

    <K, V> Uni<Boolean> fastPut(K key, V value);

    <K, V> Uni<Boolean> fastPutIfAbsent(K key, V value);

    <K, V> Uni<Boolean> fastPutIfExists(K key, V value);

    <K, V> Uni<V> getOrDefault(K key, V defaultValue);

}
