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

import org.redisson.client.codec.Codec;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.handler.State;
import org.redisson.client.protocol.Decoder;

import java.util.List;

/**
 * Decodes {@code EVAL} response shaped like {@code {status, value, token}} where:
 * <ul>
 *     <li>{@code status}: {@link Long}</li>
 *     <li>{@code value}: decoded using {@code codec.getMapValueDecoder()}</li>
 *     <li>{@code token}: {@link Long}</li>
 * </ul>
 *
 * @author nhancdt2602
 */
public class MapValueLeaseDecoder implements MultiDecoder<List<Object>> {

    @Override
    public Decoder<Object> getDecoder(Codec codec, int paramNum, State state, long size, List<Object> parts) {
        if (paramNum == 0) {
            return LongCodec.INSTANCE.getValueDecoder();
        }
        if (paramNum == 1) {
            return codec.getMapValueDecoder();
        }
        if (paramNum == 2) {
            return LongCodec.INSTANCE.getValueDecoder();
        }
        return MultiDecoder.super.getDecoder(codec, paramNum, state, size, parts);
    }

    @Override
    public List<Object> decode(List<Object> parts, State state) {
        return parts;
    }
}

