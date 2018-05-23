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

import java.util.AbstractMap;
import java.util.Map.Entry;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.redisson.client.RedisClient;
import org.redisson.client.protocol.decoder.MapScanResult;
import org.redisson.client.protocol.decoder.ScanObjectEntry;

import reactor.rx.Stream;
import reactor.rx.subscription.ReactiveSubscription;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <K> key type
 * @param <V> value type
 * @param <M> entry type
 */
public class RedissonMapReactiveIterator<K, V, M> {

    private final MapReactive<K, V> map;

    public RedissonMapReactiveIterator(MapReactive<K, V> map) {
        this.map = map;
    }

    public Publisher<M> stream() {
        return new Stream<M>() {

            @Override
            public void subscribe(final Subscriber<? super M> t) {
                t.onSubscribe(new ReactiveSubscription<M>(this, t) {

                    private long nextIterPos = 0;
                    private RedisClient client;

                    private long currentIndex;

                    @Override
                    protected void onRequest(final long n) {
                        currentIndex = n;
                        nextValues();
                    }

                    protected void nextValues() {
                        final ReactiveSubscription<M> m = this;
                        map.scanIteratorReactive(client, nextIterPos).subscribe(new Subscriber<MapScanResult<ScanObjectEntry, ScanObjectEntry>>() {

                            @Override
                            public void onSubscribe(Subscription s) {
                                s.request(Long.MAX_VALUE);
                            }
                            
                            @Override
                            public void onNext(MapScanResult<ScanObjectEntry, ScanObjectEntry> res) {
                                if (currentIndex == 0) {
                                    client = null;
                                    nextIterPos = 0;
                                    return;
                                }

                                client = res.getRedisClient();
                                nextIterPos = res.getPos();

                                for (Entry<ScanObjectEntry, ScanObjectEntry> entry : res.getMap().entrySet()) {
                                    M val = getValue(entry);
                                    m.onNext(val);
                                    currentIndex--;
                                    if (currentIndex == 0) {
                                        m.onComplete();
                                        return;
                                    }
                                }
                                
                                if (res.getPos() == 0) {
                                    currentIndex = 0;
                                    m.onComplete();
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


    M getValue(final Entry<ScanObjectEntry, ScanObjectEntry> entry) {
        return (M)new AbstractMap.SimpleEntry<K, V>((K)entry.getKey().getObj(), (V)entry.getValue().getObj()) {

            @Override
            public V setValue(V value) {
                Publisher<V> publisher = map.put((K) entry.getKey().getObj(), value);
                return ((Stream<V>)publisher).next().poll();
            }

        };
    }

}
