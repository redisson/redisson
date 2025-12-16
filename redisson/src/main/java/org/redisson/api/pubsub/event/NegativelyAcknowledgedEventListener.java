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
package org.redisson.api.pubsub.event;

import java.util.List;

/**
 * Listener interface for negatively acknowledged subscription events.
 * This interface is triggered when messages
 * in a subscription are negatively acknowledged by consumers.
 *
 * @author Nikita Koksharov
 *
 */
public interface NegativelyAcknowledgedEventListener extends ConsumerEventListener {

    /**
     * Called when messages are negatively acknowledged by a consumer.
     *
     * @param status The reason for the negative acknowledgment
     * @param ids message ids
     */
    void onNegativelyAcknowledged(NAckStatus status, List<String> ids);

}
