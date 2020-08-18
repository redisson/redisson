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

import org.redisson.api.TimeSeriesEntry;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.handler.State;
import org.redisson.client.protocol.Decoder;

import java.util.ArrayList;
import java.util.List;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <T> type of element
 */
public class TimeSeriesEntryReplayDecoder<T> implements MultiDecoder<List<TimeSeriesEntry<T>>> {

    @Override
    public Decoder<Object> getDecoder(int paramNum, State state) {
        if (paramNum % 2 != 0) {
            return LongCodec.INSTANCE.getValueDecoder();
        }
        return null;
    }
    
    @Override
    public List<TimeSeriesEntry<T>> decode(List<Object> parts, State state) {
        List<TimeSeriesEntry<T>> result = new ArrayList<>();
        for (int i = 0; i < parts.size(); i += 2) {
            result.add(new TimeSeriesEntry<T>((Long) parts.get(i + 1), (T) parts.get(i)));
        }
        return result;
    }

}
