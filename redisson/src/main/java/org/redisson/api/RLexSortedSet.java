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

/**
 * Sorted set contained values of String type
 * 
 * @author Nikita Koksharov
 *
 */
public interface RLexSortedSet extends RLexSortedSetAsync, RSortedSet<String>, RExpirable {
    
    String pollFirst();

    String pollLast();

    /**
     * Returns rank of value, with the scores ordered from high to low.
     * 
     * @param o - object
     * @return rank or <code>null</code> if value does not exist
     */
    Integer revRank(String o);
    
    int removeRangeTail(String fromElement, boolean fromInclusive);
    
    int removeRangeHead(String toElement, boolean toInclusive);

    int removeRange(String fromElement, boolean fromInclusive, String toElement, boolean toInclusive);

    int countTail(String fromElement, boolean fromInclusive);

    int countHead(String toElement, boolean toInclusive);

    Collection<String> rangeTail(String fromElement, boolean fromInclusive);

    Collection<String> rangeHead(String toElement, boolean toInclusive);

    Collection<String> range(String fromElement, boolean fromInclusive, String toElement, boolean toInclusive);

    Collection<String> rangeTail(String fromElement, boolean fromInclusive, int offset, int count);

    Collection<String> rangeHead(String toElement, boolean toInclusive, int offset, int count);

    Collection<String> range(String fromElement, boolean fromInclusive, String toElement, boolean toInclusive, int offset, int count);

    Collection<String> rangeTailReversed(String fromElement, boolean fromInclusive);

    Collection<String> rangeHeadReversed(String toElement, boolean toInclusive);

    Collection<String> rangeReversed(String fromElement, boolean fromInclusive, String toElement, boolean toInclusive);

    Collection<String> rangeTailReversed(String fromElement, boolean fromInclusive, int offset, int count);

    Collection<String> rangeHeadReversed(String toElement, boolean toInclusive, int offset, int count);

    Collection<String> rangeReversed(String fromElement, boolean fromInclusive, String toElement, boolean toInclusive, int offset, int count);
    
    int count(String fromElement, boolean fromInclusive, String toElement, boolean toInclusive);

    Integer rank(String o);

    Collection<String> range(int startIndex, int endIndex);

}
