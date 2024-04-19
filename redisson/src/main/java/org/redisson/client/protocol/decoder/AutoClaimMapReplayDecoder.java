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
import org.redisson.client.handler.State;
import org.redisson.client.protocol.Decoder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class AutoClaimMapReplayDecoder implements MultiDecoder<Object> {

    @Override
    public Decoder<Object> getDecoder(Codec codec, int paramNum, State state, long size) {
        if (state.getValue() != null) {
            return new StreamIdDecoder();
        }
        return MultiDecoder.super.getDecoder(codec, paramNum, state, size);
    }

    @Override
    public Object decode(List<Object> parts, State state) {
        if (state.getValue() != null) {
            return parts;
        }

        state.setValue(true);
        List<List<Object>> list = (List<List<Object>>) (Object) parts;
        return list.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(e -> e.get(0), e -> e.get(1),
                        (a, b) -> a, LinkedHashMap::new));
    }

}
