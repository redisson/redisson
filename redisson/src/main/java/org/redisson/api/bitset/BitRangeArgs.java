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
 * Arguments for {@link org.redisson.api.RBitSet#getIndexes(BitRangeArgs)},
 * the {@code BITRANGEINTARRAY} command.
 * <p>
 * The range is inclusive at both ends and is read as in {@code BITCOUNT}:
 * a negative index counts from the end of the value, {@code -1} being the last bit or byte.
 * It is clamped to the value, so no index past its last byte is ever returned,
 * not even with {@link #zeros()}.
 * <p>
 * Indexes are numbered as in {@link org.redisson.api.RBitSet#get(long)}:
 * index 0 is the most significant bit of the first byte.
 *
 * <p>Usage example:
 * <pre>
 *     // every bit set to one
 *     long[] all = bitSet.getIndexes(BitRangeArgs.bytes(0, -1));
 *
 *     // bits set to one among bits 100 to 199
 *     long[] some = bitSet.getIndexes(BitRangeArgs.bits(100, 199));
 *
 *     // the first 10 bits set to zero in the first 4 bytes
 *     long[] free = bitSet.getIndexes(BitRangeArgs.bits(0, 31).zeros().limit(10));
 * </pre>
 *
 * @author Nikita Koksharov
 *
 */
public interface BitRangeArgs {

    /**
     * Defines the range by bit index.
     * <p>
     * Equivalent to {@code BITRANGEINTARRAY key start end BIT}.
     *
     * @param start index of the first bit, inclusive; negative counts from the end
     * @param end index of the last bit, inclusive; negative counts from the end
     * @return arguments object
     */
    static BitRangeArgs bits(long start, long end) {
        return new BitRangeParams(start, end, true);
    }

    /**
     * Defines the range by byte index, each byte covering 8 bits.
     * {@code bytes(0, 0)} covers bits 0 to 7, and {@code bytes(0, -1)} the whole value.
     * <p>
     * Equivalent to {@code BITRANGEINTARRAY key start end BYTE}.
     *
     * @param start index of the first byte, inclusive; negative counts from the end
     * @param end index of the last byte, inclusive; negative counts from the end
     * @return arguments object
     */
    static BitRangeArgs bytes(long start, long end) {
        return new BitRangeParams(start, end, false);
    }

    /**
     * Returns only the lowest {@code count} indexes in the range.
     * <p>
     * Equivalent to the {@code LIMIT} option.
     * <p>
     * In a range defined by {@link #bits(long, long)} the result holds at most
     * {@code count} indexes, and the next page starts at the bit after the last one.
     * <p>
     * In a range defined by {@link #bytes(long, long)} the result also completes the byte
     * holding the last of them, so it can hold up to 7 indexes more than {@code count}.
     * The next page starts at the following byte, {@code (last >> 3) + 1},
     * without repeating or skipping an index.
     * <p>
     * A {@code count} of 0 returns an empty array. The last call wins.
     *
     * @param count maximum number of indexes, 0 or greater
     * @return arguments object
     * @throws IllegalArgumentException if count is negative
     */
    BitRangeArgs limit(long count);

    /**
     * Returns the indexes of the bits set to zero instead of the bits set to one.
     * <p>
     * Equivalent to the {@code ZEROS} option.
     * <p>
     * The range is clamped to the value, so the bits past its last byte,
     * which {@link org.redisson.api.RBitSet#get(long)} reads as zero, are not returned.
     *
     * @return arguments object
     */
    BitRangeArgs zeros();

}
