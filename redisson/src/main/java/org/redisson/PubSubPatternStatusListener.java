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

    public PubSubPatternStatusListener(PubSubPatternStatusListener l) {
        this.listener = l.listener;
        this.name = l.name;
    }

    public PubSubPatternStatusListener(PatternStatusListener listener, String name) {
        super();
        this.listener = listener;
        this.name = name;
    }

    @Override
    public void onMessage(CharSequence channel, Object message) {
    }

    @Override
    public void onPatternMessage(CharSequence pattern, CharSequence channel, Object message) {
    }

    @Override
    public void onStatus(PubSubType type, CharSequence channel) {
        if (channel.toString().equals(name)) {
            if (type == PubSubType.PSUBSCRIBE) {
                listener.onPSubscribe(channel.toString());
            } else if (type == PubSubType.PUNSUBSCRIBE) {
                listener.onPUnsubscribe(channel.toString());
            }
        }
    }

}
