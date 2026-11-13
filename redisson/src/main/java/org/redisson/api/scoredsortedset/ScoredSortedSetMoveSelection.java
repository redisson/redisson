/**
 * Copyright (c) 2013-2026 Nikita Koksharov
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
package org.redisson.api.scoredsortedset;

import java.util.Collection;

/**
 * Step of {@link ScoredSortedSetMoveArgs} which selects the entries to move.
 *
 * @author Nikita Koksharov
 *
 */
public interface ScoredSortedSetMoveSelection {

    /**
     * Moves the lowest scored entries, returned in ascending score order.
     * Entries sharing a score come in ascending order of their encoded value.
     * <p>
     * A single entry is moved unless {@link ScoredSortedSetMoveEndArgs#count(int)} is defined.
     * <p>
     * Equivalent to {@code ZMOVE source destination MIN},
     * or to {@code BZMOVE source destination MIN timeout} if a timeout is passed to {@code move}.
     *
     * @return arguments object
     */
    ScoredSortedSetMoveEndArgs first();

    /**
     * Moves the highest scored entries, returned in descending score order.
     * Entries sharing a score come in descending order of their encoded value.
     * <p>
     * A single entry is moved unless {@link ScoredSortedSetMoveEndArgs#count(int)} is defined.
     * <p>
     * Equivalent to {@code ZMOVE source destination MAX},
     * or to {@code BZMOVE source destination MAX timeout} if a timeout is passed to {@code move}.
     *
     * @return arguments object
     */
    ScoredSortedSetMoveEndArgs last();

    /**
     * Moves the entries scored within the range, returned in ascending score order.
     * Entries sharing a score come in ascending order of their encoded value.
     * <p>
     * Every entry in the range is moved unless {@link ScoredSortedSetMoveRangeArgs#count(int)}
     * is defined. Use {@link Double#NEGATIVE_INFINITY} or {@link Double#POSITIVE_INFINITY}
     * for an unbounded end.
     * <p>
     * Equivalent to {@code ZMOVE source destination BYSCORE start end}.
     *
     * @param startScore start score
     * @param startScoreInclusive start score inclusive
     * @param endScore end score
     * @param endScoreInclusive end score inclusive
     * @return arguments object
     */
    ScoredSortedSetMoveRangeArgs scoreRange(double startScore, boolean startScoreInclusive,
                                            double endScore, boolean endScoreInclusive);

    /**
     * Moves the given values, returned in the order they are given.
     * A value this sorted set doesn't hold is skipped, and a repeated value is moved once.
     * <p>
     * As in {@link java.util.Collection#remove(Object)} any object is accepted:
     * it is encoded with the codec of this sorted set and looked up by its encoded form.
     * <p>
     * Equivalent to {@code ZMOVEMEMBER source destination MEMBERS numMembers member [member ...]}.
     *
     * @param values values to move
     * @return arguments object
     */
    ScoredSortedSetMoveValuesArgs values(Object... values);

    /**
     * Moves the given values, returned in the order they are given.
     * A value this sorted set doesn't hold is skipped, and a repeated value is moved once.
     * <p>
     * As in {@link java.util.Collection#removeAll(Collection)} any object is accepted:
     * it is encoded with the codec of this sorted set and looked up by its encoded form.
     * <p>
     * Equivalent to {@code ZMOVEMEMBER source destination MEMBERS numMembers member [member ...]}.
     *
     * @param values values to move
     * @return arguments object
     */
    ScoredSortedSetMoveValuesArgs values(Collection<?> values);

}
