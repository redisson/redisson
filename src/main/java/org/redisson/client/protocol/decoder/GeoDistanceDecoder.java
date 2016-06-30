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

import org.redisson.client.codec.Codec;
import org.redisson.client.codec.DoubleCodec;
import org.redisson.client.handler.State;

import io.netty.buffer.ByteBuf;
import io.netty.util.CharsetUtil;

public class GeoDistanceDecoder implements MultiDecoder<List<Object>> {

    private final ThreadLocal<Integer> pos = new ThreadLocal<Integer>();
    
    private final Codec codec;
    
    public GeoDistanceDecoder(Codec codec) {
        super();
        this.codec = codec;
    }

    @Override
    public Object decode(ByteBuf buf, State state) throws IOException {
        if (pos.get() % 2 == 0) {
            return codec.getValueDecoder().decode(buf, state);
        }
        return DoubleCodec.INSTANCE.getValueDecoder().decode(buf, state);
    }

    @Override
    public boolean isApplicable(int paramNum, State state) {
        pos.set(paramNum);
        return true;
    }

    @Override
    public List<Object> decode(List<Object> parts, State state) {
        return parts;
    }

}
