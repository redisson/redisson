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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import java.util.function.Supplier;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.redisson.RedissonKeys;
import org.redisson.api.RFuture;
import org.redisson.api.RKeysReactive;
import org.redisson.api.RType;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.decoder.ListScanResult;
import org.redisson.command.CommandReactiveService;
import org.redisson.connection.MasterSlaveEntry;

import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

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
    public Publisher<String> getKeysByPattern(String pattern) {
        return getKeysByPattern(pattern, 10);
    }
    
    @Override
    public Publisher<String> getKeysByPattern(String pattern, int count) {
        List<Publisher<String>> publishers = new ArrayList<Publisher<String>>();
        for (MasterSlaveEntry entry : commandExecutor.getConnectionManager().getEntrySet()) {
            publishers.add(createKeysIterator(entry, pattern, count));
        }
        return Flux.merge(publishers);
    }

    @Override
    public Publisher<String> getKeys() {
        return getKeysByPattern(null);
    }

    @Override
    public Publisher<String> getKeys(int count) {
        return getKeysByPattern(null, count);
    }

    private Publisher<ListScanResult<String>> scanIterator(MasterSlaveEntry entry, long startPos, String pattern, int count) {
        if (pattern == null) {
            return commandExecutor.writeReactive(entry, StringCodec.INSTANCE, RedisCommands.SCAN, startPos, "COUNT", count);
        }
        return commandExecutor.writeReactive(entry, StringCodec.INSTANCE, RedisCommands.SCAN, startPos, "MATCH", pattern, "COUNT", count);
    }

    private Publisher<String> createKeysIterator(final MasterSlaveEntry entry, final String pattern, final int count) {
        return Flux.create(new Consumer<FluxSink<String>>() {
            
            @Override
            public void accept(FluxSink<String> emitter) {
                emitter.onRequest(new LongConsumer() {
                    private List<String> firstValues;
                    private long nextIterPos;
                    
                    private long currentIndex;
                    
                    @Override
                    public void accept(long value) {
                        currentIndex = value;
                        nextValues(emitter);
                    }
                    
                    protected void nextValues(FluxSink<String> emitter) {
                        scanIterator(entry, nextIterPos, pattern, count).subscribe(new Subscriber<ListScanResult<String>>() {

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
                                    emitter.complete();
                                    currentIndex = 0;
                                    return;
                                }

                                nextIterPos = res.getPos();
                                if (prevIterPos == nextIterPos) {
                                    nextIterPos = -1;
                                }
                                for (String val : res.getValues()) {
                                    emitter.next(val);
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
                            }

                            @Override
                            public void onError(Throwable error) {
                                emitter.error(error);
                            }

                            @Override
                            public void onComplete() {
                                if (currentIndex == 0) {
                                    return;
                                }
                                nextValues(emitter);
                            }
                        });
                    }

                });
            }
            

        });
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

    @Override
    public Publisher<Boolean> move(final String name, final int database) {
        return commandExecutor.reactive(new Supplier<RFuture<Boolean>>() {
            @Override
            public RFuture<Boolean> get() {
                return instance.moveAsync(name, database);
}
        });
    }

    @Override
    public Publisher<Void> migrate(final String name, final String host, final int port, final int database, final long timeout) {
        return commandExecutor.reactive(new Supplier<RFuture<Void>>() {
            @Override
            public RFuture<Void> get() {
                return instance.migrateAsync(name, host, port, database, timeout);
            }
        });
    }

    @Override
    public Publisher<Void> copy(final String name, final String host, final int port, final int database, final long timeout) {
        return commandExecutor.reactive(new Supplier<RFuture<Void>>() {
            @Override
            public RFuture<Void> get() {
                return instance.copyAsync(name, host, port, database, timeout);
            }
        });
    }

    @Override
    public Publisher<Boolean> expire(final String name, final long timeToLive, final TimeUnit timeUnit) {
        return commandExecutor.reactive(new Supplier<RFuture<Boolean>>() {
            @Override
            public RFuture<Boolean> get() {
                return instance.expireAsync(name, timeToLive, timeUnit);
            }
        });
    }

    @Override
    public Publisher<Boolean> expireAt(final String name, final long timestamp) {
        return commandExecutor.reactive(new Supplier<RFuture<Boolean>>() {
            @Override
            public RFuture<Boolean> get() {
                return instance.expireAtAsync(name, timestamp);
            }
        });
    }

    @Override
    public Publisher<Boolean> clearExpire(final String name) {
        return commandExecutor.reactive(new Supplier<RFuture<Boolean>>() {
            @Override
            public RFuture<Boolean> get() {
                return instance.clearExpireAsync(name);
            }
        });
    }

    @Override
    public Publisher<Boolean> renamenx(final String oldName, final String newName) {
        return commandExecutor.reactive(new Supplier<RFuture<Boolean>>() {
            @Override
            public RFuture<Boolean> get() {
                return instance.renamenxAsync(oldName, newName);
            }
        });
    }

    @Override
    public Publisher<Void> rename(final String currentName, final String newName) {
        return commandExecutor.reactive(new Supplier<RFuture<Void>>() {
            @Override
            public RFuture<Void> get() {
                return instance.renameAsync(currentName, newName);
            }
        });
    }

    @Override
    public Publisher<Long> remainTimeToLive(final String name) {
        return commandExecutor.reactive(new Supplier<RFuture<Long>>() {
            @Override
            public RFuture<Long> get() {
                return instance.remainTimeToLiveAsync(name);
            }
        });
    }

    @Override
    public Publisher<Long> touch(final String... names) {
        return commandExecutor.reactive(new Supplier<RFuture<Long>>() {
            @Override
            public RFuture<Long> get() {
                return instance.touchAsync(names);
            }
        });
    }

    @Override
    public Publisher<Long> countExists(final String... names) {
        return commandExecutor.reactive(new Supplier<RFuture<Long>>() {
            @Override
            public RFuture<Long> get() {
                return instance.countExistsAsync(names);
            }
        });
    }

    @Override
    public Publisher<RType> getType(final String key) {
        return commandExecutor.reactive(new Supplier<RFuture<RType>>() {
            @Override
            public RFuture<RType> get() {
                return instance.getTypeAsync(key);
            }
        });
    }

    @Override
    public Publisher<Long> unlink(final String... keys) {
        return commandExecutor.reactive(new Supplier<RFuture<Long>>() {
            @Override
            public RFuture<Long> get() {
                return instance.unlinkAsync(keys);
            }
        });
    }

    @Override
    public Publisher<Long> count() {
        return commandExecutor.reactive(new Supplier<RFuture<Long>>() {
            @Override
            public RFuture<Long> get() {
                return instance.countAsync();
            }
        });
    }

}
