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
package org.redisson.api;

import java.time.Duration;
import java.util.Map;

/**
 * Fluent API interface defining parameters for a message in a queue.
 *
 * @author Nikita Koksharov
 * @param <V> type
 *
 */
public interface MessageArgs<V> {

    /**
     * Sets the priority level for the message.
     * Defined as a number between <code>0</code> and <code>9</code>
     * <p>
     * <code>0</code> is the lowest priority level.
     * <p>
     * <code>9</code> is the highest priority level.
     * <p>
     * Default value is <code>0</code>.
     *
     * @param priority the priority level
     * @return arguments object
     */
    MessageArgs<V> priority(int priority);

    /**
     * Sets a delay interval before the message becomes available for processing.
     * <p>
     * <code>0</code> value means delay duration is not applied.
     * If not defined, the queue's delay setting value is used.
     * If queue's delay setting is also not set, the default value is <code>0</code>.
     *
     * @param interval the time duration to delay message delivery
     * @return arguments object
     */
    MessageArgs<V> delay(Duration interval);

    /**
     * Enables deduplication based on the payload hash for the specified interval.
     * <p>
     * During the specified interval, messages with the same hash will be considered duplicates
     * and won't be added to the queue.
     * </p>
     *
     * @param interval the time duration
     * @return arguments object
     */
    MessageArgs<V> deduplicationByHash(Duration interval);

    /**
     * Enables deduplication based on a custom ID for the specified interval.
     * <p>
     * During the specified interval, messages with the same ID will be considered duplicates
     * and won't be added to the queue.
     * </p>
     *
     * @param id the custom identifier
     * @param interval the time duration
     * @return arguments object
     */
    MessageArgs<V> deduplicationById(Object id, Duration interval);

    /**
     * Sets the time-to-live duration for the message.
     * <p>
     * After this duration has elapsed, the message is removed from the queue
     * if it hasn't been processed.
     * <p>
     * <code>0</code> value means expiration is not applied.
     * If not defined, the queue's timeToLive setting value is used.
     * If queue's timeToLive setting is also not set, the default value is <code>0</code>.
     *
     * @param value the time duration
     * @return arguments object
     */
    MessageArgs<V> timeToLive(Duration value);

    /**
     * Sets the maximum number of delivery attempts for the message.
     * <p>
     * If processing the message fails, it may be redelivered up to the specified count.
     * </p>
     * The minimum value is <code>1</code>. If not defined, the queue's deliveryLimit setting value is used.
     * If queue's deliveryLimit setting is also not set, the default value is <code>10</code>.
     *
     * @param count the maximum number of delivery attempts
     * @return arguments object
     */
    MessageArgs<V> deliveryLimit(int count);

    /**
     * Adds a single header entry to the message.
     *
     * @param key the header key
     * @param value the header value
     * @return arguments object
     */
    MessageArgs<V> header(String key, Object value);

    /**
     * Adds multiple header entries to the message at once.
     *
     * @param entries a map containing header key-value pairs
     * @return arguments object
     */
    MessageArgs<V> headers(Map<String, Object> entries);

    /**
     * Defines the payload to include in the message
     *
     * @param value the payload to include
     * @return arguments object
     */
    static <V> MessageArgs<V> payload(V value) {
        return new MessageParams<V>(value);
    }

}
