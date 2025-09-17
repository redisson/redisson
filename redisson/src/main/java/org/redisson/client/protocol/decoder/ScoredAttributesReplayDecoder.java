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
package org.redisson.client.protocol.decoder;

import org.redisson.client.codec.Codec;
import org.redisson.client.codec.DoubleCodec;
import org.redisson.client.handler.State;
import org.redisson.client.protocol.Decoder;
import org.redisson.client.protocol.ScoreAttributesEntry;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author seakider
 *
 * @param <T> type
 */
public class ScoredAttributesReplayDecoder<T> implements MultiDecoder<List<ScoreAttributesEntry<T>>> {

    @Override
    public Decoder<Object> getDecoder(Codec codec, int paramNum, State state, long size) {
        if (paramNum % 3 == 1) {
            return DoubleCodec.INSTANCE.getValueDecoder();
        }

        return MultiDecoder.super.getDecoder(codec, paramNum, state, size);
    }

    @Override
    public List<ScoreAttributesEntry<T>> decode(List<Object> parts, State state) {
        List<ScoreAttributesEntry<T>> result = new ArrayList<>();
        for (int i = 0; i < parts.size(); i += 3) {
            result.add(new ScoreAttributesEntry<>(((Number) parts.get(i + 1)).doubleValue(), (T) parts.get(i), (String) parts.get(i + 2)));
        }
        return result;
    }
}
