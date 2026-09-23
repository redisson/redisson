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
package org.redisson.api.tsnative;

/**
 * Aggregator applied to a time bucket by the AGGREGATION option of the range commands.
 * <p>
 * The wire token is the module's own spelling, which is why it is carried here rather than
 * derived from the constant name: <code>std.p</code> and <code>countNaN</code> do not survive
 * a mechanical lower-casing.
 *
 * @author Nikita Koksharov
 *
 */
public enum TSAggregation {

    /**
     * Arithmetic mean of the non-NaN values in the bucket.
     */
    AVG("avg"),

    /**
     * Sum of the non-NaN values.
     */
    SUM("sum"),

    /**
     * Smallest non-NaN value.
     */
    MIN("min"),

    /**
     * Largest non-NaN value.
     */
    MAX("max"),

    /**
     * Difference between the largest and the smallest non-NaN value.
     */
    RANGE("range"),

    /**
     * Number of non-NaN values.
     */
    COUNT("count"),

    /**
     * Number of NaN values.
     * <p>
     * Requires <b>Redis 8.6 or higher.</b>
     */
    COUNT_NAN("countNaN"),

    /**
     * Number of values, NaN included.
     * <p>
     * Requires <b>Redis 8.6 or higher.</b>
     */
    COUNT_ALL("countAll"),

    /**
     * Non-NaN value with the lowest timestamp in the bucket.
     */
    FIRST("first"),

    /**
     * Non-NaN value with the highest timestamp in the bucket.
     */
    LAST("last"),

    /**
     * Population standard deviation of the non-NaN values.
     */
    STD_POPULATION("std.p"),

    /**
     * Sample standard deviation of the non-NaN values.
     */
    STD_SAMPLE("std.s"),

    /**
     * Population variance of the non-NaN values.
     */
    VAR_POPULATION("var.p"),

    /**
     * Sample variance of the non-NaN values.
     */
    VAR_SAMPLE("var.s"),

    /**
     * Time-weighted average over the bucket's timeframe.
     */
    TWA("twa");

    private final String value;

    TSAggregation(String value) {
        this.value = value;
    }

    /**
     * Returns the token this aggregator is written as on the wire.
     *
     * @return wire token
     */
    public String getValue() {
        return value;
    }

}
