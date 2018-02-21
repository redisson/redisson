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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.redisson.client.RedisClient;
import org.redisson.client.protocol.decoder.ListScanResult;
import org.redisson.client.protocol.decoder.ScanObjectEntry;
import org.redisson.misc.HashValue;

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
            
            private List<HashValue> firstValues;
            private List<HashValue> lastValues;
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
                scanIteratorReactive(client, nextIterPos).subscribe(new Subscriber<ListScanResult<ScanObjectEntry>>() {

                    @Override
                    public void onSubscribe(Subscription s) {
                        s.request(Long.MAX_VALUE);
                    }

                    @Override
                    public void onNext(ListScanResult<ScanObjectEntry> res) {
                        if (finished) {
                            client = null;
                            firstValues = null;
                            lastValues = null;
                            nextIterPos = 0;
                            return;
                        }

                        long prevIterPos = nextIterPos;
                        
                        lastValues = convert(res.getValues());
                        client = res.getRedisClient();
                        
                        if (nextIterPos == 0 && firstValues == null) {
                            firstValues = lastValues;
                            lastValues = null;
                            if (firstValues.isEmpty()) {
                                client = null;
                                firstValues = null;
                                nextIterPos = 0;
                                prevIterPos = -1;
                            }
                        } else { 
                            if (firstValues.isEmpty()) {
                                firstValues = lastValues;
                                lastValues = null;
                                if (firstValues.isEmpty()) {
                                    if (res.getPos() == 0) {
                                        finished = true;
                                        emitter.complete();
                                        return;
                                    }
                                }
                            } else if (lastValues.removeAll(firstValues)) {
                                client = null;
                                firstValues = null;
                                lastValues = null;
                                nextIterPos = 0;
                                prevIterPos = -1;
                                finished = true;
                                emitter.complete();
                                return;
                            }
                        }

                        for (ScanObjectEntry val : res.getValues()) {
                            emitter.next((V)val.getObj());
                            elementsRead.incrementAndGet();
                        }

                        nextIterPos = res.getPos();
                        
                        if (elementsRead.get() >= readAmount.get()) {
                            emitter.complete();
                            elementsRead.set(0);
                            completed = true;
                            return;
                        }
                        if (prevIterPos == nextIterPos) {
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
    
    private List<HashValue> convert(List<ScanObjectEntry> list) {
        List<HashValue> result = new ArrayList<HashValue>(list.size());
        for (ScanObjectEntry entry : list) {
            result.add(entry.getHash());
        }
        return result;
    }

    protected abstract Publisher<ListScanResult<ScanObjectEntry>> scanIteratorReactive(RedisClient client, long nextIterPos);

}
