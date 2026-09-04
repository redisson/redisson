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
 * Reducer applied across the series of one group by the REDUCE option of TS.MRANGE
 * and TS.MREVRANGE.
 * <p>
 * Deliberately not {@link TSAggregation}: the server rejects <code>first</code>,
 * <code>last</code> and <code>twa</code> as reducers, so offering them here would only let a
 * caller write a command the server refuses.
 *
 * @author Nikita Koksharov
 *
 */
public enum TSReducer {

    /**
     * Arithmetic mean of the non-NaN values, NaN if there are none.
     */
    AVG("avg"),

    /**
     * Sum of the non-NaN values, NaN if there are none.
     */
    SUM("sum"),

    /**
     * Smallest non-NaN value, NaN if there are none.
     */
    MIN("min"),

    /**
     * Largest non-NaN value, NaN if there are none.
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
    VAR_SAMPLE("var.s");

    private final String value;

    TSReducer(String value) {
        this.value = value;
    }

    /**
     * Returns the token this reducer is written as on the wire.
     *
     * @return wire token
     */
    public String getValue() {
        return value;
    }

}
