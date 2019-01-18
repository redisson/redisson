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
package org.redisson.spring.data.connection;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.redisson.client.codec.DoubleCodec;
import org.redisson.client.handler.State;
import org.redisson.client.protocol.Decoder;
import org.redisson.client.protocol.decoder.MultiDecoder;
import org.springframework.data.redis.connection.DefaultTuple;
import org.springframework.data.redis.connection.RedisZSetCommands.Tuple;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class ScoredSortedSetReplayDecoder implements MultiDecoder<Set<Tuple>> {

    @Override
    public Decoder<Object> getDecoder(int paramNum, State state) {
        if (paramNum % 2 != 0) {
            return DoubleCodec.INSTANCE.getValueDecoder();
        }
        return null;
    }
    
    @Override
    public Set<Tuple> decode(List<Object> parts, State state) {
        Set<Tuple> result = new LinkedHashSet<Tuple>();
        for (int i = 0; i < parts.size(); i += 2) {
            result.add(new DefaultTuple((byte[])parts.get(i), ((Number)parts.get(i+1)).doubleValue()));
        }
        return result;
    }

}
