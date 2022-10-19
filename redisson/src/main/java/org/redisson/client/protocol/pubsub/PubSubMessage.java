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
package org.redisson.client.protocol.pubsub;

import org.redisson.client.ChannelName;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class PubSubMessage implements Message {

    private final ChannelName channel;
    private final Object value;

    public PubSubMessage(ChannelName channel, Object value) {
        super();
        this.channel = channel;
        this.value = value;
    }

    @Override
    public ChannelName getChannel() {
        return channel;
    }

    public Object getValue() {
        return value;
    }

    @Override
    public String toString() {
        return "Message [channel=" + channel + ", value=" + value + "]";
    }

}
