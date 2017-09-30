/**
 * Copyright 2016 Nikita Koksharov
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

    long length();

    void set(long fromIndex, long toIndex, boolean value);

    void clear(long fromIndex, long toIndex);

    void set(BitSet bs);

    void not();

    void set(long fromIndex, long toIndex);

    int size();

    boolean get(long bitIndex);

    void set(long bitIndex);

    void set(long bitIndex, boolean value);

    byte[] toByteArray();

    long cardinality();

    void clear(long bitIndex);

    void clear();

    BitSet asBitSet();

    void or(String... bitSetNames);

    void and(String... bitSetNames);

    void xor(String... bitSetNames);

}
