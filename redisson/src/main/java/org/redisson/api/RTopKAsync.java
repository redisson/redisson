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

import org.redisson.api.topk.TopKInitArgs;

import java.util.List;
import java.util.Map;

/**
 * Async interface for Top-K ({@code TOPK.*} commands).
 *
 * @param <V> element type
 *
 * @author Nikita Koksharov
 *
 */
public interface RTopKAsync<V> extends RExpirableAsync {

    /**
     * Initializes the Top-K to track the {@code topK} most frequent items.
     * <p>
     * Equivalent to {@code TOPK.RESERVE key topk}.
     *
     * @param topK number of top items to keep track of
     * @return void
     */
    RFuture<Void> initAsync(int topK);

    /**
     * Initializes the Top-K with detailed parameters.
     * <p>
     * Equivalent to {@code TOPK.RESERVE key topk width depth decay}.
     *
     * @param args initialization arguments
     * @return void
     */
    RFuture<Void> initAsync(TopKInitArgs args);

    /**
     * Adds an item to the Top-K.
     * <p>
     * Equivalent to {@code TOPK.ADD}.
     *
     * @param item item to add
     * @return item dropped from the top-K list as a result,
     *         or {@code null} if nothing was dropped
     */
    RFuture<V> addAsync(V item);

    /**
     * Adds items to the Top-K.
     * <p>
     * The returned list is positionally aligned to the input: the element at
     * index {@code i} is the item dropped from the top-K list as a result of
     * adding {@code items.get(i)}, or {@code null} if nothing was dropped.
     * <p>
     * Equivalent to {@code TOPK.ADD}.
     *
     * @param items items to add
     * @return list of dropped items aligned to the input, with {@code null} entries
     */
    RFuture<List<V>> addAsync(List<V> items);

    /**
     * Increases the score of an item by the given increment.
     * <p>
     * Equivalent to {@code TOPK.INCRBY}.
     *
     * @param item item to increment
     * @param increment increment value
     * @return item dropped from the top-K list as a result,
     *         or {@code null} if nothing was dropped
     */
    RFuture<V> incrementByAsync(V item, int increment);

    /**
     * Increases the score of multiple items by the given increments.
     * <p>
     * The returned list contains the items dropped from the top-K list, aligned
     * to the iteration order of the supplied map; use an ordered map (such as
     * {@link java.util.LinkedHashMap}) to correlate results with inputs.
     * <p>
     * Equivalent to {@code TOPK.INCRBY}.
     *
     * @param itemIncrements map of items to their increment values
     * @return list of dropped items, with {@code null} entries
     */
    RFuture<List<V>> incrementByAsync(Map<V, Integer> itemIncrements);

    /**
     * Checks whether an item is currently in the top-K list.
     * <p>
     * Equivalent to {@code TOPK.QUERY}.
     *
     * @param item item to check
     * @return {@code true} if the item is in the top-K list
     */
    RFuture<Boolean> containsAsync(V item);

    /**
     * Checks whether multiple items are currently in the top-K list.
     * The result is positionally aligned to the input.
     * <p>
     * Equivalent to {@code TOPK.QUERY}.
     *
     * @param items items to check
     * @return list of results aligned to the input
     */
    RFuture<List<Boolean>> containsAsync(List<V> items);

    /**
     * Returns the approximate count of an item.
     * <p>
     * Equivalent to {@code TOPK.COUNT}.
     *
     * @param item item to count
     * @return approximate count
     * @deprecated since Redis Bloom 2.4.0 the count may be inaccurate.
     *             Use {@link #listWithCountAsync()} instead.
     */
    @Deprecated
    RFuture<Long> countAsync(V item);

    /**
     * Returns the approximate counts of multiple items.
     * The result is positionally aligned to the input.
     * <p>
     * Equivalent to {@code TOPK.COUNT}.
     *
     * @param items items to count
     * @return list of approximate counts aligned to the input
     * @deprecated since Redis Bloom 2.4.0 the count may be inaccurate.
     *             Use {@link #listWithCountAsync()} instead.
     */
    @Deprecated
    RFuture<List<Long>> countAsync(List<V> items);

    /**
     * Returns the full list of items currently in the top-K list.
     * <p>
     * Equivalent to {@code TOPK.LIST}.
     *
     * @return list of top-K items
     */
    RFuture<List<V>> listAsync();

    /**
     * Returns the full list of items currently in the top-K list
     * together with their approximate counts.
     * <p>
     * Equivalent to {@code TOPK.LIST WITHCOUNT}.
     *
     * @return map of top-K items to their approximate counts
     */
    RFuture<Map<V, Long>> listWithCountAsync();

    /**
     * Returns Top-K information.
     * <p>
     * Equivalent to {@code TOPK.INFO}.
     *
     * @return Top-K information
     */
    RFuture<TopKInfo> getInfoAsync();
}
