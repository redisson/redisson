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

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.redisson.client.RedisClient;
import org.redisson.client.protocol.decoder.ListScanResult;

import reactor.core.publisher.FluxSink;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <V> value type
 */
public abstract class SetReactiveIterator<V> implements Consumer<FluxSink<V>> {

    @Override
    public void accept(FluxSink<V> emitter) {
        emitter.onRequest(new LongConsumer() {
            
            private long nextIterPos;
            private RedisClient client;
            private AtomicLong elementsRead = new AtomicLong();
            
            private boolean finished;
            private volatile boolean completed;
            private AtomicLong readAmount = new AtomicLong();
            
            @Override
            public void accept(long value) {
                readAmount.addAndGet(value);
                if (completed || elementsRead.get() == 0) {
                    nextValues(emitter);
                    completed = false;
                }
            }
            
            protected void nextValues(FluxSink<V> emitter) {
                scanIteratorReactive(client, nextIterPos).subscribe(new Subscriber<ListScanResult<Object>>() {

                    @Override
                    public void onSubscribe(Subscription s) {
                        s.request(Long.MAX_VALUE);
                    }

                    @Override
                    public void onNext(ListScanResult<Object> res) {
                        if (finished) {
                            client = null;
                            nextIterPos = 0;
                            return;
                        }

                        client = res.getRedisClient();
                        nextIterPos = res.getPos();

                        for (Object val : res.getValues()) {
                            emitter.next((V)val);
                            elementsRead.incrementAndGet();
                        }
                        
                        if (elementsRead.get() >= readAmount.get()) {
                            emitter.complete();
                            elementsRead.set(0);
                            completed = true;
                            return;
                        }
                        if (res.getPos() == 0 && !tryAgain()) {
                            finished = true;
                            emitter.complete();
                        }
                    }
                                
                    @Override
                    public void onError(Throwable error) {
                        emitter.error(error);
                    }

                    @Override
                    public void onComplete() {
                        if (finished || completed) {
                            return;
                        }
                        nextValues(emitter);
                    }
                });
            }
        });
    }

    protected boolean tryAgain() {
        return false;
    }

    protected abstract Publisher<ListScanResult<Object>> scanIteratorReactive(RedisClient client, long nextIterPos);

}
