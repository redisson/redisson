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

import org.redisson.api.TimeSeriesEntry;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.handler.State;
import org.redisson.client.protocol.Decoder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class TimeSeriesEntryReplayDecoder implements MultiDecoder<List<TimeSeriesEntry<Object, Object>>> {

    private boolean reverse;

    public TimeSeriesEntryReplayDecoder() {
        this(false);
    }

    public TimeSeriesEntryReplayDecoder(boolean reverse) {
        this.reverse = reverse;
    }

    @Override
    public Decoder<Object> getDecoder(Codec codec, int paramNum, State state) {
        if (paramNum % 4 == 2 || paramNum % 4 == 3) {
            return LongCodec.INSTANCE.getValueDecoder();
        }
        return MultiDecoder.super.getDecoder(codec, paramNum, state);
    }
    
    @Override
    public List<TimeSeriesEntry<Object, Object>> decode(List<Object> parts, State state) {
        List<TimeSeriesEntry<Object, Object>> result = new ArrayList<>();
        for (int i = 0; i < parts.size(); i += 4) {
            Long n = (Long) parts.get(i + 2);
            Object label = null;
            if (n == 3) {
               label = parts.get(i + 1);
            }
            result.add(new TimeSeriesEntry<>((Long) parts.get(i + 3), parts.get(i), label));
        }
        if (reverse) {
            Collections.reverse(result);
        }
        return result;
    }

}
