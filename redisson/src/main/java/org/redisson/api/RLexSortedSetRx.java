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

import io.reactivex.Flowable;

/**
 * RxJava2 interface for LexSortedSet object
 * 
 * @author Nikita Koksharov
 *
 */
public interface RLexSortedSetRx extends RScoredSortedSetRx<String>, RCollectionRx<String> {

    Flowable<Integer> removeRange(String fromElement, boolean fromInclusive, String toElement, boolean toInclusive);

    Flowable<Integer> removeRangeTail(String fromElement, boolean fromInclusive);

    Flowable<Integer> removeRangeHead(String toElement, boolean toInclusive);

    Flowable<Integer> countTail(String fromElement, boolean fromInclusive);

    Flowable<Integer> countHead(String toElement, boolean toInclusive);

    Flowable<Collection<String>> rangeTail(String fromElement, boolean fromInclusive);

    Flowable<Collection<String>> rangeHead(String toElement, boolean toInclusive);

    Flowable<Collection<String>> range(String fromElement, boolean fromInclusive, String toElement, boolean toInclusive);

    Flowable<Collection<String>> rangeTail(String fromElement, boolean fromInclusive, int offset, int count);

    Flowable<Collection<String>> rangeHead(String toElement, boolean toInclusive, int offset, int count);

    Flowable<Collection<String>> range(String fromElement, boolean fromInclusive, String toElement, boolean toInclusive, int offset, int count);

    Flowable<Integer> count(String fromElement, boolean fromInclusive, String toElement, boolean toInclusive);

}
