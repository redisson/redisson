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

import org.redisson.client.handler.State;
import org.redisson.client.protocol.ScoreAttributesEntry;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author seakider
 *
 * @param <T> type
 */
public class ScoredAttributesReplayDecoderV2<T> implements MultiDecoder<List<ScoreAttributesEntry<T>>> {

    @Override
    public List<ScoreAttributesEntry<T>> decode(List<Object> parts, State state) {
        List<ScoreAttributesEntry<T>> result = new ArrayList<>();
        for (int i = 0; i < parts.size(); i += 2) {
            List<Object> entry = (List<Object>) parts.get(i + 1);
            result.add(new ScoreAttributesEntry<T>(((Number) entry.get(0)).doubleValue(), (T) parts.get(i), (String) entry.get(1)));
        }
        return result;
    }
}
