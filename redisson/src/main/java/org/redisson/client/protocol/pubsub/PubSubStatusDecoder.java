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
package org.redisson.client.protocol.pubsub;

import org.redisson.client.ChannelName;
import org.redisson.client.handler.State;
import org.redisson.client.protocol.decoder.MultiDecoder;

import java.util.List;
import java.util.Locale;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class PubSubStatusDecoder implements MultiDecoder<Object> {

    @Override
    public PubSubStatusMessage decode(List<Object> parts, State state) {
        PubSubType type = PubSubType.valueOf(parts.get(0).toString().toUpperCase(Locale.ENGLISH));
        ChannelName name = new ChannelName((byte[]) parts.get(1));
        return new PubSubStatusMessage(type, name);
    }

}
