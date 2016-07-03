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

import java.util.Collection;
import java.util.Map;

import org.redisson.client.protocol.ScoredEntry;

import io.netty.util.concurrent.Future;

public interface RScoredSortedSetAsync<V> extends RExpirableAsync {

    Future<V> pollLastAsync();

    Future<V> pollFirstAsync();

    Future<V> firstAsync();

    Future<V> lastAsync();

    Future<Long> addAllAsync(Map<V, Double> objects);

    Future<Integer> removeRangeByScoreAsync(double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive);

    Future<Integer> removeRangeByRankAsync(int startIndex, int endIndex);

    Future<Integer> rankAsync(V o);
    
    Future<Integer> revRankAsync(V o);

    Future<Double> getScoreAsync(V o);

    /**
     * Adds element to this set, overrides previous score if it has been already added.
     *
     * @param score
     * @param object
     * @return <code>true</code> if element has added and <code>false</code> if not.
     */
    Future<Boolean> addAsync(double score, V object);

    /**
     * Adds element to this set only if has not been added before.
     * <p/>
     * Works only with <b>Redis 3.0.2 and higher.</b>
     *
     * @param score
     * @param object
     * @return <code>true</code> if element has added and <code>false</code> if not.
     */
    Future<Boolean> tryAddAsync(double score, V object);

    Future<Boolean> removeAsync(V object);

    Future<Integer> sizeAsync();

    Future<Boolean> containsAsync(Object o);

    Future<Boolean> containsAllAsync(Collection<?> c);

    Future<Boolean> removeAllAsync(Collection<?> c);

    Future<Boolean> retainAllAsync(Collection<?> c);

    Future<Double> addScoreAsync(V object, Number value);

    Future<Collection<V>> valueRangeAsync(int startIndex, int endIndex);

    Future<Collection<ScoredEntry<V>>> entryRangeAsync(int startIndex, int endIndex);

    Future<Collection<V>> valueRangeAsync(double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive);

    Future<Collection<V>> valueRangeReversedAsync(double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive);

    Future<Collection<ScoredEntry<V>>> entryRangeAsync(double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive);

    Future<Collection<V>> valueRangeAsync(double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive, int offset, int count);

    Future<Collection<V>> valueRangeReversedAsync(double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive, int offset, int count);

    Future<Collection<ScoredEntry<V>>> entryRangeAsync(double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive, int offset, int count);

    Future<Collection<ScoredEntry<V>>> entryRangeReversedAsync(double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive, int offset, int count);

    /**
     * Returns the number of elements with a score between <code>startScore</code> and <code>endScore</code>.
     * 
     * @param startScore
     * @param startScoreInclusive
     * @param endScore
     * @param endScoreInclusive
     * @return
     */
    Future<Long> countAsync(double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive);
    
    /**
     * Read all values at once.
     * 
     * @return
     */
    Future<Collection<V>> readAllAsync();
    
}
