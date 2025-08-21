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
package org.redisson.api;

import java.util.BitSet;

/**
 * Vector of bits that grows as needed. Asynchronous interface.
 *
 * @author Nikita Koksharov
 *
 */
public interface RBitSetAsync extends RExpirableAsync {

    /**
     * Returns signed number at specified
     * <code>offset</code> and <code>size</code>
     *
     * @param size - size of signed number up to 64 bits
     * @param offset - offset of signed number
     * @return signed number
     */
    RFuture<Long> getSignedAsync(int size, long offset);

    /**
     * Returns previous value of signed number and replaces it
     * with defined <code>value</code> at specified <code>offset</code>
     *
     * @param size - size of signed number up to 64 bits
     * @param offset - offset of signed number
     * @param value - value of signed number
     * @return previous value of signed number
     */
    RFuture<Long> setSignedAsync(int size, long offset, long value);

    /**
     * Increments current signed value by
     * defined <code>increment</code> value and <code>size</code>
     * at specified <code>offset</code>
     * and returns result.
     *
     * @param size - size of signed number up to 64 bits
     * @param offset - offset of signed number
     * @param increment - increment value
     * @return result value
     */
    RFuture<Long> incrementAndGetSignedAsync(int size, long offset, long increment);

    /**
     * Returns unsigned number at specified
     * <code>offset</code> and <code>size</code>
     *
     * @param size - size of unsigned number up to 63 bits
     * @param offset - offset of unsigned number
     * @return unsigned number
     */
    RFuture<Long> getUnsignedAsync(int size, long offset);

    /**
     * Returns previous value of unsigned number and replaces it
     * with defined <code>value</code> at specified <code>offset</code>
     *
     * @param size - size of unsigned number up to 63 bits
     * @param offset - offset of unsigned number
     * @param value - value of unsigned number
     * @return previous value of unsigned number
     */
    RFuture<Long> setUnsignedAsync(int size, long offset, long value);

    /**
     * Increments current unsigned value by
     * defined <code>increment</code> value and <code>size</code>
     * at specified <code>offset</code>
     * and returns result.
     *
     * @param size - size of unsigned number up to 63 bits
     * @param offset - offset of unsigned number
     * @param increment - increment value
     * @return result value
     */
    RFuture<Long> incrementAndGetUnsignedAsync(int size, long offset, long increment);

    /**
     * Returns byte number at specified <code>offset</code>
     *
     * @param offset - offset of number
     * @return number
     */
    RFuture<Byte> getByteAsync(long offset);

    /**
     * Returns previous value of byte number and replaces it
     * with defined <code>value</code> at specified <code>offset</code>
     *
     * @param offset - offset of number
     * @param value - value of number
     * @return previous value of number
     */
    RFuture<Byte> setByteAsync(long offset, byte value);

    /**
     * Increments current byte value on defined <code>increment</code> value at specified <code>offset</code>
     * and returns result.
     *
     * @param offset - offset of number
     * @param increment - increment value
     * @return result value
     */
    RFuture<Byte> incrementAndGetByteAsync(long offset, byte increment);

    /**
     * Returns short number at specified <code>offset</code>
     *
     * @param offset - offset of number
     * @return number
     */
    RFuture<Short> getShortAsync(long offset);

    /**
     * Returns previous value of short number and replaces it
     * with defined <code>value</code> at specified <code>offset</code>
     *
     * @param offset - offset of number
     * @param value - value of number
     * @return previous value of number
     */
    RFuture<Short> setShortAsync(long offset, short value);

    /**
     * Increments current short value on defined <code>increment</code> value at specified <code>offset</code>
     * and returns result.
     *
     * @param offset - offset of number
     * @param increment - increment value
     * @return result value
     */
    RFuture<Short> incrementAndGetShortAsync(long offset, short increment);

    /**
     * Returns integer number at specified <code>offset</code>
     *
     * @param offset - offset of number
     * @return number
     */
    RFuture<Integer> getIntegerAsync(long offset);

    /**
     * Returns previous value of integer number and replaces it
     * with defined <code>value</code> at specified <code>offset</code>
     *
     * @param offset - offset of number
     * @param value - value of number
     * @return previous value of number
     */
    RFuture<Integer> setIntegerAsync(long offset, int value);

    /**
     * Increments current integer value on defined <code>increment</code> value at specified <code>offset</code>
     * and returns result.
     *
     * @param offset - offset of number
     * @param increment - increment value
     * @return result value
     */
    RFuture<Integer> incrementAndGetIntegerAsync(long offset, int increment);

    /**
     * Returns long number at specified <code>offset</code>
     *
     * @param offset - offset of number
     * @return number
     */
    RFuture<Long> getLongAsync(long offset);

    /**
     * Returns previous value of long number and replaces it
     * with defined <code>value</code> at specified <code>offset</code>
     *
     * @param offset - offset of number
     * @param value - value of number
     * @return previous value of number
     */
    RFuture<Long> setLongAsync(long offset, long value);

    /**
     * Increments current long value on defined <code>increment</code> value at specified <code>offset</code>
     * and returns result.
     *
     * @param offset - offset of number
     * @param increment - increment value
     * @return result value
     */
    RFuture<Long> incrementAndGetLongAsync(long offset, long increment);

    RFuture<byte[]> toByteArrayAsync();

    /**
     * Returns "logical size" = index of highest set bit plus one.
     * Returns zero if there are no any set bit.
     *
     * @return "logical size" = index of highest set bit plus one
     */
    RFuture<Long> lengthAsync();

    /**
     * Set all bits to <code>value</code> from <code>fromIndex</code> (inclusive) to <code>toIndex</code> (exclusive)
     *
     * @param fromIndex inclusive
     * @param toIndex exclusive
     * @param value true = 1, false = 0
     * @return void
     *
     */
    RFuture<Void> setAsync(long fromIndex, long toIndex, boolean value);

    /**
     * Set all bits to zero from <code>fromIndex</code> (inclusive) to <code>toIndex</code> (exclusive)
     *
     * @param fromIndex inclusive
     * @param toIndex exclusive
     * @return void
     *
     */
    RFuture<Void> clearAsync(long fromIndex, long toIndex);

    /**
     * Copy bits state of source BitSet object to this object
     *
     * @param bs - BitSet source
     * @return void
     */
    RFuture<Void> setAsync(BitSet bs);

    /**
     * Executes NOT operation over all bits
     *
     * @return length in bytes of the destination key
     */
    RFuture<Long> notAsync();

    /**
     * Set all bits to one from <code>fromIndex</code> (inclusive) to <code>toIndex</code> (exclusive)
     *
     * @param fromIndex inclusive
     * @param toIndex exclusive
     * @return void
     */
    RFuture<Void> setAsync(long fromIndex, long toIndex);

    /**
     * Returns number of set bits.
     *
     * @return number of set bits.
     */
    RFuture<Long> sizeAsync();

    /**
     * Returns <code>true</code> if bit set to one and <code>false</code> overwise.
     *
     * @param bitIndex - index of bit
     * @return <code>true</code> if bit set to one and <code>false</code> overwise.
     */
    RFuture<Boolean> getAsync(long bitIndex);
    
    /**
     * Returns a boolean array where each element of the array corresponds to the query result of the input parameters.
     *
     * @param bitIndexes indexes of bit
     * @return Returns a boolean array where each element of the array corresponds to the query result of the input parameters.
     */
    RFuture<boolean[]> getAsync(long... bitIndexes);

    /**
     * Set bit to one at specified bitIndex
     *
     * @param bitIndex - index of bit
     * @return <code>true</code> - if previous value was true,
     * <code>false</code> - if previous value was false
     */
    RFuture<Boolean> setAsync(long bitIndex);

    /**
     * Set bit to <code>value</code> at specified <code>bitIndex</code>
     *
     * @param bitIndex - index of bit
     * @param value true = 1, false = 0
     * @return <code>true</code> - if previous value was true,
     * <code>false</code> - if previous value was false
     */
    RFuture<Boolean> setAsync(long bitIndex, boolean value);

    /**
     * Set all bits to <code>value</code> which index in indexArray
     *
     * @param indexArray The index array of bits that needs to be set to <code>value</code>
     * @param value true = 1, false = 0
     */
    RFuture<Void> setAsync(long[] indexArray, boolean value);

    /**
     * Returns the number of bits set to one.
     *
     * @return number of bits
     */
    RFuture<Long> cardinalityAsync();

    /**
     * Set bit to zero at specified <code>bitIndex</code>
     *
     * @param bitIndex - index of bit
     * @return <code>true</code> - if previous value was true,
     * <code>false</code> - if previous value was false
     */
    RFuture<Boolean> clearAsync(long bitIndex);

    /**
     * Set all bits to zero
     *
     * @return void
     */
    RFuture<Void> clearAsync();

    /**
     * Executes OR operation over this object and specified bitsets.
     * Stores result into this object.
     *
     * @param bitSetNames - name of stored bitsets
     * @return length in bytes of the destination key
     */
    RFuture<Long> orAsync(String... bitSetNames);

    /**
     * Executes AND operation over this object and specified bitsets.
     * Stores result into this object.
     *
     * @param bitSetNames - name of stored bitsets
     * @return length in bytes of the destination key
     */
    RFuture<Long> andAsync(String... bitSetNames);

    /**
     * Executes XOR operation over this object and specified bitsets.
     * Stores result into this object.
     *
     * @param bitSetNames - name of stored bitsets
     * @return length in bytes of the destination key
     */
    RFuture<Long> xorAsync(String... bitSetNames);

}
