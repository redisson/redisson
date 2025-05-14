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

import org.redisson.api.SyncArgs;

/**
 * Interface defining parameters for negative acknowledgment of queue messages.
 *
 * @author Nikita Koksharov
 *
 */
public interface QueueNegativeAckArgs extends SyncArgs<QueueNegativeAckArgs> {

    /**
     * Defines status which indicates that the client application failed to process the message.
     * The message is redelivered.
     *
     * @param ids message ids
     * @return arguments object
     */
    static FailedAckArgs failed(String... ids) {
        return new QueueNegativeAckParams(ids, true);
    }

    /**
     * Defines status which indicates that the client application could process the message, but it was not accepted.
     * The message is removed and moves it to the Dead Letter Queue (DLQ) if configured.
     *
     * @param ids message ids
     * @return arguments object
     */
    static QueueNegativeAckArgs rejected(String... ids) {
        return new QueueNegativeAckParams(ids, false);
    }

}
