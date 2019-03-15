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

/**
 * Async interface for sorted set contained values of String type.
 * 
 * @author Nikita Koksharov
 *
 */
public interface RLexSortedSetAsync extends RCollectionAsync<String> {

    /**
     * Removes and returns the tail element or {@code null} if this sorted set is empty.
     *
     * @return the tail element or {@code null} if this sorted set is empty
     */
    RFuture<String> pollLastAsync();

    /**
     * Removes and returns the head element or {@code null} if this sorted set is empty.
     *
     * @return the head element, 
     *         or {@code null} if this sorted set is empty
     */
    RFuture<String> pollFirstAsync();

    /**
     * Returns the first element.
     *
     * @return element
     */
    RFuture<String> firstAsync();

    /**
     * Returns the last element.
     *
     * @return element
     */
    RFuture<String> lastAsync();
    
    /**
     * Read all values at once.
     * 
     * @return collection of values
     */
    RFuture<Collection<String>> readAllAsync();
    
    /**
     * Removes values range starting with <code>fromElement</code> and ending with <code>toElement</code>.
     * 
     * @param fromElement - start element
     * @param fromInclusive - start element inclusive
     * @param toElement - end element
     * @param toInclusive - end element inclusive
     * @return number of elements removed
     */
    RFuture<Integer> removeRangeAsync(String fromElement, boolean fromInclusive, String toElement, boolean toInclusive);

    /**
     * Removes tail values range starting with <code>fromElement</code>.
     * 
     * @param fromElement - start element
     * @param fromInclusive - start element inclusive
     * @return number of elements removed
     */
    RFuture<Integer> removeRangeTailAsync(String fromElement, boolean fromInclusive);

    /**
     * Removes head values range ending with <code>toElement</code>.
     * 
     * @param toElement - end element
     * @param toInclusive - end element inclusive
     * @return number of elements removed
     */
    RFuture<Integer> removeRangeHeadAsync(String toElement, boolean toInclusive);

    /**
     * Returns the number of tail values starting with <code>fromElement</code>.
     * 
     * @param fromElement - start element
     * @param fromInclusive - start element inclusive
     * @return number of elements
     */
    RFuture<Integer> countTailAsync(String fromElement, boolean fromInclusive);

    /**
     * Returns the number of head values ending with <code>toElement</code>.
     * 
     * @param toElement - end element
     * @param toInclusive - end element inclusive
     * @return number of elements
     */
    RFuture<Integer> countHeadAsync(String toElement, boolean toInclusive);

    /**
     * Returns tail values range starting with <code>fromElement</code>.
     * 
     * @param fromElement - start element
     * @param fromInclusive - start element inclusive
     * @return collection of elements
     */
    RFuture<Collection<String>> rangeTailAsync(String fromElement, boolean fromInclusive);

    /**
     * Returns head values range ending with <code>toElement</code>.
     * 
     * @param toElement - end element
     * @param toInclusive - end element inclusive
     * @return collection of elements
     */
    RFuture<Collection<String>> rangeHeadAsync(String toElement, boolean toInclusive);

    /**
     * Returns values range starting with <code>fromElement</code> and ending with <code>toElement</code>.
     * 
     * @param fromElement - start element
     * @param fromInclusive - start element inclusive
     * @param toElement - end element
     * @param toInclusive - end element inclusive
     * @return collection of elements
     */
    RFuture<Collection<String>> rangeAsync(String fromElement, boolean fromInclusive, String toElement, boolean toInclusive);

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
    RFuture<Collection<String>> rangeTailAsync(String fromElement, boolean fromInclusive, int offset, int count);
    
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
    RFuture<Collection<String>> rangeHeadAsync(String toElement, boolean toInclusive, int offset, int count);

    /**
     * Returns values range starting with <code>fromElement</code> and ending with <code>toElement</code>.
     * Returned collection limited by <code>count</code> and starts with <code>offset</code>.
     * 
     * @param fromElement - start element
     * @param fromInclusive - start element inclusive
     * @param toElement - end element
     * @param toInclusive - end element inclusive
     * @param offset - offset of result collection
     * @param count - amount of result collection
     * @return collection of elements
     */
    RFuture<Collection<String>> rangeAsync(String fromElement, boolean fromInclusive, String toElement, boolean toInclusive, int offset, int count);
    
    /**
     * Returns tail values range in reverse order starting with <code>fromElement</code>.
     * 
     * @param fromElement - start element
     * @param fromInclusive - start element inclusive
     * @return collection of elements
     */
    RFuture<Collection<String>> rangeTailReversedAsync(String fromElement, boolean fromInclusive);

    /**
     * Returns head values range in reverse order ending with <code>toElement</code>.
     * 
     * @param toElement - end element
     * @param toInclusive - end element inclusive
     * @return collection of elements
     */
    RFuture<Collection<String>> rangeHeadReversedAsync(String toElement, boolean toInclusive);

    /**
     * Returns values range in reverse order starting with <code>fromElement</code> and ending with <code>toElement</code>.
     * 
     * @param fromElement - start element
     * @param fromInclusive - start element inclusive
     * @param toElement - end element
     * @param toInclusive - end element inclusive
     * @return collection of elements
     */
    RFuture<Collection<String>> rangeReversedAsync(String fromElement, boolean fromInclusive, String toElement, boolean toInclusive);

    /**
     * Returns tail values range in reverse order starting with <code>fromElement</code>. 
     * Returned collection limited by <code>count</code> and starts with <code>offset</code>.
     * 
     * @param fromElement - start element
     * @param fromInclusive - start element inclusive
     * @param offset - offset of result collection
     * @param count - amount of result collection
     * @return collection of elements
     */
    RFuture<Collection<String>> rangeTailReversedAsync(String fromElement, boolean fromInclusive, int offset, int count);
    
    /**
     * Returns head values range in reverse order ending with <code>toElement</code>.
     * Returned collection limited by <code>count</code> and starts with <code>offset</code>.
     * 
     * @param toElement - end element
     * @param toInclusive - end element inclusive
     * @param offset - offset of result collection
     * @param count - amount of result collection
     * @return collection of elements
     */
    RFuture<Collection<String>> rangeHeadReversedAsync(String toElement, boolean toInclusive, int offset, int count);

    /**
     * Returns values range in reverse order starting with <code>fromElement</code> and ending with <code>toElement</code>.
     * Returned collection limited by <code>count</code> and starts with <code>offset</code>.
     * 
     * @param fromElement - start element
     * @param fromInclusive - start element inclusive
     * @param toElement - end element
     * @param toInclusive - end element inclusive
     * @param offset - offset of result collection
     * @param count - amount of result collection
     * @return collection of elements
     */
    RFuture<Collection<String>> rangeReversedAsync(String fromElement, boolean fromInclusive, String toElement, boolean toInclusive, int offset, int count);

    /**
     * Returns the number of elements between <code>fromElement</code> and <code>toElement</code>.
     * 
     * @param fromElement - start element
     * @param fromInclusive - start element inclusive
     * @param toElement - end element
     * @param toInclusive - end element inclusive
     * @return number of elements
     */
    RFuture<Integer> countAsync(String fromElement, boolean fromInclusive, String toElement, boolean toInclusive);

    /**
     * Returns rank of the element
     * 
     * @param o - element to rank
     * @return rank or <code>null</code> if element does not exist
     */
    RFuture<Integer> rankAsync(String o);

    /**
     * Returns values by rank range. Indexes are zero based. 
     * <code>-1</code> means the highest score, <code>-2</code> means the second highest score.
     * 
     * @param startIndex - start index 
     * @param endIndex - end index
     * @return collection of elements
     */
    RFuture<Collection<String>> rangeAsync(int startIndex, int endIndex);
    
    /**
     * Returns rank of value, with the scores ordered from high to low.
     * 
     * @param o - value
     * @return rank or <code>null</code> if value does not exist
     */
    RFuture<Integer> revRankAsync(String o);

}
