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

import java.util.AbstractMap;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

import org.redisson.RedissonMap;
import org.redisson.api.RFuture;
import org.redisson.client.RedisClient;
import org.redisson.client.protocol.decoder.MapScanResult;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import reactor.core.publisher.FluxSink;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <K> key type
 * @param <V> value type
 * @param <M> entry type
 */
public class MapReactiveIterator<K, V, M> implements Consumer<FluxSink<M>> {

    private final RedissonMap<K, V> map;
    private final String pattern;
    private final int count;

    public MapReactiveIterator(RedissonMap<K, V> map, String pattern, int count) {
        this.map = map;
        this.pattern = pattern;
        this.count = count;
    }
    
    @Override
    public void accept(FluxSink<M> emitter) {
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
            };
            
            protected void nextValues(FluxSink<M> emitter) {
                        scanIterator(client, nextIterPos).addListener(new FutureListener<MapScanResult<Object, Object>>() {

                            @Override
                            public void operationComplete(Future<MapScanResult<Object, Object>> future)
                                    throws Exception {
                                    if (!future.isSuccess()) {
                                        emitter.error(future.cause());
                                        return;
                                    }
    
                                    if (finished) {
                                        client = null;
                                        nextIterPos = 0;
                                        return;
                                    }

                                    MapScanResult<Object, Object> res = future.getNow();
                                    client = res.getRedisClient();
                                    nextIterPos = res.getPos();
                                    
                                    for (Entry<Object, Object> entry : res.getMap().entrySet()) {
                                        M val = getValue(entry);
                                        emitter.next(val);
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

    M getValue(final Entry<Object, Object> entry) {
        return (M)new AbstractMap.SimpleEntry<K, V>((K)entry.getKey(), (V)entry.getValue()) {

            @Override
            public V setValue(V value) {
                return map.put((K) entry.getKey(), value);
            }

        };
    }

    public RFuture<MapScanResult<Object, Object>> scanIterator(RedisClient client, long nextIterPos) {
        return map.scanIteratorAsync(map.getName(), client, nextIterPos, pattern, count);
    }

}
