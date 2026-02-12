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

/**
 * Defines the retention behavior for messages in a topic, controlling when messages
 * are stored and when they may be discarded based on subscription state and processing status.
 * <p>
 * The retention mode determines:
 * <ul>
 *   <li>Whether active subscribers are required for message storage</li>
 *   <li>Whether processed messages should be retained or discarded</li>
 * </ul>
 *
 * @author Nikita Koksharov
 *
 */
public enum RetentionMode {

    /**
     * Requires at least one subscriber to store messages in the topic. Messages are discarded
     * when all subscriptions have either acknowledged them, reached the redelivery limit,
     * or negatively acknowledged them as failed.
     * <p>
     * Only subscriptions existing at the time of message publication are considered
     */
    SUBSCRIPTION_REQUIRED_DELETE_PROCESSED,

    /**
     * Requires at least one subscriber to store messages in the topic. Messages aren't discarded
     * when all subscriptions have either acknowledged them, reached the redelivery limit,
     * or negatively acknowledged them as failed.
     */
    SUBSCRIPTION_REQUIRED_RETAIN_ALL,

    /**
     * Default mode. Subscribers are not required for message storage. Messages are always stored in the
     * topic regardless of subscription state or processing status.
     */
    SUBSCRIPTION_OPTIONAL_RETAIN_ALL

}
