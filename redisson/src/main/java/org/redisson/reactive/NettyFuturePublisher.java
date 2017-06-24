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
import org.redisson.api.RFuture;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import reactor.core.support.Exceptions;
import reactor.fn.Supplier;
import reactor.rx.Stream;
import reactor.rx.subscription.ReactiveSubscription;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <T> return type
 */
public class NettyFuturePublisher<T> extends Stream<T> {

    private final Supplier<RFuture<T>> supplier;

    public NettyFuturePublisher(Supplier<RFuture<T>> supplier) {
        this.supplier = supplier;
    }

    @Override
    public void subscribe(final Subscriber<? super T> subscriber) {
        try {
            subscriber.onSubscribe(new ReactiveSubscription<T>(this, subscriber) {

                @Override
                protected void onRequest(long n) {
                    supplier.get().addListener(new FutureListener<T>() {
                        @Override
                        public void operationComplete(Future<T> future) throws Exception {
                            if (!future.isSuccess()) {
                                onError(future.cause());
                                return;
                            }
                            
                            if (future.getNow() != null) {
                                onNext(future.getNow());
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
