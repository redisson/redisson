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

import java.io.Serializable;

/**
 *
 * Reliable PubSub Subscription statistics interface.
 *
 * @author Nikita Koksharov
 *
 */
public interface SubscriptionStatistics extends Serializable {

    /**
     * Returns subscription name.
     *
     * @return name
     */
    String getSubscriptionName();

    /**
     * Returns the number of existing consumers for this subscription.
     *
     * @return consumers count
     */
    long getConsumersCount();

    /**
     * Returns the number of message redelivery attempts.
     *
     * @return redelivery attempts count
     */
    long getRedeliveryAttemptsCount();

    /**
     * Returns the number of unacknowledged messages awaiting acknowledgment.
     *
     * @return unacknowledged messages count
     */
    long getUnacknowledgedMessagesCount();

    /**
     * Return the total number of messages successfully acknowledged by consumers.
     *
     * @return acknowledged messages count
     */
    long getAcknowledgedMessagesCount();

    /**
     * Return the total number of messages negatively acknowledged by consumers.
     *
     * @return acknowledged messages count
     */
    long getNegativelyAcknowledgedMessagesCount();

    /**
     * Returns the number of messages sent to the dead letter topic.
     * <p>
     * Messages are dead lettered when they exceed the delivery limit or negatively acknowledged as rejected.
     *
     * @return count of messages sent to the dead letter topic
     */
    long getDeadLetteredMessagesCount();

}
