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

import org.redisson.api.PendingResult;
import org.redisson.client.handler.State;
import org.redisson.client.protocol.convertor.StreamIdConvertor;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class PendingResultDecoder implements MultiDecoder<Object> {

    private final StreamIdConvertor convertor = new StreamIdConvertor();

    @Override
    public Object decode(List<Object> parts, State state) {
        if (parts.isEmpty()) {
            return null;            
        }
        
        List<List<String>> customerParts = (List<List<String>>) parts.get(3);
        if (customerParts.isEmpty()) {
            return new PendingResult(0, null, null, Collections.emptyMap());
        }
        
        Map<String, Long> consumerNames = new LinkedHashMap<String, Long>();
        for (List<String> mapping : customerParts) {
            consumerNames.put(mapping.get(0), Long.valueOf(mapping.get(1)));
        }
        return new PendingResult((Long) parts.get(0), convertor.convert(parts.get(1)), convertor.convert(parts.get(2)), consumerNames);
    }

}
