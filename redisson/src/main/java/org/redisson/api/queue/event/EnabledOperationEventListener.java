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
package org.redisson.api.queue.event;

import org.redisson.api.queue.QueueOperation;

/**
 * Listener interface for queue enabled operations events.
 * This interface is triggered when queue operation switched to enabled state.
 *
 * @author Nikita Koksharov
 *
 */
public interface EnabledOperationEventListener extends QueueEventListener {

    /**
     * Called when queue operation switched to enabled state.
     *
     * @param queueName name of queue
     */
    void onEnabled(String queueName, QueueOperation operation);

}
