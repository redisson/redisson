/**
 * Copyright 2014 Nikita Koksharov, Nickolay Borbit
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
package org.redisson.core;

import java.util.BitSet;

import org.reactivestreams.Publisher;

/**
 *
 * @author Nikita Koksharov
 *
 */
public interface RBitSetReactive extends RExpirableReactive {

    Publisher<BitSet> asBitSet();

    Publisher<byte[]> toByteArray();

    Publisher<Integer> length();

    Publisher<Void> set(int fromIndex, int toIndex, boolean value);

    Publisher<Void> clear(int fromIndex, int toIndex);

    Publisher<Void> set(BitSet bs);

    Publisher<Void> not();

    Publisher<Void> set(int fromIndex, int toIndex);

    Publisher<Integer> size();

    Publisher<Boolean> get(int bitIndex);

    Publisher<Void> set(int bitIndex);

    Publisher<Void> set(int bitIndex, boolean value);

    Publisher<Integer> cardinality();

    Publisher<Void> clear(int bitIndex);

    Publisher<Void> clear();

    Publisher<Void> or(String... bitSetNames);

    Publisher<Void> and(String... bitSetNames);

    Publisher<Void> xor(String... bitSetNames);

}
