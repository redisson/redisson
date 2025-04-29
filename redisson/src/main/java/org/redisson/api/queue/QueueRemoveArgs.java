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

/**
 * Interface that defines arguments for queue removal operations.
 *
 * @author Nikita Koksharov
 *
 */
public interface QueueRemoveArgs extends QueueSyncArgs<QueueRemoveArgs>  {

    /**
     * Defines messages by ids to remove from the queue.
     *
     * @param ids the message ids to be removed from the queue
     * @return arguments object
     */
    static QueueRemoveArgs ids(String... ids) {
        return new QueueRemoveParams(ids);
    }

}
