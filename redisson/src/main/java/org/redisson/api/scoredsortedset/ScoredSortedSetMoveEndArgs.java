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
 * Arguments for moving the lowest or the highest scored entries,
 * defined by {@link ScoredSortedSetMoveSelection#first()} or {@link ScoredSortedSetMoveSelection#last()}.
 * <p>
 * The only arguments the blocking
 * {@link org.redisson.api.RScoredSortedSet#move(java.time.Duration, ScoredSortedSetMoveEndArgs)}
 * accepts, as {@code BZMOVE} can't select entries by score range or by value.
 *
 * @author Nikita Koksharov
 *
 */
public interface ScoredSortedSetMoveEndArgs extends ScoredSortedSetMoveArgs {

    /**
     * Defines the maximum amount of entries to move.
     * If this sorted set holds fewer entries then all of them are moved.
     * <p>
     * A blocking move waits for this sorted set to hold one entry,
     * not {@code count} of them, and then moves up to {@code count} entries.
     * <p>
     * Equivalent to the {@code COUNT} option. The last call wins.
     *
     * @param count maximum amount of entries, greater than zero
     * @return arguments object
     * @throws IllegalArgumentException if count isn't greater than zero
     */
    ScoredSortedSetMoveEndArgs count(int count);

}
