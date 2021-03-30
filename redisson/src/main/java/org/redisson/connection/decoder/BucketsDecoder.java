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

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class BucketsDecoder implements MultiDecoder<Map<Object, Object>> {

    private final String key;
    
    public BucketsDecoder(String key) {
        this.key = key;
    }

    @Override
    public Map<Object, Object> decode(List<Object> parts, State state) {
        if (parts.isEmpty()) {
            return new HashMap<Object, Object>();
        }

        return Collections.singletonMap(key, parts.get(0));
    }

}
