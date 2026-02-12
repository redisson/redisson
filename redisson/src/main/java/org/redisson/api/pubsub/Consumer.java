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

import org.redisson.api.RFuture;

/**
 * Base interface for message consumers within a subscription.
 * <p>
 * A consumer processes messages from a subscription and can be either a
 * {@link PullConsumer} for on-demand retrieval or a {@link PushConsumer}
 * for event-driven processing.
 *
 * @author Nikita Koksharov
 *
 */
public interface Consumer {

    /**
     * Returns the name of this consumer.
     *
     * @return the consumer name
     */
    String getName();

    /**
     * Returns statistics for this consumer.
     *
     * @return statistics object
     */
    ConsumerStatistics getStatistics();

    /**
     * Returns statistics for this consumer.
     *
     * @return statistics object
     */
    RFuture<ConsumerStatistics> getStatisticsAsync();

}
