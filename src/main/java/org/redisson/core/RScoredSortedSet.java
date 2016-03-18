/**
 * Copyright 2014 Nikita Koksharov, Nickolay Borbit
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

public interface RScoredSortedSet<V> extends RScoredSortedSetAsync<V>, Iterable<V>, RExpirable {

    V pollFirst();

    V pollLast();

    V first();

    V last();

    Long addAll(Map<V, Double> objects);

    int removeRangeByScore(double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive);

    int removeRangeByRank(int startIndex, int endIndex);

    int rank(V o);
    
    int revRank(V o);

    Double getScore(V o);

    /**
     * Adds element to this set, overrides previous score if it has been already added.
     *
     * @param score
     * @param object
     * @return <code>true</code> if element has added and <code>false</code> if not.
     */
    boolean add(double score, V object);

    /**
     * Adds element to this set only if has not been added before.
     * <p/>
     * Works only with <b>Redis 3.0.2 and higher.</b>
     *
     * @param score
     * @param object
     * @return <code>true</code> if element has added and <code>false</code> if not.
     */
    boolean tryAdd(double score, V object);

    int size();

    boolean isEmpty();

    boolean contains(Object o);

    Object[] toArray();

    <T> T[] toArray(T[] a);

    boolean remove(Object o);

    boolean containsAll(Collection<?> c);

    boolean removeAll(Collection<?> c);

    boolean retainAll(Collection<?> c);

    void clear();

    Double addScore(V object, Number value);

    Collection<V> valueRange(int startIndex, int endIndex);

    Collection<ScoredEntry<V>> entryRange(int startIndex, int endIndex);

    Collection<V> valueRange(double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive);

    Collection<V> valueRangeReversed(double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive);

    Collection<ScoredEntry<V>> entryRange(double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive);

    Collection<V> valueRange(double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive, int offset, int count);

    Collection<V> valueRangeReversed(double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive, int offset, int count);

    Collection<ScoredEntry<V>> entryRange(double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive, int offset, int count);

}
