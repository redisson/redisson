/**
 * Copyright (c) 2013-2026 Nikita Koksharov
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

import org.redisson.api.listener.PatternMessageListener;
import org.redisson.client.RedisPubSubListener;
import org.redisson.client.protocol.pubsub.PubSubType;

/**
 *
 * @author Nikita Koksharov
 *
 * @param <V> value
 */
public class PubSubPatternMessageListener<V> implements RedisPubSubListener<V> {

    private final PatternMessageListener<V> listener;
    private final String name;
    private final Class<V> type;

    public String getName() {
        return name;
    }

    public PubSubPatternMessageListener(Class<V> type, PatternMessageListener<V> listener, String name) {
        super();
        this.listener = listener;
        this.name = name;
        this.type = type;
    }

    public PatternMessageListener<V> getListener() {
        return listener;
    }
    
    @Override
    public void onMessage(CharSequence channel, V message) {
    }

    @Override
    public void onPatternMessage(CharSequence pattern, CharSequence channel, V message) {
        // could be subscribed to multiple channels
        if (name.equals(pattern.toString()) && type.isInstance(message)) {
            listener.onMessage(pattern, channel, message);
        }
    }

    @Override
    public void onStatus(PubSubType type, CharSequence channel) {
    }

}
