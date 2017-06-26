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

import org.reactivestreams.Publisher;
import org.redisson.api.RScoredSortedSet.Aggregate;
import org.redisson.client.protocol.ScoredEntry;

public interface RScoredSortedSetReactive<V> extends RExpirableReactive {

    Publisher<V> pollFirst();

    Publisher<V> pollLast();

    Publisher<V> iterator();

    Publisher<V> first();

    Publisher<V> last();

    Publisher<Integer> removeRangeByScore(double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive);

    Publisher<Integer> removeRangeByRank(int startIndex, int endIndex);

    Publisher<Integer> rank(V o);

    Publisher<Double> getScore(V o);

    Publisher<Boolean> add(double score, V object);

    Publisher<Boolean> remove(V object);

    Publisher<Integer> size();

    Publisher<Boolean> contains(V o);

    Publisher<Boolean> containsAll(Collection<?> c);

    Publisher<Boolean> removeAll(Collection<?> c);

    Publisher<Boolean> retainAll(Collection<?> c);

    Publisher<Double> addScore(V object, Number value);

    Publisher<Collection<V>> valueRange(int startIndex, int endIndex);

    Publisher<Collection<ScoredEntry<V>>> entryRange(int startIndex, int endIndex);

    Publisher<Collection<V>> valueRange(double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive);

    Publisher<Collection<ScoredEntry<V>>> entryRange(double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive);

    Publisher<Collection<V>> valueRange(double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive, int offset, int count);

    Publisher<Collection<ScoredEntry<V>>> entryRange(double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive, int offset, int count);

    Publisher<Collection<V>> valueRangeReversed(int startIndex, int endIndex);
    
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
    Publisher<Collection<V>> valueRangeReversed(double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive);

    Publisher<Collection<V>> valueRangeReversed(double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive, int offset, int count);
    
    Publisher<Collection<ScoredEntry<V>>> entryRangeReversed(int startIndex, int endIndex);
    
    Publisher<Collection<ScoredEntry<V>>> entryRangeReversed(double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive);
    
    Publisher<Collection<ScoredEntry<V>>> entryRangeReversed(double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive, int offset, int count);
    
    
    /**
     * Returns the number of elements with a score between <code>startScore</code> and <code>endScore</code>.
     * 
     * @param startScore - start score
     * @param startScoreInclusive - start score inclusive
     * @param endScore - end score
     * @param endScoreInclusive - end score inclusive
     * @return count
     */
    Publisher<Long> count(double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive);
    
    /**
     * Read all values at once.
     * 
     * @return values
     */
    Publisher<Collection<V>> readAll();

    /**
     * Intersect provided ScoredSortedSets 
     * and store result to current ScoredSortedSet
     * 
     * @param names - names of ScoredSortedSet
     * @return length of intersection
     */
    Publisher<Integer> intersection(String... names);

    /**
     * Intersect provided ScoredSortedSets with defined aggregation method 
     * and store result to current ScoredSortedSet
     * 
     * @param aggregate - score aggregation mode
     * @param names - names of ScoredSortedSet
     * @return length of intersection
     */
    Publisher<Integer> intersection(Aggregate aggregate, String... names);

    /**
     * Intersect provided ScoredSortedSets mapped to weight multiplier 
     * and store result to current ScoredSortedSet
     * 
     * @param nameWithWeight - name of ScoredSortedSet mapped to weight multiplier
     * @return length of intersection
     */
    Publisher<Integer> intersection(Map<String, Double> nameWithWeight);

    /**
     * Intersect provided ScoredSortedSets mapped to weight multiplier 
     * with defined aggregation method 
     * and store result to current ScoredSortedSet
     * 
     * @param aggregate - score aggregation mode
     * @param nameWithWeight - name of ScoredSortedSet mapped to weight multiplier
     * @return length of intersection
     */
    Publisher<Integer> intersection(Aggregate aggregate, Map<String, Double> nameWithWeight);

    /**
     * Union provided ScoredSortedSets 
     * and store result to current ScoredSortedSet
     * 
     * @param names - names of ScoredSortedSet
     * @return length of union
     */
    Publisher<Integer> union(String... names);

    /**
     * Union provided ScoredSortedSets with defined aggregation method 
     * and store result to current ScoredSortedSet
     * 
     * @param aggregate - score aggregation mode
     * @param names - names of ScoredSortedSet
     * @return length of union
     */
    Publisher<Integer> union(Aggregate aggregate, String... names);

    /**
     * Union provided ScoredSortedSets mapped to weight multiplier 
     * and store result to current ScoredSortedSet
     * 
     * @param nameWithWeight - name of ScoredSortedSet mapped to weight multiplier
     * @return length of union
     */
    Publisher<Integer> union(Map<String, Double> nameWithWeight);

    /**
     * Union provided ScoredSortedSets mapped to weight multiplier 
     * with defined aggregation method 
     * and store result to current ScoredSortedSet
     * 
     * @param aggregate - score aggregation mode
     * @param nameWithWeight - name of ScoredSortedSet mapped to weight multiplier
     * @return length of union
     */
    Publisher<Integer> union(Aggregate aggregate, Map<String, Double> nameWithWeight);

    
}
