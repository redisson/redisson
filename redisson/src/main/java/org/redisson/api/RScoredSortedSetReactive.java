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

import org.reactivestreams.Publisher;
import org.redisson.client.protocol.ScoredEntry;

public interface RScoredSortedSetReactive<V> extends RExpirableReactive {

    Publisher<V> pollFirst();

    Publisher<V> pollLast();

    Publisher<V> iterator();

    Publisher<V> first();

    Publisher<V> last();

    Publisher<Integer> removeRangeByScore(double startScore, boolean startScoreInclusive, double endScore, boolean endScoreInclusive);

    Publisher<Integer> removeRangeByRank(int startIndex, int endIndex);

    Publisher<Long> rank(V o);

    Publisher<Double> getScore(V o);

    Publisher<Boolean> add(double score, V object);

    Publisher<Boolean> remove(V object);

    Publisher<Long> size();

    Publisher<Boolean> contains(Object o);

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

}
