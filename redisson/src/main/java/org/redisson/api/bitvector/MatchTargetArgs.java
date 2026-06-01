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
package org.redisson.api.bitvector;

/**
 * Intermediate stage in the construction of {@link MatchExactArgs}. Produced by
 * {@link MatchExactArgs#mask(long)} and consumed by {@link #target(long)}, which
 * yields a fully-constructed {@link MatchExactArgs} ready for use with
 * {@link org.redisson.api.RBitVectorStore#matchExact(MatchExactArgs)} or further
 * tuning.
 * <p>
 * This staged construction enforces at compile time that both a mask and a target
 * are supplied for an exact-match query.
 *
 * @see MatchExactArgs
 *
 * @author Nikita Koksharov
 *
 */
public interface MatchTargetArgs {

    /**
     * Sets the target bit pattern to match against the bits selected by the mask.
     * The completed predicate is {@code (vector & mask) == target}.
     * <p>
     * If {@code value} has any bits set outside of the previously-supplied mask,
     * the predicate is unsatisfiable and a subsequent query will produce an empty
     * result.
     *
     * @param value the target bit pattern within the masked positions
     * @return a fully-constructed {@link MatchExactArgs} ready to use or further configure
     */
    MatchExactArgs target(long value);

}