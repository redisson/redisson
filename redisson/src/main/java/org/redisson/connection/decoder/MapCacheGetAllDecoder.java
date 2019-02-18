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
package org.redisson.connection.decoder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.redisson.client.handler.State;
import org.redisson.client.protocol.Decoder;
import org.redisson.client.protocol.decoder.MultiDecoder;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class MapCacheGetAllDecoder implements MultiDecoder<List<Object>> {

    private final int shiftIndex;
    private final List<Object> args;
    private final boolean allowNulls;

    public MapCacheGetAllDecoder(List<Object> args, int shiftIndex) {
        this(args, shiftIndex, false);
    }
    
    public MapCacheGetAllDecoder(List<Object> args, int shiftIndex, boolean allowNulls) {
        this.args = args;
        this.shiftIndex = shiftIndex;
        this.allowNulls = allowNulls;
    }

    @Override
    public Decoder<Object> getDecoder(int paramNum, State state) {
        return null;
    }
    
    @Override
    public List<Object> decode(List<Object> parts, State state) {
        if (parts.isEmpty()) {
            return Collections.emptyList();
        }
        
        List<Object> result = new ArrayList<Object>(parts.size()*5);
        for (int index = 0; index < parts.size(); index += 4) {
            Object value = parts.get(index);
            if (!allowNulls && value == null) {
                continue;
            }
            
            Object key = args.get(index/4+shiftIndex);
            result.add(key);
            result.add(value);
            result.add(parts.get(index+1));
            result.add(parts.get(index+2));
            result.add(parts.get(index+3));
        }
        return result;
    }

}
