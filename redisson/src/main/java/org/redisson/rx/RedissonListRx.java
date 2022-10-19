/**
 * Copyright (c) 2013-2022 Nikita Koksharov
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

import org.reactivestreams.Publisher;
import org.redisson.api.RFuture;
import org.redisson.api.RListAsync;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.functions.LongConsumer;
import io.reactivex.rxjava3.processors.ReplayProcessor;

/**
 * Distributed and concurrent implementation of {@link java.util.List}
 *
 * @author Nikita Koksharov
 *
 * @param <V> the type of elements held in this collection
 */
public class RedissonListRx<V> {

    private final RListAsync<V> instance;

    public RedissonListRx(RListAsync<V> instance) {
        this.instance = instance;
    }

    public Publisher<V> descendingIterator() {
        return iterator(-1, false);
    }

    public Publisher<V> iterator() {
        return iterator(0, true);
    }

    public Publisher<V> descendingIterator(int startIndex) {
        return iterator(startIndex, false);
    }

    public Publisher<V> iterator(int startIndex) {
        return iterator(startIndex, true);
    }

    private Publisher<V> iterator(int startIndex, boolean forward) {
        ReplayProcessor<V> p = ReplayProcessor.create();
        return p.doOnRequest(new LongConsumer() {

            private int currentIndex = startIndex;
            
            @Override
            public void accept(long n) throws Exception {
                instance.getAsync(currentIndex).whenComplete((value, e) -> {
                    if (e != null) {
                        p.onError(e);
                        return;
                    }
                    
                    if (value != null) {
                        p.onNext(value);
                        if (forward) {
                            currentIndex++;
                        } else {
                            currentIndex--;
                        }
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
                return instance.addAsync((V) o);
            }

        }.addAll(c);
    }

}
