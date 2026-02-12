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
 * BITFIELD offset wrapper for bit or index-based offsets.
 *
 * @author Su Ko
 *
 */
public final class BitOffset {

    private final long value;
    private final boolean indexed;

    private BitOffset(long value, boolean indexed) {
        this.value = value;
        this.indexed = indexed;
    }

    /**
     * Creates a bit offset.
     *
     * @param offset zero-based bit offset
     * @return offset wrapper
     */
    public static BitOffset bit(long offset) {
        return new BitOffset(offset, false);
    }

    /**
     * Creates an index-based offset (prefixed with '#').
     *
     * @param index index of the integer element
     * @return offset wrapper
     */
    public static BitOffset index(long index) {
        return new BitOffset(index, true);
    }

    public long getLongValue() {
        return value;
    }

    public String getValue() {
        if (indexed) {
            return "#" + value;
        }

        return Long.toString(value);
    }

    public boolean isIndexed() {
        return indexed;
    }
}
