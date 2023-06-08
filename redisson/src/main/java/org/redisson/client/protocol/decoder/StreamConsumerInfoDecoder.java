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

import org.redisson.api.StreamConsumer;
import org.redisson.client.handler.State;

import java.util.List;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class StreamConsumerInfoDecoder implements MultiDecoder<StreamConsumer> {

    @Override
    public StreamConsumer decode(List<Object> parts, State state) {
        if (parts.size() > 6) {
            return new StreamConsumer((String) parts.get(1),
                    ((Long) parts.get(3)).intValue(), (Long) parts.get(5), (Long) parts.get(7));
        }
        return new StreamConsumer((String) parts.get(1),
                ((Long) parts.get(3)).intValue(), (Long) parts.get(5), -1);
    }

}
