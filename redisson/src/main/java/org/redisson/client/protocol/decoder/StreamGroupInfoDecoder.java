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

import org.redisson.api.stream.StreamGroup;
import org.redisson.client.handler.State;
import org.redisson.client.protocol.convertor.StreamIdConvertor;

import java.util.List;
import java.util.Optional;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class StreamGroupInfoDecoder implements MultiDecoder<StreamGroup> {

    @Override
    public StreamGroup decode(List<Object> parts, State state) {
        if (parts.size() == 8) {
            return new StreamGroup((String) parts.get(1),
                                    ((Long) parts.get(3)).intValue(),
                                    ((Long) parts.get(5)).intValue(),
                                    StreamIdConvertor.INSTANCE.convert(parts.get(7)));
        }

        return new StreamGroup((String) parts.get(1),
                ((Long) parts.get(3)).intValue(),
                ((Long) parts.get(5)).intValue(),
                StreamIdConvertor.INSTANCE.convert(parts.get(7)),
                Optional.ofNullable((Long) parts.get(9)).orElse(0L).intValue(),
                Optional.ofNullable((Long) parts.get(11)).orElse(0L).intValue());
    }

}
