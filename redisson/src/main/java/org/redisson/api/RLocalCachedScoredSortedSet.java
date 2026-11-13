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

import org.redisson.cache.CachedSortedSetEntry;

import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * Scored sorted set object with local cache support.
 * <p>
 * Each instance maintains a local cache to speed up read operations and reduce
 * Redis round-trips for read-heavy workloads.
 *
 * @param <V> value type
 * @author Nikita Koksharov
 */
public interface RLocalCachedScoredSortedSet<V> extends RScoredSortedSet<V>, RDestroyable {

    /**
     * Reloads local cache state from Redis.
     */
    void preloadCache();

    /**
     * Returns local value-to-score cache.
     *
     * @return cached values mapped to scores
     */
    Map<V, Double> getCache();

    /**
     * Returns local score-to-values cache.
     *
     * @return cached scores mapped to values
     */
    ConcurrentMap<Double, ConcurrentSkipListSet<CachedSortedSetEntry<V>>> getScoreCache();

}

