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
package org.redisson.client.protocol.decoder;

import java.io.IOException;
import java.util.List;

import org.redisson.client.handler.State;
import org.redisson.client.protocol.Decoder;

import io.netty.buffer.ByteBuf;
import io.netty.util.CharsetUtil;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class KeyValueObjectDecoder implements MultiDecoder<Object> {

    @Override
    public Decoder<Object> getDecoder(int paramNum, State state) {
        if (paramNum == 0) {
            return new Decoder<Object>() {
                @Override
                public Object decode(ByteBuf buf, State state) throws IOException {
                    String status = buf.toString(CharsetUtil.UTF_8);
                    buf.skipBytes(1);
                    return status;
                }
            };
        }
        return null;
    }
    
    @Override
    public Object decode(List<Object> parts, State state) {
        if (parts.isEmpty()) {
            return null;
        }
        return new KeyValueMessage(parts.get(0), parts.get(1));
    }

}
