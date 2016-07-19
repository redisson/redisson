/**
 * Copyright 2016 Nikita Koksharov
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

import io.netty.util.concurrent.Future;

/**
 * Async set functions
 *
 * @author Nikita Koksharov
 *
 * @param <V> value
 */
public interface RSetAsync<V> extends RCollectionAsync<V> {

    /**
     * Removes and returns random element from set
     * in async mode
     *
     * @return
     */
    Future<V> removeRandomAsync();

    /**
     * Returns random element from set
     * in async mode
     *
     * @return
     */
    Future<V> randomAsync();

    /**
     * Move a member from this set to the given destination set in async mode.
     *
     * @param destination the destination set
     * @param member the member to move
     * @return true if the element is moved, false if the element is not a
     * member of this set or no operation was performed
     */
    Future<Boolean> moveAsync(String destination, V member);

    /**
     * Read all elements at once
     *
     * @return
     */
    Future<Set<V>> readAllAsync();

    /**
     * Union sets specified by name and write to current set.
     * If current set already exists, it is overwritten.
     *
     * @param names
     * @return
     */
    Future<Integer> unionAsync(String... keys);

    /**
     * Union sets specified by name with current set.
     * Without current set state change.
     *
     * @param names
     * @return
     */
    Future<Set<V>> readUnionAsync(String... keys);

    /**
     * Diff sets specified by name and write to current set.
     * If current set already exists, it is overwritten.
     *
     * @param names
     * @return
     */
    Future<Integer> diffAsync(String... keys);

    /**
     * Diff sets specified by name with current set.
     * Without current set state change.
     *
     * @param names
     * @return
     */
    Future<Set<V>> readDiffAsync(String... keys);

    /**
     * Intersection sets specified by name and write to current set.
     * If current set already exists, it is overwritten.
     *
     * @param names
     * @return
     */
    Future<Integer> intersectionAsync(String... keys);

    /**
     * Intersection sets specified by name with current set.
     * Without current set state change.
     *
     * @param names
     * @return
     */
    Future<Set<V>> readIntersectionAsync(String... keys);
    
}
