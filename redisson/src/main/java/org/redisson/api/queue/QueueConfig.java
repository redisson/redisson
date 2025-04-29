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
package org.redisson.api.queue;

import java.time.Duration;

/**
 * Defines queue parameters.
 *
 * @author Nikita Koksharov
 *
 */
public interface QueueConfig {

    /**
     * Creates a new QueueConfig instance with default settings.
     *
     * @return config object
     */
    static QueueConfig defaults() {
        return new QueueConfigParams();
    }

    /**
     * Defines the maximum number of delivery attempts for a message.
     * Once this limit is reached, the message may be moved to
     * a dead letter queue if it's configured, otherwise it will be deleted.
     * Can be overridden when adding a message.
     * <p>
     * Default value is 10 attempts.
     *
     * @param value the maximum number of delivery attempts
     * @return config object
     */
    QueueConfig deliveryLimit(int value);

    /**
     * Sets the duration for which a message becomes invisible to other consumers after being polled.
     * This prevents multiple consumers from processing the same message simultaneously.
     * Can be overridden when pooling a message.
     * <p>
     * Default value is 30 seconds.
     *
     * @param value the visibility timeout duration
     * @return config object
     */
    QueueConfig visibility(Duration value);

    /**
     * Sets the time-to-live duration for messages in the queue.
     * Messages will be automatically removed from the queue after this duration expires.
     * 0 value means expiration is not applied.
     * Can be overridden when adding a message.
     * <p>
     * Default value is 0.
     *
     * @param value the time-to-live duration
     * @return config object
     */
    QueueConfig timeToLive(Duration value);

    /**
     * Sets the name of the Dead Letter Queue (DLQ) to which messages that have reached the delivery limit or have been rejected are sent.
     * <p>
     * Dead letter queue can be removed by setting null value.
     *
     * @param value the name of the dead letter queue
     * @return config object
     */
    QueueConfig deadLetterQueueName(String value);

    /**
     * Sets the maximum allowed size (in bytes) for a single message in the queue.
     * Messages exceeding this size will be rejected.
     * 0 value means size limit is not applied.
     * <p>
     * Default value is 0.
     *
     * @param value the maximum message size in bytes
     * @return config object
     */
    QueueConfig maxMessageSize(int value);

    /**
     * Sets the delay duration before a message becomes available for consumption after being added to the queue.
     * 0 value means delay duration is not applied.
     * Can be overridden when adding a message.
     * <p>
     * Default value is 0.
     *
     * @param delay the delay duration
     * @return config object
     */
    QueueConfig delay(Duration delay);

    /**
     * Sets the maximum number of messages that can be stored in the queue.
     * When the queue reaches this size, add messages operation may be blocked and/or return empty result.
     * 0 value means queue size limit is not applied.
     * <p>
     * Default value is 0.
     *
     * @param value the maximum queue size
     * @return config object
     */
    QueueConfig maxSize(int value);

}
