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
package org.redisson.api;

import org.redisson.api.map.MapsImportArgs;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Reactive interface for mass operations with Map objects.
 *
 * @author Nikita Koksharov
 *
 * @param <K> field type
 * @param <V> value type
 */
public interface RMapsReactive<K, V> {

    /**
     * Stores Map objects mapped by name. Each object replaces
     * the whole Map object stored under the same name.
     *
     * @param maps Map objects mapped by name
     * @return void
     */
    Mono<Void> set(Map<String, Map<K, V>> maps);

    /**
     * Stores Map objects mapped by name. Each object replaces
     * the whole Map object stored under the same name.
     * <p>
     * Objects are written in portions defined by <code>batchSize</code>.
     *
     * @param maps Map objects mapped by name
     * @param batchSize amount of Map objects written per portion
     * @return void
     */
    Mono<Void> set(Map<String, Map<K, V>> maps, int batchSize);

    /**
     * Returns import object for Map objects sharing the field names defined in <code>args</code>.
     *
     * @param args import arguments object
     * @return import object
     */
    RMapsImportReactive<K, V> createImport(MapsImportArgs<K> args);

}
