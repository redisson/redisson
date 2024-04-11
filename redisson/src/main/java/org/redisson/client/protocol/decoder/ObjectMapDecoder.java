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
import java.util.Map;

import org.redisson.client.codec.Codec;
import org.redisson.client.handler.State;
import org.redisson.client.protocol.Decoder;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class ObjectMapDecoder implements MultiDecoder<Object> {

    private final boolean decodeList;
    
    public ObjectMapDecoder(boolean decodeList) {
        super();
        this.decodeList = decodeList;
    }

    @Override
    public Decoder<Object> getDecoder(Codec codec, int paramNum, State state, long size) {
        if (state.getValue() != null && (Boolean) state.getValue()) {
            return codec.getMapKeyDecoder();
        }
        
        if (paramNum % 2 == 0) {
            return codec.getMapKeyDecoder();
        }
        return codec.getMapValueDecoder();
    }
    
    @Override
    public Object decode(List<Object> parts, State state) {
        if (decodeList && (state.getValue() != null && (Boolean) state.getValue())) {
            return parts;
        }

        Map<Object, Object> result = MultiDecoder.newLinkedHashMap(parts.size()/2);
        for (int i = 0; i < parts.size(); i++) {
            if (i % 2 != 0) {
                result.put(parts.get(i-1), parts.get(i));
           }
        }

        state.setValue(true);
        return result;
    }

}
