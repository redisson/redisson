/**
 * Copyright (c) 2013-2019 Nikita Koksharov
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

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive interface for RSet object
 *
 * @author Nikita Koksharov
 *
 * @param <V> value
 */
public interface RSetReactive<V> extends RCollectionReactive<V>, RSortableReactive<Set<V>> {

    /**
     * Returns <code>RPermitExpirableSemaphore</code> instance associated with <code>value</code>
     * 
     * @param value - set value
     * @return RPermitExpirableSemaphore object
     */
    RPermitExpirableSemaphoreReactive getPermitExpirableSemaphore(V value);

    /**
     * Returns <code>RSemaphore</code> instance associated with <code>value</code>
     * 
     * @param value - set value
     * @return RSemaphore object
     */
    RSemaphoreReactive getSemaphore(V value);
    
    /**
     * Returns <code>RLock</code> instance associated with <code>value</code>
     * 
     * @param value - set value
     * @return RLock object
     */
    RLockReactive getFairLock(V value);
    
    /**
     * Returns <code>RReadWriteLock</code> instance associated with <code>value</code>
     * 
     * @param value - set value
     * @return RReadWriteLock object
     */
    RReadWriteLockReactive getReadWriteLock(V value);
    
    /**
     * Returns lock instance associated with <code>value</code>
     * 
     * @param value - set value
     * @return RLock object
     */
    RLockReactive getLock(V value);
    
    /**
     * Returns an iterator over elements in this set.
     * Elements are loaded in batch. Batch size is defined by <code>count</code> param. 
     * 
     * @param count - size of elements batch
     * @return iterator
     */
    Flux<V> iterator(int count);
    
    /**
     * Returns an iterator over elements in this set.
     * Elements are loaded in batch. Batch size is defined by <code>count</code> param.
     * If pattern is not null then only elements match this pattern are loaded.
     * 
     * @param pattern - search pattern
     * @param count - size of elements batch
     * @return iterator
     */
    Flux<V> iterator(String pattern, int count);
    
    /**
     * Returns iterator over elements in this set matches <code>pattern</code>. 
     * 
     * @param pattern - search pattern
     * @return iterator
     */
    Flux<V> iterator(String pattern);
    
    /**
     * Removes and returns random elements from set
     * in async mode
     * 
     * @param amount of random values
     * @return random values
     */
    Mono<Set<V>> removeRandom(int amount);
    
    /**
     * Removes and returns random element from set
     * in async mode
     *
     * @return value
     */
    Mono<V> removeRandom();

    /**
     * Returns random element from set
     * in async mode
     *
     * @return value
     */
    Mono<V> random();

    /**
     * Move a member from this set to the given destination set in async mode.
     *
     * @param destination the destination set
     * @param member the member to move
     * @return true if the element is moved, false if the element is not a
     * member of this set or no operation was performed
     */
    Mono<Boolean> move(String destination, V member);

    /**
     * Read all elements at once
     *
     * @return values
     */
    Mono<Set<V>> readAll();
    
    /**
     * Union sets specified by name and write to current set.
     * If current set already exists, it is overwritten.
     *
     * @param names - name of sets
     * @return size of union
     */
    Mono<Long> union(String... names);

    /**
     * Union sets specified by name with current set.
     * Without current set state change.
     *
     * @param names - name of sets
     * @return size of union
     */
    Mono<Set<V>> readUnion(String... names);
    
    /**
     * Diff sets specified by name and write to current set.
     * If current set already exists, it is overwritten.
     *
     * @param names - name of sets
     * @return size of diff
     */
    Mono<Long> diff(String... names);
    
    /**
     * Diff sets specified by name with current set.
     * Without current set state change.
     * 
     * @param names - name of sets
     * @return values
     */
    Mono<Set<V>> readDiff(String... names);
    
    /**
     * Intersection sets specified by name and write to current set.
     * If current set already exists, it is overwritten.
     *
     * @param names - name of sets
     * @return size of intersection
     */
    Mono<Long> intersection(String... names);

    /**
     * Intersection sets specified by name with current set.
     * Without current set state change.
     *
     * @param names - name of sets
     * @return values
     */
    Mono<Set<V>> readIntersection(String... names);

}
