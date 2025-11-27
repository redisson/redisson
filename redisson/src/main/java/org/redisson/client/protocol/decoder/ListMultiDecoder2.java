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

import java.util.List;

import org.redisson.client.codec.Codec;
import org.redisson.client.handler.State;
import org.redisson.client.protocol.Decoder;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <T> type
 */
public class ListMultiDecoder2<T> implements MultiDecoder<Object> {

    private final MultiDecoder<?>[] decoders;
    
    public ListMultiDecoder2(MultiDecoder<?>... decoders) {
        this.decoders = decoders;
    }

    @Override
    public Decoder<Object> getDecoder(Codec codec, int paramNum, State state, long size, List<Object> parts) {
        int index = state.getLevel();
        return decoders[index].getDecoder(codec, paramNum, state, size, parts);
    }

    @Override
    public Decoder<Object> getDecoder(Codec codec, int paramNum, State state, long size) {
        int index = state.getLevel();
        return decoders[index].getDecoder(codec, paramNum, state, size);
    }
    
    @Override
    public Object decode(List<Object> parts, State state) {
        int index = state.getLevel();
        return decoders[index].decode(parts, state);
    }
    
}
