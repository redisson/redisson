/**
 * Copyright 2016 Nikita Koksharov
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

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.redisson.client.RedisClient;
import org.redisson.client.protocol.decoder.ListScanResult;
import org.redisson.client.protocol.decoder.ScanObjectEntry;

import io.netty.buffer.ByteBuf;
import reactor.rx.Stream;
import reactor.rx.subscription.ReactiveSubscription;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <V> value type
 */
public abstract class SetReactiveIterator<V> extends Stream<V> {

    @Override
    public void subscribe(final Subscriber<? super V> t) {
        t.onSubscribe(new ReactiveSubscription<V>(this, t) {

            private List<ByteBuf> firstValues;
            private List<ByteBuf> lastValues;
            private long nextIterPos;
            private RedisClient client;

            private boolean finished;

            @Override
            protected void onRequest(long n) {
                nextValues();
            }

            private void handle(List<ScanObjectEntry> vals) {
                for (ScanObjectEntry val : vals) {
                    onNext((V)val.getObj());
                }
            }

            protected void nextValues() {
                final ReactiveSubscription<V> m = this;
                scanIteratorReactive(client, nextIterPos).subscribe(new Subscriber<ListScanResult<ScanObjectEntry>>() {

                    @Override
                    public void onSubscribe(Subscription s) {
                        s.request(Long.MAX_VALUE);
                    }

                    @Override
                    public void onNext(ListScanResult<ScanObjectEntry> res) {
                        if (finished) {
                            free(firstValues);
                            free(lastValues);

                            client = null;
                            firstValues = null;
                            lastValues = null;
                            nextIterPos = 0;
                            return;
                        }

                        long prevIterPos = nextIterPos;
                        if (lastValues != null) {
                            free(lastValues);
                        }
                        
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
                                        m.onComplete();
                                        return;
                                    }
                                }
                            } else if (lastValues.removeAll(firstValues)) {
                                free(firstValues);
                                free(lastValues);

                                client = null;
                                firstValues = null;
                                lastValues = null;
                                nextIterPos = 0;
                                prevIterPos = -1;
                                finished = true;
                                m.onComplete();
                                return;
                            }
                        }

                        handle(res.getValues());

                        nextIterPos = res.getPos();
                        
                        if (prevIterPos == nextIterPos) {
                            finished = true;
                            m.onComplete();
                        }
                    }

                    @Override
                    public void onError(Throwable error) {
                        m.onError(error);
                    }

                    @Override
                    public void onComplete() {
                        if (finished) {
                            return;
                        }
                        nextValues();
                    }
                });
            }
        });
    }
    
    private void free(List<ByteBuf> list) {
        if (list == null) {
            return;
        }
        for (ByteBuf byteBuf : list) {
            byteBuf.release();
        }
    }
    
    private List<ByteBuf> convert(List<ScanObjectEntry> list) {
        List<ByteBuf> result = new ArrayList<ByteBuf>(list.size());
        for (ScanObjectEntry entry : list) {
            result.add(entry.getBuf());
        }
        return result;
    }

    protected abstract Publisher<ListScanResult<ScanObjectEntry>> scanIteratorReactive(RedisClient client, long nextIterPos);

}
