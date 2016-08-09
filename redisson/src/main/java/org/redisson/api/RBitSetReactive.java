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

import org.reactivestreams.Publisher;

/**
 *
 * @author Nikita Koksharov
 *
 */
public interface RBitSetReactive extends RExpirableReactive {

    Publisher<BitSet> asBitSet();

    Publisher<byte[]> toByteArray();

    Publisher<Long> length();

    Publisher<Void> set(long fromIndex, long toIndex, boolean value);

    Publisher<Void> clear(long fromIndex, long toIndex);

    Publisher<Void> set(BitSet bs);

    Publisher<Void> not();

    Publisher<Void> set(long fromIndex, long toIndex);

    Publisher<Integer> size();

    Publisher<Boolean> get(long bitIndex);

    Publisher<Void> set(long bitIndex);

    Publisher<Void> set(long bitIndex, boolean value);

    Publisher<Long> cardinality();

    Publisher<Void> clear(long bitIndex);

    Publisher<Void> clear();

    Publisher<Void> or(String... bitSetNames);

    Publisher<Void> and(String... bitSetNames);

    Publisher<Void> xor(String... bitSetNames);

}
