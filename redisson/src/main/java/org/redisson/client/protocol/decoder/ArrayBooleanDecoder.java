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

import org.redisson.client.handler.State;

import java.util.List;

/**
 *
 * @author seakider
 *
 */
public class ArrayBooleanDecoder implements MultiDecoder<boolean[]> {
    
    @Override
    public boolean[] decode(List<Object> parts, State state) {
        if (parts.isEmpty()) {
            return new boolean[0];
        }
        
        boolean[] result = new boolean[parts.size()];
        for (int i = 0; i < parts.size(); i++) {
            Object part = parts.get(i);
            if (part instanceof Boolean) {
                result[i] = (boolean) part;
            } else {
                result[i] = false;
            }
        }
        return result;
    }
}
