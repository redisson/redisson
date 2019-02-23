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

import io.reactivex.Flowable;

/**
 * RxJava2 interface for sorted set contained values of String type.
 * 
 * @author Nikita Koksharov
 *
 */
public interface RLexSortedSetRx extends RScoredSortedSetRx<String>, RCollectionRx<String> {

    /**
     * Removes values range starting with <code>fromElement</code> and ending with <code>toElement</code>.
     * 
     * @param fromElement - start element
     * @param fromInclusive - start element inclusive
     * @param toElement - end element
     * @param toInclusive - end element inclusive
     * @return number of elements removed
     */
    Flowable<Integer> removeRange(String fromElement, boolean fromInclusive, String toElement, boolean toInclusive);

    /**
     * Removes tail values range starting with <code>fromElement</code>.
     * 
     * @param fromElement - start element
     * @param fromInclusive - start element inclusive
     * @return number of elements removed
     */
    Flowable<Integer> removeRangeTail(String fromElement, boolean fromInclusive);

    /**
     * Removes head values range ending with <code>toElement</code>.
     * 
     * @param toElement - end element
     * @param toInclusive - end element inclusive
     * @return number of elements removed
     */
    Flowable<Integer> removeRangeHead(String toElement, boolean toInclusive);

    /**
     * Returns the number of tail values starting with <code>fromElement</code>.
     * 
     * @param fromElement - start element
     * @param fromInclusive - start element inclusive
     * @return number of elements
     */
    Flowable<Integer> countTail(String fromElement, boolean fromInclusive);

    /**
     * Returns the number of head values ending with <code>toElement</code>.
     * 
     * @param toElement - end element
     * @param toInclusive - end element inclusive
     * @return number of elements
     */
    Flowable<Integer> countHead(String toElement, boolean toInclusive);

    /**
     * Returns tail values range starting with <code>fromElement</code>.
     * 
     * @param fromElement - start element
     * @param fromInclusive - start element inclusive
     * @return collection of elements
     */
    Flowable<Collection<String>> rangeTail(String fromElement, boolean fromInclusive);

    /**
     * Returns head values range ending with <code>toElement</code>.
     * 
     * @param toElement - end element
     * @param toInclusive - end element inclusive
     * @return collection of elements
     */
    Flowable<Collection<String>> rangeHead(String toElement, boolean toInclusive);

    /**
     * Returns values range starting with <code>fromElement</code> and ending with <code>toElement</code>.
     * 
     * @param fromElement - start element
     * @param fromInclusive - start element inclusive
     * @param toElement - end element
     * @param toInclusive - end element inclusive
     * @return collection of elements
     */
    Flowable<Collection<String>> range(String fromElement, boolean fromInclusive, String toElement, boolean toInclusive);

    /**
     * Returns tail values range starting with <code>fromElement</code>. 
     * Returned collection limited by <code>count</code> and starts with <code>offset</code>.
     * 
     * @param fromElement - start element
     * @param fromInclusive - start element inclusive
     * @param offset - offset of result collection
     * @param count - amount of result collection
     * @return collection of elements
     */
    Flowable<Collection<String>> rangeTail(String fromElement, boolean fromInclusive, int offset, int count);

    /**
     * Returns head values range ending with <code>toElement</code>.
     * Returned collection limited by <code>count</code> and starts with <code>offset</code>.
     * 
     * @param toElement - end element
     * @param toInclusive - end element inclusive
     * @param offset - offset of result collection
     * @param count - amount of result collection
     * @return collection of elements
     */
    Flowable<Collection<String>> rangeHead(String toElement, boolean toInclusive, int offset, int count);

    /**
     * Returns values range starting with <code>fromElement</code> and ending with <code>toElement</code>.
     * Returned collection limited by <code>count</code> and starts with <code>offset</code>.
     * 
     * @param fromElement - start element
     * @param fromInclusive - start element inclusive
     * @param toElement - end element
     * @param toInclusive - end element inclusive
     * @return collection of elements
     */
    Flowable<Collection<String>> range(String fromElement, boolean fromInclusive, String toElement, boolean toInclusive, int offset, int count);

    /**
     * Returns the number of elements between <code>fromElement</code> and <code>toElement</code>.
     * 
     * @param fromElement - start element
     * @param fromInclusive - start element inclusive
     * @param toElement - end element
     * @param toInclusive - end element inclusive
     * @return number of elements
     */
    Flowable<Integer> count(String fromElement, boolean fromInclusive, String toElement, boolean toInclusive);

}
