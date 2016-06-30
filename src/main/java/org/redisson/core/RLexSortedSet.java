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
import java.util.Set;

public interface RLexSortedSet extends RLexSortedSetAsync, Set<String>, RExpirable {

    String pollFirst();

    String pollLast();

    String first();

    String last();

    /**
     * Returns rank of value, with the scores ordered from high to low.
     * 
     * @param o
     * @return rank or <code>null</code> if value does not exist
     */
    Integer revRank(String o);
    
    /**
     * Read all values at once.
     * 
     * @return
     */
    Collection<String> readAll();
    
    int removeRangeTail(String fromElement, boolean fromInclusive);
    
    /**
     * Use {@link RLexSortedSet#removeRangeTail(String, boolean)}
     */
    @Deprecated
    int removeRangeTailByLex(String fromElement, boolean fromInclusive);

    int removeRangeHead(String toElement, boolean toInclusive);

    /**
     * Use {@link RLexSortedSet#removeRangeHead(String, boolean)}
     */
    @Deprecated
    int removeRangeHeadByLex(String toElement, boolean toInclusive);

    int removeRange(String fromElement, boolean fromInclusive, String toElement, boolean toInclusive);

    /**
     * Use {@link RLexSortedSet#removeRange(String, boolean)}
     */
    @Deprecated
    int removeRangeByLex(String fromElement, boolean fromInclusive, String toElement, boolean toInclusive);

    int countTail(String fromElement, boolean fromInclusive);

    /**
     * Use {@link RLexSortedSet#countTail(String, boolean)}
     */
    @Deprecated
    int lexCountTail(String fromElement, boolean fromInclusive);

    int countHead(String toElement, boolean toInclusive);

    /**
     * Use {@link RLexSortedSet#countHead(String, boolean)}
     */
    @Deprecated
    int lexCountHead(String toElement, boolean toInclusive);

    Collection<String> rangeTail(String fromElement, boolean fromInclusive);

    /**
     * Use {@link RLexSortedSet#rangeTail(String, boolean)}
     */
    @Deprecated
    Collection<String> lexRangeTail(String fromElement, boolean fromInclusive);

    Collection<String> rangeHead(String toElement, boolean toInclusive);

    /**
     * Use {@link RLexSortedSet#rangeHead(String, boolean)}
     */
    @Deprecated
    Collection<String> lexRangeHead(String toElement, boolean toInclusive);

    Collection<String> range(String fromElement, boolean fromInclusive, String toElement, boolean toInclusive);

    /**
     * Use {@link RLexSortedSet#range(String, boolean, String, boolean)}
     */
    @Deprecated
    Collection<String> lexRange(String fromElement, boolean fromInclusive, String toElement, boolean toInclusive);

    Collection<String> rangeTail(String fromElement, boolean fromInclusive, int offset, int count);

    /**
     * Use {@link RLexSortedSet#rangeTail(String, boolean, int, int)}
     */
    @Deprecated
    Collection<String> lexRangeTail(String fromElement, boolean fromInclusive, int offset, int count);

    Collection<String> rangeHead(String toElement, boolean toInclusive, int offset, int count);

    /**
     * Use {@link RLexSortedSet#rangeHead(String, boolean, int, int)}
     */
    @Deprecated
    Collection<String> lexRangeHead(String toElement, boolean toInclusive, int offset, int count);

    Collection<String> range(String fromElement, boolean fromInclusive, String toElement, boolean toInclusive, int offset, int count);

    /**
     * Use {@link RLexSortedSet#range(String, boolean, String, boolean, int, int)}
     */
    @Deprecated
    Collection<String> lexRange(String fromElement, boolean fromInclusive, String toElement, boolean toInclusive, int offset, int count);

    int count(String fromElement, boolean fromInclusive, String toElement, boolean toInclusive);

    /**
     * Use {@link RLexSortedSet#count(String, boolean, String, boolean)}
     */
    @Deprecated
    int lexCount(String fromElement, boolean fromInclusive, String toElement, boolean toInclusive);

    Integer rank(String o);

    Collection<String> range(int startIndex, int endIndex);

    /**
     * Use {@link RLexSortedSet#range(int, int)}
     */
    @Deprecated
    Collection<String> valueRange(int startIndex, int endIndex);

}
