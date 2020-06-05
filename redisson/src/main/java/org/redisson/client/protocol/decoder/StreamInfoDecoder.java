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

import java.util.List;
import java.util.Map;

import org.redisson.api.StreamInfo;
import org.redisson.api.StreamMessageId;
import org.redisson.client.handler.State;
import org.redisson.client.protocol.Decoder;
import org.redisson.client.protocol.convertor.StreamIdConvertor;

/**
 *
 * @author Nikita Koksharov, Fabian Witte
 *
 */
public class StreamInfoDecoder implements MultiDecoder<StreamInfo<Object, Object>> {

    @Override
    public Decoder<Object> getDecoder(int paramNum, State state) {
        return null;
    }

    @Override
    public StreamInfo<Object, Object> decode(List<Object> parts, State state) {
        StreamInfoWrapper fields = new StreamInfoWrapper(parts);

        StreamInfo<Object, Object> info = new StreamInfo<>();
        info.setLength(((Long) fields.get(StreamInfoWrapper.Key.LENGTH)).intValue());
        info.setRadixTreeKeys(((Long) fields.get(StreamInfoWrapper.Key.RADIX_TREE_KEYS)).intValue());
        info.setRadixTreeNodes(((Long) fields.get(StreamInfoWrapper.Key.RADIX_TREE_NODES)).intValue());
        info.setGroups(((Long) fields.get(StreamInfoWrapper.Key.GROUPS)).intValue());
        info.setLastGeneratedId(StreamIdConvertor.INSTANCE.convert(fields.get(StreamInfoWrapper.Key.LAST_GENERATED_ID)));

        List<?> firstEntry = (List<?>) fields.get(StreamInfoWrapper.Key.FIRST_ENTRY);
        if (firstEntry != null) {
            StreamInfo.Entry<Object, Object> first = createStreamInfoEntry(firstEntry);
            info.setFirstEntry(first);
        }

        List<?> lastEntry = (List<?>) fields.get(StreamInfoWrapper.Key.LAST_ENTRY);
        if (lastEntry != null) {
            StreamInfo.Entry<Object, Object> last = createStreamInfoEntry(lastEntry);
            info.setLastEntry(last);
        }
        return info;
    }

    private StreamInfo.Entry<Object, Object> createStreamInfoEntry(List<?> fieldValue) {
        StreamMessageId id = StreamIdConvertor.INSTANCE.convert(fieldValue.get(0));
        Map<Object, Object> data = (Map<Object, Object>) fieldValue.get(1);
        return new StreamInfo.Entry<>(id, data);
    }

    /**
     * Wrapper for StreamInfo fields.
     */
    private static final class StreamInfoWrapper {
        private enum Key {
            LENGTH("length"),
            RADIX_TREE_KEYS("radix-tree-keys"),
            RADIX_TREE_NODES("radix-tree-nodes"),
            GROUPS("groups"),
            LAST_GENERATED_ID("last-generated-id"),
            FIRST_ENTRY("first-entry"),
            LAST_ENTRY("last-entry");

            private final String value;

            Key(String value) {
                this.value = value;
            }
        }

        private List<Object> parts;

        private StreamInfoWrapper(List<Object> parts) {
            this.parts = parts;
        }

        public Object get(Key key) {
            int index = parts.indexOf(key.value);

            if (index > -1) {
                return parts.get(index+1);
            } else {
                return null;
            }
        }
    }

}
