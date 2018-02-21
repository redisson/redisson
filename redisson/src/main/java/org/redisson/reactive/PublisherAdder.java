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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscription;
import org.redisson.api.RCollectionReactive;

import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Mono;

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
        CompletableFuture<Integer> promise = new CompletableFuture<>();
        c.subscribe(new BaseSubscriber<V>() {

            volatile boolean completed;
            AtomicLong values = new AtomicLong();
            Subscription s;
            Integer lastSize = 0;

            @Override
            protected void hookOnSubscribe(Subscription s) {
                this.s = s;
                s.request(1);
            }

            @Override
            protected void hookOnNext(V o) {
                values.getAndIncrement();
                destination.add(o).subscribe(new BaseSubscriber<Integer>() {

                    @Override
                    protected void hookOnSubscribe(Subscription s) {
                        s.request(1);
                    }

                    @Override
                    protected void hookOnError(Throwable t) {
                        promise.completeExceptionally(t);
                    }

                    @Override
                    protected void hookOnNext(Integer o) {
                        lastSize = sum(lastSize, o);
                        s.request(1);
                        if (values.decrementAndGet() == 0 && completed) {
                            promise.complete(lastSize);
                        }
                    }
                });
            }

            @Override
            protected void hookOnComplete() {
                completed = true;
                if (values.get() == 0) {
                    promise.complete(lastSize);
                }
            }
        });

        return Mono.fromCompletionStage(promise);
    }

}
