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

import java.util.ArrayList;
import java.util.List;

import org.reactivestreams.Publisher;
import org.redisson.RedissonKeys;
import org.redisson.client.RedisClient;
import org.redisson.client.protocol.decoder.ListScanResult;
import org.redisson.connection.MasterSlaveEntry;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.reactivex.Flowable;
import io.reactivex.functions.LongConsumer;
import io.reactivex.processors.ReplayProcessor;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonKeysRx {

    private final CommandRxExecutor commandExecutor;

    private final RedissonKeys instance;

    public RedissonKeysRx(CommandRxExecutor commandExecutor) {
        instance = new RedissonKeys(commandExecutor);
        this.commandExecutor = commandExecutor;
    }

    public Flowable<String> getKeysByPattern(String pattern) {
        return getKeysByPattern(pattern, 10);
    }
    
    public Flowable<String> getKeysByPattern(String pattern, int count) {
        List<Publisher<String>> publishers = new ArrayList<Publisher<String>>();
        for (MasterSlaveEntry entry : commandExecutor.getConnectionManager().getEntrySet()) {
            publishers.add(createKeysIterator(entry, pattern, count));
        }
        return Flowable.merge(publishers);
    }

    private Publisher<String> createKeysIterator(final MasterSlaveEntry entry, final String pattern, final int count) {
        final ReplayProcessor<String> p = ReplayProcessor.create();
        return p.doOnRequest(new LongConsumer() {

            private RedisClient client;
            private List<String> firstValues;
            private long nextIterPos;
            
            private long currentIndex;
            
            @Override
            public void accept(long value) {
                currentIndex = value;
                nextValues();
            }
            
            protected void nextValues() {
                instance.scanIteratorAsync(client, entry, nextIterPos, pattern, count).addListener(new FutureListener<ListScanResult<Object>>() {

                    @Override
                    public void operationComplete(Future<ListScanResult<Object>> future) throws Exception {
                        if (!future.isSuccess()) {
                            p.onError(future.cause());
                            return;
                        }
                        
                        ListScanResult<Object> res = future.get();
                        client = res.getRedisClient();
                        long prevIterPos = nextIterPos;
                        if (nextIterPos == 0 && firstValues == null) {
                            firstValues = (List<String>)(Object)res.getValues();
                        } else if (res.getValues().equals(firstValues)) {
                            p.onComplete();
                            currentIndex = 0;
                            return;
                        }

                        nextIterPos = res.getPos();
                        if (prevIterPos == nextIterPos) {
                            nextIterPos = -1;
                        }
                        for (Object val : res.getValues()) {
                            p.onNext((String)val);
                            currentIndex--;
                            if (currentIndex == 0) {
                                p.onComplete();
                                return;
                            }
                        }
                        if (nextIterPos == -1) {
                            p.onComplete();
                            currentIndex = 0;
                        }
                        
                        if (currentIndex == 0) {
                            return;
                        }
                        nextValues();
                    }
                    
                });
            }
        });
    }

}
