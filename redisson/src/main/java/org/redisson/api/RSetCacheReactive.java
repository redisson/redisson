/**
 * Copyright 2018 Nikita Koksharov
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

import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.reactivestreams.Publisher;

/**
 * Async set functions
 *
 * @author Nikita Koksharov
 *
 * @param <V> value
 */
public interface RSetCacheReactive<V> extends RCollectionReactive<V> {

    Publisher<Boolean> add(V value, long ttl, TimeUnit unit);

    /**
     * Returns the number of elements in cache.
     * This number can reflects expired elements too
     * due to non realtime cleanup process.
     *
     */
    @Override
    Publisher<Integer> size();

    /**
     * Read all elements at once
     *
     * @return values
     */
    Publisher<Set<V>> readAll();
    
}
