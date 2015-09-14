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

import org.redisson.client.protocol.ScoredEntry;

public interface RScoredSortedSet<V> extends RScoredSortedSetAsync<V>, Iterable<V>, RExpirable {

    V first();

    V last();

    int removeRangeByScore(double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive);

    int removeRangeByRank(int startIndex, int endIndex);

    int rank(V o);

    Double getScore(V o);

    boolean add(double score, V object);

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

}
