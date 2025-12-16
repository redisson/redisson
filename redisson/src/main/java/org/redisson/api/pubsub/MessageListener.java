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

import org.redisson.api.Message;

import java.util.EventListener;

/**
 * Listener interface for receiving messages from a {@link PushConsumer}.
 *
 * @param <V> the type of message values
 *
 * @author Nikita Koksharov
 *
 */
public interface MessageListener<V> extends EventListener {

    /**
     * Invoked when a message is received from the subscription.
     * <p>
     * Messages are received one by one, no batches are used.
     * <p>
     * The implementation should process the message and then call either
     * {@link Acknowledgment#acknowledge(MessageAckArgs)} to confirm successful processing
     * or {@link Acknowledgment#negativeAcknowledge(MessageNegativeAckArgs)} to trigger
     * redelivery or dead letter handling.
     * <p>
     * Unacknowledged messages will be automatically redelivered after the
     * visibility timeout expires.
     *
     * @param message the received message containing the payload and metadata
     * @param acknowledgment the acknowledgment handler for this message
     */
    void onMessage(Message<V> message, Acknowledgment acknowledgment);

}
