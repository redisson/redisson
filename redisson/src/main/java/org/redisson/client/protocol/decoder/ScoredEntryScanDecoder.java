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

import org.redisson.client.codec.Codec;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.handler.State;
import org.redisson.client.protocol.Decoder;
import org.redisson.client.protocol.ScoredEntry;

import java.util.LinkedList;
import java.util.List;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <T> type
 */
public class ScoredEntryScanDecoder<T> implements MultiDecoder<ListScanResult<ScoredEntry<T>>> {

    @Override
    public Decoder<Object> getDecoder(Codec codec, int paramNum, State state) {
        return LongCodec.INSTANCE.getValueDecoder();
    }
    
    @Override
    public ListScanResult<ScoredEntry<T>> decode(List<Object> parts, State state) {
        List<ScoredEntry<T>> result = new LinkedList<>();
        List<Object> values = (List<Object>) parts.get(1);
        for (int i = 0; i < values.size(); i += 2) {
            result.add(new ScoredEntry<T>(((Number) values.get(i+1)).doubleValue(), (T) values.get(i)));
        }
        return new ListScanResult<>((Long) parts.get(0), result);
    }

}
