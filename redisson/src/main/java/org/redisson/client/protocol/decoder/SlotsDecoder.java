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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.redisson.client.codec.StringCodec;
import org.redisson.client.handler.State;
import org.redisson.client.protocol.Decoder;
import org.redisson.cluster.ClusterSlotRange;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class SlotsDecoder implements MultiDecoder<Object> {

    @Override
    public Decoder<Object> getDecoder(int paramNum, State state) {
        return StringCodec.INSTANCE.getValueDecoder();
    }
    
    @Override
    public Object decode(List<Object> parts, State state) {
        if (parts.size() > 2 && parts.get(0) instanceof List) {
            Map<ClusterSlotRange, Set<String>> result = new HashMap<ClusterSlotRange, Set<String>>();
            List<List<Object>> rows = (List<List<Object>>)(Object)parts;
            for (List<Object> row : rows) {
                Iterator<Object> iterator = row.iterator();
                Long startSlot = (Long)iterator.next();
                Long endSlot = (Long)iterator.next();
                ClusterSlotRange range = new ClusterSlotRange(startSlot.intValue(), endSlot.intValue());
                Set<String> addresses = new HashSet<String>();
                while(iterator.hasNext()) {
                    List<Object> addressParts = (List<Object>) iterator.next();
                    addresses.add(addressParts.get(0) + ":" + addressParts.get(1));
                }
                result.put(range, addresses);
            }
            return result;
        }
        return parts;
    }

}
