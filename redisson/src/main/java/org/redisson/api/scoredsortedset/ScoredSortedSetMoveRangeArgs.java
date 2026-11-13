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
 * Arguments for moving the entries scored within a range,
 * defined by {@link ScoredSortedSetMoveSelection#scoreRange(double, boolean, double, boolean)}.
 *
 * @author Nikita Koksharov
 *
 */
public interface ScoredSortedSetMoveRangeArgs extends ScoredSortedSetMoveArgs {

    /**
     * Defines the maximum amount of entries to move: the lowest scored entries of the range are moved.
     * <p>
     * Without it every entry in the range is moved by one command, so it bounds
     * a batch claimed from a range that may be large. Claiming the next batch is
     * the same call again, as the entries already moved are no longer in the range.
     * <p>
     * Equivalent to the {@code COUNT} option. The last call wins.
     *
     * @param count maximum amount of entries, greater than zero
     * @return arguments object
     * @throws IllegalArgumentException if count isn't greater than zero
     */
    ScoredSortedSetMoveRangeArgs count(int count);

}
