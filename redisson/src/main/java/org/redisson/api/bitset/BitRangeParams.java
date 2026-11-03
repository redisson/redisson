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
package org.redisson.api.bitset;

/**
 *
 * @author Nikita Koksharov
 *
 */
public final class BitRangeParams implements BitRangeArgs {

    private final long start;
    private final long end;
    private final boolean bitUnit;
    // null sends no LIMIT; 0 is a valid limit that returns nothing
    private Long limit;
    private boolean zeros;

    BitRangeParams(long start, long end, boolean bitUnit) {
        this.start = start;
        this.end = end;
        this.bitUnit = bitUnit;
    }

    @Override
    public BitRangeArgs limit(long count) {
        if (count < 0) {
            throw new IllegalArgumentException("count can't be negative");
        }
        this.limit = count;
        return this;
    }

    @Override
    public BitRangeArgs zeros() {
        this.zeros = true;
        return this;
    }

    public long getStart() {
        return start;
    }

    public long getEnd() {
        return end;
    }

    public boolean isBitUnit() {
        return bitUnit;
    }

    public Long getLimit() {
        return limit;
    }

    public boolean isZeros() {
        return zeros;
    }

}
