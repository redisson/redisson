/**
 * Copyright (c) 2013-2020 Nikita Koksharov
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

import org.redisson.api.StreamMessageId;
import org.redisson.client.handler.State;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class StreamResultDecoder implements MultiDecoder<Object> {

    private final boolean firstResult;
    
    public StreamResultDecoder(boolean firstResult) {
        super();
        this.firstResult = firstResult;
    }

    @Override
    public Object decode(List<Object> parts, State state) {
        List<List<Object>> list = (List<List<Object>>) (Object) parts;
        Map<String, Map<StreamMessageId, Map<Object, Object>>> result = new HashMap<>();
        for (List<Object> entries : list) {
            List<List<Object>> streamEntries = (List<List<Object>>) entries.get(1);
            if (!streamEntries.isEmpty()) {
                String name = (String) entries.get(0);
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
