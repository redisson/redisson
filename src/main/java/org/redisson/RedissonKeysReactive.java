/**
 * Copyright 2014 Nikita Koksharov, Nickolay Borbit
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
package org.redisson;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.decoder.ListScanResult;
import org.redisson.cluster.ClusterSlotRange;
import org.redisson.command.CommandReactiveService;
import org.redisson.core.RKeysReactive;

import reactor.rx.Stream;
import reactor.rx.Streams;
import reactor.rx.subscription.ReactiveSubscription;

public class RedissonKeysReactive implements RKeysReactive {

    private final CommandReactiveService commandExecutor;

    public RedissonKeysReactive(CommandReactiveService commandExecutor) {
        super();
        this.commandExecutor = commandExecutor;
    }

    @Override
    public Publisher<Integer> getSlot(String key) {
        return commandExecutor.readObservable(null, RedisCommands.KEYSLOT, key);
    }

    @Override
    public Publisher<String> getKeysByPattern(final String pattern) {
        List<Publisher<String>> publishers = new ArrayList<Publisher<String>>();
        for (ClusterSlotRange slot : commandExecutor.getConnectionManager().getEntries().keySet()) {
            publishers.add(createKeysIterator(slot.getStartSlot(), pattern));
        }
        return Streams.merge(publishers);
    }

    @Override
    public Publisher<String> getKeys() {
        return getKeysByPattern(null);
    }

    private Publisher<ListScanResult<String>> scanIterator(int slot, long startPos, String pattern) {
        if (pattern == null) {
            return commandExecutor.writeObservable(slot, StringCodec.INSTANCE, RedisCommands.SCAN, startPos);
        }
        return commandExecutor.writeObservable(slot, StringCodec.INSTANCE, RedisCommands.SCAN, startPos, "MATCH", pattern);
    }

    private Publisher<String> createKeysIterator(final int slot, final String pattern) {
        return new Stream<String>() {

            @Override
            public void subscribe(final Subscriber<? super String> t) {
                t.onSubscribe(new ReactiveSubscription<String>(this, t) {

                    private List<String> firstValues;
                    private long nextIterPos;
                    private InetSocketAddress client;

                    private long currentIndex;

                    @Override
                    protected void onRequest(final long n) {
                        currentIndex = n;
                        nextValues();
                    }

                    protected void nextValues() {
                        final ReactiveSubscription<String> m = this;
                        scanIterator(slot, nextIterPos, pattern).subscribe(new Subscriber<ListScanResult<String>>() {

                            @Override
                            public void onSubscribe(Subscription s) {
                                s.request(Long.MAX_VALUE);
                            }

                            @Override
                            public void onNext(ListScanResult<String> res) {
                                client = res.getRedisClient();

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
    public Publisher<String> randomKey() {
        return commandExecutor.readRandomObservable(RedisCommands.RANDOM_KEY);
    }

    /**
     * Delete multiple objects by a key pattern
     *
     *  Supported glob-style patterns:
     *    h?llo subscribes to hello, hallo and hxllo
     *    h*llo subscribes to hllo and heeeello
     *    h[ae]llo subscribes to hello and hallo, but not hillo
     *
     * @param pattern
     * @return
     */
    @Override
    public Publisher<Long> deleteByPattern(String pattern) {
        return commandExecutor.evalWriteAllObservable(RedisCommands.EVAL_LONG, new SlotCallback<Long, Long>() {
            AtomicLong results = new AtomicLong();
            @Override
            public void onSlotResult(Long result) {
                results.addAndGet(result);
            }

            @Override
            public Long onFinish() {
                return results.get();
            }
        }, "local keys = redis.call('keys', ARGV[1]) "
                + "local n = 0 "
                + "for i=1, table.getn(keys),5000 do "
                    + "n = n + redis.call('del', unpack(keys, i, math.min(i+4999, table.getn(keys)))) "
                + "end "
            + "return n;",Collections.emptyList(), pattern);
    }

    /**
     * Delete multiple objects by name
     *
     * @param keys - object names
     * @return
     */
    @Override
    public Publisher<Long> delete(String ... keys) {
        return commandExecutor.writeAllObservable(RedisCommands.DEL, new SlotCallback<Long, Long>() {
            AtomicLong results = new AtomicLong();
            @Override
            public void onSlotResult(Long result) {
                results.addAndGet(result);
            }

            @Override
            public Long onFinish() {
                return results.get();
            }
        }, (Object[])keys);
    }


}
