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
 * Defines reliable topic parameters.
 *
 * @author Nikita Koksharov
 *
 */
public interface TopicConfig {

    /**
     * Creates a new TopicConfig instance with default settings.
     *
     * @return config object
     */
    static TopicConfig defaults() {
        return new TopicConfigParams();
    }

    /**
     * Sets the duration for which a message becomes invisible to other consumers after being polled.
     * This prevents multiple consumers from processing the same message simultaneously.
     * Can be overridden in a subscription, when pooling a message or defining push listener.
     * <p>
     * Default value is 30 seconds.
     *
     * @param value the visibility timeout duration
     * @return config object
     */
    TopicConfig visibility(Duration value);

    /**
     * Sets the time-to-live duration for messages in the topic.
     * Messages will be automatically removed from the topic after this duration expires.
     * 0 value means expiration is not applied.
     * Can be overridden when publishing a message.
     * <p>
     * Default value is 0.
     *
     * @param value the time-to-live duration
     * @return config object
     */
    TopicConfig timeToLive(Duration value);

    /**
     * Sets the maximum allowed size (in bytes) for a single message in the topic.
     * Messages exceeding this size will be rejected.
     * 0 value means size limit is not applied.
     * <p>
     * Default value is 0.
     *
     * @param value the maximum message size in bytes
     * @return config object
     */
    TopicConfig maxMessageSize(int value);

    /**
     * Sets the delay duration before a message becomes available for consumption after being added to the topic.
     * 0 value means delay duration is not applied.
     * Can be overridden when publishing a message.
     * <p>
     * Default value is 0.
     *
     * @param delay the delay duration
     * @return config object
     */
    TopicConfig delay(Duration delay);

    /**
     * Sets the maximum number of messages that can be stored in the topic.
     * When the topic reaches this size, add messages operation may be blocked and/or return empty result.
     * 0 value means topic size limit is not applied.
     * <p>
     * Default value is 0.
     *
     * @param value the maximum topic size
     * @return config object
     */
    TopicConfig maxSize(int value);

    /**
     * Defines the maximum number of delivery attempts for a message.
     * Once this limit is reached, the message may be moved to
     * a dead letter topic if it's configured, otherwise it will be deleted.
     * Can be overridden in a subscription or when publishing a message.
     * <p>
     * Default value is 10 attempts.
     *
     * @param value the maximum number of delivery attempts
     * @return config object
     */
    TopicConfig deliveryLimit(int value);

    /**
     * Defines the retention behavior for messages in a topic, controlling when messages
     * are stored and when they may be discarded based on subscription state and processing status.
     * <p>
     * Default value is {@link RetentionMode#SUBSCRIPTION_OPTIONAL_RETAIN_ALL}
     *
     * @param mode the retention mode
     * @return config object
     */
    TopicConfig retentionMode(RetentionMode mode);

}
