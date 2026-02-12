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
package org.redisson.reactive;

import org.reactivestreams.Publisher;
import org.redisson.RedissonTransferQueue;
import org.redisson.api.RFuture;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.util.function.Consumer;
import java.util.function.LongConsumer;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <V> - value type
 */
public class RedissonTransferQueueReactive<V> {

    private final RedissonTransferQueue<V> queue;

    public RedissonTransferQueueReactive(RedissonTransferQueue<V> queue) {
        this.queue = queue;
    }

    public Flux<V> takeElements() {
        return ElementsStream.takeElements(queue::takeAsync);
    }

    public Publisher<V> iterator() {
        return Flux.create(new Consumer<FluxSink<V>>() {

            @Override
            public void accept(FluxSink<V> emitter) {
                emitter.onRequest(new LongConsumer() {

                    int currentIndex = 0;

                    @Override
                    public void accept(long value) {
                        onRequest(true, emitter, value);
                    }

                    protected void onRequest(boolean forward, FluxSink<V> emitter, long n) {
                        queue.getValueAsync(currentIndex).whenComplete((value, e) -> {
                                if (e != null) {
                                    emitter.error(e);
                                    return;
                                }

                                if (value != null) {
                                    emitter.next(value);
                                    if (forward) {
                                        currentIndex++;
                                    } else {
                                        currentIndex--;
                                    }
                                }

                                if (value == null) {
                                    emitter.complete();
                                    return;
                                }
                                if (n-1 == 0) {
                                    return;
                                }
                                onRequest(forward, emitter, n-1);
                        });
                    }
                });

            }

        });
    }

    public Publisher<Boolean> addAll(Publisher<? extends V> c) {
        return new PublisherAdder<V>() {

            @Override
            public RFuture<Boolean> add(Object o) {
                return queue.addAsync((V) o);
            }

        }.addAll(c);
    }


}
