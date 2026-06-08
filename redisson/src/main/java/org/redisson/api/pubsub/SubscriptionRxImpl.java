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

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import org.redisson.api.RFuture;
import org.redisson.rx.CommandRxExecutor;
import org.redisson.rx.RxProxyBuilder;

public class SubscriptionRxImpl<V> {

    Subscription<V> instance;
    CommandRxExecutor executor;

    public SubscriptionRxImpl(Subscription<V> instance, CommandRxExecutor executor) {
        this.instance = instance;
        this.executor = executor;
    }

    public Single<PullConsumerRx<V>> createPullConsumer() {
        Flowable<PullConsumerRx<V>> f = executor.flowable(() -> {
            RFuture<PullConsumer<V>> ff = instance.createPullConsumerAsync();
            return ff.thenApply(s -> {
                return RxProxyBuilder.create(executor, s, PullConsumerRx.class);
            });
        });
        return f.singleOrError();
    }

    public Single<PullConsumerRx<V>> createPullConsumer(ConsumerConfig config) {
        Flowable<PullConsumerRx<V>> f = executor.flowable(() -> {
            RFuture<PullConsumer<V>> ff = instance.createPullConsumerAsync(config);
            return ff.thenApply(s -> {
                return RxProxyBuilder.create(executor, s, PullConsumerRx.class);
            });
        });
        return f.singleOrError();
    }

    public Single<PushConsumerRx<V>> createPushConsumer() {
        Flowable<PushConsumerRx<V>> f = executor.flowable(() -> {
            RFuture<PushConsumer<V>> ff = instance.createPushConsumerAsync();
            return ff.thenApply(s -> {
                return RxProxyBuilder.create(executor, s, PushConsumerRx.class);
            });
        });
        return f.singleOrError();
    }

    public Single<PushConsumerRx<V>> createPushConsumer(ConsumerConfig config) {
        Flowable<PushConsumerRx<V>> f = executor.flowable(() -> {
            RFuture<PushConsumer<V>> ff = instance.createPushConsumerAsync(config);
            return ff.thenApply(s -> {
                return RxProxyBuilder.create(executor, s, PushConsumerRx.class);
            });
        });
        return f.singleOrError();
    }

}
