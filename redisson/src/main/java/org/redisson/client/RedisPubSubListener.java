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
package org.redisson.client;

import org.redisson.api.listener.MessageListener;
import org.redisson.client.protocol.pubsub.PubSubType;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <V> value type
 */
public interface RedisPubSubListener<V> extends MessageListener<V> {

    boolean onStatus(PubSubType type, CharSequence channel);

    void onPatternMessage(CharSequence pattern, CharSequence channel, V message);

}
