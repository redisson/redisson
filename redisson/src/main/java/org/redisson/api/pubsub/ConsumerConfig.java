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

import java.time.Duration;

/**
 * Defines consumer parameters for a subscription to a reliable topic.
 *
 * @author Nikita Koksharov
 *
 */
public interface ConsumerConfig {

    /**
     * Creates a consumer configuration with an auto-generated name.
     *
     * @return consumer config
     */
    static ConsumerConfig generatedName() {
        return new ConsumerConfigParams(null);
    }

    /**
     * Creates a consumer configuration with the specified name.
     *
     * @param value the consumer name
     * @return consumer config
     */
    static ConsumerConfig name(String value) {
        return new ConsumerConfigParams(value);
    }

    /**
     * Define a timeout for reassigning a message group id's ownership to a new consumer.
     * <p>
     * Messages with the same {@code groupId} setting delivered to a single "owner" consumer.
     * Ownership is reassigned to a new consumer when <b>both</b> conditions are met:
     * <ol>
     *   <li>The current owner has not pulled the next pending message for this group id
     *       within the timeout period (i.e., message is stuck at head of the key's sequence).</li>
     *   <li>The current owner has been inactive (no {@code acknowledge()}, {@code negativeAcknowledge()},
     *       {@code pull()}, or push listener invocations) for longer than this timeout.</li>
     * </ol>
     * <p>
     * This prevents a stalled or slow consumer from blocking message delivery for a group id
     * indefinitely, while still respecting active consumers that are processing other messages.
     * <p>
     * Note: {@code visibilityTimeout} setting still applies to individual messages with a group id.
     * This timeout governs key-level ownership, not message-level visibility.
     *
     * @param value timeout duration
     * @return config object
     */
    ConsumerConfig groupIdClaimTimeout(Duration value);

}
