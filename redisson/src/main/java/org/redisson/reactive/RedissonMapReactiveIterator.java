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
import org.redisson.RedissonMap;
import org.redisson.client.RedisClient;
import org.redisson.client.protocol.decoder.MapScanResult;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
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

    private final RedissonMap<K, V> map;
    private final String pattern;
    private final int count;

    public RedissonMapReactiveIterator(RedissonMap<K, V> map, String pattern, int count) {
        this.map = map;
        this.pattern = pattern;
        this.count = count;
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
                        map.scanIteratorAsync(map.getName(), client, nextIterPos, pattern, count).addListener(new FutureListener<MapScanResult<Object, Object>>() {

                            @Override
                            public void operationComplete(Future<MapScanResult<Object, Object>> future)
                                    throws Exception {
                                    if (!future.isSuccess()) {
                                        m.onError(future.cause());
                                        return;
                                    }
    
                                    if (currentIndex == 0) {
                                        client = null;
                                        nextIterPos = 0;
                                        return;
                                    }
    
                                    MapScanResult<Object, Object> res = future.get();
                                    client = res.getRedisClient();
                                    nextIterPos = res.getPos();
    
                                    for (Entry<Object, Object> entry : res.getMap().entrySet()) {
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
    
                                    if (currentIndex == 0) {
                                        return;
                                    }
                                    nextValues();
                                }
                        });
                    }
                });
            };

        };
    }


    M getValue(final Entry<Object, Object> entry) {
        return (M)new AbstractMap.SimpleEntry<K, V>((K)entry.getKey(), (V)entry.getValue()) {

            @Override
            public V setValue(V value) {
                return map.put((K) entry.getKey(), value);
            }

        };
    }

}
