package org.redisson.api;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.redisson.api.bitvector.MatchArgs;
import org.redisson.api.bitvector.MatchExactArgs;

import java.util.Map;
import java.util.Set;

/**
 * Distributed store of fixed-width 64-bit vectors mapped by keys,
 * with bitmask-based filtering.
 * <p>
 * This object is thread-safe.
 *
 * @param <K> the type of keys identifying stored vectors
 *
 * @author Nikita Koksharov
 *
 */
public interface RBitVectorStoreRx<K> extends RExpirableRx {

    /**
     * Returns {@code true} if a vector is stored under the given key.
     *
     * @param id the key to test
     * @return {@code true} if a vector exists for {@code id}, {@code false} otherwise
     */
    Single<Boolean> contains(K id);

    /**
     * Returns the number of vectors currently stored.
     *
     * @return the count of stored entries
     */
    Single<Long> size();

    /**
     * Returns the vector stored under the given key, or {@code null} if no vector
     * is stored for that key.
     *
     * @param id the key whose vector to retrieve
     * @return the stored vector, or {@code null} if {@code id} is not present
     */
    Maybe<Long> get(K id);

    /**
     * Bulk variant of {@link #get(Object)}. Retrieves the vectors for the given keys
     * in a single operation. Keys without a stored vector are absent from the returned
     * map (rather than mapped to {@code null}).
     *
     * @param ids the keys to look up
     * @return a map from present keys to their vectors; never {@code null}
     */
    Maybe<Map<K, Long>> get(Set<K> ids);

    /**
     * Stores a vector under the given key, overwriting any previous value.
     *
     * @param id     the key under which to store
     * @param vector the 64-bit vector value
     * @return the previously stored vector, or {@code null} if {@code id} was new
     */
    Maybe<Long> put(K id, long vector);

    /**
     * Bulk variant of {@link #put(Object, long)}. Stores all entries in a single
     * pipelined operation. Existing keys are overwritten.
     *
     * @param entries the keys and vectors to store
     */
    Completable put(Map<K, Long> entries);

    /**
     * Removes the vector stored under the given key.
     *
     * @param id the key to remove
     * @return {@code true} if a vector was present and removed, {@code false} if
     *         {@code id} was not present
     */
    Single<Boolean> remove(K id);

    /**
     * Bulk variant of {@link #remove(Object)}. Removes vectors for all given keys
     * in a single operation.
     *
     * @param ids the keys to remove
     * @return the number of entries actually removed (may be less than
     *         {@code ids.size()} if some keys were not present)
     */
    Single<Long> remove(Set<K> ids);

    /**
     * Returns an {@link Iterable} over all keys currently stored, in ascending order
     * of internal allocation. The returned {@code Iterable} is single-pass; calling
     * {@link Iterable#iterator()} more than once is not supported and may yield
     * undefined results.
     * <p>
     * Iteration is performed server-side in batches and is safe for stores of any
     * size; results reflect a non-strict snapshot — concurrent modifications during
     * iteration may or may not be visible.
     *
     * @return an iterable over all stored keys
     */
    Flowable<K> ids();

    /**
     * Equivalent to {@link #ids()} but with a caller-specified batch size used when
     * fetching keys from the server.
     * <p>
     * Larger values reduce round-trips at the cost of more memory per batch and
     * longer per-request server work. Smaller values reduce memory and per-request
     * latency at the cost of more round-trips. A reasonable range is 100 – 10000.
     *
     * @param chunkSize the number of keys to fetch per round-trip
     * @return an iterable over all stored keys
     */
    Flowable<K> ids(int chunkSize);

    /**
     * Returns the number of stored vectors {@code v} such that {@code (v & mask) == mask}
     * — i.e. vectors that have every bit of {@code mask} set.
     * <p>
     * When {@code mask == 0L} the predicate is trivially true and the result equals
     * {@link #size()}.
     *
     * @param mask the bitmask to test against
     * @return the count of matching vectors
     */
    Flowable<Long> countMatchAll(long mask);

    /**
     * Returns the number of stored vectors {@code v} such that {@code (v & mask) != 0}
     * — i.e. vectors that have at least one bit of {@code mask} set.
     * <p>
     * When {@code mask == 0L} the predicate is trivially false and the result is {@code 0}.
     *
     * @param mask the bitmask to test against
     * @return the count of matching vectors
     */
    Flowable<Long> countMatchAny(long mask);

    /**
     * Returns the number of stored vectors {@code v} such that {@code (v & mask) == 0}
     * — i.e. vectors that have none of the bits of {@code mask} set.
     * <p>
     * When {@code mask == 0L} the predicate is trivially true and the result equals
     * {@link #size()}.
     *
     * @param mask the bitmask to test against
     * @return the count of matching vectors
     */
    Single<Long> countMatchNone(long mask);

    /**
     * Returns the number of stored vectors {@code v} such that {@code (v & mask) == target}
     * — i.e. vectors that match {@code target} exactly on the bits selected by {@code mask}.
     * Bits outside {@code mask} are ignored.
     * <p>
     * If {@code target} has any bits set outside of {@code mask}, the predicate is
     * unsatisfiable and the result is {@code 0}. When {@code mask == 0L} and
     * {@code target == 0L}, the predicate is trivially true and the result equals
     * {@link #size()}.
     *
     * @param mask   the bitmask selecting which bits to compare
     * @param target the required bit pattern within the masked positions
     * @return the count of matching vectors
     */
    Single<Long> countMatchExact(long mask, long target);

    /**
     * Returns an {@link Iterable} of keys whose vectors {@code v} satisfy
     * {@code (v & mask) == target} — exact match on the bits selected by the mask.
     * <p>
     * The {@code MatchExactArgs} builder controls the mask, target, and iteration
     * tuning parameters; see {@link MatchExactArgs}. Construct with:
     * <pre>{@code
     *   MatchExactArgs.mask(mask).target(target);
     *   MatchExactArgs.mask(mask).target(target).chunkSize(2048);
     * }</pre>
     * <p>
     * The returned {@code Iterable} is single-pass; calling {@link Iterable#iterator()}
     * more than once is not supported.
     * <p>
     * Iteration walks the result server-side in batches. The query result is held
     * in temporary server-side state for the duration of iteration; abandoning the
     * iterator without consuming it fully relies on the configured TTL (see
     * {@link MatchExactArgs#chunkFetchTTL(java.time.Duration)}) to clean up.
     *
     * @param args the mask, target, and iteration parameters
     * @return an iterable over keys whose vectors match the predicate
     */
    Flowable<K> matchExact(MatchExactArgs args);

    /**
     * Returns an {@link Iterable} of keys whose vectors {@code v} satisfy
     * {@code (v & mask) == mask} — vector contains every bit set in the mask.
     * <p>
     * See {@link #matchExact(MatchExactArgs)} for iteration semantics and tuning.
     *
     * @param args the mask and iteration parameters
     * @return an iterable over keys whose vectors match the predicate
     */
    Flowable<K> matchAll(MatchArgs args);

    /**
     * Returns an {@link Iterable} of keys whose vectors {@code v} satisfy
     * {@code (v & mask) != 0} — vector contains at least one bit of the mask.
     * <p>
     * Results can be large for broad masks (in the limit, every stored vector matches).
     * Consider checking {@link #countMatchAny(long)} first when result size matters,
     * or use {@code chunkSize} to control memory pressure during iteration.
     * <p>
     * See {@link #matchExact(MatchExactArgs)} for iteration semantics and tuning.
     *
     * @param args the mask and iteration parameters
     * @return an iterable over keys whose vectors match the predicate
     */
    Flowable<K> matchAny(MatchArgs args);

    /**
     * Returns an {@link Iterable} of keys whose vectors {@code v} satisfy
     * {@code (v & mask) == 0} — vector has none of the bits set in the mask.
     * <p>
     * Results can be large for sparse masks (in the limit, every stored vector matches).
     * Consider checking {@link #countMatchNone(long)} first when result size matters.
     * <p>
     * See {@link #matchExact(MatchExactArgs)} for iteration semantics and tuning.
     *
     * @param args the mask and iteration parameters
     * @return an iterable over keys whose vectors match the predicate
     */
    Flowable<K> matchNone(MatchArgs args);

}