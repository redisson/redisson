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
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.redisson.client.protocol.decoder.MapScanResult;
import org.redisson.client.protocol.decoder.ScanObjectEntry;

import io.netty.buffer.ByteBuf;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <K> key type
 * @param <V> value type
 * @param <M> entry type
 */
public class RedissonMapReactiveIterator<K, V, M> implements Consumer<FluxSink<M>> {

    private final MapReactive<K, V> map;

    public RedissonMapReactiveIterator(MapReactive<K, V> map) {
        this.map = map;
    }
    
    @Override
    public void accept(FluxSink<M> emitter) {
        emitter.onRequest(new LongConsumer() {

            private Map<ByteBuf, ByteBuf> firstValues;
            private Map<ByteBuf, ByteBuf> lastValues;
            private long nextIterPos;
            private InetSocketAddress client;
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
            };
            
            protected void nextValues(FluxSink<M> emitter) {
                map.scanIteratorReactive(client, nextIterPos).subscribe(new Subscriber<MapScanResult<ScanObjectEntry, ScanObjectEntry>>() {

                    @Override
                    public void onSubscribe(Subscription s) {
                        s.request(Long.MAX_VALUE);
                    }
                    
                    private void free(Map<ByteBuf, ByteBuf> map) {
                        if (map == null) {
                            return;
                        }
                        for (Entry<ByteBuf, ByteBuf> entry : map.entrySet()) {
                            entry.getKey().release();
                            entry.getValue().release();
                        }
                    }

                    @Override
                    public void onNext(MapScanResult<ScanObjectEntry, ScanObjectEntry> res) {
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
                        
                        lastValues = convert(res.getMap());
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
                            } else if (lastValues.keySet().removeAll(firstValues.keySet())) {
                                free(firstValues);
                                free(lastValues);

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

                        for (Entry<ScanObjectEntry, ScanObjectEntry> entry : res.getMap().entrySet()) {
                            M val = getValue(entry);
                            emitter.next(val);
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
    
    private Map<ByteBuf, ByteBuf> convert(Map<ScanObjectEntry, ScanObjectEntry> map) {
        Map<ByteBuf, ByteBuf> result = new HashMap<ByteBuf, ByteBuf>(map.size());
        for (Entry<ScanObjectEntry, ScanObjectEntry> entry : map.entrySet()) {
            result.put(entry.getKey().getBuf(), entry.getValue().getBuf());
        }
        return result;
    }

    M getValue(final Entry<ScanObjectEntry, ScanObjectEntry> entry) {
        return (M)new AbstractMap.SimpleEntry<K, V>((K)entry.getKey().getObj(), (V)entry.getValue().getObj()) {

            @Override
            public V setValue(V value) {
                Publisher<V> publisher = map.put((K) entry.getKey().getObj(), value);
                return Mono.from(publisher).block();
            }

        };
    }

}
