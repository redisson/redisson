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

import org.redisson.api.listener.MessageListener;
import org.redisson.client.RedisPubSubListener;
import org.redisson.client.protocol.pubsub.PubSubType;

import java.util.Set;

/**
 *
 * @author Nikita Koksharov
 *
 * @param <V> value
 */
public class PubSubMessageListener<V> implements RedisPubSubListener<Object> {

    private final MessageListener<V> listener;
    private final Set<String> names;
    private final Class<V> type;
    private Runnable callback;

    public PubSubMessageListener(Class<V> type, MessageListener<V> listener, Set<String> names) {
        super();
        this.type = type;
        this.listener = listener;
        this.names = names;
    }

    public PubSubMessageListener(Class<V> type, MessageListener<V> listener, Set<String> names, Runnable callback) {
        super();
        this.type = type;
        this.listener = listener;
        this.names = names;
        this.callback = callback;
    }

    public MessageListener<V> getListener() {
        return listener;
    }

    @Override
    public void onMessage(CharSequence channel, Object message) {
        // could be subscribed to multiple channels
        if (names.contains(channel.toString()) && type.isInstance(message)) {
            listener.onMessage(channel, (V) message);
            if (callback != null) {
                callback.run();
            }
        }
    }

    @Override
    public void onPatternMessage(CharSequence pattern, CharSequence channel, Object message) {
        // could be subscribed to multiple channels
        if (names.contains(pattern.toString()) && type.isInstance(message)) {
            listener.onMessage(channel, (V) message);
            if (callback != null) {
                callback.run();
            }
        }
    }

    @Override
    public void onStatus(PubSubType type, CharSequence channel) {
    }

}
