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
package org.redisson.api.ts;

/**
 * Aggregation applied to the values of a time bucket.
 * <p>
 * Every aggregation reads the stored value as a number, so the collection's codec has to
 * encode numbers as text. <code>StringCodec</code>, <code>DoubleCodec</code>,
 * <code>LongCodec</code> and <code>IntegerCodec</code> all do; a binary codec such as the
 * default one does not, and aggregating under it fails with an explanation rather than
 * quietly returning nothing, as does a stored value that is not a finite number.
 * <p>
 * The accumulators are double precision. Variance and standard deviation are computed from a
 * running sum of squared deviations, which holds up where a sum of squares would not, but a
 * bucket whose values are all around 1e-300 still underflows to zero.
 *
 * @author Nikita Koksharov
 *
 */
public enum TimeSeriesAggregation {

    /**
     * Number of values in the bucket.
     */
    COUNT,

    /**
     * Sum of the values.
     */
    SUM,

    /**
     * Arithmetic mean of the values.
     */
    AVG,

    /**
     * Smallest value.
     */
    MIN,

    /**
     * Largest value.
     */
    MAX,

    /**
     * Difference between the largest and the smallest value.
     */
    VALUE_RANGE,

    /**
     * Value of the entry with the lowest timestamp in the bucket.
     */
    FIRST,

    /**
     * Value of the entry with the highest timestamp in the bucket.
     */
    LAST,

    /**
     * Population standard deviation. Defined for a bucket holding at least one value.
     */
    STD_DEV_POPULATION,

    /**
     * Sample standard deviation. Undefined, and reported as <code>null</code>, for a bucket
     * holding fewer than two values.
     */
    STD_DEV_SAMPLE,

    /**
     * Population variance. Defined for a bucket holding at least one value.
     */
    VARIANCE_POPULATION,

    /**
     * Sample variance. Undefined, and reported as <code>null</code>, for a bucket holding
     * fewer than two values.
     */
    VARIANCE_SAMPLE

}
