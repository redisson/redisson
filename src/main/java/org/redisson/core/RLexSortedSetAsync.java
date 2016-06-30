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

import io.netty.util.concurrent.Future;

public interface RLexSortedSetAsync extends RCollectionAsync<String> {

    Future<String> pollLastAsync();

    Future<String> pollFirstAsync();

    Future<String> firstAsync();

    Future<String> lastAsync();
    
    /**
     * Read all values at once.
     * 
     * @return
     */
    Future<Collection<String>> readAllAsync();
    
    Future<Integer> removeRangeAsync(String fromElement, boolean fromInclusive, String toElement, boolean toInclusive);

    /**
     * Use {@link RLexSortedSetAsync#removeRangeAsync(String, boolean, String, boolean)}
     */
    @Deprecated
    Future<Integer> removeRangeByLexAsync(String fromElement, boolean fromInclusive, String toElement, boolean toInclusive);

    Future<Integer> removeRangeTailAsync(String fromElement, boolean fromInclusive);

    /**
     * Use {@link RLexSortedSetAsync#removeRangeTailAsync(String, boolean, String, boolean)}
     */
    @Deprecated
    Future<Integer> removeRangeTailByLexAsync(String fromElement, boolean fromInclusive);

    Future<Integer> removeRangeHeadAsync(String toElement, boolean toInclusive);

    /**
     * Use {@link RLexSortedSetAsync#removeRangeHeadAsync(String, boolean)}
     */
    @Deprecated
    Future<Integer> removeRangeHeadByLexAsync(String toElement, boolean toInclusive);

    Future<Integer> countTailAsync(String fromElement, boolean fromInclusive);

    /**
     * Use {@link RLexSortedSetAsync#countTailAsync(String, boolean)}
     */
    @Deprecated
    Future<Integer> lexCountTailAsync(String fromElement, boolean fromInclusive);

    Future<Integer> countHeadAsync(String toElement, boolean toInclusive);

    /**
     * Use {@link RLexSortedSetAsync#countHeadAsync(String, boolean)}
     */
    @Deprecated
    Future<Integer> lexCountHeadAsync(String toElement, boolean toInclusive);

    Future<Collection<String>> rangeTailAsync(String fromElement, boolean fromInclusive);

    /**
     * Use {@link RLexSortedSetAsync#rangeTailAsync(String, boolean)}
     */
    @Deprecated
    Future<Collection<String>> lexRangeTailAsync(String fromElement, boolean fromInclusive);

    Future<Collection<String>> rangeHeadAsync(String toElement, boolean toInclusive);

    /**
     * Use {@link RLexSortedSetAsync#rangeHeadAsync(String, boolean)}
     */
    @Deprecated
    Future<Collection<String>> lexRangeHeadAsync(String toElement, boolean toInclusive);

    Future<Collection<String>> rangeAsync(String fromElement, boolean fromInclusive, String toElement, boolean toInclusive);

    /**
     * Use {@link RLexSortedSetAsync#rangeAsync(String, boolean, String, boolean)}
     */
    @Deprecated
    Future<Collection<String>> lexRangeAsync(String fromElement, boolean fromInclusive, String toElement, boolean toInclusive);

    Future<Collection<String>> rangeTailAsync(String fromElement, boolean fromInclusive, int offset, int count);
    
    /**
     * Use {@link RLexSortedSetAsync#rangeTailAsync(String, boolean, int, int)}
     */
    @Deprecated
    Future<Collection<String>> lexRangeTailAsync(String fromElement, boolean fromInclusive, int offset, int count);

    Future<Collection<String>> rangeHeadAsync(String toElement, boolean toInclusive, int offset, int count);

    /**
     * Use {@link RLexSortedSetAsync#rangeHeadAsync(String, boolean, int, int)}
     */
    @Deprecated
    Future<Collection<String>> lexRangeHeadAsync(String toElement, boolean toInclusive, int offset, int count);

    Future<Collection<String>> rangeAsync(String fromElement, boolean fromInclusive, String toElement, boolean toInclusive, int offset, int count);

    /**
     * Use {@link RLexSortedSetAsync#rangeAsync(String, boolean, String, boolean, int, int)}
     */
    @Deprecated
    Future<Collection<String>> lexRangeAsync(String fromElement, boolean fromInclusive, String toElement, boolean toInclusive, int offset, int count);

    Future<Integer> countAsync(String fromElement, boolean fromInclusive, String toElement, boolean toInclusive);

    /**
     * Use {@link RLexSortedSetAsync#countAsync(String, boolean, String, boolean)}
     */
    @Deprecated
    Future<Integer> lexCountAsync(String fromElement, boolean fromInclusive, String toElement, boolean toInclusive);

    Future<Integer> rankAsync(String o);

    Future<Collection<String>> rangeAsync(int startIndex, int endIndex);

    /**
     * Use {@link RLexSortedSetAsync#rangeAsync(int, int)}
     */
    @Deprecated
    Future<Collection<String>> valueRangeAsync(int startIndex, int endIndex);
    
    /**
     * Returns rank of value, with the scores ordered from high to low.
     * 
     * @param o
     * @return rank or <code>null</code> if value does not exist
     */
    Future<Integer> revRankAsync(String o);

}
