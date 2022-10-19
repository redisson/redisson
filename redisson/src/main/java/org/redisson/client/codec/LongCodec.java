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
package org.redisson.client.codec;

import java.io.IOException;

import org.redisson.client.handler.State;
import org.redisson.client.protocol.Decoder;

import io.netty.buffer.ByteBuf;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class LongCodec extends StringCodec {

    public static final LongCodec INSTANCE = new LongCodec();

    private final Decoder<Object> decoder = new Decoder<Object>() {
        @Override
        public Object decode(ByteBuf buf, State state) throws IOException {
            String str = (String) LongCodec.super.getValueDecoder().decode(buf, state);
            return Long.valueOf(str);
        }
    };

    @Override
    public Decoder<Object> getValueDecoder() {
        return decoder;
    }

}
