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

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.redisson.api.RScoredSortedSet.Aggregate;
import org.redisson.client.protocol.ScoredEntry;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive interface for SortedSet object
 * 
 * @author Nikita Koksharov
 *
 * @param <V> value type
 */
public interface RScoredSortedSetReactive<V> extends RExpirableReactive, RSortableReactive<Set<V>> {

    /**
     * Removes and returns first available tail element of <b>any</b> sorted set,
     * waiting up to the specified wait time if necessary for an element to become available
     * in any of defined sorted sets <b>including</b> this one.
     * <p>
     * Requires <b>Redis 5.0.0 and higher.</b>
     * 
     * @param queueNames - names of queue
     * @param timeout how long to wait before giving up, in units of
     *        {@code unit}
     * @param unit a {@code TimeUnit} determining how to interpret the
     *        {@code timeout} parameter
     * @return the tail element, or {@code null} if all sorted sets are empty 
     */
    Mono<V> pollLastFromAny(long timeout, TimeUnit unit, String ... queueNames);
    
    /**
     * Removes and returns first available head element of <b>any</b> sorted set,
     * waiting up to the specified wait time if necessary for an element to become available
     * in any of defined sorted sets <b>including</b> this one.
     * <p>
     * Requires <b>Redis 5.0.0 and higher.</b>
     * 
     * @param queueNames - names of queue
     * @param timeout how long to wait before giving up, in units of
     *        {@code unit}
     * @param unit a {@code TimeUnit} determining how to interpret the
     *        {@code timeout} parameter
     * @return the head element, or {@code null} if all sorted sets are empty
     *  
     */
    Mono<V> pollFirstFromAny(long timeout, TimeUnit unit, String ... queueNames);
    
    /**
     * Removes and returns the head element or {@code null} if this sorted set is empty.
     * <p>
     * Requires <b>Redis 5.0.0 and higher.</b>
     *
     * @param timeout how long to wait before giving up, in units of
     *        {@code unit}
     * @param unit a {@code TimeUnit} determining how to interpret the
     *        {@code timeout} parameter
     * @return the head element, 
     *         or {@code null} if this sorted set is empty
     */
    Mono<V> pollFirst(long timeout, TimeUnit unit);

    /**
     * Removes and returns the tail element or {@code null} if this sorted set is empty.
     * <p>
     * Requires <b>Redis 5.0.0 and higher.</b>
     *
     * @param timeout how long to wait before giving up, in units of
     *        {@code unit}
     * @param unit a {@code TimeUnit} determining how to interpret the
     *        {@code timeout} parameter
     * @return the tail element or {@code null} if this sorted set is empty
     */
    Mono<V> pollLast(long timeout, TimeUnit unit);
    
    /**
     * Removes and returns the head elements or {@code null} if this sorted set is empty.
     *
     * @param count - elements amount
     * @return the head element, 
     *         or {@code null} if this sorted set is empty
     */
    Mono<Collection<V>> pollFirst(int count);

    /**
     * Removes and returns the tail elements or {@code null} if this sorted set is empty.
     *
     * @param count - elements amount
     * @return the tail element or {@code null} if this sorted set is empty
     */
    Mono<Collection<V>> pollLast(int count);

    /**
     * Removes and returns the head element or {@code null} if this sorted set is empty.
     *
     * @return the head element, 
     *         or {@code null} if this sorted set is empty
     */
    Mono<V> pollFirst();

    /**
     * Removes and returns the tail element or {@code null} if this sorted set is empty.
     *
     * @return the tail element or {@code null} if this sorted set is empty
     */
    Mono<V> pollLast();

    /**
     * Returns the head element or {@code null} if this sorted set is empty.
     *
     * @return the head element or {@code null} if this sorted set is empty
     */
    Mono<V> first();

    /**
     * Returns the tail element or {@code null} if this sorted set is empty.
     *
     * @return the tail element or {@code null} if this sorted set is empty
     */
    Mono<V> last();

    /**
     * Returns score of the head element or returns {@code null} if this sorted set is empty.
     *
     * @return the tail element or {@code null} if this sorted set is empty
     */
    Mono<Double> firstScore();

    /**
     * Returns score of the tail element or returns {@code null} if this sorted set is empty.
     *
     * @return the tail element or {@code null} if this sorted set is empty
     */
    Mono<Double> lastScore();
    
    /**
     * Returns an iterator over elements in this set.
     * If <code>pattern</code> is not null then only elements match this pattern are loaded.
     * 
     * @param pattern - search pattern
     * @return iterator
     */
    Flux<V> iterator(String pattern);
    
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
    
    Flux<V> iterator();

    Mono<Integer> removeRangeByScore(double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive);

    Mono<Integer> removeRangeByRank(int startIndex, int endIndex);

    /**
     * Returns rank of value, with the scores ordered from low to high.
     * 
     * @param o - object
     * @return rank or <code>null</code> if value does not exist
     */
    Mono<Integer> rank(V o);
    
    /**
     * Returns rank of value, with the scores ordered from high to low.
     * 
     * @param o - object
     * @return rank or <code>null</code> if value does not exist
     */
    Mono<Integer> revRank(V o);

    Mono<Double> getScore(V o);

    /**
     * Adds element to this set, overrides previous score if it has been already added.
     *
     * @param score - object score
     * @param object - object itself
     * @return <code>true</code> if element has added and <code>false</code> if not.
     */
    Mono<Boolean> add(double score, V object);

    Mono<Long> addAll(Map<V, Double> objects);
    
    /**
     * Adds element to this set, overrides previous score if it has been already added.
     * Finally return the rank of the item
     * 
     * @param score - object score
     * @param object - object itself
     * @return rank
     */
    Mono<Integer> addAndGetRank(double score, V object);

    /**
     * Adds element to this set, overrides previous score if it has been already added.
     * Finally return the reverse rank of the item
     * 
     * @param score - object score
     * @param object - object itself
     * @return reverse rank
     */
    Mono<Integer> addAndGetRevRank(double score, V object);
    
    /**
     * Adds element to this set only if has not been added before.
     * <p>
     * Requires <b>Redis 3.0.2 and higher.</b>
     *
     * @param score - object score
     * @param object - object itself
     * @return <code>true</code> if element has added and <code>false</code> if not.
     */
    Mono<Boolean> tryAdd(double score, V object);
    
    Mono<Boolean> remove(V object);

    Mono<Integer> size();

    Mono<Boolean> contains(V o);

    Mono<Boolean> containsAll(Collection<?> c);

    Mono<Boolean> removeAll(Collection<?> c);

    Mono<Boolean> retainAll(Collection<?> c);

    Mono<Double> addScore(V object, Number value);

    /**
     * Adds score to element and returns its reverse rank
     * 
     * @param object - object itself
     * @param value - object score
     * @return reverse rank
     */
    Mono<Integer> addScoreAndGetRevRank(V object, Number value);
    
    /**
     * Adds score to element and returns its rank
     * 
     * @param object - object itself
     * @param value - object score
     * @return rank
     */
    Mono<Integer> addScoreAndGetRank(V object, Number value);
    
    Mono<Collection<V>> valueRange(int startIndex, int endIndex);

    Mono<Collection<ScoredEntry<V>>> entryRange(int startIndex, int endIndex);

    Mono<Collection<V>> valueRange(double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive);

    Mono<Collection<ScoredEntry<V>>> entryRange(double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive);

    Mono<Collection<V>> valueRange(double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive, int offset, int count);

    Mono<Collection<ScoredEntry<V>>> entryRange(double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive, int offset, int count);

    Mono<Collection<V>> valueRangeReversed(int startIndex, int endIndex);
    
    /**
     * Returns all values between <code>startScore</code> and <code>endScore</code> in reversed order.
     * 
     * @param startScore - start score. 
     *                     Use <code>Double.POSITIVE_INFINITY</code> or <code>Double.NEGATIVE_INFINITY</code> 
     *                     to define infinity numbers
     * @param startScoreInclusive - start score inclusive
     * @param endScore - end score
     *                     Use <code>Double.POSITIVE_INFINITY</code> or <code>Double.NEGATIVE_INFINITY</code> 
     *                     to define infinity numbers
     * 
     * @param endScoreInclusive - end score inclusive
     * @return values
     */
    Mono<Collection<V>> valueRangeReversed(double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive);

    Mono<Collection<V>> valueRangeReversed(double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive, int offset, int count);
    
    Mono<Collection<ScoredEntry<V>>> entryRangeReversed(int startIndex, int endIndex);
    
    Mono<Collection<ScoredEntry<V>>> entryRangeReversed(double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive);
    
    Mono<Collection<ScoredEntry<V>>> entryRangeReversed(double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive, int offset, int count);
    
    
    /**
     * Returns the number of elements with a score between <code>startScore</code> and <code>endScore</code>.
     * 
     * @param startScore - start score
     * @param startScoreInclusive - start score inclusive
     * @param endScore - end score
     * @param endScoreInclusive - end score inclusive
     * @return count
     */
    Mono<Long> count(double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive);
    
    /**
     * Read all values at once.
     * 
     * @return values
     */
    Mono<Collection<V>> readAll();

    /**
     * Intersect provided ScoredSortedSets 
     * and store result to current ScoredSortedSet
     * 
     * @param names - names of ScoredSortedSet
     * @return length of intersection
     */
    Mono<Integer> intersection(String... names);

    /**
     * Intersect provided ScoredSortedSets with defined aggregation method 
     * and store result to current ScoredSortedSet
     * 
     * @param aggregate - score aggregation mode
     * @param names - names of ScoredSortedSet
     * @return length of intersection
     */
    Mono<Integer> intersection(Aggregate aggregate, String... names);

    /**
     * Intersect provided ScoredSortedSets mapped to weight multiplier 
     * and store result to current ScoredSortedSet
     * 
     * @param nameWithWeight - name of ScoredSortedSet mapped to weight multiplier
     * @return length of intersection
     */
    Mono<Integer> intersection(Map<String, Double> nameWithWeight);

    /**
     * Intersect provided ScoredSortedSets mapped to weight multiplier 
     * with defined aggregation method 
     * and store result to current ScoredSortedSet
     * 
     * @param aggregate - score aggregation mode
     * @param nameWithWeight - name of ScoredSortedSet mapped to weight multiplier
     * @return length of intersection
     */
    Mono<Integer> intersection(Aggregate aggregate, Map<String, Double> nameWithWeight);

    /**
     * Union provided ScoredSortedSets 
     * and store result to current ScoredSortedSet
     * 
     * @param names - names of ScoredSortedSet
     * @return length of union
     */
    Mono<Integer> union(String... names);

    /**
     * Union provided ScoredSortedSets with defined aggregation method 
     * and store result to current ScoredSortedSet
     * 
     * @param aggregate - score aggregation mode
     * @param names - names of ScoredSortedSet
     * @return length of union
     */
    Mono<Integer> union(Aggregate aggregate, String... names);

    /**
     * Union provided ScoredSortedSets mapped to weight multiplier 
     * and store result to current ScoredSortedSet
     * 
     * @param nameWithWeight - name of ScoredSortedSet mapped to weight multiplier
     * @return length of union
     */
    Mono<Integer> union(Map<String, Double> nameWithWeight);

    /**
     * Union provided ScoredSortedSets mapped to weight multiplier 
     * with defined aggregation method 
     * and store result to current ScoredSortedSet
     * 
     * @param aggregate - score aggregation mode
     * @param nameWithWeight - name of ScoredSortedSet mapped to weight multiplier
     * @return length of union
     */
    Mono<Integer> union(Aggregate aggregate, Map<String, Double> nameWithWeight);

    /**
     * Removes and returns the head element waiting if necessary for an element to become available.
     *
     * @return the head element
     */
    Mono<V> takeFirst();

    /**
     * Removes and returns the tail element waiting if necessary for an element to become available.
     *
     * @return the tail element
     */
    Mono<V> takeLast();

    /**
     * Retrieves and removes continues stream of elements from the head of this queue. 
     * Waits for next element become available.
     * 
     * @return stream of head elements
     */
    Flux<V> takeFirstElements();
    
    /**
     * Retrieves and removes continues stream of elements from the tail of this queue. 
     * Waits for next element become available.
     * 
     * @return stream of tail elements
     */
    Flux<V> takeLastElements();

}
