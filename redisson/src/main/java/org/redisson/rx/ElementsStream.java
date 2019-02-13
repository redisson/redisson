/**
 * Copyright (c) 2013-2019 Nikita Koksharov
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

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.redisson.api.RFuture;

import io.reactivex.Flowable;
import io.reactivex.functions.Action;
import io.reactivex.functions.LongConsumer;
import io.reactivex.processors.ReplayProcessor;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class ElementsStream {

    public static <V> Flowable<V> takeElements(Supplier<RFuture<V>> callable) {
        ReplayProcessor<V> p = ReplayProcessor.create();
        return p.doOnRequest(new LongConsumer() {
            @Override
            public void accept(long n) throws Exception {
                AtomicLong counter = new AtomicLong(n);
                AtomicReference<RFuture<V>> futureRef = new AtomicReference<RFuture<V>>();
                
                take(callable, p, counter, futureRef);

                p.doOnCancel(new Action() {
                    @Override
                    public void run() throws Exception {
                        futureRef.get().cancel(true);
                    }
                });
            }
        });
    }
    
    private static <V> void take(Supplier<RFuture<V>> factory, ReplayProcessor<V> p, AtomicLong counter, AtomicReference<RFuture<V>> futureRef) {
        RFuture<V> future = factory.get();
        futureRef.set(future);
        future.onComplete((res, e) -> {
            if (e != null) {
                p.onError(e);
                return;
            }
            
            p.onNext(res);
            if (counter.decrementAndGet() == 0) {
                p.onComplete();
            }
            
            take(factory, p, counter, futureRef);
        });
    }
    
}
