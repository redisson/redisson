/**
 * Copyright 2014 Nikita Koksharov, Nickolay Borbit
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

public class PubSubStatusMessage {

    public enum Type {SUBSCRIBE, PSUBSCRIBE, PUNSUBSCRIBE, UNSUBSCRIBE}

    private final Type type;
    private final List<String> channels;

    public PubSubStatusMessage(Type type, List<String> channels) {
        super();
        this.type = type;
        this.channels = channels;
    }

    public List<String> getChannels() {
        return channels;
    }

    public Type getType() {
        return type;
    }

    @Override
    public String toString() {
        return "PubSubStatusMessage [type=" + type + ", channels=" + channels + "]";
    }

}
