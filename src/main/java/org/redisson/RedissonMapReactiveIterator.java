/**
 * Copyright 2014 Nikita Koksharov, Nickolay Borbit
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
package org.redisson;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Map.Entry;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.redisson.client.protocol.decoder.MapScanResult;

import reactor.rx.Stream;
import reactor.rx.subscription.ReactiveSubscription;

public class RedissonMapReactiveIterator<K, V, M> {

    private final RedissonMapReactive<K, V> map;

    public RedissonMapReactiveIterator(RedissonMapReactive<K, V> map) {
        this.map = map;
    }

    public Publisher<M> stream() {
        return new Stream<M>() {

            @Override
            public void subscribe(final Subscriber<? super M> t) {
                t.onSubscribe(new ReactiveSubscription<M>(this, t) {

                    private Map<K, V> firstValues;
                    private long iterPos = 0;
                    private InetSocketAddress client;

                    private long currentIndex;

                    @Override
                    protected void onRequest(final long n) {
                        currentIndex = n;
                        nextValues();
                    }

                    protected void nextValues() {
                        final ReactiveSubscription<M> m = this;
                        map.scanIteratorReactive(client, iterPos).subscribe(new Subscriber<MapScanResult<Object, V>>() {

                            @Override
                            public void onSubscribe(Subscription s) {
                                s.request(Long.MAX_VALUE);
                            }

                            @Override
                            public void onNext(MapScanResult<Object, V> res) {
                                client = res.getRedisClient();
                                if (iterPos == 0 && firstValues == null) {
                                    firstValues = (Map<K, V>) res.getMap();
                                } else if (res.getMap().equals(firstValues)) {
                                    m.onComplete();
                                    currentIndex = 0;
                                    return;
                                }

                                iterPos = res.getPos();
                                for (Entry<K, V> entry : ((Map<K, V>)res.getMap()).entrySet()) {
                                    M val = getValue(entry);
                                    m.onNext(val);
                                    currentIndex--;
                                    if (currentIndex == 0) {
                                        m.onComplete();
                                        return;
                                    }
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

        };
    }


    M getValue(Entry<K, V> entry) {
        return (M) entry;
    }

}
