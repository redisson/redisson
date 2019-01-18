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
import java.util.List;
import java.util.Map;

import org.redisson.api.StreamMessageId;
import org.redisson.client.handler.State;
import org.redisson.client.protocol.Decoder;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class StreamResultDecoder implements MultiDecoder<Object> {

    @Override
    public Object decode(List<Object> parts, State state) {
        if (!parts.isEmpty()) {
            Map<String, Map<StreamMessageId, Map<Object, Object>>> result = (Map<String, Map<StreamMessageId, Map<Object, Object>>>) parts.get(0);
            return result.values().iterator().next();
        }
        return Collections.emptyMap();
    }

    @Override
    public Decoder<Object> getDecoder(int paramNum, State state) {
        return null;
    }

}
