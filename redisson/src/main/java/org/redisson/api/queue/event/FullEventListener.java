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
package org.redisson.api.queue.event;

/**
 * Listener interface for queue full events.
 * This interface is triggered when the queue is full.
 *
 * @author Nikita Koksharov
 *
 */
public interface FullEventListener extends QueueEventListener {

    /**
     * Called when the queue is full.
     *
     * @param queueName name of queue
     */
    void onFull(String queueName);

}
