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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.redisson.client.handler.State;
import org.redisson.client.protocol.Decoder;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class StreamObjectMapReplayDecoder extends ObjectMapReplayDecoder {

    private Decoder<Object> codec;
    
    public StreamObjectMapReplayDecoder() {
    }
    
    public StreamObjectMapReplayDecoder(Decoder<Object> codec) {
        super();
        this.codec = codec;
    }

    @Override
    public Map<Object, Object> decode(List<Object> parts, State state) {
        if (parts.get(0) == null
                || (parts.get(0) instanceof List && ((List) parts.get(0)).isEmpty())) {
            parts.clear();
            return Collections.emptyMap();
        }

        if (parts.get(0) instanceof Map) {
            Map<Object, Object> result = new LinkedHashMap<Object, Object>(parts.size());
            for (int i = 0; i < parts.size(); i++) {
                result.putAll((Map<? extends Object, ? extends Object>) parts.get(i));
            }
            return result;
        }
        return super.decode(parts, state);
    }

    @Override
    public Decoder<Object> getDecoder(int paramNum, State state) {
        if (codec != null) {
            return codec;
        }
        return null;
    }

}
