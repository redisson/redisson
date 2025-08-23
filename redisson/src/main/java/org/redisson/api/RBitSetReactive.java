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

import reactor.core.publisher.Mono;

import java.util.BitSet;

/**
 * Reactive interface for BitSet object
 *
 * @author Nikita Koksharov
 *
 */
public interface RBitSetReactive extends RExpirableReactive {

    /**
     * Returns signed number at specified
     * <code>offset</code> and <code>size</code>
     *
     * @param size - size of signed number up to 64 bits
     * @param offset - offset of signed number
     * @return signed number
     */
    Mono<Long> getSigned(int size, long offset);

    /**
     * Returns previous value of signed number and replaces it
     * with defined <code>value</code> at specified <code>offset</code>
     *
     * @param size - size of signed number up to 64 bits
     * @param offset - offset of signed number
     * @param value - value of signed number
     * @return previous value of signed number
     */
    Mono<Long> setSigned(int size, long offset, long value);

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
    Mono<Long> incrementAndGetSigned(int size, long offset, long increment);

    /**
     * Returns unsigned number at specified
     * <code>offset</code> and <code>size</code>
     *
     * @param size - size of unsigned number up to 64 bits
     * @param offset - offset of unsigned number
     * @return unsigned number
     */
    Mono<Long> getUnsigned(int size, long offset);

    /**
     * Returns previous value of unsigned number and replaces it
     * with defined <code>value</code> at specified <code>offset</code>
     *
     * @param size - size of unsigned number up to 64 bits
     * @param offset - offset of unsigned number
     * @param value - value of unsigned number
     * @return previous value of unsigned number
     */
    Mono<Long> setUnsigned(int size, long offset, long value);

    /**
     * Increments current unsigned value by
     * defined <code>increment</code> value and <code>size</code>
     * at specified <code>offset</code>
     * and returns result.
     *
     * @param size - size of unsigned number up to 64 bits
     * @param offset - offset of unsigned number
     * @param increment - increment value
     * @return result value
     */
    Mono<Long> incrementAndGetUnsigned(int size, long offset, long increment);

    /**
     * Returns byte number at specified <code>offset</code>
     *
     * @param offset - offset of number
     * @return number
     */
    Mono<Byte> getByte(long offset);

    /**
     * Returns previous value of byte number and replaces it
     * with defined <code>value</code> at specified <code>offset</code>
     *
     * @param offset - offset of number
     * @param value - value of number
     * @return previous value of number
     */
    Mono<Byte> setByte(long offset, byte value);

    /**
     * Increments current byte value on defined <code>increment</code> value at specified <code>offset</code>
     * and returns result.
     *
     * @param offset - offset of number
     * @param increment - increment value
     * @return result value
     */
    Mono<Byte> incrementAndGetByte(long offset, byte increment);

    /**
     * Returns short number at specified <code>offset</code>
     *
     * @param offset - offset of number
     * @return number
     */
    Mono<Short> getShort(long offset);

    /**
     * Returns previous value of short number and replaces it
     * with defined <code>value</code> at specified <code>offset</code>
     *
     * @param offset - offset of number
     * @param value - value of number
     * @return previous value of number
     */
    Mono<Short> setShort(long offset, short value);

    /**
     * Increments current short value on defined <code>increment</code> value at specified <code>offset</code>
     * and returns result.
     *
     * @param offset - offset of number
     * @param increment - increment value
     * @return result value
     */
    Mono<Short> incrementAndGetShort(long offset, short increment);

    /**
     * Returns integer number at specified <code>offset</code>
     *
     * @param offset - offset of number
     * @return number
     */
    Mono<Integer> getInteger(long offset);

    /**
     * Returns previous value of integer number and replaces it
     * with defined <code>value</code> at specified <code>offset</code>
     *
     * @param offset - offset of number
     * @param value - value of number
     * @return previous value of number
     */
    Mono<Integer> setInteger(long offset, int value);

    /**
     * Increments current integer value on defined <code>increment</code> value at specified <code>offset</code>
     * and returns result.
     *
     * @param offset - offset of number
     * @param increment - increment value
     * @return result value
     */
    Mono<Integer> incrementAndGetInteger(long offset, int increment);

    /**
     * Returns long number at specified <code>offset</code>
     *
     * @param offset - offset of number
     * @return number
     */
    Mono<Long> getLong(long offset);

    /**
     * Returns previous value of long number and replaces it
     * with defined <code>value</code> at specified <code>offset</code>
     *
     * @param offset - offset of number
     * @param value - value of number
     * @return previous value of number
     */
    Mono<Long> setLong(long offset, long value);

    /**
     * Increments current long value on defined <code>increment</code> value at specified <code>offset</code>
     * and returns result.
     *
     * @param offset - offset of number
     * @param increment - increment value
     * @return result value
     */
    Mono<Long> incrementAndGetLong(long offset, long increment);

    Mono<byte[]> toByteArray();

    /**
     * Returns "logical size" = index of highest set bit plus one.
     * Returns zero if there are no any set bit.
     * 
     * @return "logical size" = index of highest set bit plus one
     */
    Mono<Long> length();

    /**
     * Set all bits to <code>value</code> from <code>fromIndex</code> (inclusive) to <code>toIndex</code> (exclusive)
     * 
     * @param fromIndex inclusive
     * @param toIndex exclusive
     * @param value true = 1, false = 0
     * @return void
     * 
     */
    Mono<Void> set(long fromIndex, long toIndex, boolean value);

    /**
     * Set all bits to zero from <code>fromIndex</code> (inclusive) to <code>toIndex</code> (exclusive)
     * 
     * @param fromIndex inclusive
     * @param toIndex exclusive
     * @return void
     * 
     */
    Mono<Void> clear(long fromIndex, long toIndex);

    /**
     * Copy bits state of source BitSet object to this object
     * 
     * @param bs - BitSet source
     * @return void
     */
    Mono<Void> set(BitSet bs);

    /**
     * Executes NOT operation over all bits
     * 
     * @return length in bytes of the destination key
     */
    Mono<Long> not();

    /**
     * Set all bits to one from <code>fromIndex</code> (inclusive) to <code>toIndex</code> (exclusive)
     * 
     * @param fromIndex inclusive
     * @param toIndex exclusive
     * @return void
     */
    Mono<Void> set(long fromIndex, long toIndex);

    /**
     * Returns number of set bits.
     * 
     * @return number of set bits.
     */
    Mono<Long> size();

    /**
     * Returns <code>true</code> if bit set to one and <code>false</code> overwise.
     * 
     * @param bitIndex - index of bit
     * @return <code>true</code> if bit set to one and <code>false</code> overwise.
     */
    Mono<Boolean> get(long bitIndex);
    
    /**
     * Returns a boolean array where each element of the array corresponds to the query result of the input parameters.
     *
     * @param bitIndexes indexes of bit
     * @return Returns a boolean array where each element of the array corresponds to the query result of the input parameters.
     */
    Mono<boolean[]> get(long... bitIndexes);

    /**
     * Set bit to one at specified bitIndex
     * 
     * @param bitIndex - index of bit
     * @return <code>true</code> - if previous value was true, 
     * <code>false</code> - if previous value was false
     */
    Mono<Boolean> set(long bitIndex);

    /**
     * Set bit to <code>value</code> at specified <code>bitIndex</code>
     * 
     * @param bitIndex - index of bit
     * @param value true = 1, false = 0
     * @return <code>true</code> - if previous value was true, 
     * <code>false</code> - if previous value was false
     */
    Mono<Boolean> set(long bitIndex, boolean value);

    /**
     * Returns the number of bits set to one.
     * 
     * @return number of bits
     */
    Mono<Long> cardinality();

    /**
     * Set bit to zero at specified <code>bitIndex</code>
     *
     * @param bitIndex - index of bit
     * @return <code>true</code> - if previous value was true, 
     * <code>false</code> - if previous value was false
     */
    Mono<Boolean> clear(long bitIndex);

    /**
     * Set all bits to zero
     * 
     * @return void
     */
    Mono<Void> clear();

    /**
     * Executes OR operation over this object and specified bitsets.
     * Stores result into this object.
     * 
     * @param bitSetNames - name of stored bitsets
     * @return length in bytes of the destination key
     */
    Mono<Long> or(String... bitSetNames);

    /**
     * Executes AND operation over this object and specified bitsets.
     * Stores result into this object.
     * 
     * @param bitSetNames - name of stored bitsets
     * @return length in bytes of the destination key
     */
    Mono<Long> and(String... bitSetNames);

    /**
     * Executes XOR operation over this object and specified bitsets.
     * Stores result into this object.
     * 
     * @param bitSetNames - name of stored bitsets
     * @return length in bytes of the destination key
     */
    Mono<Long> xor(String... bitSetNames);

    /**
     * Executes bitwise DIFF operation over this object and specified bitsets.
     * Sets bits that are set in this object but not in any of the other bitsets.
     * Stores result into this object.
     *
     * @param bitSetNames name of stored bitsets
     * @return length in bytes of the destination key
     */
    Mono<Long> diff(String... bitSetNames);

    /**
     * Executes bitwise DIFF1 operation over this object and specified bitsets.
     * Sets bits that are set in one or more of the other bitsets but not in this object.
     * Stores result into this object.
     *
     * @param bitSetNames name of stored bitsets
     * @return length in bytes of the destination key
     */
    Mono<Long> diffInverse(String... bitSetNames);

    /**
     * Executes bitwise ANDOR operation over this object and specified bitsets.
     * Sets bits that are set in this object AND also in one or more of the other bitsets.
     * Stores result into this object.
     *
     * @param bitSetNames name of stored bitsets
     * @return length in bytes of the destination key
     */
    Mono<Long> andOr(String... bitSetNames);

    /**
     * Executes bitwise ONE operation over this object and specified bitsets.
     * Sets bits that are set in exactly one of the provided bitsets.
     * Stores result into this object.
     *
     * @param bitSetNames name of stored bitsets
     * @return length in bytes of the destination key
     */
    Mono<Long> setExclusive(String... bitSetNames);

}
