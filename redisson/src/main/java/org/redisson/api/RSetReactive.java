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

import org.reactivestreams.Publisher;

/**
 * Async set functions
 *
 * @author Nikita Koksharov
 *
 * @param <V> value
 */
public interface RSetReactive<V> extends RCollectionReactive<V>, RSortableReactive<Set<V>> {

    /**
     * Returns an iterator over elements in this set.
     * Elements are loaded in batch. Batch size is defined by <code>count</code> param. 
     * 
     * @param count - size of elements batch
     * @return iterator
     */
    Publisher<V> iterator(int count);
    
    /**
     * Returns an iterator over elements in this set.
     * Elements are loaded in batch. Batch size is defined by <code>count</code> param.
     * If pattern is not null then only elements match this pattern are loaded.
     * 
     * @param pattern - search pattern
     * @param count - size of elements batch
     * @return iterator
     */
    Publisher<V> iterator(String pattern, int count);
    
    /**
     * Returns iterator over elements in this set matches <code>pattern</code>. 
     * 
     * @param pattern - search pattern
     * @return iterator
     */
    Publisher<V> iterator(String pattern);
    
    /**
     * Removes and returns random elements from set
     * in async mode
     * 
     * @param amount of random values
     * @return random values
     */
    Publisher<Set<V>> removeRandom(int amount);
    
    /**
     * Removes and returns random element from set
     * in async mode
     *
     * @return value
     */
    Publisher<V> removeRandom();

    /**
     * Returns random element from set
     * in async mode
     *
     * @return value
     */
    Publisher<V> random();

    /**
     * Move a member from this set to the given destination set in async mode.
     *
     * @param destination the destination set
     * @param member the member to move
     * @return true if the element is moved, false if the element is not a
     * member of this set or no operation was performed
     */
    Publisher<Boolean> move(String destination, V member);

    /**
     * Read all elements at once
     *
     * @return values
     */
    Publisher<Set<V>> readAll();
    
    /**
     * Union sets specified by name and write to current set.
     * If current set already exists, it is overwritten.
     *
     * @param names - name of sets
     * @return size of union
     */
    Publisher<Long> union(String... names);

    /**
     * Union sets specified by name with current set.
     * Without current set state change.
     *
     * @param names - name of sets
     * @return size of union
     */
    Publisher<Set<V>> readUnion(String... names);
    
    /**
     * Diff sets specified by name and write to current set.
     * If current set already exists, it is overwritten.
     *
     * @param names - name of sets
     * @return size of diff
     */
    Publisher<Long> diff(String... names);
    
    /**
     * Diff sets specified by name with current set.
     * Without current set state change.
     * 
     * @param names - name of sets
     * @return values
     */
    Publisher<Set<V>> readDiff(String... names);
    
    /**
     * Intersection sets specified by name and write to current set.
     * If current set already exists, it is overwritten.
     *
     * @param names - name of sets
     * @return size of intersection
     */
    Publisher<Long> intersection(String... names);

    /**
     * Intersection sets specified by name with current set.
     * Without current set state change.
     *
     * @param names - name of sets
     * @return values
     */
    Publisher<Set<V>> readIntersection(String... names);

}
