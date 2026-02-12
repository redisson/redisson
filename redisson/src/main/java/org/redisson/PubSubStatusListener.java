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

import org.redisson.api.listener.StatusListener;
import org.redisson.client.RedisPubSubListener;
import org.redisson.client.protocol.pubsub.PubSubType;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class PubSubStatusListener implements RedisPubSubListener<Object> {

    private final StatusListener listener;
    private final String[] names;
    private final Set<String> notified = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public PubSubStatusListener(StatusListener listener, String... names) {
        super();
        this.listener = listener;
        this.names = names;
        notified.addAll(Arrays.asList(names));
    }

    @Override
    public void onMessage(CharSequence channel, Object message) {
    }

    @Override
    public void onPatternMessage(CharSequence pattern, CharSequence channel, Object message) {
    }

    @Override
    public void onStatus(PubSubType type, CharSequence channel) {
        notified.remove(channel.toString());
        if (notified.isEmpty()) {
            if (type == PubSubType.SUBSCRIBE || type == PubSubType.SSUBSCRIBE || type == PubSubType.PSUBSCRIBE) {
                listener.onSubscribe(channel.toString());
                notified.addAll(Arrays.asList(names));
            } else if (type == PubSubType.UNSUBSCRIBE || type == PubSubType.SUNSUBSCRIBE || type == PubSubType.PUNSUBSCRIBE) {
                listener.onUnsubscribe(channel.toString());
            }
        }
    }

    public String[] getNames() {
        return names;
    }

    public StatusListener getListener() {
        return listener;
    }

}
