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

import java.time.Duration;

/**
 * Argument builder for the
 * {@link org.redisson.api.RBitVectorStore#matchExact(MatchExactArgs) matchExact} query.
 * <p>
 * Carries the required mask and target plus optional iteration-tuning parameters
 * that control server-side batching during result iteration. The exact-match
 * predicate is {@code (vector & mask) == target}: bits outside the mask are
 * ignored, and bits inside the mask must equal the corresponding bits of
 * {@code target}.
 * <pre>{@code
 *   MatchExactArgs args = MatchExactArgs.mask(0b101001L)
 *                                       .target(0b100001L)
 *                                       .chunkSize(2048)
 *                                       .chunkFetchTTL(Duration.ofMinutes(2));
 * }</pre>
 * <p>
 * If {@code target} has any bits set outside of {@code mask}, the predicate is
 * unsatisfiable and the query will produce an empty result.
 *
 * @see MatchArgs
 * @see MatchTargetArgs
 *
 * @author Nikita Koksharov
 *
 */
public interface MatchExactArgs {

    /**
     * Begins construction by setting the bitmask. The mask selects which bit
     * positions participate in the equality check; bits outside the mask are
     * ignored during matching.
     * <p>
     * Returns a {@link MatchTargetArgs} stage which must be completed by calling
     * {@link MatchTargetArgs#target(long)} to obtain a usable {@code MatchExactArgs}.
     *
     * @param value the bitmask
     * @return the next builder stage, awaiting a target value
     */
    static MatchTargetArgs mask(long value) {
        return new MatchExactParams(value);
    }

    /**
     * Sets the number of keys fetched per server round-trip during result iteration.
     *
     * @param value the batch size; must be positive
     * @return this builder, for chaining
     */
    MatchExactArgs chunkSize(int value);

    /**
     * Sets the time-to-live applied to the server-side iteration state created by
     * the query. This is a safety net: if the caller abandons the iterator without
     * consuming it fully (or the JVM dies mid-iteration), the server-side state
     * will be reclaimed automatically once the TTL expires.
     *
     * @param value the TTL applied to server-side iteration state
     * @return this builder, for chaining
     */
    MatchExactArgs chunkFetchTTL(Duration value);

}