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
package org.redisson.rx;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.functions.Consumer;
import io.reactivex.rxjava3.processors.ReplayProcessor;
import org.reactivestreams.Publisher;
import org.redisson.api.RFuture;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <V> value type
 */
public abstract class PublisherAdder<V> {

    public abstract RFuture<Boolean> add(Object o);
    
    public Single<Boolean> addAll(Publisher<? extends V> c) {
        final Flowable<? extends V> cc = Flowable.fromPublisher(c);
        final ReplayProcessor<Boolean> p = ReplayProcessor.create();
        return p.doOnRequest(t -> {
            final AtomicBoolean completed = new AtomicBoolean();
            final AtomicLong values = new AtomicLong();
            final AtomicBoolean lastSize = new AtomicBoolean();

            cc.subscribe((Consumer<V>) t2 -> {
                values.getAndIncrement();
                add(t2).whenComplete((res, e) -> {
                    if (e != null) {
                        p.onError(e);
                        return;
                    }

                    if (res) {
                        lastSize.set(true);
                    }
                    if (values.decrementAndGet() == 0 && completed.get()) {
                        p.onNext(lastSize.get());
                        p.onComplete();
                    }
                });
            }, t1 -> p.onError(t1), () -> {
                completed.set(true);
                if (values.get() == 0) {
                    p.onNext(lastSize.get());
                    p.onComplete();
                }
            });
        }).singleOrError();
    }

}
