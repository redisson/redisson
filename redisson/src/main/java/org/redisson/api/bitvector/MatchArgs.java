package org.redisson.api.bitvector;

import org.redisson.api.RBitVectorStore;

import java.time.Duration;

/**
 * Argument builder for the
 * {@link RBitVectorStore#matchAll(MatchArgs) matchAll},
 * {@link RBitVectorStore#matchAny(MatchArgs) matchAny}, and
 * {@link RBitVectorStore#matchNone(MatchArgs) matchNone} queries.
 * <p>
 * Carries the required bitmask plus optional iteration-tuning parameters that
 * control server-side batching during result iteration.
 * <p>
 * Construct with {@link #mask(long)} and chain further configuration:
 * <pre>{@code
 *   MatchArgs args = MatchArgs.mask(0b101001L)
 *                             .chunkSize(2048)
 *                             .chunkFetchTTL(Duration.ofMinutes(2));
 * }</pre>
 *
 * @see MatchExactArgs
 *
 * @author Nikita Koksharov
 *
 */
public interface MatchArgs {

    /**
     * Creates a new {@code MatchArgs} with the given bitmask. The mask selects
     * which bit positions participate in the query predicate; its precise role
     * depends on which match method it is passed to:
     * <ul>
     *   <li>{@code matchAll(args)} — every set bit of the mask must be set in the vector</li>
     *   <li>{@code matchAny(args)} — at least one set bit of the mask must be set in the vector</li>
     *   <li>{@code matchNone(args)} — no set bit of the mask may be set in the vector</li>
     * </ul>
     *
     * @param value the bitmask
     * @return a new {@code MatchArgs} carrying the mask and default tuning values
     */
    static MatchArgs mask(long value) {
        return new MatchParams(value);
    }

    /**
     * Sets the number of keys fetched per server round-trip during result iteration.
     *
     * @param value the batch size; must be positive
     * @return this builder, for chaining
     */
    MatchArgs chunkSize(int value);

    /**
     * Sets the time-to-live applied to the server-side iteration state created by
     * the query. This is a safety net: if the caller abandons the iterator without
     * consuming it fully (or the JVM dies mid-iteration), the server-side state
     * will be reclaimed automatically once the TTL expires.
     *
     * @param value the TTL applied to server-side iteration state
     * @return this builder, for chaining
     */
    MatchArgs chunkFetchTTL(Duration value);

}