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

import org.redisson.api.countmin.CountMinInitArgs;
import org.redisson.api.countmin.CountMinMergeArgs;

import java.util.Collection;
import java.util.Map;

/**
 * Async interface for count-min sketch ({@code CMS.*} commands).
 *
 * @param <V> element type
 *
 * @author Nikita Koksharov
 *
 */
public interface RCountMinAsync<V> extends RExpirableAsync {

    /**
     * Initializes the sketch.
     * <p>
     * Equivalent to {@code CMS.INITBYDIM} or {@code CMS.INITBYPROB}
     * depending on the arguments.
     * <p>
     * A sketch is not created implicitly by other operations
     * and has to be initialized before use.
     *
     * @param args initialization arguments
     * @return void
     */
    RFuture<Void> initAsync(CountMinInitArgs args);

    /**
     * Increases the count of an element by 1.
     * <p>
     * Equivalent to {@code CMS.INCRBY}.
     * <p>
     * Overflow is handled as described for {@link #addAsync(Object, long)}.
     *
     * @param element element to count
     * @return new count of the element
     */
    RFuture<Long> addAsync(V element);

    /**
     * Changes the count of an element by the defined increment.
     * A negative increment decreases the count.
     * <p>
     * Equivalent to {@code CMS.INCRBY}.
     * <p>
     * The change is applied to all counters of the element or to none.
     * It fails with {@link org.redisson.client.RedisException} and leaves
     * the sketch unchanged if it would take one of them above the maximum
     * of the cell size (see {@link CountMinInitArgs#cellSize(int)}) or below 0,
     * or the total count of the sketch above {@link Long#MAX_VALUE}.
     * <p>
     * The count stays an upper bound of the real count only while no element
     * is decreased by more than was added for it. Counters are shared between
     * elements, so a larger decrease can succeed, and then it lowers the counts
     * of other elements too.
     * <p>
     * Negative increments require <b>Redis 8.12.0 or higher.</b>
     * Older versions reject them. Their counters saturate at {@code 2^32-1}
     * instead of rejecting an overflow, while {@link CountMinInfo#getCount()}
     * keeps accumulating.
     *
     * @param element element to count
     * @param increment value to add to the current count, negative to decrease it
     * @return new count of the element
     * @throws IllegalArgumentException if increment is {@link Long#MIN_VALUE}
     */
    RFuture<Long> addAsync(V element, long increment);

    /**
     * Changes the counts of multiple elements at once.
     * A negative increment decreases the count.
     * <p>
     * Equivalent to {@code CMS.INCRBY}.
     * <p>
     * Each element is changed on its own, as described for
     * {@link #addAsync(Object, long)}. If the change of one element fails,
     * the call fails with {@link org.redisson.client.RedisException},
     * but the other elements are still changed. Only the failed one
     * is left as it was.
     * <p>
     * Negative increments require <b>Redis 8.12.0 or higher.</b>
     * Older versions reject the whole call and change nothing.
     *
     * @param elements map of elements and their increments
     * @return map of elements and their new counts
     * @throws IllegalArgumentException if any increment is {@link Long#MIN_VALUE}
     */
    RFuture<Map<V, Long>> addAsync(Map<V, Long> elements);

    /**
     * Returns the count of an element.
     * <p>
     * Equivalent to {@code CMS.QUERY}.
     * <p>
     * The returned count is never lower than the real count
     * and may be overestimated. A decrease larger than what was
     * added for an element breaks this, see {@link #addAsync(Object, long)}.
     *
     * @param element element to query
     * @return count of the element
     */
    RFuture<Long> countAsync(V element);

    /**
     * Returns the counts of multiple elements at once.
     * <p>
     * Equivalent to {@code CMS.QUERY}.
     *
     * @param elements elements to query
     * @return map of elements and their counts
     */
    RFuture<Map<V, Long>> countAsync(Collection<V> elements);

    /**
     * Replaces the counters of this sketch with the weighted sum
     * of the defined sketches.
     * <p>
     * Equivalent to {@code CMS.MERGE}.
     * <p>
     * Counters held by this sketch before the call are <b>discarded</b>,
     * they are not added to. To accumulate instead, name this sketch
     * among the sources:
     * <pre>
     *     sketch.mergeWith(CountMinMergeArgs.sources(sketch.getName(), "other"));
     * </pre>
     * <p>
     * This sketch has to be initialized and all merged sketches,
     * including this one, have to share the same width, depth and cell size.
     *
     * @param args merge arguments
     * @return void
     */
    RFuture<Void> mergeWithAsync(CountMinMergeArgs args);

    /**
     * Returns sketch information.
     * <p>
     * Equivalent to {@code CMS.INFO}.
     *
     * @return sketch information
     */
    RFuture<CountMinInfo> getInfoAsync();

}
