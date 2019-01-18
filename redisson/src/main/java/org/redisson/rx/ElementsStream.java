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

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.redisson.api.RFuture;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
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

    public static <V> Flowable<V> takeElements(final Callable<RFuture<V>> callable) {
        final ReplayProcessor<V> p = ReplayProcessor.create();
        return p.doOnRequest(new LongConsumer() {
            @Override
            public void accept(long n) throws Exception {
                final AtomicLong counter = new AtomicLong(n);
                final AtomicReference<RFuture<V>> futureRef = new AtomicReference<RFuture<V>>();
                
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
    
    private static <V> void take(final Callable<RFuture<V>> factory, final ReplayProcessor<V> p, final AtomicLong counter, final AtomicReference<RFuture<V>> futureRef) throws Exception {
        RFuture<V> future = factory.call();
        futureRef.set(future);
        future.addListener(new FutureListener<V>() {
            @Override
            public void operationComplete(Future<V> future) throws Exception {
                if (!future.isSuccess()) {
                    p.onError(future.cause());
                    return;
                }
                
                p.onNext(future.getNow());
                if (counter.decrementAndGet() == 0) {
                    p.onComplete();
                }
                
                take(factory, p, counter, futureRef);
            }
        });
    }
    
}
