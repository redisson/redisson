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

import java.util.concurrent.atomic.AtomicLong;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscription;
import org.redisson.api.RCollectionReactive;

import reactor.rx.Promise;
import reactor.rx.Promises;
import reactor.rx.action.support.DefaultSubscriber;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <V> value type
 */
public class PublisherAdder<V> {

    private final RCollectionReactive<V> destination;

    public PublisherAdder(RCollectionReactive<V> destination) {
        this.destination = destination;
    }

    public Integer sum(Integer first, Integer second) {
        return first + second;
    }

    public Publisher<Integer> addAll(Publisher<? extends V> c) {
        final Promise<Integer> promise = Promises.prepare();

        c.subscribe(new DefaultSubscriber<V>() {

            volatile boolean completed;
            AtomicLong values = new AtomicLong();
            Subscription s;
            Integer lastSize = 0;

            @Override
            public void onSubscribe(Subscription s) {
                this.s = s;
                s.request(1);
            }

            @Override
            public void onNext(V o) {
                values.getAndIncrement();
                destination.add(o).subscribe(new DefaultSubscriber<Integer>() {

                    @Override
                    public void onSubscribe(Subscription s) {
                        s.request(1);
                    }

                    @Override
                    public void onError(Throwable t) {
                        promise.onError(t);
                    }

                    @Override
                    public void onNext(Integer o) {
                        lastSize = sum(lastSize, o);
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
