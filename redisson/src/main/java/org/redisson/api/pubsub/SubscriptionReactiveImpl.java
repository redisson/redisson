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
import org.redisson.reactive.CommandReactiveExecutor;
import org.redisson.reactive.ReactiveProxyBuilder;
import reactor.core.publisher.Mono;

public class SubscriptionReactiveImpl<V> {

    Subscription<V> instance;
    CommandReactiveExecutor executor;

    public SubscriptionReactiveImpl(Subscription<V> instance, CommandReactiveExecutor executor) {
        this.instance = instance;
        this.executor = executor;
    }

    public Mono<PullConsumerReactive<V>> createPullConsumer() {
        return executor.reactive(() -> {
            RFuture<PullConsumer<V>> ff = instance.createPullConsumerAsync();
            return ff.thenApply(s -> {
                return ReactiveProxyBuilder.create(executor, s, PullConsumerReactive.class);
            });
        });
    }

    public Mono<PullConsumerReactive<V>> createPullConsumer(ConsumerConfig config) {
        return executor.reactive(() -> {
            RFuture<PullConsumer<V>> ff = instance.createPullConsumerAsync(config);
            return ff.thenApply(s -> {
                return ReactiveProxyBuilder.create(executor, s, PullConsumerReactive.class);
            });
        });
    }

    public Mono<PushConsumerReactive<V>> createPushConsumer() {
        return executor.reactive(() -> {
            RFuture<PushConsumer<V>> ff = instance.createPushConsumerAsync();
            return ff.thenApply(s -> {
                return ReactiveProxyBuilder.create(executor, s, PushConsumerReactive.class);
            });
        });
    }

    public Mono<PushConsumerReactive<V>> createPushConsumer(ConsumerConfig config) {
        return executor.reactive(() -> {
            RFuture<PushConsumer<V>> ff = instance.createPushConsumerAsync(config);
            return ff.thenApply(s -> {
                return ReactiveProxyBuilder.create(executor, s, PushConsumerReactive.class);
            });
        });
    }

}
