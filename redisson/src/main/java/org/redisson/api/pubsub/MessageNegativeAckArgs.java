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

import org.redisson.api.SyncArgs;

import java.util.Map;

/**
 * Interface defining parameters for negative acknowledgment of messages.
 *
 * @author Nikita Koksharov
 *
 */
public interface MessageNegativeAckArgs extends SyncArgs<MessageNegativeAckArgs> {

    /**
     * Defines status which indicates that the client application failed
     * to process the message with the specified id.
     * The message is redelivered.
     *
     * @param ids message ids
     * @return arguments object
     */
    static FailedAckArgs failed(String... ids) {
        return new MessageNegativeAckParams(ids, true);
    }

    /**
     * Defines status which indicates that the client application failed
     * to process the message with the specified id and delivery number.
     * The message is redelivered.
     * <p>
     * If the specified delivery number doesn't match the current one, then the message status can't be changed.
     *
     * @param deliveriesById delivery number by message identifiers
     * @return arguments object
     */
    static FailedAckArgs failed(Map<String, Integer> deliveriesById) {
        return new MessageNegativeAckParams(deliveriesById, true);
    }

    /**
     * Defines status which indicates that the client application could process
     * the message with the specified id and delivery number, but it was not accepted.
     * The message is removed and moves it to the Dead Letter Topic if configured.
     * <p>
     * If the specified delivery number doesn't match the current one, then the message status can't be changed.
     *
     * @param ids message ids
     * @return arguments object
     */
    static MessageNegativeAckArgs rejected(String... ids) {
        return new MessageNegativeAckParams(ids, false);
    }

    /**
     * Defines status which indicates that the client application could process
     * the message with the specified id, but it was not accepted.
     * The message is removed and moves it to the Dead Letter Topic if configured.
     * <p>
     * If the specified delivery number doesn't match the current one, then the message status can't be changed.
     *
     * @param deliveriesById delivery number by message identifiers
     * @return arguments object
     */
    static MessageNegativeAckArgs rejected(Map<String, Integer> deliveriesById) {
        return new MessageNegativeAckParams(deliveriesById, false);
    }

}
