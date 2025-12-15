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

import org.redisson.api.stream.StreamMessageId;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.handler.State;
import org.redisson.client.protocol.Decoder;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class StreamResultDecoderV2 implements MultiDecoder<Object> {

    private final boolean firstResult;

    public StreamResultDecoderV2(boolean firstResult) {
        super();
        this.firstResult = firstResult;
    }

    @Override
    public Decoder<Object> getDecoder(Codec codec, int paramNum, State state, long size) {
        return StringCodec.INSTANCE.getValueDecoder();
    }

    @Override
    public Object decode(List<Object> parts, State state) {
        List<Object> list = parts;

        Map<String, Map<StreamMessageId, Map<Object, Object>>> result = new HashMap<>();
        for (int i = 0; i < list.size(); i += 2) {
            String name = (String) list.get(i);
            List<List<Object>> streamEntries = (List<List<Object>>) list.get(i + 1);
            if (!streamEntries.isEmpty()) {
                Map<StreamMessageId, Map<Object, Object>> ee = new LinkedHashMap<>();
                result.put(name, ee);

                for (List<Object> se : streamEntries) {
                    ee.put((StreamMessageId) se.get(0), (Map<Object, Object>) se.get(1));
                }

                if (firstResult) {
                    return ee;
                }
            }
        }
        return result;
    }

}
