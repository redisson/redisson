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
package org.redisson.api.pubsub;

import org.redisson.RedissonReliablePubSubTopic;
import org.redisson.api.RFuture;

/**
 *
 * @author Nikita Koksharov
 *
 */
public class PushConsumerImpl<V> implements PushConsumer<V>, Acknowledgment {

    volatile boolean listenerAdded;
    final String name;
    final RedissonReliablePubSubTopic<V> topic;
    final String subscriptionName;

    public PushConsumerImpl(RedissonReliablePubSubTopic<V> topic, String subscriptionName, String name) {
        this.topic = topic;
        this.name = name;
        this.subscriptionName = subscriptionName;
    }

    @Override
    public void registerListener(MessageListenerArgs<V> listenerArgs) {
        if (listenerAdded) {
            throw new IllegalStateException("Listener can be added only once");
        }

        topic.registerListener(subscriptionName, name, listenerArgs, this);
        listenerAdded = true;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public ConsumerStatistics getStatistics() {
        return topic.getStatistics(subscriptionName, name);
    }

    @Override
    public RFuture<ConsumerStatistics> getStatisticsAsync() {
        return topic.getStatisticsAsync(subscriptionName, name);
    }

    @Override
    public void acknowledge(MessageAckArgs args) {
        topic.acknowledge(subscriptionName, name, args);
    }

    @Override
    public void negativeAcknowledge(MessageNegativeAckArgs args) {
        topic.negativeAcknowledge(subscriptionName, name, args);
    }

    @Override
    public RFuture<Void> acknowledgeAsync(MessageAckArgs args) {
        return topic.acknowledgeAsync(subscriptionName, name, args);
    }

    @Override
    public RFuture<Void> negativeAcknowledgeAsync(MessageNegativeAckArgs args) {
        return topic.negativeAcknowledgeAsync(subscriptionName, name, args);
    }
}
