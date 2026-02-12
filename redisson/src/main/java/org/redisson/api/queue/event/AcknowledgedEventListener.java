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

import java.util.List;

/**
 * Listener interface for acknowledged queue events.
 * This interface is triggered when messages
 * in a queue are successfully acknowledged by consumers.
 *
 * @author Nikita Koksharov
 *
 */
public interface AcknowledgedEventListener extends QueueEventListener {

    /**
     * Called when messages are acknowledged by a consumer.
     *
     * @param ids message ids
     */
    void onAcknowledged(List<String> ids);

}
