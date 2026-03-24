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
package org.redisson.api.queue;

import org.redisson.api.SyncArgs;

/**
 * Interface defining parameters for transferring elements between queues.
 *
 * @author Nikita Koksharov
 *
 */
public interface QueueMoveArgs extends SyncArgs<QueueMoveArgs> {

    /**
     * Defines messages by ids to move to the destination queue.
     *
     * @param ids identifiers of queue messages to move
     * @return arguments object
     */
    static QueueMoveDestination ids(String... ids) {
        return new QueueMoveParams(ids);
    }

    /**
     * Defines number of messages to move from the beginning of the queue to the end of the destination queue.
     *
     * @param count The number of elements to move from the beginning of the queue
     * @return arguments object
     */
    static QueueMoveDestination count(int count) {
        return new QueueMoveParams(count);
    }

}
