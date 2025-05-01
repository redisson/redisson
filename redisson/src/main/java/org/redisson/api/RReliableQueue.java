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
package org.redisson.api;

import org.redisson.api.queue.*;
import org.redisson.client.codec.Codec;

import java.util.List;
import java.util.Set;

/**
 * Reliable queue implementation based on Stream object.
 * <p>
 * Unlike regular Valkey or Redis based queues, this implementation provides features like:
 * <ul>
 *   <li>Message acknowledgment to confirm successful processing</li>
 *   <li>Message negative acknowledgment to redeliver a message or delete it if DLQ is not defined</li>
 *   <li>Redundancy and synchronous replication</li>
 *   <li>Deduplication by id or hash within a defined time interval</li>
 *   <li>Bulk operations</li>
 *   <li>Configurable queue size limit</li>
 *   <li>Configurable message size limit</li>
 *   <li>Configurable message expiration timeout</li>
 *   <li>Configurable message visibility timeout</li>
 *   <li>Configurable message priority</li>
 *   <li>Configurable message delay</li>
 *   <li>Configurable message delivery limit</li>
 *   <li>Automatic redelivery of unacknowledged messages</li>
 *   <li>Dead letter queue support for failed message handling</li>
 * </ul>
 *
 * @author Nikita Koksharov
 *
 */
public interface RReliableQueue<V> extends RExpirable, RReliableQueueAsync<V> {

    /**
     * Sets the configuration for this reliable queue.
     *
     * @param config the queue configuration to apply
     */
    void setConfig(QueueConfig config);

    /**
     * Attempts to set the configuration for this reliable queue.
     * <p>
     * This method only applies the configuration if no configuration has been set previously.
     *
     * @param config the queue configuration to apply
     * @return {@code true} if the configuration was successfully applied,
     *         {@code false} if a configuration already exists
     */
    boolean setConfigIfAbsent(QueueConfig config);

    /**
     * Returns the total number of messages in the queue, including messages in all states
     * (ready for polling, delayed, and unacknowledged).
     *
     * @return the total number of messages
     */
    int size();

    /**
     * Returns the number of delayed messages in the queue.
     * <p>
     * Delayed messages are those scheduled for future delivery and not yet available for consumption.
     *
     * @return the number of delayed messages
     */
    int countDelayedMessages();

    /**
     * Returns the number of unacknowledged messages in the queue.
     * <p>
     * Unacknowledged messages are those that have been delivered to consumers
     * but not yet acknowledged as successfully processed.
     *
     * @return the number of unacknowledged messages
     */
    int countUnacknowledgedMessages();

    /**
     * Checks if the queue is empty.
     * <p>
     * A queue is considered empty when it contains no messages in any state
     * (ready, delayed, or unacknowledged).
     *
     * @return {@code true} if the queue is empty, {@code false} otherwise
     */
    boolean isEmpty();

    /**
     * Removes all messages from the queue.
     * <p>
     * This operation clears messages in all states (ready, delayed, and unacknowledged).
     *
     * @return {@code true} if the queue existed and has been cleared, otherwise false
     */
    boolean clear();

    /**
     * Retrieves and removes the head of this queue, or returns {@code null} if this queue is empty.
     * <p>
     * The retrieved message remains unacknowledged until explicitly acknowledged
     * using the {@link #acknowledge(QueueAckArgs)} or {@link #negativeAcknowledge(QueueNegativeAckArgs)} method.
     *
     * @return the message in the head of this queue, or {@code null} if this queue is empty
     */
    Message<V> poll();

    /**
     * Retrieves and removes the head of this queue with the specified polling arguments.
     * <p>
     * The retrieved message remains unacknowledged until explicitly acknowledged
     * using the {@link #acknowledge(QueueAckArgs)} or {@link #negativeAcknowledge(QueueNegativeAckArgs)} method.
     *
     * @param args polling arguments
     * @return the message in the head of this queue, or {@code null} if this queue is empty
     */
    Message<V> poll(QueuePollArgs args);

    /**
     * Retrieves and removes multiple messages from the queue with the specified polling arguments.
     * <p>
     * This batch operation is more efficient than polling messages individually.
     * <p>
     * The retrieved messages remain unacknowledged until explicitly acknowledged
     * using the {@link #acknowledge(QueueAckArgs)} or {@link #negativeAcknowledge(QueueNegativeAckArgs)} method.
     *
     * @param pargs polling arguments
     * @return a list of retrieved messages
     */
    List<Message<V>> pollMany(QueuePollArgs pargs);

    /**
     * Acknowledges the successful processing of a message.
     * <p>
     * Once acknowledged, a message is permanently removed from the queue and will not be redelivered.
     *
     * @param args acknowledgment arguments
     */
    void acknowledge(QueueAckArgs args);

    /**
     * Checks if the queue contains a message with the specified ID.
     *
     * @param id the message ID to check
     * @return {@code true} if a message with the specified ID exists in the queue, {@code false} otherwise
     */
    boolean contains(String id);

    /**
     * Checks if the queue contains messages with the specified IDs.
     *
     * @param ids the message IDs to check
     * @return the number of matching messages found in the queue
     */
    int containsMany(String... ids);

    /**
     * Removes a specific message from the queue.
     * <p>
     * This operation can remove messages in any state (ready, delayed, or unacknowledged).
     *
     * @param args removal arguments
     * @return {@code true} if the message was successfully removed, {@code false} if the message was not found
     */
    boolean remove(QueueRemoveArgs args);

    /**
     * Removes multiple messages from the queue in a single operation.
     *
     * @param args removal arguments
     * @return the number of messages successfully removed
     */
    int removeMany(QueueRemoveArgs args);

    /**
     * Moves messages between queues.
     *
     * @param args move arguments
     * @return the number of messages successfully moved
     */
    int move(QueueMoveArgs args);

    /**
     * Adds a message to the queue with the specified parameters.
     * <p>
     * Returns {@code null} if the message hasn't been added for one of the following reasons:
     * <ul>
     *     <li>Due to message deduplication by id or hash</li>
     *     <li>Due to configured queue size limit and queue is full</li>
     * </ul>
     *
     * @param params parameters for the message to be added
     * @return the added message with its assigned ID and metadata
     *          or {@code null} if timeout defined and no space becomes available in full queue.
     */
    Message<V> add(QueueAddArgs<V> params);

    /**
     * Adds multiple messages to the queue in a single operation.
     * <p>
     * This batch operation is more efficient than adding messages individually.
     * <p>
     * Messages may not be added for one of the following reasons:
     * <ul>
     *     <li>Due to message deduplication by id or hash</li>
     *     <li>Due to configured queue size limit and queue is full</li>
     * </ul>
     *
     * @param params parameters for the messages to be added
     * @return a list of added messages with their assigned IDs and metadata
     *          or empty list if timeout defined and no space becomes available in full queue.
     */
    List<Message<V>> addMany(QueueAddArgs<V> params);

    /**
     * Returns the names of source queues which uses this reliable queue as dead letter queue.
     * <p>
     * This only applies if this queue is configured as a dead letter queue in the source queue configurations.
     *
     * @return a set of source queue names
     */
    Set<String> getDeadLetterQueueSources();

    /**
     * Returns all messages in the queue, ready to be retrieved by the poll() command, without removing them.
     * <p>
     * This operation is useful for inspection and debugging purposes.
     *
     * @return a list of all messages in the queue
     */
    List<Message<V>> listAll();

    /**
     * Returns all messages in the queue, ready to be retrieved by the poll() command,
     * using the specified codec for message header values.
     *
     * @param headersCodec the codec to use for deserializing message header values
     * @return a list of all messages in the queue
     */
    List<Message<V>> listAll(Codec headersCodec);

    /**
     * Explicitly marks a message as failed or rejected.
     *
     * @param args arguments specifying the message to negatively acknowledge
     */
    void negativeAcknowledge(QueueNegativeAckArgs args);

}
