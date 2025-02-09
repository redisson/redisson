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
 * Vector of bits that grows as needed.
 *
 * @author Nikita Koksharov
 *
 */
public interface RBitSet extends RExpirable, RBitSetAsync {

    /**
     * Returns signed number at specified
     * <code>offset</code> and <code>size</code>
     *
     * @param size - size of signed number up to 64 bits
     * @param offset - offset of signed number
     * @return signed number
     */
    long getSigned(int size, long offset);

    /**
     * Returns previous value of signed number and replaces it
     * with defined <code>value</code> at specified <code>offset</code>
     *
     * @param size - size of signed number up to 64 bits
     * @param offset - offset of signed number
     * @param value - value of signed number
     * @return previous value of signed number
     */
    long setSigned(int size, long offset, long value);

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
    long incrementAndGetSigned(int size, long offset, long increment);

    /**
     * Returns unsigned number at specified
     * <code>offset</code> and <code>size</code>
     *
     * @param size - size of unsigned number up to 64 bits
     * @param offset - offset of unsigned number
     * @return unsigned number
     */
    long getUnsigned(int size, long offset);

    /**
     * Returns previous value of unsigned number and replaces it
     * with defined <code>value</code> at specified <code>offset</code>
     *
     * @param size - size of unsigned number up to 64 bits
     * @param offset - offset of unsigned number
     * @param value - value of unsigned number
     * @return previous value of unsigned number
     */
    long setUnsigned(int size, long offset, long value);

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
    long incrementAndGetUnsigned(int size, long offset, long increment);

    /**
     * Returns byte number at specified <code>offset</code>
     *
     * @param offset - offset of number
     * @return number
     */
    byte getByte(long offset);

    /**
     * Returns previous value of byte number and replaces it
     * with defined <code>value</code> at specified <code>offset</code>
     *
     * @param offset - offset of number
     * @param value - value of number
     * @return previous value of number
     */
    byte setByte(long offset, byte value);

    /**
     * Increments current byte value on defined <code>increment</code> value at specified <code>offset</code>
     * and returns result.
     *
     * @param offset - offset of number
     * @param increment - increment value
     * @return result value
     */
    byte incrementAndGetByte(long offset, byte increment);

    /**
     * Returns short number at specified <code>offset</code>
     *
     * @param offset - offset of number
     * @return number
     */
    short getShort(long offset);

    /**
     * Returns previous value of short number and replaces it
     * with defined <code>value</code> at specified <code>offset</code>
     *
     * @param offset - offset of number
     * @param value - value of number
     * @return previous value of number
     */
    short setShort(long offset, short value);

    /**
     * Increments current short value on defined <code>increment</code> value at specified <code>offset</code>
     * and returns result.
     *
     * @param offset - offset of number
     * @param increment - increment value
     * @return result value
     */
    short incrementAndGetShort(long offset, short increment);

    /**
     * Returns integer number at specified <code>offset</code>
     *
     * @param offset - offset of number
     * @return number
     */
    int getInteger(long offset);

    /**
     * Returns previous value of integer number and replaces it
     * with defined <code>value</code> at specified <code>offset</code>
     *
     * @param offset - offset of number
     * @param value - value of number
     * @return previous value of number
     */
    int setInteger(long offset, int value);

    /**
     * Increments current integer value on defined <code>increment</code> value at specified <code>offset</code>
     * and returns result.
     *
     * @param offset - offset of number
     * @param increment - increment value
     * @return result value
     */
    int incrementAndGetInteger(long offset, int increment);

    /**
     * Returns long number at specified <code>offset</code>
     *
     * @param offset - offset of number
     * @return number
     */
    long getLong(long offset);

    /**
     * Returns previous value of long number and replaces it
     * with defined <code>value</code> at specified <code>offset</code>
     *
     * @param offset - offset of number
     * @param value - value of number
     * @return previous value of number
     */
    long setLong(long offset, long value);

    /**
     * Increments current long value on defined <code>increment</code> value at specified <code>offset</code>
     * and returns result.
     *
     * @param offset - offset of number
     * @param increment - increment value
     * @return result value
     */
    long incrementAndGetLong(long offset, long increment);

    /**
     * Returns "logical size" = index of highest set bit plus one.
     * Returns zero if there are no any set bit.
     * 
     * @return "logical size" = index of highest set bit plus one
     */
    long length();

    /**
     * Set all bits to <code>value</code> from <code>fromIndex</code> (inclusive) to <code>toIndex</code> (exclusive)
     * 
     * @param fromIndex inclusive
     * @param toIndex exclusive
     * @param value true = 1, false = 0
     * 
     */
    void set(long fromIndex, long toIndex, boolean value);

    /**
     * Set all bits to zero from <code>fromIndex</code> (inclusive) to <code>toIndex</code> (exclusive)
     * 
     * @param fromIndex inclusive
     * @param toIndex exclusive
     * 
     */
    void clear(long fromIndex, long toIndex);

    /**
     * Copy bits state of source BitSet object to this object
     * 
     * @param bs - BitSet source
     */
    void set(BitSet bs);

    /**
     * Set all bits to <code>value</code> which index in indexArray
     *
     * @param indexArray The index array of bits that needs to be set to <code>value</code>
     * @param value true = 1, false = 0
     */
    void set(long[] indexArray, boolean value);

    /**
     * Executes NOT operation over all bits
     */
    void not();

    /**
     * Set all bits to one from <code>fromIndex</code> (inclusive) to <code>toIndex</code> (exclusive)
     * 
     * @param fromIndex inclusive
     * @param toIndex exclusive
     * 
     */
    void set(long fromIndex, long toIndex);

    /**
     * Returns number of set bits.
     * 
     * @return number of set bits.
     */
    long size();

    /**
     * Returns <code>true</code> if bit set to one and <code>false</code> overwise.
     * 
     * @param bitIndex - index of bit
     * @return <code>true</code> if bit set to one and <code>false</code> overwise.
     */
    boolean get(long bitIndex);
    
    /**
     * Returns a boolean array where each element of the array corresponds to the query result of the input parameters.
     *
     * @param bitIndexes indexes of bit
     * @return Returns a boolean array where each element of the array corresponds to the query result of the input parameters.
     */
    boolean[] get(long... bitIndexes);

    /**
     * Set bit to one at specified bitIndex
     * 
     * @param bitIndex - index of bit
     * @return <code>true</code> - if previous value was true, 
     * <code>false</code> - if previous value was false
     */
    boolean set(long bitIndex);

    /**
     * Set bit to <code>value</code> at specified <code>bitIndex</code>
     * 
     * @param bitIndex - index of bit
     * @param value true = 1, false = 0
     * @return <code>true</code> - if previous value was true,
     * <code>false</code> - if previous value was false
     *
    */
    boolean set(long bitIndex, boolean value);

    byte[] toByteArray();

    /**
     * Returns the number of bits set to one.
     * 
     * @return number of bits
     */
    long cardinality();

    /**
     * Set bit to zero at specified <code>bitIndex</code>
     * 
     * @param bitIndex - index of bit
     * @return <code>true</code> - if previous value was true, 
     * <code>false</code> - if previous value was false
     */
    boolean clear(long bitIndex);

    /**
     * Set all bits to zero
     */
    void clear();

    BitSet asBitSet();

    /**
     * Executes OR operation over this object and specified bitsets.
     * Stores result into this object.
     * 
     * @param bitSetNames - name of stored bitsets
     */
    void or(String... bitSetNames);

    /**
     * Executes AND operation over this object and specified bitsets.
     * Stores result into this object.
     * 
     * @param bitSetNames - name of stored bitsets
     */
    void and(String... bitSetNames);

    /**
     * Executes XOR operation over this object and specified bitsets.
     * Stores result into this object.
     * 
     * @param bitSetNames - name of stored bitsets
     */
    void xor(String... bitSetNames);

}
