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