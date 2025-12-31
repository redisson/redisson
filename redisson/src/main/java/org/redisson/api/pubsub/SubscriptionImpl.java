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

import java.util.Set;

/**
 *
 * @author Nikita Koksharov
 *
 */
public final class SubscriptionImpl<V> implements Subscription<V> {

    final RedissonReliablePubSubTopic<V> topic;
    final String name;

    public SubscriptionImpl(RedissonReliablePubSubTopic<V> topic, String name) {
        this.topic = topic;
        this.name = name;
    }

    @Override
    public PullConsumer<V> createPullConsumer() {
        return topic.createPullConsumer(name);
    }

    @Override
    public RFuture<PullConsumer<V>> createPullConsumerAsync() {
        return topic.createPullConsumerAsync(name);
    }

    @Override
    public PullConsumer<V> createPullConsumer(ConsumerConfig config) {
        ConsumerConfigParams params = (ConsumerConfigParams) config;
        return topic.createPullConsumer(this.name, params.getName(), params.getOrderingKeyClaimTimeout());
    }

    @Override
    public RFuture<PullConsumer<V>> createPullConsumerAsync(ConsumerConfig config) {
        ConsumerConfigParams params = (ConsumerConfigParams) config;
        return topic.createPullConsumerAsync(this.name, params.getName(), params.getOrderingKeyClaimTimeout());
    }

    @Override
    public PushConsumer<V> createPushConsumer(ConsumerConfig config) {
        ConsumerConfigParams params = (ConsumerConfigParams) config;
        return topic.createPushConsumer(name, params.getName(), params.getOrderingKeyClaimTimeout());
    }

    @Override
    public RFuture<PushConsumer<V>> createPushConsumerAsync(ConsumerConfig config) {
        ConsumerConfigParams params = (ConsumerConfigParams) config;
        return topic.createPushConsumerAsync(name, params.getName(), params.getOrderingKeyClaimTimeout());
    }

    @Override
    public PushConsumer<V> createPushConsumer() {
        return createPushConsumer(ConsumerConfig.generatedName());
    }

    @Override
    public RFuture<PushConsumer<V>> createPushConsumerAsync() {
        return createPushConsumerAsync(ConsumerConfig.generatedName());
    }

    @Override
    public boolean removeConsumer(String consumerName) {
        return topic.removeConsumer(getName(), consumerName);
    }

    @Override
    public RFuture<Boolean> removeConsumerAsync(String consumerName) {
        return topic.removeConsumerAsync(getName(), consumerName);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void seek(Position value) {
        topic.seekSubscription(getName(), value);
    }

    @Override
    public RFuture<Void> seekAsync(Position value) {
        return topic.seekSubscriptionAsync(getName(), value);
    }

    @Override
    public boolean hasConsumer(String consumerName) {
        return topic.hasConsumer(getName(), consumerName);
    }

    @Override
    public RFuture<Boolean> hasConsumerAsync(String consumerName) {
        return topic.hasConsumerAsync(getName(), consumerName);
    }

    @Override
    public Set<String> getConsumerNames() {
        return topic.getConsumerNames(getName());
    }

    @Override
    public RFuture<Set<String>> getConsumerNamesAsync() {
        return topic.getConsumerNamesAsync(getName());
    }

    @Override
    public SubscriptionStatistics getStatistics() {
        return topic.getStatistics(getName());
    }

    @Override
    public RFuture<SubscriptionStatistics> getStatisticsAsync() {
        return topic.getStatisticsAsync(getName());
    }
}
