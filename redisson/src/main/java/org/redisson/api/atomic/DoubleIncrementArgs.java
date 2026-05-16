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
 * Arguments for extended atomic double increment operations.
 *
 * @author lamnt2008
 *
 */
public interface DoubleIncrementArgs extends BaseIncrementArgs<DoubleIncrementArgs> {

    /**
     * Defines default increment by {@code 1}.
     *
     * @return arguments object
     */
    static DoubleIncrementArgs defaults() {
        return new DoubleIncrementParams();
    }

    /**
     * Defines increment value.
     *
     * @param increment increment value
     * @return arguments object
     */
    static DoubleIncrementArgs by(double increment) {
        return new DoubleIncrementParams(increment);
    }

    /**
     * Defines lower bound for increment result.
     *
     * @param value lower bound value
     * @return arguments object
     */
    DoubleIncrementArgs lowerBound(double value);

    /**
     * Defines upper bound for increment result.
     *
     * @param value upper bound value
     * @return arguments object
     */
    DoubleIncrementArgs upperBound(double value);

}
