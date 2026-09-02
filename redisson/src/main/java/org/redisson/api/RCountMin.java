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
 * Count-min sketch.
 * <p>
 * A count-min sketch is a probabilistic data structure that keeps
 * the approximate count of elements in a sublinear amount of memory.
 * The returned count is never lower than the real count and may be
 * overestimated depending on the sketch dimensions.
 * <p>
 * Unlike other probabilistic structures a sketch is not created
 * implicitly, {@link #init(CountMinInitArgs)} has to be invoked
 * before any other operation.
 * <p>
 * Covers {@code CMS.*} commands of the Redis Bloom module.
 *
 * @param <V> element type
 *
 * @author Nikita Koksharov
 *
 */
public interface RCountMin<V> extends RExpirable, RCountMinAsync<V> {

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
     */
    void init(CountMinInitArgs args);

    /**
     * Increases the count of an element by 1.
     * <p>
     * Equivalent to {@code CMS.INCRBY}.
     *
     * @param element element to count
     * @return new count of the element
     */
    long add(V element);

    /**
     * Increases the count of an element by the defined increment.
     * <p>
     * Equivalent to {@code CMS.INCRBY}.
     *
     * @param element element to count
     * @param increment value to add to the current count
     * @return new count of the element
     */
    long add(V element, long increment);

    /**
     * Increases the count of multiple elements at once.
     * <p>
     * Equivalent to {@code CMS.INCRBY}.
     *
     * @param elements map of elements and their increments
     * @return map of elements and their new counts
     */
    Map<V, Long> add(Map<V, Long> elements);

    /**
     * Returns the count of an element.
     * <p>
     * Equivalent to {@code CMS.QUERY}.
     * <p>
     * The returned count is never lower than the real count
     * and may be overestimated.
     *
     * @param element element to query
     * @return count of the element
     */
    long count(V element);

    /**
     * Returns the counts of multiple elements at once.
     * <p>
     * Equivalent to {@code CMS.QUERY}.
     *
     * @param elements elements to query
     * @return map of elements and their counts
     */
    Map<V, Long> count(Collection<V> elements);

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
     * including this one, have to share the same width and depth.
     *
     * @param args merge arguments
     */
    void mergeWith(CountMinMergeArgs args);

    /**
     * Returns sketch information.
     * <p>
     * Equivalent to {@code CMS.INFO}.
     *
     * @return sketch information
     */
    CountMinInfo getInfo();

}
