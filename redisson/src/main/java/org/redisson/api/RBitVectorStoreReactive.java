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
package org.redisson.api;

import org.redisson.api.annotation.EmptyAsAbsent;
import org.redisson.api.bitvector.MatchArgs;
import org.redisson.api.bitvector.MatchExactArgs;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Set;

/**
 * Distributed store of 64-bit vectors mapped by keys,
 * with bitmask-based filtering.
 * <p>
 * This object is thread-safe.
 *
 * @param <K> the type of keys identifying stored vectors
 *
 * @author Nikita Koksharov
 *
 */
public interface RBitVectorStoreReactive<K> extends RExpirable, RBitVectorStoreAsync<K> {

    /**
     * Returns {@code true} if a vector is stored under the given key.
     *
     * @param id the key to test
     * @return {@code true} if a vector exists for {@code id}, {@code false} otherwise
     */
    Mono<Boolean> contains(K id);

    /**
     * Returns the number of vectors currently stored.
     *
     * @return the count of stored entries
     */
    Mono<Long> size();

    /**
     * Returns the vector stored under the given key, or {@code null} if no vector
     * is stored for that key.
     *
     * @param id the key whose vector to retrieve
     * @return the stored vector, or {@code null} if {@code id} is not present
     */
    Mono<Long> get(K id);

    /**
     * Bulk variant of {@link #get(Object)}. Retrieves the vectors for the given keys
     * in a single operation. Keys without a stored vector are absent from the returned
     * map (rather than mapped to {@code null}).
     *
     * @param ids the keys to look up
     * @return a map from present keys to their vectors; never {@code null}
     */
    @EmptyAsAbsent
    Mono<Map<K, Long>> get(Set<K> ids);

    /**
     * Stores a vector under the given key, overwriting any previous value.
     *
     * @param id     the key under which to store
     * @param vector the 64-bit vector value
     * @return the previously stored vector, or {@code null} if {@code id} was new
     */
    Mono<Long> put(K id, long vector);

    /**
     * Bulk variant of {@link #put(Object, long)}. Stores all entries in a single
     * pipelined operation. Existing keys are overwritten.
     *
     * @param entries the keys and vectors to store
     */
    Mono<Void> put(Map<K, Long> entries);

    /**
     * Removes the vector stored under the given key.
     *
     * @param id the key to remove
     * @return {@code true} if a vector was present and removed, {@code false} if
     *         {@code id} was not present
     */
    Mono<Boolean> remove(K id);

    /**
     * Bulk variant of {@link #remove(Object)}. Removes vectors for all given keys
     * in a single operation.
     *
     * @param ids the keys to remove
     * @return the number of entries actually removed (may be less than
     *         {@code ids.size()} if some keys were not present)
     */
    Mono<Long> remove(Set<K> ids);

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
    Flux<K> ids();

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
    Flux<K> ids(int chunkSize);

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
    Mono<Long> countMatchAll(long mask);

    /**
     * Returns the number of stored vectors {@code v} such that {@code (v & mask) != 0}
     * — i.e. vectors that have at least one bit of {@code mask} set.
     * <p>
     * When {@code mask == 0L} the predicate is trivially false and the result is {@code 0}.
     *
     * @param mask the bitmask to test against
     * @return the count of matching vectors
     */
    Mono<Long> countMatchAny(long mask);

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
    Mono<Long> countMatchNone(long mask);

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
    Mono<Long> countMatchExact(long mask, long target);

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
    Flux<K> matchExact(MatchExactArgs args);

    /**
     * Returns an {@link Iterable} of keys whose vectors {@code v} satisfy
     * {@code (v & mask) == mask} — vector contains every bit set in the mask.
     * <p>
     * See {@link #matchExact(MatchExactArgs)} for iteration semantics and tuning.
     *
     * @param args the mask and iteration parameters
     * @return an iterable over keys whose vectors match the predicate
     */
    Flux<K> matchAll(MatchArgs args);

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
    Flux<K> matchAny(MatchArgs args);

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
    Flux<K> matchNone(MatchArgs args);

    /**
     * Atomically updates the vector under {@code id} to
     * {@code (old & ~clearMask) | setMask} and returns the new value.
     * <p>
     * Bits set in {@code setMask} become {@code 1}; bits set in {@code clearMask}
     * become {@code 0}; all other bits are unchanged. If a bit is set in both
     * masks, {@code setMask} takes precedence and the bit becomes {@code 1} —
     * this follows directly from the formula above.
     * <p>
     * If {@code id} is absent, a new entry is created with {@code old} treated
     * as {@code 0L}, so the result is {@code setMask}.
     * <p>
     * The pair {@code (setMask = 0L, clearMask = 0L)} is a no-op: no write is
     * performed, no entry is created for absent ids, and the method returns the
     * current stored value (or {@code 0L} if {@code id} is absent).
     *
     * @param id        the key to update
     * @param setMask   bits to set to {@code 1}
     * @param clearMask bits to clear to {@code 0} (overridden by {@code setMask} on overlap)
     * @return the new vector value after the update, or the current value if the
     *         call is a no-op
     */
    Mono<Long> updateAndGet(K id, long setMask, long clearMask);

    /**
     * Atomically updates the vector under {@code id} to
     * {@code (old & ~clearMask) | setMask} and returns the previous value.
     * <p>
     * Has the same effect on stored state as
     * {@link #updateAndGet(Object, long, long)}; differs only in the return value.
     * If {@code id} is absent, a new entry is created with value {@code setMask}
     * and {@code null} is returned.
     * <p>
     * The pair {@code (setMask = 0L, clearMask = 0L)} is a no-op: no write is
     * performed, no entry is created for absent ids, and the method returns the
     * current stored value (or {@code null} if {@code id} is absent).
     *
     * @param id        the key to update
     * @param setMask   bits to set to {@code 1}
     * @param clearMask bits to clear to {@code 0} (overridden by {@code setMask} on overlap)
     * @return the previously stored vector, or {@code null} if {@code id} was absent
     */
    Mono<Long> getAndUpdate(K id, long setMask, long clearMask);

    /**
     * Atomically applies {@code new = old | mask} to the vector under {@code id}
     * — sets every bit of {@code mask} to {@code 1}, leaving other bits unchanged.
     * <p>
     * If {@code id} is absent, a new entry is created with value {@code mask}.
     * Equivalent to {@code updateAndGet(id, mask, 0L)}.
     *
     * @param id   the key to update
     * @param mask bits to set to {@code 1}
     * @return the new vector value after the update
     */
    Mono<Long> setBits(K id, long mask);

    /**
     * Atomically applies {@code new = old & ~mask} to the vector under {@code id}
     * — clears every bit of {@code mask} to {@code 0}, leaving other bits unchanged.
     * <p>
     * If {@code id} is absent, a new entry is created with value {@code 0L}.
     * Equivalent to {@code updateAndGet(id, 0L, mask)}.
     *
     * @param id   the key to update
     * @param mask bits to clear to {@code 0}
     * @return the new vector value after the update
     */
    Mono<Long> clearBits(K id, long mask);

    /**
     * Atomically applies {@code new = old ^ mask} to the vector under {@code id}
     * — flips every bit selected by {@code mask}, leaving other bits unchanged.
     * <p>
     * If {@code id} is absent, a new entry is created with value {@code mask}.
     *
     * @param id   the key to update
     * @param mask bits to flip
     * @return the new vector value after the update
     */
    Mono<Long> flipBits(K id, long mask);

    /**
     * Atomically replaces the bits of the vector under {@code id} selected by
     * {@code mask} with the corresponding bits of {@code value}, leaving bits
     * outside {@code mask} unchanged: {@code new = (old & ~mask) | (value & mask)}.
     * <p>
     * Bits of {@code value} outside {@code mask} are ignored. If {@code id} is
     * absent, a new entry is created with value {@code value & mask}.
     *
     * @param id    the key to update
     * @param mask  the bitmask selecting which bits to replace
     * @param value the source bits for the masked positions
     * @return the new vector value after the update
     */
    Mono<Long> replaceBits(K id, long mask, long value);

    /**
     * Returns the current value of the bit at position {@code bit} in the vector
     * stored under {@code id}, or {@code null} if {@code id} is not present.
     *
     * @param id  the key to read
     * @param bit the bit position, in {@code [0, 63]}
     * @return the bit value, or {@code null} if {@code id} is not present
     * @throws IndexOutOfBoundsException if {@code bit} is outside {@code [0, 63]}
     */
    Mono<Boolean> getBit(K id, int bit);

    /**
     * Atomically sets the bit at position {@code bit} to {@code 1} in the vector
     * stored under {@code id}.
     * <p>
     * If {@code id} is absent, a new entry is created with only this bit set.
     * Equivalent to {@code setBits(id, 1L << bit)}.
     *
     * @param id  the key to update
     * @param bit the bit position, in {@code [0, 63]}
     * @return the new vector value after the update
     * @throws IndexOutOfBoundsException if {@code bit} is outside {@code [0, 63]}
     */
    Mono<Long> setBit(K id, int bit);

    /**
     * Atomically sets the bit at position {@code bit} to {@code value} in the
     * vector stored under {@code id}.
     * <p>
     * If {@code id} is absent, a new entry is created with only this bit set to
     * {@code value} (i.e. value {@code 1L << bit} if {@code value} is {@code true},
     * else {@code 0L}).
     *
     * @param id    the key to update
     * @param bit   the bit position, in {@code [0, 63]}
     * @param value the value to assign to the bit
     * @return the new vector value after the update
     * @throws IndexOutOfBoundsException if {@code bit} is outside {@code [0, 63]}
     */
    Mono<Long> setBit(K id, int bit, boolean value);

    /**
     * Atomically clears the bit at position {@code bit} to {@code 0} in the
     * vector stored under {@code id}.
     * <p>
     * If {@code id} is absent, a new entry is created with value {@code 0L}.
     * Equivalent to {@code clearBits(id, 1L << bit)}.
     *
     * @param id  the key to update
     * @param bit the bit position, in {@code [0, 63]}
     * @return the new vector value after the update
     * @throws IndexOutOfBoundsException if {@code bit} is outside {@code [0, 63]}
     */
    Mono<Long> clearBit(K id, int bit);

    /**
     * Atomically flips the bit at position {@code bit} in the vector stored
     * under {@code id}.
     * <p>
     * If {@code id} is absent, a new entry is created with only this bit set
     * (value {@code 1L << bit}).
     *
     * @param id  the key to update
     * @param bit the bit position, in {@code [0, 63]}
     * @return the new vector value after the update
     * @throws IndexOutOfBoundsException if {@code bit} is outside {@code [0, 63]}
     */
    Mono<Long> flipBit(K id, int bit);

    /**
     * Atomically sets the bit at position {@code bit} to {@code 1} in the vector
     * stored under {@code id}, and returns the previous full vector value.
     * <p>
     * If {@code id} is absent, a new entry is created with only this bit set, and
     * {@code 0L} is returned to denote the "before" state.
     *
     * @param id  the key to update
     * @param bit the bit position, in {@code [0, 63]}
     * @return the previous vector value before the update
     * @throws IndexOutOfBoundsException if {@code bit} is outside {@code [0, 63]}
     */
    Mono<Long> getAndSetBit(K id, int bit);

    /**
     * Atomically sets the bit at position {@code bit} to {@code value} in the
     * vector stored under {@code id}, and returns the previous full vector value.
     * <p>
     * If {@code id} is absent, a new entry is created and {@code 0L} is returned
     * to denote the "before" state.
     *
     * @param id    the key to update
     * @param bit   the bit position, in {@code [0, 63]}
     * @param value the value to assign to the bit
     * @return the previous vector value before the update
     * @throws IndexOutOfBoundsException if {@code bit} is outside {@code [0, 63]}
     */
    Mono<Long> getAndSetBit(K id, int bit, boolean value);

    /**
     * Atomically flips the bit at position {@code bit} in the vector stored
     * under {@code id}, and returns the previous full vector value.
     * <p>
     * If {@code id} is absent, a new entry is created with only this bit set, and
     * {@code 0L} is returned to denote the "before" state.
     *
     * @param id  the key to update
     * @param bit the bit position, in {@code [0, 63]}
     * @return the previous vector value before the update
     * @throws IndexOutOfBoundsException if {@code bit} is outside {@code [0, 63]}
     */
    Mono<Long> getAndFlipBit(K id, int bit);

    /**
     * Atomically sets the vector under {@code id} to {@code update} if and only
     * if the currently stored vector equals {@code expected}.
     * <p>
     * If {@code id} is absent, the operation succeeds only when {@code expected}
     * is {@code 0L}, in which case a new entry is created with value {@code update}.
     *
     * @param id       the key to update
     * @param expected the expected current value
     * @param update   the value to set if the current value matches
     * @return {@code true} if the value was updated, {@code false} otherwise
     */
    Mono<Boolean> compareAndSet(K id, long expected, long update);

    /**
     * Atomically replaces the bits selected by {@code mask} of the vector under
     * {@code id} with the masked bits of {@code update}, if and only if the
     * masked bits of the current value equal the masked bits of {@code expected}.
     * <p>
     * The comparison and update both apply only to bits set in {@code mask};
     * bits outside the mask are ignored on the comparison side and left unchanged
     * on the update side:
     * <pre>{@code
     *   if ((current & mask) == (expected & mask)) {
     *       new = (current & ~mask) | (update & mask);
     *       return true;
     *   }
     *   return false;
     * }</pre>
     * <p>
     * If {@code id} is absent, the operation succeeds only when
     * {@code (expected & mask) == 0L}, in which case a new entry is created with
     * value {@code update & mask}.
     *
     * @param id       the key to update
     * @param mask     the bitmask selecting which bits participate
     * @param expected the expected current value within the masked positions
     * @param update   the new value within the masked positions
     * @return {@code true} if the value was updated, {@code false} otherwise
     */
    Mono<Boolean> compareAndSetBits(K id, long mask, long expected, long update);

    /**
     * Atomically stores {@code vector} under {@code id} only if {@code id} is not
     * already present.
     * <p>
     * Follows {@link Map#putIfAbsent(Object, Object)} convention: returns the
     * currently stored value (preventing the put) if {@code id} was already present,
     * or {@code null} if the put succeeded.
     *
     * @param id     the key
     * @param vector the vector to store if absent
     * @return the existing vector if {@code id} was already present, or
     *         {@code null} if the vector was stored
     */
    Mono<Long> putIfAbsent(K id, long vector);

    /**
     * Atomically stores {@code vector} under {@code id} only if {@code id} is
     * already present.
     * <p>
     * Returns the previously stored vector if the update succeeded, or
     * {@code null} if {@code id} was absent and no update was performed.
     *
     * @param id     the key
     * @param vector the vector to store if present
     * @return the previously stored vector if the update succeeded, or
     *         {@code null} if {@code id} was absent
     */
    Mono<Long> putIfExists(K id, long vector);

    /**
     * Atomically applies {@code new = (old & ~clearMask) | setMask} to the vector
     * under {@code id} only if {@code id} is already present.
     * <p>
     * Differs from {@link #updateAndGet(Object, long, long)} in that absent ids
     * are left untouched rather than upserted, and {@code null} is returned to
     * signal that no update was performed. The set-wins semantics on overlapping
     * masks are identical to {@link #updateAndGet(Object, long, long)}.
     *
     * @param id        the key to update
     * @param setMask   bits to set to {@code 1}
     * @param clearMask bits to clear to {@code 0} (overridden by {@code setMask} on overlap)
     * @return the new vector value if the update was applied, or {@code null} if
     *         {@code id} was absent
     */
    Mono<Long> updateIfExists(K id, long setMask, long clearMask);

    /**
     * Bulk variant of {@link #setBits(Object, long)}. Applies {@code new = old | mask}
     * to every key in {@code ids} that is currently present in the store, in a
     * single operation. Absent keys are skipped (this method does not upsert).
     *
     * @param ids  the keys to update
     * @param mask bits to set to {@code 1} on each present key
     * @return the number of entries actually updated (may be less than
     *         {@code ids.size()} if some keys were not present)
     */
    Mono<Long> setBits(Set<K> ids, long mask);
    
}