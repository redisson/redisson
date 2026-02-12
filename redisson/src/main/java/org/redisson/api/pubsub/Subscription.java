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

import java.util.Set;

/**
 * Subscription to a reliable pubsub topic.
 * <p>
 * A subscription maintains its own offset and tracks message consumption independently
 * of other subscriptions on the same topic. Each subscription can have multiple
 * pull or push consumers that share the workload of processing messages.
 *
 * @param <V> the type of message values
 *
 * @author Nikita Koksharov
 *
 */
public interface Subscription<V> extends SubscriptionAsync<V> {

    /**
     * Creates a new pull consumer with an auto-generated name.
     * <p>
     * Pull consumers retrieve messages on-demand, providing manual control
     * over message consumption rate and timing.
     *
     * @return pull consumer object
     */
    PullConsumer<V> createPullConsumer();

    /**
     * Creates a new pull consumer with the specified configuration.
     * <p>
     * Pull consumers retrieve messages on-demand, providing manual control
     * over message consumption rate and timing.
     *
     * @param config the consumer configuration
     * @return pull consumer object
     */
    PullConsumer<V> createPullConsumer(ConsumerConfig config);

    /**
     * Creates a new push consumer with an auto-generated name.
     * <p>
     * Push consumers receive messages automatically via registered listener,
     * enabling event-driven message processing.
     *
     * @return pull consumer object
     */
    PushConsumer<V> createPushConsumer();

    /**
     * Creates a new push consumer with the specified configuration.
     * <p>
     * Push consumers receive messages automatically via registered listeners,
     * enabling event-driven message processing.
     *
     * @param config the consumer configuration
     * @return pull consumer object
     */
    PushConsumer<V> createPushConsumer(ConsumerConfig config);

    /**
     * Returns the names of all consumers registered to this subscription.
     *
     * @return a set of consumer names
     */
    Set<String> getConsumerNames();

    /**
     * Checks if a consumer with the specified name exists in this subscription.
     *
     * @param name the consumer name to check
     * @return {@code true} if the consumer exists, {@code false} otherwise
     */
    boolean hasConsumer(String name);

    /**
     * Removes the consumer with the specified name from this subscription.
     *
     * @param name the consumer name to remove
     * @return {@code true} if the consumer was removed, {@code false} if it did not exist
     */
    boolean removeConsumer(String name);

    /**
     * Returns the name of this subscription.
     *
     * @return the subscription name
     */
    String getName();

    /**
     * Moves the subscription offset to the specified position.
     * <p>
     * This allows replaying messages from a specific point or skipping
     * ahead to newer messages. Affects all consumers within this subscription.
     *
     * @param value the position to seek to
     */
    void seek(Position value);

    /**
     * Returns statistics for this subscription.
     *
     * @return statistics object
     */
    SubscriptionStatistics getStatistics();

}
