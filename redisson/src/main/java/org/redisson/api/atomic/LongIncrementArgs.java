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
 * Arguments for extended atomic long increment operations.
 *
 * @author lamnt2008
 *
 */
public interface LongIncrementArgs extends BaseIncrementArgs<LongIncrementArgs> {

    /**
     * Defines default increment by {@code 1}.
     *
     * @return arguments object
     */
    static LongIncrementArgs defaults() {
        return new LongIncrementParams();
    }

    /**
     * Defines increment value.
     *
     * @param increment increment value
     * @return arguments object
     */
    static LongIncrementArgs by(long increment) {
        return new LongIncrementParams(increment);
    }

    /**
     * Defines lower bound for increment result.
     *
     * @param value lower bound value
     * @return arguments object
     */
    LongIncrementArgs lowerBound(long value);

    /**
     * Defines upper bound for increment result.
     *
     * @param value upper bound value
     * @return arguments object
     */
    LongIncrementArgs upperBound(long value);

}
