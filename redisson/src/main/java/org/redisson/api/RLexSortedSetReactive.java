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

import java.util.Collection;

import org.reactivestreams.Publisher;

public interface RLexSortedSetReactive extends RScoredSortedSetReactive<String>, RCollectionReactive<String> {

    Publisher<Integer> removeRange(String fromElement, boolean fromInclusive, String toElement, boolean toInclusive);

    Publisher<Integer> removeRangeTail(String fromElement, boolean fromInclusive);

    Publisher<Integer> removeRangeHead(String toElement, boolean toInclusive);

    Publisher<Integer> countTail(String fromElement, boolean fromInclusive);

    Publisher<Integer> countHead(String toElement, boolean toInclusive);

    Publisher<Collection<String>> rangeTail(String fromElement, boolean fromInclusive);

    Publisher<Collection<String>> rangeHead(String toElement, boolean toInclusive);

    Publisher<Collection<String>> range(String fromElement, boolean fromInclusive, String toElement, boolean toInclusive);

    Publisher<Collection<String>> rangeTail(String fromElement, boolean fromInclusive, int offset, int count);

    Publisher<Collection<String>> rangeHead(String toElement, boolean toInclusive, int offset, int count);

    Publisher<Collection<String>> range(String fromElement, boolean fromInclusive, String toElement, boolean toInclusive, int offset, int count);

    Publisher<Integer> count(String fromElement, boolean fromInclusive, String toElement, boolean toInclusive);

    Publisher<Collection<String>> range(int startIndex, int endIndex);

}
