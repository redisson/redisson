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

import java.time.Duration;

/**
 * Defines parameters for a subscription to a reliable topic.
 *
 * @author Nikita Koksharov
 *
 */
public interface SubscriptionConfig {

    /**
     * Creates a subscription configuration with an auto-generated name.
     *
     * @return subscription config
     */
    static SubscriptionConfig generatedName() {
        return new SubscriptionConfigParams(null);
    }

    /**
     * Creates a subscription configuration with the specified name.
     *
     * @param value the consumer name
     * @return consumer config
     */
    static SubscriptionConfig name(String value) {
        return new SubscriptionConfigParams(value);
    }

    /**
     * Sets the name of the Dead Letter Topic (DLT) to which messages that have reached the delivery limit or have been rejected are sent.
     * <p>
     * Dead letter topic can be removed by setting null value.
     *
     * @param value the name of the dead letter topic
     * @return config object
     */
    SubscriptionConfig deadLetterTopicName(String value);

    /**
     * Defines the maximum number of delivery attempts for a message.
     * Once this limit is reached, the message may be moved to
     * a dead letter topic if it's configured, otherwise it will be deleted.
     * Can be overridden when publishing a message.
     * <p>
     * Default value is 10 attempts.
     *
     * @param value the maximum number of delivery attempts
     * @return config object
     */
    SubscriptionConfig deliveryLimit(int value);

    /**
     * Sets the duration for which a message becomes invisible to other consumers after being polled.
     * This prevents multiple consumers from processing the same message simultaneously.
     * Can be overridden when pooling a message or defining push listener.
     * <p>
     * Default value is 30 seconds.
     *
     * @param value the visibility timeout duration
     * @return config object
     */
    SubscriptionConfig visibility(Duration value);

    /**
     * Sets the initial position for message consumption in the subscription.
     * This determines where the subscription starts reading messages from.
     * <p>
     * Available positions:
     * <ul>
     *   <li>{@link Position#latest()} - Start from the newest messages (default)</li>
     *   <li>{@link Position#earliest()} - Start from the oldest available messages</li>
     *   <li>{@link Position#messageId(String)} - Start from a specific message ID (inclusive)</li>
     *   <li>{@link Position#messageIdExclusive(String)} - Start after a specific message ID (exclusive)</li>
     *   <li>{@link Position#timestamp(java.time.Instant)} - Start from a specific timestamp (inclusive)</li>
     *   <li>{@link Position#timestampExclusive(java.time.Instant)} - Start after a specific timestamp (exclusive)</li>
     * </ul>
     * <p>
     * Default value is {@link Position#latest()}.
     *
     * @param value the position to start consuming messages from
     * @return config object
     */
    SubscriptionConfig position(Position value);

    /**
     * Enables message retention after acknowledgment.
     * <p>
     * When enabled, messages are retained in the topic after being acknowledged
     * instead of being removed. This allows for message replay or audit purposes.
     * <p>
     * By default, messages are removed after acknowledgment.
     *
     * @return config object
     */
    SubscriptionConfig retainAfterAck();

}
