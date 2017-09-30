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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.redisson.RedissonKeys;
import org.redisson.api.RFuture;
import org.redisson.api.RKeysReactive;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.decoder.ListScanResult;
import org.redisson.command.CommandReactiveService;
import org.redisson.connection.MasterSlaveEntry;

import reactor.fn.Supplier;
import reactor.rx.Stream;
import reactor.rx.Streams;
import reactor.rx.subscription.ReactiveSubscription;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonKeysReactive implements RKeysReactive {

    private final CommandReactiveService commandExecutor;

    private final RedissonKeys instance;

    public RedissonKeysReactive(CommandReactiveService commandExecutor) {
        super();
        instance = new RedissonKeys(commandExecutor);
        this.commandExecutor = commandExecutor;
    }

    @Override
    public Publisher<Integer> getSlot(final String key) {
        return commandExecutor.reactive(new Supplier<RFuture<Integer>>() {
            @Override
            public RFuture<Integer> get() {
                return instance.getSlotAsync(key);
            }
        });
    }

    @Override
    public Publisher<String> getKeysByPattern(final String pattern) {
        List<Publisher<String>> publishers = new ArrayList<Publisher<String>>();
        for (MasterSlaveEntry entry : commandExecutor.getConnectionManager().getEntrySet()) {
            publishers.add(createKeysIterator(entry, pattern));
        }
        return Streams.merge(publishers);
    }

    @Override
    public Publisher<String> getKeys() {
        return getKeysByPattern(null);
    }

    private Publisher<ListScanResult<String>> scanIterator(MasterSlaveEntry entry, long startPos, String pattern) {
        if (pattern == null) {
            return commandExecutor.writeReactive(entry, StringCodec.INSTANCE, RedisCommands.SCAN, startPos);
        }
        return commandExecutor.writeReactive(entry, StringCodec.INSTANCE, RedisCommands.SCAN, startPos, "MATCH", pattern);
    }

    private Publisher<String> createKeysIterator(final MasterSlaveEntry entry, final String pattern) {
        return new Stream<String>() {

            @Override
            public void subscribe(final Subscriber<? super String> t) {
                t.onSubscribe(new ReactiveSubscription<String>(this, t) {

                    private List<String> firstValues;
                    private long nextIterPos;

                    private long currentIndex;

                    @Override
                    protected void onRequest(final long n) {
                        currentIndex = n;
                        nextValues();
                    }

                    protected void nextValues() {
                        final ReactiveSubscription<String> m = this;
                        scanIterator(entry, nextIterPos, pattern).subscribe(new Subscriber<ListScanResult<String>>() {

                            @Override
                            public void onSubscribe(Subscription s) {
                                s.request(Long.MAX_VALUE);
                            }

                            @Override
                            public void onNext(ListScanResult<String> res) {
                                long prevIterPos = nextIterPos;
                                if (nextIterPos == 0 && firstValues == null) {
                                    firstValues = res.getValues();
                                } else if (res.getValues().equals(firstValues)) {
                                    m.onComplete();
                                    currentIndex = 0;
                                    return;
                                }

                                nextIterPos = res.getPos();
                                if (prevIterPos == nextIterPos) {
                                    nextIterPos = -1;
                                }
                                for (String val : res.getValues()) {
                                    m.onNext(val);
                                    currentIndex--;
                                    if (currentIndex == 0) {
                                        m.onComplete();
                                        return;
                                    }
                                }
                                if (nextIterPos == -1) {
                                    m.onComplete();
                                    currentIndex = 0;
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

    @Override
    public Publisher<Collection<String>> findKeysByPattern(final String pattern) {
        return commandExecutor.reactive(new Supplier<RFuture<Collection<String>>>() {
            @Override
            public RFuture<Collection<String>> get() {
                return instance.findKeysByPatternAsync(pattern);
            }
        });
    }

    @Override
    public Publisher<String> randomKey() {
        return commandExecutor.reactive(new Supplier<RFuture<String>>() {
            @Override
            public RFuture<String> get() {
                return instance.randomKeyAsync();
            }
        });
    }

    @Override
    public Publisher<Long> deleteByPattern(final String pattern) {
        return commandExecutor.reactive(new Supplier<RFuture<Long>>() {
            @Override
            public RFuture<Long> get() {
                return instance.deleteByPatternAsync(pattern);
            }
        });
    }

    @Override
    public Publisher<Long> delete(final String ... keys) {
        return commandExecutor.reactive(new Supplier<RFuture<Long>>() {
            @Override
            public RFuture<Long> get() {
                return instance.deleteAsync(keys);
            }
        });
    }

    @Override
    public Publisher<Void> flushdb() {
        return commandExecutor.reactive(new Supplier<RFuture<Void>>() {
            @Override
            public RFuture<Void> get() {
                return instance.flushdbAsync();
            }
        });
    }

    @Override
    public Publisher<Void> flushall() {
        return commandExecutor.reactive(new Supplier<RFuture<Void>>() {
            @Override
            public RFuture<Void> get() {
                return instance.flushallAsync();
            }
        });
    }

}
