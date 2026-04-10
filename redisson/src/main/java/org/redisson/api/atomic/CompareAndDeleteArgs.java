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
package org.redisson.api.atomic;

import java.util.Objects;

/**
 * Arguments for {@link org.redisson.api.RAtomicLong#compareAndDelete(CompareAndDeleteArgs)}
 * and {@link org.redisson.api.RAtomicDouble#compareAndDelete(CompareAndDeleteArgs)} methods.
 * Defines conditions for conditional deletion of atomic value.
 *
 * @author Nikita Koksharov
 *
 */
public final class CompareAndDeleteArgs {

    private final ComparisonCondition condition;
    private final Number threshold;

    private CompareAndDeleteArgs(ComparisonCondition condition, Number threshold) {
        Objects.requireNonNull(condition, "Condition can't be null");
        Objects.requireNonNull(threshold, "Threshold can't be null");
        this.condition = condition;
        this.threshold = threshold;
    }

    /**
     * Deletes entry if stored value is less than specified value.
     *
     * @param value threshold value
     * @return arguments object
     */
    public static CompareAndDeleteArgs less(long value) {
        return new CompareAndDeleteArgs(ComparisonCondition.LESS, value);
    }

    /**
     * Deletes entry if stored value is less than or equal to specified value.
     *
     * @param value threshold value
     * @return arguments object
     */
    public static CompareAndDeleteArgs lessOrEqual(long value) {
        return new CompareAndDeleteArgs(ComparisonCondition.LESS_OR_EQUAL, value);
    }

    /**
     * Deletes entry if stored value is greater than specified value.
     *
     * @param value threshold value
     * @return arguments object
     */
    public static CompareAndDeleteArgs greater(long value) {
        return new CompareAndDeleteArgs(ComparisonCondition.GREATER, value);
    }

    /**
     * Deletes entry if stored value is greater than or equal to specified value.
     *
     * @param value threshold value
     * @return arguments object
     */
    public static CompareAndDeleteArgs greaterOrEqual(long value) {
        return new CompareAndDeleteArgs(ComparisonCondition.GREATER_OR_EQUAL, value);
    }

    /**
     * Deletes entry if stored value is equal to specified value.
     *
     * @param value threshold value
     * @return arguments object
     */
    public static CompareAndDeleteArgs equal(long value) {
        return new CompareAndDeleteArgs(ComparisonCondition.EQUAL, value);
    }

    /**
     * Deletes entry if stored value is not equal to specified value.
     *
     * @param value threshold value
     * @return arguments object
     */
    public static CompareAndDeleteArgs notEqual(long value) {
        return new CompareAndDeleteArgs(ComparisonCondition.NOT_EQUAL, value);
    }

    /**
     * Deletes entry if stored value is less than specified value.
     *
     * @param value threshold value
     * @return arguments object
     */
    public static CompareAndDeleteArgs less(double value) {
        return new CompareAndDeleteArgs(ComparisonCondition.LESS, value);
    }

    /**
     * Deletes entry if stored value is less than or equal to specified value.
     *
     * @param value threshold value
     * @return arguments object
     */
    public static CompareAndDeleteArgs lessOrEqual(double value) {
        return new CompareAndDeleteArgs(ComparisonCondition.LESS_OR_EQUAL, value);
    }

    /**
     * Deletes entry if stored value is greater than specified value.
     *
     * @param value threshold value
     * @return arguments object
     */
    public static CompareAndDeleteArgs greater(double value) {
        return new CompareAndDeleteArgs(ComparisonCondition.GREATER, value);
    }

    /**
     * Deletes entry if stored value is greater than or equal to specified value.
     *
     * @param value threshold value
     * @return arguments object
     */
    public static CompareAndDeleteArgs greaterOrEqual(double value) {
        return new CompareAndDeleteArgs(ComparisonCondition.GREATER_OR_EQUAL, value);
    }

    /**
     * Deletes entry if stored value is equal to specified value.
     *
     * @param value threshold value
     * @return arguments object
     */
    public static CompareAndDeleteArgs equal(double value) {
        return new CompareAndDeleteArgs(ComparisonCondition.EQUAL, value);
    }

    /**
     * Deletes entry if stored value is not equal to specified value.
     *
     * @param value threshold value
     * @return arguments object
     */
    public static CompareAndDeleteArgs notEqual(double value) {
        return new CompareAndDeleteArgs(ComparisonCondition.NOT_EQUAL, value);
    }

    public ComparisonCondition getCondition() {
        return condition;
    }

    public Number getThreshold() {
        return threshold;
    }

}
