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
import java.util.List;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.redisson.client.protocol.decoder.ListScanResult;

import reactor.rx.Stream;
import reactor.rx.subscription.ReactiveSubscription;

public abstract class SetReactiveIterator<V> extends Stream<V> {

    @Override
    public void subscribe(final Subscriber<? super V> t) {
        t.onSubscribe(new ReactiveSubscription<V>(this, t) {

            private List<V> firstValues;
            private long nextIterPos;
            private InetSocketAddress client;

            private long currentIndex;

            @Override
            protected void onRequest(long n) {
                currentIndex = n;

                nextValues();
            }

            private void handle(List<V> vals) {
                for (V val : vals) {
                    onNext(val);
                }
            }

            protected void nextValues() {
                final ReactiveSubscription<V> m = this;
                scanIteratorReactive(client, nextIterPos).subscribe(new Subscriber<ListScanResult<V>>() {

                    @Override
                    public void onSubscribe(Subscription s) {
                        s.request(Long.MAX_VALUE);
                    }

                    @Override
                    public void onNext(ListScanResult<V> res) {
                        client = res.getRedisClient();

                        long prevIterPos = nextIterPos;
                        if (nextIterPos == 0 && firstValues == null) {
                            firstValues = res.getValues();
                        } else if (res.getValues().equals(firstValues)) {
                            m.onComplete();
                            currentIndex = 0;
                            return;
                        }

                        nextIterPos = res.getPos();
                        if (prevIterPos == nextIterPos) {
                            nextIterPos = -1;
                        }

                        handle(res.getValues());

                        if (currentIndex == 0) {
                            return;
                        }

                        if (nextIterPos == -1) {
                            m.onComplete();
                            currentIndex = 0;
                        }
                    }

                    @Override
                    public void onError(Throwable error) {
                        m.onError(error);
                    }

                    @Override
                    public void onComplete() {
                        if (currentIndex == 0) {
                            return;
                        }
                        nextValues();
                    }
                });
            }
        });
    }

    protected abstract Publisher<ListScanResult<V>> scanIteratorReactive(InetSocketAddress client, long nextIterPos);

}
