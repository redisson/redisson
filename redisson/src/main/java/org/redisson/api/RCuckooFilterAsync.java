/**
 * Copyright (c) 2013-2024 Nikita Koksharov
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

import org.redisson.api.cuckoofilter.CuckooFilterAddArgs;
import org.redisson.api.cuckoofilter.CuckooFilterInitArgs;

import java.util.Collection;
import java.util.Set;

/**
 * Async interface for Cuckoo filter ({@code CF.*} commands).
 *
 * @param <V> element type
 *
 * @author Nikita Koksharov
 *
 */
public interface RCuckooFilterAsync<V> extends RExpirableAsync {

    /**
     * Initializes the cuckoo filter with the specified capacity.
     * <p>
     * Equivalent to {@code CF.RESERVE key capacity}.
     *
     * @param capacity expected number of items
     * @return void
     */
    RFuture<Void> initAsync(long capacity);

    /**
     * Initializes the cuckoo filter with detailed parameters.
     * <p>
     * Equivalent to {@code CF.RESERVE key capacity [BUCKETSIZE ..] [MAXITERATIONS ..] [EXPANSION ..]}.
     *
     * @param args initialization arguments
     * @return void
     */
    RFuture<Void> initAsync(CuckooFilterInitArgs args);

    /**
     * Adds an element to the filter.
     * Allows adding the same element multiple times.
     * <p>
     * Equivalent to {@code CF.ADD}.
     *
     * @param element element to add
     * @return {@code true} if the element was successfully added
     */
    RFuture<Boolean> addAsync(V element);

    /**
     * Adds elements in bulk with optional capacity and noCreate control.
     * Returns the set of elements that were successfully added.
     * <p>
     * Equivalent to {@code CF.INSERT}.
     *
     * @param args add arguments containing items and optional parameters
     * @return set of elements successfully added
     */
    RFuture<Set<V>> addAsync(CuckooFilterAddArgs<V> args);

    /**
     * Adds an element only if it does not already exist in the filter.
     * <p>
     * Equivalent to {@code CF.ADDNX}.
     *
     * @param element element to add
     * @return {@code true} if the element was added,
     *         {@code false} if it may already exist
     */
    RFuture<Boolean> addIfAbsentAsync(V element);

    /**
     * Adds elements in bulk only if they do not already exist.
     * Returns the set of elements that were successfully added.
     * <p>
     * Equivalent to {@code CF.INSERTNX}.
     *
     * @param args add arguments containing items and optional parameters
     * @return set of elements that were newly added
     */
    RFuture<Set<V>> addIfAbsentAsync(CuckooFilterAddArgs<V> args);

    /**
     * Checks if an element may exist in the filter.
     * <p>
     * Equivalent to {@code CF.EXISTS}.
     *
     * @param element element to check
     * @return {@code true} if the element may exist,
     *         {@code false} if it definitely does not
     */
    RFuture<Boolean> existsAsync(V element);

    /**
     * Checks multiple elements for existence.
     * Returns the set of elements that may exist in the filter.
     * <p>
     * Equivalent to {@code CF.MEXISTS}.
     *
     * @param elements elements to check
     * @return set of elements that may exist
     */
    RFuture<Set<V>> existsAsync(Collection<V> elements);

    /**
     * Removes an element from the filter.
     * <p>
     * Equivalent to {@code CF.DEL}.
     *
     * @param element element to remove
     * @return {@code true} if the element was found and removed,
     *         {@code false} if the element was not found
     */
    RFuture<Boolean> removeAsync(V element);

    /**
     * Returns the approximate count of times an element
     * may be in the filter.
     * <p>
     * Equivalent to {@code CF.COUNT}.
     *
     * @param element element to count
     * @return approximate count
     */
    RFuture<Long> countAsync(V element);

    /**
     * Returns filter information.
     * <p>
     * Equivalent to {@code CF.INFO}.
     *
     * @return filter information
     */
    RFuture<CuckooFilterInfo> getInfoAsync();
}
