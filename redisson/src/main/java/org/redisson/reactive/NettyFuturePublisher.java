/**
 * Copyright 2016 Nikita Koksharov
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
package org.redisson.reactive;

import org.reactivestreams.Subscriber;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import reactor.core.support.Exceptions;
import reactor.rx.Stream;
import reactor.rx.action.Action;
import reactor.rx.subscription.ReactiveSubscription;

public class NettyFuturePublisher<T> extends Stream<T> {

    private final Future<? extends T> that;

    public NettyFuturePublisher(Future<? extends T> that) {
        this.that = that;
    }

    @Override
    public void subscribe(final Subscriber<? super T> subscriber) {
        try {
            subscriber.onSubscribe(new ReactiveSubscription<T>(this, subscriber) {

                @Override
                public void request(long elements) {
                    Action.checkRequest(elements);
                    if (isComplete()) return;

                    that.addListener(new FutureListener<T>() {
                        @Override
                        public void operationComplete(Future<T> future) throws Exception {
                            if (!future.isSuccess()) {
                                subscriber.onError(future.cause());
                                return;
                            }

                            if (future.getNow() != null) {
                                subscriber.onNext(future.getNow());
                            }
                            onComplete();
                        }
                    });
                }
            });
        } catch (Throwable throwable) {
            Exceptions.throwIfFatal(throwable);
            subscriber.onError(throwable);
        }
    }


}
