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
import java.util.Set;

public interface RLexSortedSet extends RLexSortedSetAsync, Set<String>, RExpirable {

    Integer lexCountTail(String fromElement, boolean fromInclusive);

    Integer lexCountHead(String toElement, boolean toInclusive);

    Collection<String> lexRangeTail(String fromElement, boolean fromInclusive);

    Collection<String> lexRangeHead(String toElement, boolean toInclusive);

    Collection<String> lexRange(String fromElement, boolean fromInclusive, String toElement, boolean toInclusive);

    Integer lexCount(String fromElement, boolean fromInclusive, String toElement, boolean toInclusive);

    Integer rank(String o);

    Collection<String> valueRange(int startIndex, int endIndex);

}
