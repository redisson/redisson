/**
 * Copyright (c) 2013-2019 Nikita Koksharov
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
 */
public class StreamInfoMapDecoder implements MultiDecoder<Object> {

    boolean hasNonZeroLevel = false;
    final StreamInfoDecoder streamInfo = new StreamInfoDecoder();
    final ObjectMapDecoder decoder;
    
    public StreamInfoMapDecoder(Codec codec) {
        decoder = new ObjectMapDecoder(codec);
    }
    
    @Override
    public Decoder<Object> getDecoder(int paramNum, State state) {
        if (state.getLevel() > 0) {
            hasNonZeroLevel = true;
        }
        if (state.getLevel() == 2) {
            return decoder.getDecoder(paramNum, state);
        }
        return null;
    }
    
    @Override
    public Object decode(List<Object> parts, State state) {
        if (hasNonZeroLevel) {
            return decoder.decode(parts, state);
        }
        return streamInfo.decode(parts, state);
    }

}
