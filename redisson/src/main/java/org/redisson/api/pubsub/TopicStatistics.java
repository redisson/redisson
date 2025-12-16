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
 * Reliable PubSub Topic statistics interface.
 *
 * @author Nikita Koksharov
 *
 */
public interface TopicStatistics extends Serializable {

    /**
     * Returns topic name.
     *
     * @return name
     */
    String getTopicName();

    /**
     * Returns the number of delayed messages in this topic.
     * <p>
     * Delayed messages are those scheduled for future delivery and not yet available for consumption.
     *
     * @return delayed messages count
     */
    long getDelayedMessagesCount();

    /**
     * Returns the number of existing subscriptions for this topic.
     *
     * @return subscriptions count
     */
    long getSubscriptionsCount();

    /**
     * Return the number of published messages by this topic.
     *
     * @return acknowledged messages count
     */
    long getPublishedMessagesCount();

}
