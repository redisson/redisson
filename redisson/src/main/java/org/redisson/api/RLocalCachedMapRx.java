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
package org.redisson.api;

import io.reactivex.rxjava3.core.Completable;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * Map object with local entry cache support.
 * <p>
 * Each instance maintains local cache to achieve fast read operations.
 * Suitable for maps which used mostly for read operations and network roundtrip delays are undesirable.
 *
 * @author Nikita Koksharov
 *
 * @param <K> map key
 * @param <V> map value
 */
public interface RLocalCachedMapRx<K, V> extends RMapRx<K, V> {

    /**
     * Clears local cache across all instances
     *
     * @return void
     */
    Completable clearLocalCache();

    /**
     * Returns all keys stored in local cache
     *
     * @return keys
     */
    Set<K> cachedKeySet();

    /**
     * Returns all values stored in local cache
     *
     * @return values
     */
    Collection<V> cachedValues();

    /**
     * Returns all map entries stored in local cache
     *
     * @return entries
     */
    Set<Map.Entry<K, V>> cachedEntrySet();

    /**
     * Returns state of local cache
     *
     * @return map
     */
    Map<K, V> getCachedMap();

}
