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
package org.redisson.rx;

import java.util.AbstractMap;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicLong;

import org.redisson.RedissonMap;
import org.redisson.client.RedisClient;
import org.redisson.client.protocol.decoder.MapScanResult;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.reactivex.Flowable;
import io.reactivex.functions.LongConsumer;
import io.reactivex.processors.ReplayProcessor;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <K> key type
 * @param <V> value type
 * @param <M> entry type
 */
public class RedissonMapRxIterator<K, V, M> {

    private final RedissonMap<K, V> map;
    private final String pattern;
    private final int count;

    public RedissonMapRxIterator(RedissonMap<K, V> map, String pattern, int count) {
        this.map = map;
        this.pattern = pattern;
        this.count = count;
    }
    
    public Flowable<M> create() {
        final ReplayProcessor<M> p = ReplayProcessor.create();
        return p.doOnRequest(new LongConsumer() {

            private long nextIterPos;
            private RedisClient client;
            private AtomicLong elementsRead = new AtomicLong();
            
            private boolean finished;
            private volatile boolean completed;
            private AtomicLong readAmount = new AtomicLong();
            
            @Override
            public void accept(long value) throws Exception {
                readAmount.addAndGet(value);
                if (completed || elementsRead.get() == 0) {
                    nextValues();
                    completed = false;
                }
            };
            
            protected void nextValues() {
                map.scanIteratorAsync(map.getName(), client, nextIterPos, pattern, count).addListener(new FutureListener<MapScanResult<Object, Object>>() {

                    @Override
                    public void operationComplete(Future<MapScanResult<Object, Object>> future)
                            throws Exception {
                            if (!future.isSuccess()) {
                                p.onError(future.cause());
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
                                p.onNext(val);
                                elementsRead.incrementAndGet();
                            }
                            
                            if (elementsRead.get() >= readAmount.get()) {
                                p.onComplete();
                                elementsRead.set(0);
                                completed = true;
                                return;
                            }
                            if (res.getPos() == 0 && !tryAgain()) {
                                finished = true;
                                p.onComplete();
                            }
                            
                            if (finished || completed) {
                                return;
                            }
                            nextValues();
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

}
