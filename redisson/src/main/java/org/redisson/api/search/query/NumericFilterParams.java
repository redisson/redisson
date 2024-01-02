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
package org.redisson.api.search.query;

/**
 *
 * @author Nikita Koksharov
 *
 */
public final class NumericFilterParams implements NumericFilter, NumericFilterMax, QueryFilter {

    private final String fieldName;
    private double max;
    private double min;
    private boolean minExclusive;
    private boolean maxExclusive;

    NumericFilterParams(String fieldName) {
        this.fieldName = fieldName;
    }

    @Override
    public NumericFilterMax min(double value) {
        this.min = value;
        return this;
    }

    @Override
    public NumericFilterMax minExclusive(double value) {
        this.min = value;
        minExclusive = true;
        return this;
    }

    @Override
    public QueryFilter max(double value) {
        this.max = value;
        return this;
    }

    @Override
    public QueryFilter maxExclusive(double value) {
        this.max = value;
        maxExclusive = true;
        return this;
    }

    public String getFieldName() {
        return fieldName;
    }

    public double getMax() {
        return max;
    }

    public double getMin() {
        return min;
    }

    public boolean isMinExclusive() {
        return minExclusive;
    }

    public boolean isMaxExclusive() {
        return maxExclusive;
    }
}
