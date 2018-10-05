/**
 * Copyright 2018 Nikita Koksharov
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

import java.util.concurrent.atomic.AtomicLong;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscription;
import org.redisson.api.RFuture;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import reactor.rx.Promise;
import reactor.rx.Promises;
import reactor.rx.action.support.DefaultSubscriber;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <V> value type
 */
public abstract class PublisherAdder<V> {

    public abstract RFuture<Boolean> add(Object o);
    
    public Publisher<Boolean> addAll(Publisher<? extends V> c) {
        final Promise<Boolean> promise = Promises.prepare();

        c.subscribe(new DefaultSubscriber<V>() {

            volatile boolean completed;
            AtomicLong values = new AtomicLong();
            Subscription s;
            Boolean lastSize = false;

            @Override
            public void onSubscribe(Subscription s) {
                this.s = s;
                s.request(1);
            }

            @Override
            public void onNext(V o) {
                values.getAndIncrement();
                add(o).addListener(new FutureListener<Boolean>() {
                    @Override
                    public void operationComplete(Future<Boolean> future) throws Exception {
                        if (!future.isSuccess()) {
                            promise.onError(future.cause());
                            return;
                        }
                        
                        if (future.getNow()) {
                            lastSize = true;
                        }
                        s.request(1);
                        if (values.decrementAndGet() == 0 && completed) {
                            promise.onNext(lastSize);
                        }
                    }
                });
            }

            @Override
            public void onComplete() {
                completed = true;
                if (values.get() == 0) {
                    promise.onNext(lastSize);
                }
            }
        });

        return promise;
    }

}
