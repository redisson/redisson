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
package org.redisson.api.topk;

/**
 * Arguments for Top-K initialization.
 *
 * <p>Usage example:
 * <pre>
 *     topK.init(TopKInitArgs.topK(50)
 *                     .width(2000)
 *                     .depth(7)
 *                     .decay(0.925));
 * </pre>
 *
 * @author Nikita Koksharov
 *
 */
public interface TopKInitArgs {

    /**
     * Creates arguments for a Top-K tracking the {@code topK} most frequent items.
     *
     * @param topK number of top items to keep track of
     * @return arguments instance
     */
    static TopKInitArgs topK(int topK) {
        return new TopKInitArgsImpl(topK);
    }

    /**
     * Defines the number of counters in each array (width).
     * <p>
     * Default value is 8.
     *
     * @param width number of counters in each array
     * @return arguments instance
     */
    TopKInitArgs width(int width);

    /**
     * Defines the number of counter arrays (depth).
     * <p>
     * Default value is 7.
     *
     * @param depth number of counter arrays
     * @return arguments instance
     */
    TopKInitArgs depth(int depth);

    /**
     * Defines the probability of a counter being decreased on collision.
     * Must be a value in the range {@code (0, 1)}.
     * <p>
     * Default value is 0.9.
     *
     * @param decay counter decay probability
     * @return arguments instance
     */
    TopKInitArgs decay(double decay);

}
