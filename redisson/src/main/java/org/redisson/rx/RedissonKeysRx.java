/**
 * Copyright (c) 2013-2024 Nikita Koksharov
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
import org.redisson.connection.MasterSlaveEntry;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.functions.LongConsumer;
import io.reactivex.rxjava3.processors.ReplayProcessor;

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

    public Flowable<String> getKeys() {
        return getKeysByPattern(null);
    }

    public Flowable<String> getKeys(int count) {
        return getKeysByPattern(null, count);
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

    private Publisher<String> createKeysIterator(MasterSlaveEntry entry, String pattern, int count) {
        ReplayProcessor<String> p = ReplayProcessor.create();
        return p.doOnRequest(new LongConsumer() {

            private RedisClient client;
            private List<String> firstValues;
            private String nextIterPos = "0";
            
            private long currentIndex;
            
            @Override
            public void accept(long value) {
                currentIndex = value;
                nextValues();
            }
            
            protected void nextValues() {
                instance.scanIteratorAsync(client, entry, nextIterPos, pattern, count).whenComplete((res, e) -> {
                    if (e != null) {
                        p.onError(e);
                        return;
                    }
                    
                    client = res.getRedisClient();
                    String prevIterPos = nextIterPos;
                    if ("0".equals(nextIterPos) && firstValues == null) {
                        firstValues = (List<String>) (Object) res.getValues();
                    } else if (res.getValues().equals(firstValues)) {
                        p.onComplete();
                        currentIndex = 0;
                        return;
                    }

                    nextIterPos = res.getPos();
                    if (prevIterPos.equals(nextIterPos)) {
                        nextIterPos = "-1";
                    }
                    for (Object val : res.getValues()) {
                        p.onNext((String) val);
                        currentIndex--;
                        if (currentIndex == 0) {
                            p.onComplete();
                            return;
                        }
                    }
                    if ("-1".equals(nextIterPos)) {
                        p.onComplete();
                        currentIndex = 0;
                    }
                    
                    if (currentIndex == 0) {
                        return;
                    }
                    nextValues();
                });
            }
        });
    }

}
