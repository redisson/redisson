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
package org.redisson.api.pubsub.event;

/**
 * Enumeration representing the possible statuses for negatively acknowledged messages.
 * Used to indicate why a message was negatively acknowledged (NAcked).
 *
 * @author Nikita Koksharov
 *
 */
public enum NAckStatus {

    /**
     * Indicates that the client application could process the message,
     * but it was not accepted.
     * The message is removed and moved to the Dead Letter Topic if configured.
     */
    REJECTED,

    /**
     * Indicates that the client application failed to process the message.
     * The message is redelivered. Allows to define the delay duration before
     * the failed message is eligible for redelivery.
     */
    FAILED

}
