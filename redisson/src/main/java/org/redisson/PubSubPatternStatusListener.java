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
package org.redisson;

import org.redisson.api.listener.PatternStatusListener;
import org.redisson.client.RedisPubSubListener;
import org.redisson.client.protocol.pubsub.PubSubType;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class PubSubPatternStatusListener implements RedisPubSubListener<Object> {

    private final PatternStatusListener listener;
    private final String name;

    public String getName() {
        return name;
    }

    public PubSubPatternStatusListener(PatternStatusListener listener, String name) {
        super();
        this.listener = listener;
        this.name = name;
    }

    @Override
    @SuppressWarnings("AvoidInlineConditionals")
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((listener == null) ? 0 : listener.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        PubSubPatternStatusListener other = (PubSubPatternStatusListener) obj;
        if (listener == null) {
            if (other.listener != null)
                return false;
        } else if (!listener.equals(other.listener))
            return false;
        return true;
    }

    @Override
    public void onMessage(CharSequence channel, Object message) {
    }

    @Override
    public void onPatternMessage(CharSequence pattern, CharSequence channel, Object message) {
    }

    @Override
    public boolean onStatus(PubSubType type, CharSequence channel) {
        if (channel.toString().equals(name)) {
            if (type == PubSubType.PSUBSCRIBE) {
                listener.onPSubscribe(channel.toString());
            } else if (type == PubSubType.PUNSUBSCRIBE) {
                listener.onPUnsubscribe(channel.toString());
            }
            return true;
        }
        return false;
    }

}
