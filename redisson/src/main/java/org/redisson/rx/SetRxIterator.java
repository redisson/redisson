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

import java.util.concurrent.atomic.AtomicLong;

import org.redisson.api.RFuture;
import org.redisson.client.RedisClient;
import org.redisson.client.protocol.decoder.ListScanResult;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.reactivex.Flowable;
import io.reactivex.functions.LongConsumer;
import io.reactivex.processors.ReplayProcessor;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <V> value type
 */
public abstract class SetRxIterator<V> {

    public Flowable<V> create() {
        final ReplayProcessor<V> p = ReplayProcessor.create();
        return p.doOnRequest(new LongConsumer() {
            
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
                    nextValues();
                    completed = false;
                }
            }
            
            protected void nextValues() {
                scanIterator(client, nextIterPos).addListener(new FutureListener<ListScanResult<Object>>() {
                    @Override
                    public void operationComplete(Future<ListScanResult<Object>> future) throws Exception {
                        if (!future.isSuccess()) {
                            p.onError(future.cause());
                            return;
                        }
                        
                        if (finished) {
                            client = null;
                            nextIterPos = 0;
                            return;
                        }

                        ListScanResult<Object> res = future.getNow();
                        client = res.getRedisClient();
                        nextIterPos = res.getPos();

                        for (Object val : res.getValues()) {
                            p.onNext((V)val);
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

    protected abstract RFuture<ListScanResult<Object>> scanIterator(RedisClient client, long nextIterPos);

}
