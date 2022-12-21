/**
 * Copyright (c) 2013-2022 Nikita Koksharov
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

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.redisson.client.codec.Codec;
import org.redisson.client.handler.State;
import org.redisson.client.protocol.Decoder;

import io.netty.buffer.ByteBuf;
import io.netty.util.CharsetUtil;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class StringMapDataDecoder implements MultiDecoder<Map<String, String>> {

    private final Decoder decoder = new Decoder() {
        @Override
        public Object decode(ByteBuf buf, State state) throws IOException {
            String value = buf.toString(CharsetUtil.UTF_8);
            Map<String, String> result = new HashMap<String, String>();
            for (String entry : value.split("\r\n|\n")) {
                String[] parts = entry.split(":");
                if (parts.length == 2) {
                    result.put(parts[0], parts[1]);
                }
            }
            return result;
        }
    };

    @Override
    public Decoder<Object> getDecoder(Codec codec, int paramNum, State state) {
        return decoder;
    }

    @Override
    public Map<String, String> decode(List<Object> parts, State state) {
        return null;
    }
}
