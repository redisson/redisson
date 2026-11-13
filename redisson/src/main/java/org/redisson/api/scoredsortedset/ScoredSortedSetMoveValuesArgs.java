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

/**
 * Arguments for moving the given values,
 * defined by {@link ScoredSortedSetMoveSelection#values(Object...)}.
 *
 * @author Nikita Koksharov
 *
 */
public interface ScoredSortedSetMoveValuesArgs extends ScoredSortedSetMoveArgs {

    /**
     * Moves only the values the destination doesn't hold.
     * A value the destination holds stays in this sorted set, with its score.
     * <p>
     * Equivalent to the {@code NX} option.
     *
     * @return arguments object
     */
    ScoredSortedSetMoveArgs ifAbsent();

    /**
     * Moves only the values the destination holds already, replacing their score there.
     * A value the destination doesn't hold stays in this sorted set, with its score,
     * and a destination which doesn't exist isn't created.
     * <p>
     * Equivalent to the {@code XX} option.
     *
     * @return arguments object
     */
    ScoredSortedSetMoveArgs ifExists();

}
