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
package org.redisson.client.protocol.decoder;

import java.util.List;

import org.redisson.client.codec.Codec;
import org.redisson.client.codec.DoubleCodec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.handler.State;
import org.redisson.client.protocol.Decoder;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class ScoredSortedSetPolledObjectDecoder implements MultiDecoder<Object> {

    private final int valueIndex;

    /**
     * Decodes the reply of a blocking poll, {@code [key, value, score]}.
     */
    public ScoredSortedSetPolledObjectDecoder() {
        this(1);
    }

    /**
     * @param valueIndex position of the value in the reply. Everything before it is a key
     *                   decoded as a string, everything after it is a score decoded as a double.
     */
    public ScoredSortedSetPolledObjectDecoder(int valueIndex) {
        this.valueIndex = valueIndex;
    }

    @Override
    public Object decode(List<Object> parts, State state) {
        if (!parts.isEmpty()) {
            return parts.get(valueIndex);
        }
        return null;
    }

    @Override
    public Decoder<Object> getDecoder(Codec codec, int paramNum, State state, long size) {
        if (paramNum < valueIndex) {
            return StringCodec.INSTANCE.getValueDecoder();
        }
        if (paramNum > valueIndex) {
            return DoubleCodec.INSTANCE.getValueDecoder();
        }
        return MultiDecoder.super.getDecoder(codec, paramNum, state, size);
    }

}
