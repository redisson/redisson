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
package org.redisson.reactive;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscription;
import org.redisson.api.RFuture;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <V> value type
 */
public abstract class PublisherAdder<V> {

    public abstract RFuture<Boolean> add(Object o);

    public Publisher<Boolean> addAll(Publisher<? extends V> c) {
        return Mono.create(emitter -> emitter.onRequest(n -> {
            c.subscribe(new BaseSubscriber<V>() {

                volatile boolean completed;
                final AtomicLong values = new AtomicLong();
                Subscription s;
                volatile Boolean lastSize = false;

                @Override
                protected void hookOnSubscribe(Subscription s) {
                    this.s = s;
                    s.request(1);
                }

                @Override
                protected void hookOnNext(V o) {
                    values.getAndIncrement();
                    add(o).whenComplete((res, e) -> {
                        if (e != null) {
                            emitter.error(e);
                            return;
                        }

                        if (res) {
                            lastSize = true;
                        }
                        s.request(1);
                        if (values.decrementAndGet() == 0 && completed) {
                            emitter.success(lastSize);
                        }
                    });
                }

                @Override
                protected void hookOnComplete() {
                    completed = true;
                    if (values.get() == 0) {
                        emitter.success(lastSize);
                    }
                }
            });
        }));
    }

}
