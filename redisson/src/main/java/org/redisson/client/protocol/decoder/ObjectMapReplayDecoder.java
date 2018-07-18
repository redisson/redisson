/**
 * Copyright 2018 Nikita Koksharov
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.redisson.client.handler.State;
import org.redisson.client.protocol.Decoder;
import org.redisson.client.protocol.convertor.Convertor;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class ObjectMapReplayDecoder implements MultiDecoder<Map<Object, Object>> {

    private Decoder<Object> codec;
    private Convertor<?> convertor;
    
    public ObjectMapReplayDecoder() {
    }
    
    public ObjectMapReplayDecoder(Decoder<Object> codec) {
        super();
        this.codec = codec;
    }
    
    public ObjectMapReplayDecoder(Decoder<Object> codec, Convertor<?> convertor) {
        super();
        this.codec = codec;
        this.convertor = convertor;
    }

    @Override
    public Map<Object, Object> decode(List<Object> parts, State state) {
        Map<Object, Object> result = new LinkedHashMap<Object, Object>(parts.size()/2);
        for (int i = 0; i < parts.size(); i++) {
            if (i % 2 != 0) {
                if (convertor != null) {
                    result.put(convertor.convert(parts.get(i-1)), parts.get(i));
                } else {
                    result.put(parts.get(i-1), parts.get(i));
                }
           }
        }
        return result;
    }

    @Override
    public Decoder<Object> getDecoder(int paramNum, State state) {
        if (codec != null) {
            return codec;
        }
        return null;
    }

}
