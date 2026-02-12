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

/**
 * Map object with entry expiration and local cache support.
 * <p>
 * Each instance maintains local cache to achieve fast read operations.
 * Suitable for maps which used mostly for read operations and network roundtrip delays are undesirable.
 * 
 * @author Nikita Koksharov
 *
 * @param <K> map key
 * @param <V> map value
 */
public interface RLocalCachedMapCache<K, V> extends RMapCache<K, V>, RLocalCachedMap<K, V> {

    /**
     * Pre-warm the cached values.  Not guaranteed to load ALL values, but statistically
     * will preload approximately all (all if no concurrent mutating activity)
     * Intended for use with no-eviction caches where entire maps are locally cached
     */
    void preloadCache();

}
