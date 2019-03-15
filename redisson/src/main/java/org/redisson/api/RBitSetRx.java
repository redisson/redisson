/**
 * Copyright (c) 2013-2019 Nikita Koksharov
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

import io.reactivex.Completable;
import io.reactivex.Single;

/**
 * RxJava2 interface for BitSet object
 *
 * @author Nikita Koksharov
 *
 */
public interface RBitSetRx extends RExpirableRx {

    Single<byte[]> toByteArray();

    /**
     * Returns "logical size" = index of highest set bit plus one.
     * Returns zero if there are no any set bit.
     * 
     * @return "logical size" = index of highest set bit plus one
     */
    Single<Long> length();

    /**
     * Set all bits to <code>value</code> from <code>fromIndex</code> (inclusive) to <code>toIndex</code> (exclusive)
     * 
     * @param fromIndex inclusive
     * @param toIndex exclusive
     * @param value true = 1, false = 0
     * @return void
     * 
     */
    Completable set(long fromIndex, long toIndex, boolean value);

    /**
     * Set all bits to zero from <code>fromIndex</code> (inclusive) to <code>toIndex</code> (exclusive)
     * 
     * @param fromIndex inclusive
     * @param toIndex exclusive
     * @return void
     * 
     */
    Completable clear(long fromIndex, long toIndex);

    /**
     * Copy bits state of source BitSet object to this object
     * 
     * @param bs - BitSet source
     * @return void
     */
    Completable set(BitSet bs);

    /**
     * Executes NOT operation over all bits
     * 
     * @return void
     */
    Completable not();

    /**
     * Set all bits to one from <code>fromIndex</code> (inclusive) to <code>toIndex</code> (exclusive)
     * 
     * @param fromIndex inclusive
     * @param toIndex exclusive
     * @return void
     */
    Completable set(long fromIndex, long toIndex);

    /**
     * Returns number of set bits.
     * 
     * @return number of set bits.
     */
    Single<Long> size();

    /**
     * Returns <code>true</code> if bit set to one and <code>false</code> overwise.
     * 
     * @param bitIndex - index of bit
     * @return <code>true</code> if bit set to one and <code>false</code> overwise.
     */
    Single<Boolean> get(long bitIndex);

    /**
     * Set bit to one at specified bitIndex
     * 
     * @param bitIndex - index of bit
     * @return <code>true</code> - if previous value was true, 
     * <code>false</code> - if previous value was false
     */
    Single<Boolean> set(long bitIndex);

    /**
     * Set bit to <code>value</code> at specified <code>bitIndex</code>
     * 
     * @param bitIndex - index of bit
     * @param value true = 1, false = 0
     * @return <code>true</code> - if previous value was true, 
     * <code>false</code> - if previous value was false
     */
    Single<Boolean> set(long bitIndex, boolean value);

    /**
     * Returns the number of bits set to one.
     * 
     * @return number of bits
     */
    Single<Long> cardinality();

    /**
     * Set bit to zero at specified <code>bitIndex</code>
     *
     * @param bitIndex - index of bit
     * @return <code>true</code> - if previous value was true, 
     * <code>false</code> - if previous value was false
     */
    Single<Boolean> clear(long bitIndex);

    /**
     * Set all bits to zero
     * 
     * @return void
     */
    Completable clear();

    /**
     * Executes OR operation over this object and specified bitsets.
     * Stores result into this object.
     * 
     * @param bitSetNames - name of stored bitsets
     * @return void
     */
    Completable or(String... bitSetNames);

    /**
     * Executes AND operation over this object and specified bitsets.
     * Stores result into this object.
     * 
     * @param bitSetNames - name of stored bitsets
     * @return void
     */
    Completable and(String... bitSetNames);

    /**
     * Executes XOR operation over this object and specified bitsets.
     * Stores result into this object.
     * 
     * @param bitSetNames - name of stored bitsets
     * @return void
     */
    Completable xor(String... bitSetNames);

}
