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
 * Long increment arguments implementation.
 *
 * @author lamnt2008
 *
 */
public final class LongIncrementParams extends BaseIncrementParams<LongIncrementArgs> implements LongIncrementArgs {

    private Long increment;
    private Long lowerBound;
    private Long upperBound;

    LongIncrementParams() {
    }

    LongIncrementParams(long increment) {
        this.increment = increment;
    }

    @Override
    public LongIncrementArgs lowerBound(long value) {
        lowerBound = value;
        return this;
    }

    @Override
    public LongIncrementArgs upperBound(long value) {
        upperBound = value;
        return this;
    }

    public Long getIncrement() {
        return increment;
    }

    public Long getLowerBound() {
        return lowerBound;
    }

    public Long getUpperBound() {
        return upperBound;
    }

}
