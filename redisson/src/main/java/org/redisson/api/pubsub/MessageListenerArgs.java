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
package org.redisson.api.pubsub;

import org.redisson.api.SyncArgs;
import org.redisson.client.codec.Codec;

import java.time.Duration;

/**
 * Configuration arguments for registering a {@link MessageListener} with a {@link PushConsumer}.
 *
 * @param <V> the type of message values
 * @author Nikita Koksharov
 */
public interface MessageListenerArgs<V> extends SyncArgs<MessageListenerArgs<V>> {

    /**
     * Creates a new instance of MessageListenerArgs with the specified listener.
     *
     * @param <V> the type of message values
     * @param listener the message listener to register
     * @return a new arguments instance
     */
    static <V> MessageListenerArgs<V> listener(MessageListener<V> listener) {
        return new MessageListenerParams<>(listener);
    }

    /**
     * Sets the acknowledgment mode for message processing.
     *
     * <p>The acknowledgment mode determines how messages are acknowledged after retrieval:
     * <ul>
     *   <li>{@code AcknowledgeMode.AUTO} - Messages are automatically acknowledged after delivery</li>
     *   <li>{@code AcknowledgeMode.MANUAL} - Messages must be explicitly acknowledged by the consumer</li>
     * </ul></p>
     * Default value is {@link AcknowledgeMode#MANUAL}.
     *
     * @param mode the acknowledgment mode to use
     * @return arguments object
     * @see AcknowledgeMode
     */
    MessageListenerArgs<V> acknowledgeMode(AcknowledgeMode mode);

    /**
     * Specifies the codec to use for decoding message headers.
     *
     * @param codec the codec to use for header deserialization
     * @return arguments object
     */
    MessageListenerArgs<V> headersCodec(Codec codec);

    /**
     * Sets the visibility timeout for retrieved messages.
     * <p>
     * The visibility timeout specifies how long a message will be hidden from other consumers
     * after it has been retrieved but before it has been acknowledged or negatively acknowledged. This prevents other
     * consumers from processing the same message while it's being handled.
     * <p>
     * If a message is not acknowledged within this time period, it will become visible
     * again in the subscription and may be delivered to another consumer.
     * <p>
     * If not defined, the subscription's visibility setting value is used.
     * If subscription's visibility setting is also not set, the default value is <code>30 seconds</code>.
     *
     * @param value the duration for which retrieved messages should remain invisible to other consumers
     * @return arguments object
     */
    MessageListenerArgs<V> visibility(Duration value);

}
