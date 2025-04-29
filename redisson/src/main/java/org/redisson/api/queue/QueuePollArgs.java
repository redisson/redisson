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

import org.redisson.client.codec.Codec;

import java.time.Duration;

/**
 * Arguments for queue polling operations.
 *
 * <p>Use the {@code defaults()} factory method to create a new instance with default settings.</p>
 *
 * <p>Example usage:</p>
 * <pre>
 * QueuePollArgs args = QueuePollArgs.defaults()
 *     .acknowledgeMode(AcknowledgeMode.MANUAL)
 *     .timeout(Duration.ofSeconds(5))
 *     .count(10);
 * </pre>
 *
 * @author Nikita Koksharov
 *
 */
public interface QueuePollArgs extends QueueSyncArgs<QueuePollArgs> {

    /**
     * Creates a new instance of QueuePollArgs with default settings.
     *
     * @return arguments object
     */
    static QueuePollArgs defaults() {
        return new QueuePollParams();
    }

    /**
     * Sets the acknowledgment mode for message processing.
     *
     * <p>The acknowledgment mode determines how messages are acknowledged after retrieval:
     * <ul>
     *   <li>{@code AcknowledgeMode.AUTO} - Messages are automatically acknowledged after delivery</li>
     *   <li>{@code AcknowledgeMode.MANUAL} - Messages must be explicitly acknowledged by the consumer</li>
     * </ul></p>
     * Default value is AcknowledgeMode.MANUAL
     *
     * @param mode the acknowledgment mode to use
     * @return arguments object
     * @see AcknowledgeMode
     */
    QueuePollArgs acknowledgeMode(AcknowledgeMode mode);

    /**
     * Specifies the codec to use for decoding message headers.
     *
     * @param codec the codec to use for header deserialization
     * @return arguments object
     */
    QueuePollArgs headersCodec(Codec codec);

    /**
     * Sets the maximum time to wait for messages to become available.
     *
     * <p>If the queue is empty, the poll operation will block until either:
     * <ul>
     *   <li>At least one message becomes available</li>
     *   <li>The specified timeout duration elapses</li>
     * </ul>
     *
     * <p>If the timeout elapses without any messages becoming available,
     * the poll operation will return empty collection of messages.
     * <p>
     * <code>0</code> means to wait indefinitely for a message.
     * <p>
     * Default value is undefined.
     *
     * @param value the maximum duration to wait for messages
     * @return arguments object
     */
    QueuePollArgs timeout(Duration value);

    /**
     * Sets the visibility timeout for retrieved messages.
     * <p>
     * The visibility timeout specifies how long a message will be hidden from other consumers
     * after it has been retrieved but before it has been acknowledged or negatively acknowledged. This prevents other
     * consumers from processing the same message while it's being handled.
     * <p>
     * If a message is not acknowledged within this time period, it will become visible
     * again in the queue and may be delivered to another consumer.
     * <p>
     * If not defined, the queue's visibility setting value is used.
     * If queue's visibility setting is also not set, the default value is <code>30 seconds</code>.
     *
     * @param value the duration for which retrieved messages should remain invisible to other consumers
     * @return arguments object
     */
    QueuePollArgs visibility(Duration value);

    /**
     * Sets the maximum number of messages to retrieve in a single poll operation.
     *
     * <p>This parameter enables batch retrieval of messages, which can improve throughput
     * when processing multiple messages at once. The actual number of messages returned
     * may be less than the requested count if fewer messages are available.</p>
     *
     * @param value the maximum number of messages to retrieve
     * @return arguments object
     */
    QueuePollArgs count(int value);

}
