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
package org.redisson.api.countmin;

/**
 * Arguments for count-min sketch initialization.
 * <p>
 * A sketch is initialized either by explicit dimensions or by the
 * desired error rate. These modes are mutually exclusive, so an
 * instance is always created through one of the static methods below.
 *
 * <p>Usage example:
 * <pre>
 *     sketch.init(CountMinInitArgs.dimensions(2000, 5));
 *
 *     sketch.init(CountMinInitArgs.probability(0.001, 0.01));
 * </pre>
 *
 * @author Nikita Koksharov
 *
 */
public interface CountMinInitArgs {

    /**
     * Creates arguments defining the sketch dimensions.
     * <p>
     * Equivalent to {@code CMS.INITBYDIM key width depth}.
     * <p>
     * The {@code width} is the number of counters in each array,
     * the {@code depth} is the number of counter arrays. A larger
     * width reduces the overestimation of counts, a larger depth
     * reduces the probability of exceeding the expected error.
     *
     * @param width number of counters in each array, greater than 0
     * @param depth number of counter arrays, greater than 0
     * @return arguments instance
     * @throws IllegalArgumentException if width or depth is less than 1
     */
    static CountMinInitArgs dimensions(long width, long depth) {
        return new CountMinInitArgsImpl(width, depth);
    }

    /**
     * Creates arguments defining the acceptable error.
     * The sketch dimensions are calculated by Redis.
     * <p>
     * Equivalent to {@code CMS.INITBYPROB key error probability}.
     * <p>
     * The estimated count may deviate from the real count by no more
     * than {@code errorRate} multiplied by the total number of counted
     * items, with a probability of at least {@code 1 - probability}.
     *
     * @param errorRate estimation error, a value between 0 and 1
     * @param probability probability of the error exceeding
     *                    the {@code errorRate}, a value between 0 and 1
     * @return arguments instance
     * @throws IllegalArgumentException if either value is not
     *         strictly between 0 and 1
     */
    static CountMinInitArgs probability(double errorRate, double probability) {
        return new CountMinInitArgsImpl(errorRate, probability);
    }

}
