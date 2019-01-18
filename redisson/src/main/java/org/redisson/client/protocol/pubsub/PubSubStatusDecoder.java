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
package org.redisson.client.protocol.pubsub;

import java.util.List;

import org.redisson.client.ChannelName;
import org.redisson.client.handler.State;
import org.redisson.client.protocol.Decoder;
import org.redisson.client.protocol.decoder.MultiDecoder;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class PubSubStatusDecoder implements MultiDecoder<Object> {

    @Override
    public Decoder<Object> getDecoder(int paramNum, State state) {
        return null;
    }
    
    @Override
    public PubSubStatusMessage decode(List<Object> parts, State state) {
        PubSubType type = PubSubType.valueOf(parts.get(0).toString().toUpperCase());
        ChannelName name = new ChannelName((byte[])parts.get(1));
        return new PubSubStatusMessage(type, name);
    }

}
