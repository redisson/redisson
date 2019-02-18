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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

import org.reactivestreams.Publisher;
import org.redisson.RedissonKeys;
import org.redisson.client.RedisClient;
import org.redisson.connection.MasterSlaveEntry;

import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonKeysReactive {

    private final CommandReactiveExecutor commandExecutor;

    private final RedissonKeys instance;

    public RedissonKeysReactive(CommandReactiveExecutor commandExecutor) {
        super();
        instance = new RedissonKeys(commandExecutor);
        this.commandExecutor = commandExecutor;
    }

    public Flux<String> getKeysByPattern(String pattern) {
        return getKeysByPattern(pattern, 10);
    }
    
    public Flux<String> getKeysByPattern(String pattern, int count) {
        List<Publisher<String>> publishers = new ArrayList<Publisher<String>>();
        for (MasterSlaveEntry entry : commandExecutor.getConnectionManager().getEntrySet()) {
            publishers.add(createKeysIterator(entry, pattern, count));
        }
        return Flux.merge(publishers);
    }

    private Flux<String> createKeysIterator(final MasterSlaveEntry entry, final String pattern, final int count) {
        return Flux.create(new Consumer<FluxSink<String>>() {
            
            @Override
            public void accept(FluxSink<String> emitter) {
                emitter.onRequest(new LongConsumer() {
                    
                    private RedisClient client;
                    private List<String> firstValues;
                    private long nextIterPos;
                    
                    private long currentIndex;
                    
                    @Override
                    public void accept(long value) {
                        currentIndex = value;
                        nextValues(emitter);
                    }
                    
                    protected void nextValues(FluxSink<String> emitter) {
                        instance.scanIteratorAsync(client, entry, nextIterPos, pattern, count).onComplete((res, e) -> {
                            if (e != null) {
                                emitter.error(e);
                                return;
                            }
                            
                            client = res.getRedisClient();
                            long prevIterPos = nextIterPos;
                            if (nextIterPos == 0 && firstValues == null) {
                                firstValues = (List<String>) (Object) res.getValues();
                            } else if (res.getValues().equals(firstValues)) {
                                emitter.complete();
                                currentIndex = 0;
                                return;
                            }

                            nextIterPos = res.getPos();
                            if (prevIterPos == nextIterPos) {
                                nextIterPos = -1;
                            }
                            for (Object val : res.getValues()) {
                                emitter.next((String) val);
                                currentIndex--;
                                if (currentIndex == 0) {
                                    emitter.complete();
                                    return;
                                }
                            }
                            if (nextIterPos == -1) {
                                emitter.complete();
                                currentIndex = 0;
                            }
                            
                            if (currentIndex == 0) {
                                return;
                            }
                            nextValues(emitter);
                        });
                    }

                });
            }
            

        });
    }

            }
