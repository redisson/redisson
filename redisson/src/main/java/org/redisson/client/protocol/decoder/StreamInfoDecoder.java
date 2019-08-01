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

import java.util.List;
import java.util.Map;

import org.redisson.api.StreamInfo;
import org.redisson.api.StreamMessageId;
import org.redisson.client.handler.State;
import org.redisson.client.protocol.Decoder;
import org.redisson.client.protocol.convertor.StreamIdConvertor;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class StreamInfoDecoder implements MultiDecoder<StreamInfo<Object, Object>> {

    @Override
    public Decoder<Object> getDecoder(int paramNum, State state) {
        return null;
    }

    @Override
    public StreamInfo<Object, Object> decode(List<Object> parts, State state) {
        StreamInfo<Object, Object> info = new StreamInfo<>();
        info.setLength(((Long) parts.get(1)).intValue());
        info.setRadixTreeKeys(((Long) parts.get(3)).intValue());
        info.setRadixTreeNodes(((Long) parts.get(5)).intValue());
        info.setGroups(((Long) parts.get(7)).intValue());
        info.setLastGeneratedId(StreamIdConvertor.INSTANCE.convert(parts.get(9)));

        List<?> firstEntry = (List<?>) parts.get(11);
        if (firstEntry != null) {
            StreamMessageId firstId = StreamIdConvertor.INSTANCE.convert(firstEntry.get(0));
            Map<Object, Object> firstData = (Map<Object, Object>) firstEntry.get(1);
            StreamInfo.Entry<Object, Object> first = new StreamInfo.Entry<>(firstId, firstData);
            info.setFirstEntry(first);
        }

        List<?> lastEntry = (List<?>) parts.get(13);
        if (lastEntry != null) {
            StreamMessageId lastId = StreamIdConvertor.INSTANCE.convert(lastEntry.get(0));
            Map<Object, Object> lastData = (Map<Object, Object>) lastEntry.get(1);
            StreamInfo.Entry<Object, Object> last = new StreamInfo.Entry<>(lastId, lastData);
            info.setLastEntry(last);
        }
        return info;
    }

}
