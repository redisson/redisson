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

import io.netty.util.concurrent.Future;

public interface RLexSortedSetAsync extends RCollectionAsync<String> {

    Future<Integer> removeRangeByLexAsync(String fromElement, boolean fromInclusive, String toElement, boolean toInclusive);

    Future<Integer> removeRangeTailByLexAsync(String fromElement, boolean fromInclusive);

    Future<Integer> removeRangeHeadByLexAsync(String toElement, boolean toInclusive);

    Future<Integer> lexCountTailAsync(String fromElement, boolean fromInclusive);

    Future<Integer> lexCountHeadAsync(String toElement, boolean toInclusive);

    Future<Collection<String>> lexRangeTailAsync(String fromElement, boolean fromInclusive);

    Future<Collection<String>> lexRangeHeadAsync(String toElement, boolean toInclusive);

    Future<Collection<String>> lexRangeAsync(String fromElement, boolean fromInclusive, String toElement, boolean toInclusive);

    Future<Integer> lexCountAsync(String fromElement, boolean fromInclusive, String toElement, boolean toInclusive);

    Future<Integer> rankAsync(String o);

    Future<Collection<String>> valueRangeAsync(int startIndex, int endIndex);

}
