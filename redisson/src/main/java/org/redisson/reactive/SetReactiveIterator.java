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

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.redisson.client.RedisClient;
import org.redisson.client.protocol.decoder.ListScanResult;
import org.redisson.client.protocol.decoder.ScanObjectEntry;

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

            private long nextIterPos;
            private RedisClient client;

            private boolean finished;

            @Override
            protected void onRequest(long n) {
                nextValues();
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
                            client = null;
                            nextIterPos = 0;
                            return;
                        }

                        client = res.getRedisClient();
                        nextIterPos = res.getPos();
                        
                        for (ScanObjectEntry val : res.getValues()) {
                            m.onNext((V)val.getObj());
                        }

                        if (res.getPos() == 0) {
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
    
    protected abstract Publisher<ListScanResult<ScanObjectEntry>> scanIteratorReactive(RedisClient client, long nextIterPos);

}
