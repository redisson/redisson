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
package org.redisson.reactive;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.redisson.api.RFuture;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class ElementsStream {

    private static <V> void take(final Callable<RFuture<V>> factory, final FluxSink<V> emitter, final AtomicLong counter, final AtomicReference<RFuture<V>> futureRef) {
        RFuture<V> future;
        try {
            future = factory.call();
        } catch (Exception e) {
            emitter.error(e);
            return;
        }
        futureRef.set(future);
        future.addListener(new FutureListener<V>() {
            @Override
            public void operationComplete(Future<V> future) throws Exception {
                if (!future.isSuccess()) {
                    emitter.error(future.cause());
                    return;
                }
                
                emitter.next(future.getNow());
                if (counter.decrementAndGet() == 0) {
                    emitter.complete();
                }
                
                take(factory, emitter, counter, futureRef);
            }
        });
    }
    
    public static <V> Flux<V> takeElements(Callable<RFuture<V>> callable) {
        return Flux.<V>create(emitter -> {
            emitter.onRequest(n -> {
                AtomicLong counter = new AtomicLong(n);
                AtomicReference<RFuture<V>> futureRef = new AtomicReference<RFuture<V>>();
                take(callable, emitter, counter, futureRef);
                emitter.onDispose(() -> {
                    futureRef.get().cancel(true);
                });
            });
        });
    }

    
}
