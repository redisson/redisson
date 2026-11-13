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
 * Arguments for {@link org.redisson.api.RScoredSortedSet#move(ScoredSortedSetMoveArgs)},
 * which removes entries from this sorted set and adds them, with their scores,
 * to the destination sorted set as a single atomic operation.
 *
 * @author Nikita Koksharov
 *
 */
public interface ScoredSortedSetMoveArgs {

    /**
     * Defines the name of the destination sorted set.
     *
     * @param name name of the destination sorted set
     * @return arguments object
     */
    static ScoredSortedSetMoveSelection to(String name) {
        return new ScoredSortedSetMoveParams(name);
    }

}
