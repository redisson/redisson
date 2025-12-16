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

import java.util.List;

/**
 * A pull-based consumer for retrieving messages from a subscription on-demand.
 * <p>
 * Pull consumers provide manual control over message consumption, allowing
 * to request messages when ready to process them.
 * <p>
 * Retrieved messages must be explicitly acknowledged using {@link #acknowledge(MessageAckArgs)}
 * or {@link #negativeAcknowledge(MessageNegativeAckArgs)}. Unacknowledged messages
 * will be automatically redelivered after the visibility timeout expires.
 *
 * @param <V> the type of message values
 *
 * @author Nikita Koksharov
 *
 */
public interface PullConsumer<V> extends PullConsumerAsync<V>, Acknowledgment {

    /**
     * Retrieves and removes the head of this subscription, or returns {@code null} if this subscription is empty.
     * <p>
     * The retrieved message remains unacknowledged until explicitly acknowledged
     * using the {@link #acknowledge(MessageAckArgs)} or {@link #negativeAcknowledge(MessageNegativeAckArgs)} method.
     *
     * @return the message in the head of this subscription, or {@code null} if this subscription is empty
     * @throws OperationDisabledException if this operation is disabled
     */
    Message<V> pull();

    /**
     * Retrieves and removes the head of this subscription with the specified pulling arguments.
     * <p>
     * The retrieved message remains unacknowledged until explicitly acknowledged
     * using the {@link #acknowledge(MessageAckArgs)} or {@link #negativeAcknowledge(MessageNegativeAckArgs)} method.
     *
     * @param args pulling arguments
     * @return the message in the head of this subscription, or {@code null} if this subscription is empty
     * @throws OperationDisabledException if this operation is disabled
     */
    Message<V> pull(PullArgs args);

    /**
     * Retrieves and removes multiple messages from the subscription with the specified pulling arguments.
     * <p>
     * This batch operation is more efficient than pulling messages individually.
     * <p>
     * The retrieved messages remain unacknowledged until explicitly acknowledged
     * using the {@link #acknowledge(MessageAckArgs)} or {@link #negativeAcknowledge(MessageNegativeAckArgs)} method.
     *
     * @param pargs pulling arguments
     * @return a list of retrieved messages
     * @throws OperationDisabledException if this operation is disabled
     */
    List<Message<V>> pullMany(PullArgs pargs);

}
