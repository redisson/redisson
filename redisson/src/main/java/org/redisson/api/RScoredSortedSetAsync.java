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
package org.redisson.api;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import org.redisson.api.RScoredSortedSet.Aggregate;
import org.redisson.client.protocol.ScoredEntry;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <V> value
 */
public interface RScoredSortedSetAsync<V> extends RExpirableAsync, RSortableAsync<Set<V>> {

    RFuture<V> pollLastAsync();

    RFuture<V> pollFirstAsync();

    RFuture<V> firstAsync();

    RFuture<V> lastAsync();
    
    RFuture<Double> firstScoreAsync();
    
    RFuture<Double> lastScoreAsync();

    RFuture<Long> addAllAsync(Map<V, Double> objects);

    RFuture<Integer> removeRangeByScoreAsync(double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive);

    RFuture<Integer> removeRangeByRankAsync(int startIndex, int endIndex);

    RFuture<Integer> rankAsync(V o);
    
    RFuture<Integer> revRankAsync(V o);

    RFuture<Double> getScoreAsync(V o);

    /**
     * Adds element to this set, overrides previous score if it has been already added.
     *
     * @param score - object score
     * @param object - object itself
     * @return <code>true</code> if element has added and <code>false</code> if not.
     */
    RFuture<Boolean> addAsync(double score, V object);

    /**
     * Adds element to this set, overrides previous score if it has been already added.
     * Finally return the rank of the item
     * @param score - object score
     * @param object - object itself
     * @return rank
     */
    RFuture<Integer> addAndGetRankAsync(double score, V object);

    /**
     * Adds element to this set, overrides previous score if it has been already added.
     * Finally return the reverse rank of the item
     * @param score - object score
     * @param object - object itself
     * @return reverse rank
     */
    RFuture<Integer> addAndGetRevRankAsync(double score, V object);

    /**
     * Adds element to this set only if has not been added before.
     * <p>
     * Works only with <b>Redis 3.0.2 and higher.</b>
     *
     * @param score - object score
     * @param object - object itself
     * @return <code>true</code> if element has added and <code>false</code> if not.
     */
    RFuture<Boolean> tryAddAsync(double score, V object);

    RFuture<Boolean> removeAsync(V object);

    RFuture<Integer> sizeAsync();

    RFuture<Boolean> containsAsync(Object o);

    RFuture<Boolean> containsAllAsync(Collection<?> c);

    RFuture<Boolean> removeAllAsync(Collection<?> c);

    RFuture<Boolean> retainAllAsync(Collection<?> c);

    RFuture<Double> addScoreAsync(V object, Number value);

    /**
     * Adds score to element and returns its reverse rank
     * 
     * @param object - object itself
     * @param value - object score
     * @return reverse rank
     */
    RFuture<Integer> addScoreAndGetRevRankAsync(V object, Number value);
    
    /**
     * Adds score to element and returns its rank
     * 
     * @param object - object itself
     * @param value - object score
     * @return rank
     */
    RFuture<Integer> addScoreAndGetRankAsync(V object, Number value);
    
    RFuture<Collection<V>> valueRangeAsync(int startIndex, int endIndex);
    
    RFuture<Collection<V>> valueRangeReversedAsync(int startIndex, int endIndex);

    RFuture<Collection<ScoredEntry<V>>> entryRangeAsync(int startIndex, int endIndex);
    
    RFuture<Collection<ScoredEntry<V>>> entryRangeReversedAsync(int startIndex, int endIndex);

    /**
     * Returns all values between <code>startScore</code> and <code>endScore</code>.
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
    RFuture<Collection<V>> valueRangeAsync(double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive);

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
    RFuture<Collection<V>> valueRangeReversedAsync(double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive);

    RFuture<Collection<ScoredEntry<V>>> entryRangeAsync(double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive);

    RFuture<Collection<V>> valueRangeAsync(double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive, int offset, int count);

    RFuture<Collection<V>> valueRangeReversedAsync(double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive, int offset, int count);

    RFuture<Collection<ScoredEntry<V>>> entryRangeAsync(double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive, int offset, int count);

    RFuture<Collection<ScoredEntry<V>>> entryRangeReversedAsync(double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive);
    
    RFuture<Collection<ScoredEntry<V>>> entryRangeReversedAsync(double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive, int offset, int count);

    /**
     * Returns the number of elements with a score between <code>startScore</code> and <code>endScore</code>.
     * 
     * @param startScore - start score
     * @param startScoreInclusive - start score inclusive
     * @param endScore - end score
     * @param endScoreInclusive - end score inclusive
     * @return count
     */
    RFuture<Long> countAsync(double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive);
    
    /**
     * Read all values at once.
     * 
     * @return values
     */
    RFuture<Collection<V>> readAllAsync();

    /**
     * Intersect provided ScoredSortedSets 
     * and store result to current ScoredSortedSet
     * 
     * @param names - names of ScoredSortedSet
     * @return length of intersection
     */
    RFuture<Integer> intersectionAsync(String... names);

    /**
     * Intersect provided ScoredSortedSets with defined aggregation method 
     * and store result to current ScoredSortedSet
     * 
     * @param aggregate - score aggregation mode
     * @param names - names of ScoredSortedSet
     * @return length of intersection
     */
    RFuture<Integer> intersectionAsync(Aggregate aggregate, String... names);

    /**
     * Intersect provided ScoredSortedSets mapped to weight multiplier 
     * and store result to current ScoredSortedSet
     * 
     * @param nameWithWeight - name of ScoredSortedSet mapped to weight multiplier
     * @return length of intersection
     */
    RFuture<Integer> intersectionAsync(Map<String, Double> nameWithWeight);

    /**
     * Intersect provided ScoredSortedSets mapped to weight multiplier 
     * with defined aggregation method 
     * and store result to current ScoredSortedSet
     * 
     * @param aggregate - score aggregation mode
     * @param nameWithWeight - name of ScoredSortedSet mapped to weight multiplier
     * @return length of intersection
     */
    RFuture<Integer> intersectionAsync(Aggregate aggregate, Map<String, Double> nameWithWeight);

    /**
     * Union provided ScoredSortedSets 
     * and store result to current ScoredSortedSet
     * 
     * @param names - names of ScoredSortedSet
     * @return length of union
     */
    RFuture<Integer> unionAsync(String... names);

    /**
     * Union provided ScoredSortedSets with defined aggregation method 
     * and store result to current ScoredSortedSet
     * 
     * @param aggregate - score aggregation mode
     * @param names - names of ScoredSortedSet
     * @return length of union
     */
    RFuture<Integer> unionAsync(Aggregate aggregate, String... names);

    /**
     * Union provided ScoredSortedSets mapped to weight multiplier 
     * and store result to current ScoredSortedSet
     * 
     * @param nameWithWeight - name of ScoredSortedSet mapped to weight multiplier
     * @return length of union
     */
    RFuture<Integer> unionAsync(Map<String, Double> nameWithWeight);

    /**
     * Union provided ScoredSortedSets mapped to weight multiplier 
     * with defined aggregation method 
     * and store result to current ScoredSortedSet
     * 
     * @param aggregate - score aggregation mode
     * @param nameWithWeight - name of ScoredSortedSet mapped to weight multiplier
     * @return length of union
     */
    RFuture<Integer> unionAsync(Aggregate aggregate, Map<String, Double> nameWithWeight);
    
}
