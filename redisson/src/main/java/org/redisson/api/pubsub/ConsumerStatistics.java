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
package org.redisson.api.pubsub;

import java.io.Serializable;

/**
 * Reliable PubSub Consumer statistics interface.
 *
 * @author Nikita Koksharov
 *
 */
public interface ConsumerStatistics extends Serializable {

    /**
     * Returns consumer name.
     *
     * @return name
     */
    String getConsumerName();

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

}
