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
package org.redisson.rx;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.functions.LongConsumer;
import io.reactivex.rxjava3.processors.ReplayProcessor;
import org.reactivestreams.Publisher;
import org.redisson.RedissonTransferQueue;
import org.redisson.api.RFuture;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <V> - value type
 */
public class RedissonTransferQueueRx<V> {

    private final RedissonTransferQueue<V> queue;

    public RedissonTransferQueueRx(RedissonTransferQueue<V> queue) {
        this.queue = queue;
    }

    public Flowable<V> takeElements() {
        return ElementsStream.takeElements(queue::takeAsync);
    }

    public Publisher<V> iterator() {
        ReplayProcessor<V> p = ReplayProcessor.create();
        return p.doOnRequest(new LongConsumer() {

            private int currentIndex = 0;

            @Override
            public void accept(long n) throws Exception {
                queue.getValueAsync(currentIndex).whenComplete((value, e) -> {
                    if (e != null) {
                        p.onError(e);
                        return;
                    }

                    if (value != null) {
                        p.onNext(value);
                        currentIndex++;
                    }

                    if (value == null) {
                        p.onComplete();
                        return;
                    }
                    if (n-1 == 0) {
                        return;
                    }
                    try {
                        accept(n-1);
                    } catch (Exception e1) {
                        e1.printStackTrace();
                    }
                });
            }

        });
    }

    public Single<Boolean> addAll(Publisher<? extends V> c) {
        return new PublisherAdder<V>() {

            @Override
            public RFuture<Boolean> add(Object o) {
                return queue.addAsync((V) o);
            }

        }.addAll(c);
    }


}
