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

/**
 * Double increment arguments implementation.
 *
 * @author lamnt2008
 *
 */
public final class DoubleIncrementParams extends BaseIncrementParams<DoubleIncrementArgs> implements DoubleIncrementArgs {

    private Double increment;
    private Double lowerBound;
    private Double upperBound;

    DoubleIncrementParams() {
    }

    DoubleIncrementParams(double increment) {
        this.increment = increment;
    }

    @Override
    public DoubleIncrementArgs lowerBound(double value) {
        lowerBound = value;
        return this;
    }

    @Override
    public DoubleIncrementArgs upperBound(double value) {
        upperBound = value;
        return this;
    }

    public Double getIncrement() {
        return increment;
    }

    public Double getLowerBound() {
        return lowerBound;
    }

    public Double getUpperBound() {
        return upperBound;
    }

}
