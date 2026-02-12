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

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.Set;

/**
 * Reactive interface for Redis based implementation of {@link java.util.Set}
 *
 * @author Nikita Koksharov
 *
 * @param <V> type of value
 */
public interface RSetReactive<V> extends RCollectionReactive<V>, RSortableReactive<Set<V>> {

    /**
     * Adds all elements contained in the specified collection.
     * Returns number of added elements.
     *
     * @param c - collection of elements to add
     * @return number of added elements
     */
    Mono<Integer> addAllCounted(Collection<? extends V> c);

    /**
     * Removes all elements contained in the specified collection.
     * Returns number of removed elements.
     *
     * @param c - collection of elements to add
     * @return number of removed elements
     */
    Mono<Integer> removeAllCounted(Collection<? extends V> c);

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
     * Returns elements iterator fetches elements in a batch.
     * Batch size is defined by <code>count</code> param.
     * 
     * @param count - size of elements batch
     * @return iterator
     */
    Flux<V> iterator(int count);
    
    /**
     * Returns elements iterator fetches elements in a batch.
     * Batch size is defined by <code>count</code> param.
     * If pattern is not null then only elements match this pattern are loaded.
     * 
     * @param pattern - search pattern
     * @param count - size of elements batch
     * @return iterator
     */
    Flux<V> iterator(String pattern, int count);
    
    /**
     * Returns elements iterator.
     * If <code>pattern</code> is not null then only elements match this pattern are loaded.
     * 
     * @param pattern - search pattern
     * @return iterator
     */
    Flux<V> iterator(String pattern);
    
    /**
     * Removes and returns random elements limited by <code>amount</code>
     *
     * @param amount of random elements
     * @return random elements
     */
    Mono<Set<V>> removeRandom(int amount);
    
    /**
     * Removes and returns random element
     *
     * @return random element
     */
    Mono<V> removeRandom();

    /**
     * Returns random element
     *
     * @return random element
     */
    Mono<V> random();

    /**
     * Returns random elements from set limited by <code>count</code>
     *
     * @param count - values amount to return
     * @return random elements
     */
    Mono<Set<V>> random(int count);

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
    Mono<Integer> union(String... names);

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
    Mono<Integer> diff(String... names);
    
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
    Mono<Integer> intersection(String... names);

    /**
     * Counts elements of set as a result of sets intersection with current set.
     * <p>
     * Requires <b>Redis 7.0.0 and higher.</b>
     *
     * @param names - name of sets
     * @return amount of elements
     */
    Mono<Integer> countIntersection(String... names);

    /**
     * Counts elements of set as a result of sets intersection with current set.
     * <p>
     * Requires <b>Redis 7.0.0 and higher.</b>
     *
     * @param names - name of sets
     * @param limit - sets intersection limit
     * @return amount of elements
     */
    Mono<Integer> countIntersection(int limit, String... names);

    /**
     * Intersection sets specified by name with current set.
     * Without current set state change.
     *
     * @param names - name of sets
     * @return values
     */
    Mono<Set<V>> readIntersection(String... names);

    /**
     * Tries to add elements only if none of them in set.
     *
     * @param values - values to add
     * @return <code>true</code> if elements successfully added,
     *          otherwise <code>false</code>.
     */
    Mono<Boolean> tryAdd(V... values);

    /**
     * Check if each element is contained in the specified collection.
     * Returns contained elements.
     * <p>
     * Requires <b>Redis 6.2.0 and higher.</b>
     *
     * @param c - collection to check
     * @return contained elements
     */
    Mono<Set<V>> containsEach(Collection<V> c);

    /**
     * Adds object event listener
     *
     * @see org.redisson.api.listener.TrackingListener
     * @see org.redisson.api.listener.SetAddListener
     * @see org.redisson.api.listener.SetRemoveListener
     * @see org.redisson.api.listener.SetRemoveRandomListener
     * @see org.redisson.api.ExpiredObjectListener
     * @see org.redisson.api.DeletedObjectListener
     *
     * @param listener - object event listener
     * @return listener id
     */
    Mono<Integer> addListener(ObjectListener listener);

}
