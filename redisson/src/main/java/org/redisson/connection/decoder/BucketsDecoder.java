/**
 * Copyright (c) 2013-2021 Nikita Koksharov
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
package org.redisson.connection.decoder;

import org.redisson.client.handler.State;
import org.redisson.client.protocol.decoder.MultiDecoder;

import java.util.*;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class BucketsDecoder implements MultiDecoder<Map<Object, Object>> {

    private final Object key;
    
    public BucketsDecoder(Object key) {
        this.key = key;
    }

    @Override
    public Map<Object, Object> decode(List<Object> parts, State state) {
        if (parts.isEmpty()) {
            return new HashMap<Object, Object>();
        }

        Map<Object, Object> result = new LinkedHashMap<Object, Object>(parts.size());

        List<String> keys = (List<String>) key;

        for(int index = 0; index < keys.size(); index++){
            Object value = parts.get(index);
            if (value == null) {
                continue;
            }
            result.put(keys.get(index),value);
        }
        return result;
    }

}
