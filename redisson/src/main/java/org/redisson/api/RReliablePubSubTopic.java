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

import org.redisson.api.pubsub.*;
import org.redisson.api.pubsub.event.PubSubEventListener;
import org.redisson.client.codec.Codec;

import java.util.List;
import java.util.Set;

/**
 * Reliable PubSub Topic implementation based on Stream object.
 * <p>
 * Unlike regular Valkey or Redis based PubSub, this implementation provides features like:
 * <ul>
 *   <li>Messages delivered in FIFO order
 *   <li>Message acknowledgment to confirm successful processing</li>
 *   <li>Message negative acknowledgment to redeliver a message or delete it if DLT is not defined</li>
 *   <li>Redundancy and synchronous replication</li>
 *   <li>Deduplication by id or hash within a defined time interval</li>
 *   <li>Bulk operations</li>
 *   <li>Configurable topic size limit</li>
 *   <li>Configurable message size limit</li>
 *   <li>Configurable message expiration timeout</li>
 *   <li>Configurable message visibility timeout</li>
 *   <li>Configurable message priority</li>
 *   <li>Configurable message delay</li>
 *   <li>Configurable message delivery limit</li>
 *   <li>Per-subscription seek operation for message replay or offset adjustment</li>
 *   <li>Pull and push consumer models for flexible message consumption</li>
 *   <li>Key-based message grouping for sequential processing guarantees by the same consumer</li>
 *   <li>Automatic redelivery of unacknowledged messages, may affect ordering if visibility values are different per message</li>
 *   <li>Dead letter topic support for failed message handling</li>
 * </ul>
 *
 * @author Nikita Koksharov
 *
 */
public interface RReliablePubSubTopic<V> extends RExpirable, RReliablePubSubTopicAsync<V>, RDestroyable {

    /**
     * Sets the configuration for this reliable pubsub topic.
     *
     * @param config the topic configuration to apply
     */
    void setConfig(TopicConfig config);

    /**
     * Attempts to set the configuration for this reliable pubsub topic.
     * <p>
     * This method only applies the configuration if no configuration has been set previously.
     *
     * @param config the topic configuration to apply
     * @return {@code true} if the configuration was successfully applied,
     *         {@code false} if a configuration already exists
     */
    boolean setConfigIfAbsent(TopicConfig config);

    /**
     * Returns the total number of messages in the pubsub topic ready for polling,
     * excluding delayed and unacknowledged messages.
     *
     * @return the total number of messages
     */
    int size();

    /**
     * Checks if the pubsub topic is empty.
     * <p>
     * A topic is considered empty when it contains no messages in any state
     * (ready, delayed, or unacknowledged).
     *
     * @return {@code true} if the topic is empty, {@code false} otherwise
     */
    boolean isEmpty();

    /**
     * Removes all messages from the pubsub topic.
     * <p>
     * This operation clears messages in all states (ready, delayed, and unacknowledged).
     *
     * @return {@code true} if the topic existed and has been cleared, otherwise false
     */
    boolean clear();

    /**
     * Checks if the pubsub topic contains a message with the specified ID.
     *
     * @param id the message ID to check
     * @return {@code true} if a message with the specified ID exists in the topic, {@code false} otherwise
     */
    boolean contains(String id);

    /**
     * Checks if the pubsub topic contains messages with the specified IDs.
     *
     * @param ids the message IDs to check
     * @return the number of matching messages found in the topic
     */
    int containsMany(String... ids);

    /**
     * Adds a message to the pubsub topic with the specified parameters.
     * <p>
     * Returns {@code null} if the message hasn't been added for one of the following reasons:
     * <ul>
     *     <li>Due to message deduplication by id or hash</li>
     *     <li>Due to configured topic size limit and topic is full</li>
     * </ul>
     *
     * @param params parameters for the message to be added
     * @return the added message with its assigned ID and metadata
     *          or {@code null} if timeout defined and no space becomes available in full pubsub topic.
     * @throws  if this operation is disabled
     */
    Message<V> publish(PublishArgs<V> params);

    /**
     * Adds multiple messages to the pubsub topic in a single operation.
     * <p>
     * This batch operation is more efficient than adding messages individually.
     * <p>
     * Messages may not be added for one of the following reasons:
     * <ul>
     *     <li>Due to message deduplication by id or hash</li>
     *     <li>Due to configured topic size limit and topic is full</li>
     * </ul>
     *
     * @param params parameters for the messages to be added
     * @return a list of added messages with their assigned IDs and metadata
     *          or empty list if timeout defined and no space becomes available in full topic.
     * @throws OperationDisabledException if this operation is disabled
     */
    List<Message<V>> publishMany(PublishArgs<V> params);

    /**
     * Returns the names of source pubsub topics which uses this topic as dead letter topic.
     * <p>
     * This only applies if this topic is configured as a dead letter topic in the source topic configurations.
     *
     * @return a set of source topic names
     */
    Set<String> getDeadLetterTopicSources();

    /**
     * Returns all messages in the pubsub topic, ready to be retrieved by the poll() command, without removing them.
     * <p>
     * This operation is useful for inspection and debugging purposes.
     *
     * @return a list of all messages in the topic
     */
    List<Message<V>> listAll();

    /**
     * Returns all messages in the pubsub topic, ready to be retrieved by the poll() command,
     * using the specified codec for message header values.
     *
     * @param headersCodec the codec to use for deserializing message header values
     * @return a list of all messages in the topic
     */
    List<Message<V>> listAll(Codec headersCodec);

    /**
     * Returns message by id
     *
     * @param id message id
     * @return message
     */
    Message<V> get(String id);

    /**
     * Returns message by id applying specified codec to headers
     *
     * @param id message id
     * @param headersCodec codec for headers
     * @return message
     */
    Message<V> get(Codec headersCodec, String id);

    /**
     * Returns messages by ids
     *
     * @param ids message ids
     * @return message
     */
    List<Message<V>> getAll(String... ids);

    /**
     * Returns messages by ids applying specified codec to headers
     *
     * @param ids message ids
     * @param headersCodec codec for headers
     * @return message
     */
    List<Message<V>> getAll(Codec headersCodec, String... ids);

    /**
     * Adds pubsub listener
     *
     * @see org.redisson.api.pubsub.event.PublishedEventListener
     * @see org.redisson.api.pubsub.event.TopicConfigEventListener
     * @see org.redisson.api.pubsub.event.DisabledOperationEventListener
     * @see org.redisson.api.pubsub.event.EnabledOperationEventListener
     * @see org.redisson.api.pubsub.event.TopicFullEventListener
     *
     * @param listener entry listener
     * @return listener id
     */
    String addListener(PubSubEventListener listener);

    /**
     * Removes map entry listener
     *
     * @param id listener id
     */
    void removeListener(String id);

    /**
     * Disables a pubsub operation
     *
     * @param operation pubsub operation
     */
    void disableOperation(PubSubOperation operation);

    /**
     * Enables a pubsub operation
     *
     * @param operation pubsub operation
     */
    void enableOperation(PubSubOperation operation);

    /**
     * Returns an existing subscription by name.
     *
     * @param name the subscription name
     * @return the subscription with the specified name, or {@code null} if not found
     */
    Subscription<V> getSubscription(String name);

    /**
     * Creates a new subscription with an auto-generated name.
     * <p>
     * The subscription maintains its own offset and tracks message
     * consumption independently of other subscriptions on the same topic.
     *
     * @return the subscription object
     */
    Subscription<V> createSubscription();

    /**
     * Creates a new subscription with the specified configuration.
     * <p>
     * The subscription maintains its own offset and tracks message
     * consumption independently of other subscriptions on the same topic.
     *
     * @param config the subscription configuration
     * @return the subscription object
     */
    Subscription<V> createSubscription(SubscriptionConfig config);

    /**
     * Checks if a subscription with the specified name exists.
     *
     * @param name the subscription name to check
     * @return {@code true} if the subscription exists, {@code false} otherwise
     */
    boolean hasSubscription(String name);

    /**
     * Removes the subscription with the specified name.
     * <p>
     * This operation also removes all consumers associated with the subscription.
     *
     * @param name the subscription name to remove
     * @return {@code true} if the subscription was removed, {@code false} if it did not exist
     */
    boolean removeSubscription(String name);

    /**
     * Returns the names of all subscriptions registered to this topic.
     *
     * @return a set of subscription names
     */
    Set<String> getSubscriptions();

    /**
     * Returns statistics for this topic.
     * <p>
     * Statistics include message counts, throughput metrics, and other operational data.
     *
     * @return the topic statistics
     */
    TopicStatistics getStatistics();

}
