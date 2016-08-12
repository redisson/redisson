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

public interface RLexSortedSetReactive extends RCollectionReactive<String> {

    Publisher<Integer> removeRangeByLex(String fromElement, boolean fromInclusive, String toElement, boolean toInclusive);

    Publisher<Integer> removeRangeTailByLex(String fromElement, boolean fromInclusive);

    Publisher<Integer> removeRangeHeadByLex(String toElement, boolean toInclusive);

    Publisher<Integer> lexCountTail(String fromElement, boolean fromInclusive);

    Publisher<Integer> lexCountHead(String toElement, boolean toInclusive);

    Publisher<Collection<String>> lexRangeTail(String fromElement, boolean fromInclusive);

    Publisher<Collection<String>> lexRangeHead(String toElement, boolean toInclusive);

    Publisher<Collection<String>> lexRange(String fromElement, boolean fromInclusive, String toElement, boolean toInclusive);

    Publisher<Collection<String>> lexRangeTail(String fromElement, boolean fromInclusive, int offset, int count);

    Publisher<Collection<String>> lexRangeHead(String toElement, boolean toInclusive, int offset, int count);

    Publisher<Collection<String>> lexRange(String fromElement, boolean fromInclusive, String toElement, boolean toInclusive, int offset, int count);

    Publisher<Integer> lexCount(String fromElement, boolean fromInclusive, String toElement, boolean toInclusive);

    Publisher<Long> rank(String o);

    Publisher<Collection<String>> valueRange(int startIndex, int endIndex);

}
